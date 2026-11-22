// AI assistance was used for implementation support.

import assert from 'node:assert/strict';
import {before, test} from 'node:test';

const project = process.env.GCLOUD_PROJECT ?? 'demo-roamigo';
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
assert.ok(firestoreHost && authHost, 'Run through the Firestore and Auth emulators');

const documents = `http://${firestoreHost}/v1/projects/${project}/databases/(default)/documents`;

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

async function expectStatus(status, method, path, token, fields) {
  const response = await fetch(`${documents}/${path}`, {
    method,
    headers: {
      ...(token && {Authorization: `Bearer ${token}`}),
      'Content-Type': 'application/json',
    },
    ...(fields && {body: JSON.stringify({fields})}),
  });
  const text = await response.text();
  assert.equal(response.status, status, text);
  return text ? JSON.parse(text) : null;
}

let recipient;
let other;
let inbox;
let entry;
let fields;
const markRead = {isRead: {booleanValue: true}};

before(async () => {
  [recipient, other] = await Promise.all([account(), account()]);
  inbox = `users/${recipient.uid}/notifications`;
  entry = `${inbox}/friend-request`;
  fields = {
    recipientUserId: {stringValue: recipient.uid},
    type: {stringValue: 'FRIEND_REQUEST_RECEIVED'},
    requestId: {stringValue: 'request-id'},
    senderUserId: {stringValue: other.uid},
    createdAt: {timestampValue: '2026-11-22T09:00:00Z'},
    isRead: {booleanValue: false},
  };
  // The emulator's Admin token seeds inbox entries independently of client rules.
  await expectStatus(200, 'PATCH', entry, 'owner', fields);
  await expectStatus(200, 'PATCH', `${inbox}/trip-vote`, 'owner', {
    recipientUserId: fields.recipientUserId,
    type: {stringValue: 'TRIP_VOTE_CREATED'},
    tripId: {stringValue: 'trip-id'},
    voteId: {stringValue: 'vote-id'},
    createdAt: {timestampValue: '2026-11-22T10:00:00Z'},
    isRead: fields.isRead,
  });
});

test('recipient can list their newest notifications, retrieve one, and mark it read', async () => {
  const list = await expectStatus(200, 'GET', `${inbox}?orderBy=createdAt%20desc`, recipient.token);
  assert.deepEqual(list.documents.map(document => document.name.split('/').at(-1)), [
    'trip-vote',
    'friend-request',
  ]);
  const beforeRead = await expectStatus(200, 'GET', entry, recipient.token);
  assert.equal(beforeRead.fields.isRead.booleanValue, false);

  const update = `${entry}?updateMask.fieldPaths=isRead`;
  await expectStatus(200, 'PATCH', update, recipient.token, markRead);
  await expectStatus(200, 'PATCH', update, recipient.token, markRead);
  await expectStatus(403, 'PATCH', update, recipient.token, {isRead: fields.isRead});
  const afterRead = await expectStatus(200, 'GET', entry, recipient.token);
  assert.deepEqual(afterRead.fields, {...fields, ...markRead});
});

test('another user and a signed-out client cannot read or mark an entry', async () => {
  for (const token of [other.token, undefined]) {
    await expectStatus(403, 'GET', entry, token);
    await expectStatus(403, 'GET', `${inbox}?orderBy=createdAt%20desc`, token);
    await expectStatus(403, 'PATCH', `${entry}?updateMask.fieldPaths=isRead`, token, markRead);
  }
});

test('recipient cannot create or delete entries or change payloads', async () => {
  await expectStatus(403, 'PATCH', `${inbox}/client-write`, recipient.token, fields);
  await expectStatus(403, 'DELETE', entry, recipient.token);
  await expectStatus(403, 'PATCH', entry, recipient.token, {
    ...fields,
    ...markRead,
    senderUserId: {stringValue: 'different-sender'},
  });
});
