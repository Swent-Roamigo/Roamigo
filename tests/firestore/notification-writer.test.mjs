// AI assistance was used for implementation support.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {createRequire} from 'node:module';
import {test} from 'node:test';

const project = process.env.GCLOUD_PROJECT ?? 'demo-roamigo';
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
assert.ok(authHost && firestoreHost, 'Run through the Firestore and Auth emulators');

const require = createRequire(new URL('../../functions/package.json', import.meta.url));
const {initializeApp} = require('firebase-admin/app');
const {getFirestore, Timestamp} = require('firebase-admin/firestore');
const {createNotification} = require('./lib/notifications.js');
const db = getFirestore(initializeApp({projectId: project}));

const requestEvent = {
  type: 'FRIEND_REQUEST_RECEIVED',
  requestId: 'request-id',
  senderUserId: 'sender',
};
const sourceIds = ['friendship-id', requestEvent.requestId];
const inbox = recipient => db.collection(`users/${recipient}/notifications`);

test('concurrent creation and replay preserve the original notification', async () => {
  const signUp = await fetch(
    `http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator`,
    {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({returnSecureToken: true}),
    },
  );
  assert.equal(signUp.status, 200, await signUp.clone().text());
  const {localId: recipient, idToken} = await signUp.json();

  await Promise.all([
    createNotification(db, recipient, sourceIds, requestEvent),
    createNotification(db, recipient, sourceIds, requestEvent),
  ]);
  const created = await inbox(recipient).get();
  assert.equal(created.size, 1);
  const [entry] = created.docs;
  const original = entry.data();
  assert.ok(original.createdAt instanceof Timestamp);
  assert.deepEqual(original, {
    ...requestEvent, recipientUserId: recipient, createdAt: original.createdAt, isRead: false,
  });

  const markRead = await fetch(
    `http://${firestoreHost}/v1/projects/${project}/databases/(default)/documents/${entry.ref.path}?updateMask.fieldPaths=isRead`,
    {
      method: 'PATCH',
      headers: {'Content-Type': 'application/json', Authorization: `Bearer ${idToken}`},
      body: JSON.stringify({fields: {isRead: {booleanValue: true}}}),
    },
  );
  assert.equal(markRead.status, 200, await markRead.text());

  await createNotification(db, recipient, sourceIds, requestEvent);
  const replayed = await inbox(recipient).get();
  assert.equal(replayed.size, 1);
  assert.equal(replayed.docs[0].id, entry.id);
  assert.deepEqual(replayed.docs[0].data(), {...original, isRead: true});
});

test('event identity distinguishes request attempts, source documents, and recipient inboxes', async () => {
  const recipient = randomUUID();
  const other = randomUUID();
  const newRequest = {...requestEvent, requestId: 'new-request-id'};
  await Promise.all([
    createNotification(db, recipient, sourceIds, requestEvent),
    createNotification(db, recipient, [sourceIds[0], newRequest.requestId], newRequest),
    createNotification(db, recipient, ['other-friendship', requestEvent.requestId], requestEvent),
    createNotification(db, other, sourceIds, requestEvent),
  ]);

  const notifications = await inbox(recipient).get();
  assert.deepEqual(notifications.docs.map(entry => entry.get('requestId')).sort(), [
    newRequest.requestId, requestEvent.requestId, requestEvent.requestId,
  ]);
  const otherInbox = await inbox(other).get();
  assert.equal(otherInbox.size, 1);
  assert.equal(otherInbox.docs[0].get('recipientUserId'), other);
});

test('a write failure other than an existing document propagates to the caller', async () => {
  const unavailable = Object.assign(new Error('Firestore unavailable'), {code: 14});
  const failingDb = {doc: () => ({create: async () => { throw unavailable; }})};
  await assert.rejects(
    createNotification(failingDb, 'recipient', sourceIds, requestEvent),
    error => error === unavailable,
  );
});
