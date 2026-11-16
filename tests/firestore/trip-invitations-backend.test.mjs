// Run with isolated Auth, Firestore and Functions emulators. Calls the deployed callable handlers.
import {test, before} from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require = createRequire(new URL('../../functions/package.json', import.meta.url));
const {initializeApp} = require('firebase-admin/app');
const {getFirestore, Timestamp} = require('firebase-admin/firestore');
assert.ok(process.env.FIRESTORE_EMULATOR_HOST);
assert.ok(process.env.FIREBASE_AUTH_EMULATOR_HOST);
assert.ok(process.env.VOTE_FUNCTIONS_URL);
initializeApp({projectId: 'demo-roamigo'});
const db = getFirestore();
let owner, guest, other;
async function account() {
  const response = await fetch(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator`, {
    method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({returnSecureToken: true}),
  });
  assert.equal(response.status, 200, await response.clone().text());
  const data = await response.json();
  return {uid: data.localId, token: data.idToken};
}
before(async () => { [owner, guest, other] = await Promise.all(Array.from({length: 3}, account)); });
async function call(name, data, actor = guest) {
  const response = await fetch(`${process.env.VOTE_FUNCTIONS_URL}/${name}`, {
    method: 'POST', headers: {'Content-Type': 'application/json', ...(actor ? {Authorization: `Bearer ${actor.token}`} : {})},
    body: JSON.stringify({data}),
  });
  return response.json();
}
// A trip the owner is the only member of, with the owner's own member document.
async function trip(extra = {}) {
  const ref = db.collection('trips').doc();
  await ref.set({name: 'Lisbon', ownerId: owner.uid, memberIds: [owner.uid], travelMode: 'GROUP', travelerCount: 3, ...extra});
  await ref.collection('members').doc(owner.uid).set({userId: owner.uid, role: 'OWNER', joinTime: Timestamp.now()});
  return ref;
}
async function link(ref, actor = owner) {
  const response = await call('createTripInvitation', {tripId: ref.id}, actor);
  assert.equal(typeof response.result?.token, 'string', JSON.stringify(response));
  return response.result.token;
}
function failed(response, status) { assert.equal(response.error?.status, status, JSON.stringify(response)); }
function succeeded(response) { assert.ok(response.result, JSON.stringify(response)); return response.result; }

test('only the trip owner can create a link, and only when signed in', async () => {
  const ref = await trip();
  failed(await call('createTripInvitation', {tripId: ref.id}, null), 'UNAUTHENTICATED');
  failed(await call('createTripInvitation', {tripId: ref.id}, guest), 'PERMISSION_DENIED');
  failed(await call('createTripInvitation', {tripId: 'missing'}, owner), 'NOT_FOUND');
  assert.ok((await link(ref)).length >= 16);
});

test('asking again returns the same link, even for concurrent calls', async () => {
  const ref = await trip();
  const token = await link(ref);
  assert.equal(await link(ref), token);

  const other = await trip();
  const burst = await Promise.all(Array.from({length: 5}, () => call('createTripInvitation', {tripId: other.id}, owner)));
  const tokens = new Set(burst.map(response => response.result?.token));
  assert.equal(tokens.size, 1, JSON.stringify(burst));
  assert.ok(!tokens.has(undefined), JSON.stringify(burst));
  const pending = await db.collection('tripInvitations').where('tripId', '==', other.id).where('status', '==', 'PENDING').get();
  assert.equal(pending.size, 1);
});

test('an expired link is marked EXPIRED, refused, and replaced by a new one', async () => {
  const ref = await trip();
  const expired = await link(ref);
  await db.doc(`tripInvitations/${expired}`).update({expirationTime: Timestamp.fromMillis(Date.now() - 1000)});
  failed(await call('acceptTripInvitation', {token: expired}, other), 'NOT_FOUND');

  const fresh = await link(ref);
  assert.notEqual(fresh, expired);
  assert.equal((await db.doc(`tripInvitations/${expired}`).get()).data().status, 'EXPIRED');
});

test('the preview shows the trip and the inviter, and says nothing for an unknown token', async () => {
  const ref = await trip();
  await db.doc(`users/${owner.uid}`).set({displayName: 'Olivia'});
  const token = await link(ref);

  const preview = succeeded(await call('previewTripInvitation', {token}));
  assert.deepEqual([preview.tripName, preview.inviterName, preview.alreadyMember], ['Lisbon', 'Olivia', false]);
  failed(await call('previewTripInvitation', {token: 'does-not-exist-123456'}), 'NOT_FOUND');
  assert.equal(succeeded(await call('previewTripInvitation', {token}, owner)).alreadyMember, true);
});

test('joining adds the member once, as an editor', async () => {
  const ref = await trip();
  const token = await link(ref);

  assert.equal(succeeded(await call('acceptTripInvitation', {token})).tripId, ref.id);
  assert.equal(succeeded(await call('acceptTripInvitation', {token})).tripId, ref.id);

  const data = (await ref.get()).data();
  assert.equal(data.memberIds.filter(id => id === guest.uid).length, 1);
  assert.equal((await ref.collection('members').doc(guest.uid).get()).data().role, 'EDITOR');
});

test('a solo trip that gains a member becomes a group trip, and a group trip keeps its planned count', async () => {
  const solo = await trip({travelMode: 'SOLO', travelerCount: 1});
  succeeded(await call('acceptTripInvitation', {token: await link(solo)}));
  const soloData = (await solo.get()).data();
  assert.deepEqual([soloData.travelMode, soloData.travelerCount], ['GROUP', 2]);

  const group = await trip({travelerCount: 5});
  succeeded(await call('acceptTripInvitation', {token: await link(group)}));
  const groupData = (await group.get()).data();
  assert.deepEqual([groupData.travelMode, groupData.travelerCount], ['GROUP', 5]);
});

test('a full trip refuses another member', async () => {
  const ref = await trip();
  const token = await link(ref);
  await ref.update({memberIds: Array.from({length: 10}, (_, index) => `member${index}`)});
  failed(await call('acceptTripInvitation', {token}, other), 'RESOURCE_EXHAUSTED');
});

test('a leftover member document does not block joining', async () => {
  const ref = await trip();
  await ref.collection('members').doc(guest.uid).set({userId: guest.uid, role: 'EDITOR', joinTime: Timestamp.fromMillis(1)});
  const response = await call('acceptTripInvitation', {token: await link(ref)});
  assert.equal(succeeded(response).tripId, ref.id);
  assert.ok((await ref.get()).data().memberIds.includes(guest.uid));
});

test('a link of a former owner is refused and shows no preview', async () => {
  const ref = await trip();
  const token = await link(ref);
  await ref.update({ownerId: other.uid, memberIds: [other.uid]});

  failed(await call('acceptTripInvitation', {token}), 'NOT_FOUND');
  failed(await call('previewTripInvitation', {token}), 'NOT_FOUND');
  assert.ok(!(await ref.get()).data().memberIds.includes(guest.uid));
});

test('only the owner can revoke, and a revoked link stays dead', async () => {
  const ref = await trip();
  const token = await link(ref);
  failed(await call('revokeTripInvitations', {tripId: ref.id}, guest), 'PERMISSION_DENIED');
  assert.equal(succeeded(await call('revokeTripInvitations', {tripId: ref.id}, owner)).revoked, 1);

  failed(await call('previewTripInvitation', {token}), 'NOT_FOUND');
  failed(await call('acceptTripInvitation', {token}, other), 'NOT_FOUND');
  assert.ok(!(await ref.get()).data().memberIds.includes(other.uid));
  assert.notEqual(await link(ref), token);
});
