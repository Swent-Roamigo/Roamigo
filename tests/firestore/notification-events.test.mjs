// AI assistance was used for implementation support.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
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
const {notifyTripVoteCreated} = require('./lib/notifications.js');
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

function createVote(name, tripId, voteId, creator) {
  return commit({
    update: {
      name,
      fields: {
        uid: {stringValue: voteId},
        tripId: {stringValue: tripId},
        question: {stringValue: 'Where should we eat?'},
        options: {
          arrayValue: {values: ['a', 'b'].map(uid => ({
            mapValue: {fields: {uid: {stringValue: uid}, name: {stringValue: uid}}},
          }))},
        },
        createdByUserId: {stringValue: creator.uid},
        status: {stringValue: 'OPEN'},
        closingTime: {nullValue: null},
        winningOptionId: {nullValue: null},
      },
    },
    updateTransforms: [{fieldPath: 'creationTime', setToServerValue: 'REQUEST_TIME'}],
  }, creator);
}

test('votes and revotes notify current trip members other than their creator', async () => {
  const functionsUrl = process.env.VOTE_FUNCTIONS_URL;
  assert.ok(functionsUrl, 'Set VOTE_FUNCTIONS_URL to the local Functions emulator');
  const [creator, member, otherMember] = await Promise.all([account(), account(), account()]);
  const outsider = randomUUID();
  const trip = db.collection('trips').doc();
  await trip.set({ownerId: creator.uid, memberIds: [creator.uid, member.uid, otherMember.uid]});
  const vote = trip.collection('votes').doc('vote');
  const voteName = `${database}/documents/${vote.path}`;
  await createVote(voteName, trip.id, vote.id, creator);

  for (const recipient of [member, otherMember]) {
    const notifications = await waitForInboxSize(recipient.uid, 1);
    assert.equal(notifications.size, 1);
    const createdAt = notifications.docs[0].get('createdAt');
    assert.ok(createdAt instanceof Timestamp);
    assert.deepEqual(notifications.docs[0].data(), {
      type: 'TRIP_VOTE_CREATED', recipientUserId: recipient.uid,
      tripId: trip.id, voteId: vote.id, createdAt, isRead: false,
    });
  }
  assert.equal((await inbox(creator.uid).get()).size, 0);

  // Give the first round a tie so the existing callable can create a revote.
  await Promise.all([[member, 'a'], [otherMember, 'b']].map(([voter, optionId]) =>
    vote.collection('ballots').doc(voter.uid).set({
      userId: voter.uid, optionId, updatedAt: Timestamp.now(),
    }),
  ));
  await commit({
    update: {name: voteName, fields: {status: {stringValue: 'CLOSED'}}},
    updateMask: {fieldPaths: ['status']},
  }, creator);

  const voteId = 'revote';
  const response = await fetch(`${functionsUrl}/createRevote`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json', Authorization: `Bearer ${member.token}`},
    body: JSON.stringify({data: {tripId: trip.id, parentVoteId: vote.id, voteId}}),
  });
  assert.equal(response.status, 200, await response.clone().text());
  assert.deepEqual(await response.json(), {result: {voteId}});
  assert.equal((await waitForInboxSize(creator.uid, 1)).docs[0].get('voteId'), voteId);
  await waitForInboxSize(otherMember.uid, 2);

  const revote = await trip.collection('votes').doc(voteId).get();
  await notifyTripVoteCreated.run({params: {tripId: trip.id, voteId}, data: revote});
  const inboxVoteIds = async user => (await inbox(user).get()).docs.map(entry => entry.get('voteId')).sort();
  assert.deepEqual(await inboxVoteIds(creator.uid), [voteId]);
  assert.deepEqual(await inboxVoteIds(member.uid), [vote.id]);
  assert.deepEqual(await inboxVoteIds(otherMember.uid), [voteId, vote.id].sort());
  assert.deepEqual(await inboxVoteIds(outsider), []);
});

test('a malformed vote or trip membership produces no notifications', async () => {
  const recipient = randomUUID();
  const trip = db.collection('trips').doc();
  const params = {tripId: trip.id, voteId: 'vote'};
  const vote = {tripId: trip.id, uid: 'vote', status: 'OPEN', createdByUserId: 'creator'};
  await notifyTripVoteCreated.run({params, data: {data: () => vote}});

  await trip.set({memberIds: [recipient, null]});
  await notifyTripVoteCreated.run({params, data: {data: () => vote}});

  await trip.update({memberIds: [recipient]});
  for (const data of [
    {...vote, tripId: 'different-trip'},
    {...vote, createdByUserId: ''},
    {...vote, status: 'CLOSED'},
  ]) {
    await notifyTripVoteCreated.run({params, data: {data: () => data}});
  }
  assert.equal((await inbox(recipient).get()).size, 0);
});
