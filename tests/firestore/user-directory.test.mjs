// AI assistance was used for implementation support and code review.
// Runs against the local Firebase Firestore emulator.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';

const host = process.env.FIRESTORE_EMULATOR_HOST;
assert.ok(host, 'Run this test through the Firestore emulator');

const project = 'demo-roamigo';
const base =
  `http://${host}/v1/projects/${project}/databases/(default)/documents`;

const owner = `directory-${randomUUID()}`;
const other = `visitor-${randomUUID()}`;
const email = `Test.${randomUUID()}@Example.org`;

const normalize = value => value.trim().toLowerCase();

const hash = value =>
  createHash('sha256')
    .update(normalize(value))
    .digest('hex');

const index = `userEmailLookups/${hash(email)}`;
const str = value => ({ stringValue: value });

function token(uid, address = undefined, verified = false) {
  const encode = value =>
    Buffer.from(JSON.stringify(value)).toString('base64url');

  const now = Math.floor(Date.now() / 1000);

  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({
    sub: uid,
    user_id: uid,
    aud: project,
    iss: `https://securetoken.google.com/${project}`,
    iat: now,
    exp: now + 3600,
    ...(address
      ? { email: address, email_verified: verified }
      : {}),
    firebase: {
      sign_in_provider: 'custom',
      identities: {},
    },
  })}.`;
}

async function request(
  method,
  path,
  uid,
  emailAddress,
  verified,
  fields,
) {
  const response = await fetch(`${base}/${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token(uid, emailAddress, verified)}`,
      'Content-Type': 'application/json',
    },
    ...(fields && {
      body: JSON.stringify({ fields }),
    }),
  });

  return {
    status: response.status,
    body: await response.text(),
  };
}

async function expectStatus(expected, ...args) {
  const response = await request(...args);
  assert.equal(response.status, expected, response.body);
  return response;
}

test('exact email lookup belongs to the verified email holder', async () => {
  await expectStatus(
    403, 'PATCH', index, owner, email, false,
    { uid: str(owner) },
  );

  await expectStatus(
    403, 'PATCH', index, owner, undefined, false,
    { uid: str(owner) },
  );

  await expectStatus(
    403, 'PATCH', index, other, `${other}@epfl.ch`, true,
    { uid: str(other) },
  );

  await expectStatus(
    403, 'PATCH', index, owner, email, true,
    { uid: str(other) },
  );

  await expectStatus(
    200, 'PATCH', index, owner, email, true,
    { uid: str(owner) },
  );

  const lookup =
    await expectStatus(200, 'GET', index, other);

  assert.equal(
    JSON.parse(lookup.body).fields.uid.stringValue,
    owner,
  );

  await expectStatus(
    403, 'GET', 'userEmailLookups', other,
  );

  await expectStatus(
    403, 'PATCH', index, other, `${other}@epfl.ch`, true,
    { uid: str(other) },
  );

  await expectStatus(
    403, 'PATCH', index, owner, email, false,
    { uid: str(owner) },
  );

  await expectStatus(
    403, 'DELETE', index, other,
  );

  await expectStatus(
    200, 'DELETE', index, owner,
  );

  await expectStatus(
    404, 'GET', index, other,
  );
});
test('email rotation permits deleting the old lookup and creating the new one', async () => {
  const uid = `rotation-${randomUUID()}`;
  const oldEmail = `old.${randomUUID()}@example.org`;
  const newEmail = `new.${randomUUID()}@example.org`;

  const oldPath = `userEmailLookups/${hash(oldEmail)}`;
  const newPath = `userEmailLookups/${hash(newEmail)}`;

  // The old verified address may publish its lookup.
  await expectStatus(
    200, 'PATCH', oldPath, uid, oldEmail, true,
    { uid: str(uid) },
  );

  // The old identity cannot claim the new email prematurely.
  await expectStatus(
    403, 'PATCH', newPath, uid, oldEmail, true,
    { uid: str(uid) },
  );

  // Once the verified email changes, the old index cannot be recreated.
  await expectStatus(
    403, 'PATCH', oldPath, uid, newEmail, true,
    { uid: str(uid) },
  );

  // The owner may remove their old lookup.
  await expectStatus(
    200, 'DELETE', oldPath, uid, newEmail, true,
  );

  // The new verified address may publish its lookup.
  await expectStatus(
    200, 'PATCH', newPath, uid, newEmail, true,
    { uid: str(uid) },
  );

  await expectStatus(
    404, 'GET', oldPath, uid,
  );

  const result = await expectStatus(
    200, 'GET', newPath, uid,
  );

  assert.equal(
    JSON.parse(result.body).fields.uid.stringValue,
    uid,
  );
});

test('unverified account cannot create an email lookup but can clean up its own', async () => {
  const uid = `unverified-${randomUUID()}`;
  const address = `account.${randomUUID()}@example.org`;
  const path = `userEmailLookups/${hash(address)}`;

  await expectStatus(
    200, 'PATCH', path, uid, address, true,
    { uid: str(uid) },
  );

  // Verification is lost.
  await expectStatus(
    403, 'PATCH', path, uid, address, false,
    { uid: str(uid) },
  );

  await expectStatus(
    200, 'DELETE', path, uid, address, false,
  );

  await expectStatus(
    404, 'GET', path, uid,
  );
});