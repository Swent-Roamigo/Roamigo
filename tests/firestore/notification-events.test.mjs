// AI assistance was used for implementation support.
import assert from 'node:assert/strict';
import {createHash, randomUUID} from 'node:crypto';
import {createRequire} from 'node:module';
import {test} from 'node:test';
import {setTimeout as delay} from 'node:timers/promises';

const project = process.env.GCLOUD_PROJECT ?? 'demo-roamigo';
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
assert.ok(authHost && firestoreHost, 'Run through the local Firebase emulators');

const require = createRequire(new URL('../../functions/package.json', import.meta.url));
const {initializeApp} = require('firebase-admin/app');
const {getFirestore, Timestamp} = require('firebase-admin/firestore');
const {notifyFriendRequest} = require('./lib/notifications.js');
const db = getFirestore(initializeApp({projectId: project}));
const database = `projects/${project}/databases/(default)`;
const documents = `http://${firestoreHost}/v1/${database}/documents`;

async function account() {
  const response = await fetch(
    `http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator`,
    {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({returnSecureToken: true}),
    },
  );
  assert.equal(response.status, 200, await response.clone().text());
  const {localId: uid, idToken: token} = await response.json();
  return {uid, token};
}

function friendshipPath(sender, recipient) {
  const id = [sender.uid, recipient.uid]
    .map(uid => createHash('sha256').update(uid).digest('hex'))
    .sort()
    .join('_');
  return `${database}/documents/friendships/${id}`;
}

async function commit(write, actor) {
  const response = await fetch(`${documents}:commit`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${actor.token}`,
    },
    body: JSON.stringify({writes: [write]}),
  });
  assert.equal(response.status, 200, await response.text());
}

function sendRequest(name, sender, recipient, requestId) {
  return commit({
    update: {
      name,
      fields: {
        requestId: {stringValue: requestId},
        senderUserId: {stringValue: sender.uid},
        recipientUserId: {stringValue: recipient.uid},
        participantIds: {
          arrayValue: {values: [{stringValue: sender.uid}, {stringValue: recipient.uid}]},
        },
        status: {stringValue: 'PENDING'},
        respondedAt: {nullValue: null},
      },
    },
    updateTransforms: [{fieldPath: 'createdAt', setToServerValue: 'REQUEST_TIME'}],
  }, sender);
}

function declineRequest(name, recipient) {
  return commit({
    update: {name, fields: {status: {stringValue: 'DECLINED'}}},
    updateMask: {fieldPaths: ['status']},
    updateTransforms: [{fieldPath: 'respondedAt', setToServerValue: 'REQUEST_TIME'}],
  }, recipient);
}

const inbox = uid => db.collection(`users/${uid}/notifications`);

async function waitForInboxSize(uid, count) {
  const deadline = Date.now() + 20_000;
  do {
    const snapshot = await inbox(uid).get();
    if (snapshot.size >= count) return snapshot;
    await delay(100);
  } while (Date.now() < deadline);
  assert.fail(`Expected ${count} inbox entries before the trigger timed out`);
}

test('authenticated friendship writes create an entry for each new request', async () => {
  const [sender, recipient] = await Promise.all([account(), account()]);
  const name = friendshipPath(sender, recipient);
  await sendRequest(name, sender, recipient, 'first-request');

  const first = await waitForInboxSize(recipient.uid, 1);
  assert.equal(first.size, 1);
  const createdAt = first.docs[0].get('createdAt');
  assert.ok(createdAt instanceof Timestamp);
  assert.deepEqual(first.docs[0].data(), {
    requestId: 'first-request',
    type: 'FRIEND_REQUEST_RECEIVED',
    senderUserId: sender.uid,
    recipientUserId: recipient.uid,
    createdAt,
    isRead: false,
  });

  await declineRequest(name, recipient);
  await sendRequest(name, sender, recipient, 'second-request');

  const reissued = await waitForInboxSize(recipient.uid, 2);
  assert.deepEqual(reissued.docs.map(entry => entry.get('requestId')).sort(), [
    'first-request', 'second-request',
  ]);
  assert.equal((await inbox(sender.uid).get()).size, 0);
});

test('responses, unchanged requests, deletions, and malformed requests are ignored', async () => {
  const recipientUserId = randomUUID();
  const pending = {status: 'PENDING', requestId: 'request-id', senderUserId: 'sender', recipientUserId};
  const declined = {...pending, status: 'DECLINED'};
  const documentId = randomUUID();

  // Run each ignored transition to completion before asserting its lack of side effects.
  for (const [before, after] of [
    [pending, {...pending, status: 'ACCEPTED'}],
    [pending, declined],
    [pending, pending],
    [declined, pending],
    [pending, undefined],
    [undefined, {...pending, senderUserId: ''}],
    [undefined, {...pending, senderUserId: recipientUserId}],
  ]) {
    await notifyFriendRequest.run({
      params: {documentId},
      data: {before: {data: () => before}, after: {data: () => after}},
    });
  }
  assert.equal((await inbox(recipientUserId).get()).size, 0);
});
