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
let owner, creator, member, outsider;
async function account() {
  const response = await fetch(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator`, {
    method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({returnSecureToken: true}),
  });
  assert.equal(response.status, 200, await response.clone().text());
  const data = await response.json();
  return {uid: data.localId, token: data.idToken};
}
before(async () => { [owner, creator, member, outsider] = await Promise.all(Array.from({length: 4}, account)); });
async function call(name, data, actor = member) {
  const response = await fetch(`${process.env.VOTE_FUNCTIONS_URL}/${name}`, {
    method: 'POST', headers: {'Content-Type': 'application/json', ...(actor ? {Authorization: `Bearer ${actor.token}`} : {})},
    body: JSON.stringify({data}),
  });
  return response.json();
}
async function fixture(choices = ['a', 'b'], ownerIsCreator = false) {
  const tripRef = db.collection('trips').doc();
  const actualOwner = ownerIsCreator ? creator : owner;
  await tripRef.set({ownerId: actualOwner.uid, memberIds: [owner.uid, creator.uid, member.uid], status: 'PLANNED', endDate: Timestamp.fromMillis(4102444800000)});
  const ref = tripRef.collection('votes').doc('source');
  const vote = {uid: ref.id, tripId: tripRef.id, question: 'Dinner?', options: ['a', 'b', 'c'].map(uid => ({uid, name: uid})), createdByUserId: creator.uid, status: 'CLOSED', creationTime: Timestamp.fromMillis(1000), closingTime: null, winningOptionId: null};
  await ref.set(vote);
  for (const [index, optionId] of choices.entries()) await ref.collection('ballots').doc(`ballot${index}`).set({userId: `ballot${index}`, optionId, updatedAt: Timestamp.fromMillis(2000)});
  const request = {tripId: tripRef.id, voteId: ref.id};
  const revote = {tripId: tripRef.id, parentVoteId: ref.id, voteId: 'round2'};
  return {ref, tripRef, vote, request, revote, actualOwner};
}
async function ballotData(ref) { return (await ref.collection('ballots').orderBy('userId').get()).docs.map(doc => doc.data()); }
function failed(response, status = 'FAILED_PRECONDITION') { assert.equal(response.error?.status, status, JSON.stringify(response)); }
function succeeded(response) { assert.ok(response.result?.voteId, JSON.stringify(response)); }

test('trusted owner resolution recomputes a real tie, preserves counts and is idempotent', async () => {
  const {ref, vote, request} = await fixture(['a', 'a', 'b', 'b']);
  const originalBallots = await ballotData(ref);
  failed(await call('resolveVoteByOwner', {...request, optionId: 'a'}, creator), 'PERMISSION_DENIED');
  failed(await call('resolveVoteByOwner', {...request, optionId: 'a'}, member), 'PERMISSION_DENIED');
  failed(await call('resolveVoteByOwner', {...request, optionId: 'a'}, outsider), 'PERMISSION_DENIED');
  failed(await call('resolveVoteByOwner', {...request, optionId: 'a'}, null), 'UNAUTHENTICATED');
  failed(await call('resolveVoteByOwner', {...request, optionId: 'c'}, owner)); // existing losing option
  failed(await call('resolveVoteByOwner', {...request, optionId: 'missing'}, owner));
  succeeded(await call('resolveVoteByOwner', {...request, optionId: 'a', resolvedByUserId: creator.uid}, owner));
  succeeded(await call('resolveVoteByOwner', {...request, optionId: 'a'}, owner));
  failed(await call('resolveVoteByOwner', {...request, optionId: 'b'}, owner));
  assert.deepEqual((await ref.get()).data(), {...vote, resolvedOptionId: 'a', resolvedByUserId: owner.uid});
  assert.deepEqual(await ballotData(ref), originalBallots);
});

test('unique winners and zero ballots cannot be resolved or delegated even by the owner', async () => {
  for (const choices of [['a', 'a', 'b'], []]) {
    const {ref, vote, request, revote} = await fixture(choices);
    failed(await call('resolveVoteByOwner', {...request, optionId: 'a'}, owner));
    failed(await call('createRevote', revote));
    failed(await call('createRevote', revote, owner));
    assert.deepEqual((await ref.get()).data(), vote);
    assert.equal((await ref.parent.doc('round2').get()).exists, false);
  }
});

test('revote carries exactly the tied options, creates new metadata and preserves the source', async () => {
  const {ref, vote, revote} = await fixture();
  const originalBallots = await ballotData(ref);
  failed(await call('createRevote', revote, outsider), 'PERMISSION_DENIED');
  failed(await call('createRevote', revote, null), 'UNAUTHENTICATED');
  succeeded(await call('createRevote', {...revote, options: [{uid: 'c', name: 'forged'}], question: 'forged', createdByUserId: owner.uid}));
  const child = (await ref.parent.doc('round2').get()).data();
  assert.equal(child.uid, 'round2');
  assert.equal(child.parentVoteId, ref.id);
  assert.equal(child.round, 2);
  assert.equal(child.question, vote.question);
  assert.equal(child.createdByUserId, member.uid);
  assert.equal(child.status, 'OPEN');
  assert.ok(child.creationTime.toMillis() > vote.creationTime.toMillis());
  assert.deepEqual(child.options, vote.options.slice(0, 2));
  assert.deepEqual((await ref.get()).data(), {...vote, nextRoundVoteId: 'round2'});
  assert.deepEqual(await ballotData(ref), originalBallots);
  succeeded(await call('createRevote', revote)); // lost-response retry
  failed(await call('createRevote', {...revote, voteId: 'duplicate'}));
});

test('concurrent revotes and competing owner decisions allow one outcome', async () => {
  const round = await fixture();
  const responses = await Promise.all(['round2a', 'round2b'].map(voteId => call('createRevote', {...round.revote, voteId})));
  assert.equal(responses.filter(value => value.result).length, 1, JSON.stringify(responses));
  assert.equal((await round.ref.parent.get()).size, 2);
  const decision = await fixture(['a', 'b'], true);
  const outcomes = await Promise.all(['a', 'b'].map(optionId => call('resolveVoteByOwner', {...decision.request, optionId}, creator)));
  assert.equal(outcomes.filter(value => value.result).length, 1, JSON.stringify(outcomes));
  const competing = await fixture();
  const mixed = await Promise.all([call('createRevote', competing.revote), call('resolveVoteByOwner', {...competing.request, optionId: 'a'}, owner)]);
  assert.equal(mixed.filter(value => value.result).length, 1, JSON.stringify(mixed));
});

test('a three-way tied round can tie again and create round three', async () => {
  const {ref, revote} = await fixture(['a', 'b', 'c']);
  succeeded(await call('createRevote', revote, owner));
  const childRef = ref.parent.doc('round2');
  assert.equal((await childRef.get()).get('options').length, 3);
  await childRef.update({status: 'CLOSED'});
  for (const optionId of ['a', 'b']) await childRef.collection('ballots').doc(optionId).set({userId: optionId, optionId, updatedAt: Timestamp.now()});
  succeeded(await call('createRevote', {...revote, parentVoteId: 'round2', voteId: 'round3'}));
  const third = (await ref.parent.doc('round3').get()).data();
  assert.equal(third.round, 3);
  assert.equal(third.parentVoteId, 'round2');
  assert.deepEqual(third.options.map(option => option.uid), ['a', 'b']);
  assert.equal((await childRef.get()).get('nextRoundVoteId'), 'round3');
});

test('missing/open sources are rejected; server does not invent a calendar or status policy', async () => {
  const {ref, tripRef, request, revote} = await fixture();
  failed(await call('createRevote', {...revote, parentVoteId: 'missing'}), 'NOT_FOUND');
  await ref.update({status: 'OPEN'});
  failed(await call('createRevote', revote));
  failed(await call('resolveVoteByOwner', {...request, optionId: 'a'}, owner));
  await ref.update({status: 'CLOSED'});
  await tripRef.update({endDate: Timestamp.fromMillis(1), status: 'COMPLETED'});
  // This policy is deliberately Android/repository-enforced, not falsely enforced by the server.
  succeeded(await call('createRevote', revote));
});

test('duplicate option IDs cannot turn a unique option winner into a fake tie', async () => {
  const {ref, request, revote} = await fixture(['a', 'a']);
  await ref.update({options: [{uid: 'a', name: 'A'}, {uid: 'a', name: 'Duplicate'}]});
  failed(await call('resolveVoteByOwner', {...request, optionId: 'a'}, owner));
  failed(await call('createRevote', revote));
  assert.equal((await ref.get()).get('nextRoundVoteId'), undefined);
});
