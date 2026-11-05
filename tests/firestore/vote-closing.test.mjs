// Run with: firebase emulators:exec --only firestore --project demo-roamigo 'node --test tests/firestore/vote-closing.test.mjs'
import { test } from 'node:test';
import assert from 'node:assert/strict';

const project = 'demo-roamigo';
const host = process.env.FIRESTORE_EMULATOR_HOST;
assert.ok(host, 'Run this suite through the Firestore emulator');
const base = `http://${host}/v1/projects/${project}/databases/(default)/documents`;
const str = stringValue => ({ stringValue });
const timestamp = { timestampValue: '2026-11-04T12:00:00Z' };
const tripId = `vote-closing-${Date.now()}`;
const path = `trips/${tripId}/votes/dinner`;
const vote = {
  uid: str('dinner'), tripId: str(tripId), question: str('Dinner?'),
  createdByUserId: str('creator'), status: str('OPEN'), creationTime: timestamp,
  closingTime: { nullValue: null }, winningOptionId: { nullValue: null },
  options: { arrayValue: { values: ['a', 'b'].map(uid => ({ mapValue: { fields: { uid: str(uid), name: str(uid) } } })) } },
};

// Unsigned emulator-only ID tokens; never sent to a production Firebase endpoint.
function token(uid) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: uid, user_id: uid, aud: project, iss: `https://securetoken.google.com/${project}`, iat: now, exp: now + 3600, firebase: { sign_in_provider: 'custom', identities: {} } })}.`;
}
async function write(document, fields, user = 'owner', mask = []) {
  const query = new URLSearchParams(mask.map(field => ['updateMask.fieldPaths', field]));
  const response = await fetch(`${base}/${document}?${query}`, {
    method: 'PATCH', headers: { Authorization: `Bearer ${user === 'owner' ? 'owner' : token(user)}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ fields }),
  });
  return { status: response.status, body: await response.text() };
}
async function expectWrite(expected, ...args) {
  const response = await write(...args);
  assert.equal(response.status, expected, response.body);
}
const ballot = (user, choice) => ({ userId: str(user), optionId: str(choice), updatedAt: timestamp });

test('creator-only status transition finalizes persisted ballots and rejects later ballot writes', async () => {
  await expectWrite(200, `trips/${tripId}`, { memberIds: { arrayValue: { values: ['creator', 'member'].map(str) } } });
  await expectWrite(200, path, vote, 'creator');
  await expectWrite(200, `${path}/ballots/member`, ballot('member', 'a'), 'member');
  await expectWrite(200, `${path}/ballots/member`, ballot('member', 'b'), 'member');
  await expectWrite(403, path, { status: str('CLOSED') }, 'member', ['status']);
  await expectWrite(403, path, { status: str('CLOSED') }, 'outsider', ['status']);
  await expectWrite(403, path, { status: str('CLOSED'), question: str('Changed') }, 'creator', ['status', 'question']);
  await expectWrite(200, path, { status: str('CLOSED') }, 'creator', ['status']);
  const read = await fetch(`${base}/${path}`, { headers: { Authorization: `Bearer ${token('member')}` } });
  assert.equal(read.status, 200);
  const persisted = await read.json();
  assert.deepEqual(persisted.fields, { ...vote, status: str('CLOSED') });
  await expectWrite(403, `${path}/ballots/creator`, ballot('creator', 'a'), 'creator');
  await expectWrite(403, `${path}/ballots/member`, ballot('member', 'a'), 'member');
  await expectWrite(403, path, { status: str('OPEN') }, 'creator', ['status']);
  await expectWrite(403, path, { status: str('CLOSED') }, 'creator', ['status']);
});
