
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash, randomUUID} from 'node:crypto';
import {createRequire} from 'node:module';

const require = createRequire(
  new URL('../../functions/package.json', import.meta.url),
);

const {initializeApp} = require('firebase-admin/app');
const {getAuth} = require('firebase-admin/auth');
const {getFirestore} = require('firebase-admin/firestore');

assert.ok(process.env.FIREBASE_AUTH_EMULATOR_HOST);
assert.ok(process.env.FIRESTORE_EMULATOR_HOST);
assert.ok(process.env.VOTE_FUNCTIONS_URL);

initializeApp({projectId: 'demo-roamigo'});

const auth = getAuth();
const db = getFirestore();

async function createVerifiedUser(email) {
  const password = 'EmulatorTestPassword123!';
  const user = await auth.createUser({
    email,
    emailVerified: true,
    password,
  });

  const response = await fetch(
    `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}` +
      '/identitytoolkit.googleapis.com/v1/' +
      'accounts:signInWithPassword?key=emulator',
    {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({
        email,
        password,
        returnSecureToken: true,
      }),
    },
  );

  assert.equal(response.status, 200, await response.clone().text());

  return {
    uid: user.uid,
    token: (await response.json()).idToken,
  };
}

async function reclaim(email, token) {
  const response = await fetch(
    `${process.env.VOTE_FUNCTIONS_URL}/reclaimEmailLookup`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? {Authorization: `Bearer ${token}`} : {}),
      },
      body: JSON.stringify({data: {email}}),
    },
  );

  return response.json();
}

test('reclaim checks current Auth ownership, not stale tokens', async () => {
  const suffix = randomUUID();
  const email = `previous.${suffix}@example.org`;
  const replacement = `replacement.${suffix}@example.org`;

  const previousOwner = await createVerifiedUser(email);
  const hash = createHash('sha256').update(email).digest('hex');
  const index = db.collection('userEmailLookups').doc(hash);

  await index.set({uid: previousOwner.uid});

  // The old token remains available, but Auth has a new address.
  await auth.updateUser(previousOwner.uid, {
    email: replacement,
    emailVerified: true,
  });

  const newOwner = await createVerifiedUser(email);

  // The previous owner cannot reclaim using its stale token.
  const stale = await reclaim(email, previousOwner.token);
  assert.equal(stale.error?.status, 'PERMISSION_DENIED');

  const stranger = await createVerifiedUser(
    `stranger.${suffix}@example.org`,
  );

  const unauthorized = await reclaim(email, stranger.token);
  assert.equal(unauthorized.error?.status, 'PERMISSION_DENIED');

  // Current verified owner can reclaim the old index.
  const success = await reclaim(email, newOwner.token);
  assert.equal(success.result?.reclaimed, true, JSON.stringify(success));

  assert.equal((await index.get()).get('uid'), newOwner.uid);

  // Repeated recovery does not change ownership.
  const retry = await reclaim(email, newOwner.token);
  assert.equal(retry.result?.reclaimed, false);

  assert.equal((await index.get()).get('uid'), newOwner.uid);
});
