// Run with isolated Auth, Firestore and Functions emulators. Calls the deployed callable handlers.
import {test, before} from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
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

function directHash(s) {
  return createHash('sha256').update(s, 'utf8').digest('hex');
}

function directFriendId(a, b) {
  return [directHash(a), directHash(b)].sort().join('_');
}

function directDocId(tripId, userId) {
  return directHash(JSON.stringify([tripId, userId]));
}

async function seedAcceptedFriend(
  recipient,
  requestId = `friend-${recipient.uid}`,
  createdAtMillis = 1000,
) {
  const ref = db.doc(
    `friendships/${directFriendId(owner.uid, recipient.uid)}`
  );

  await ref.set({
    requestId,
    senderUserId: owner.uid,
    recipientUserId: recipient.uid,
    participantIds: [owner.uid, recipient.uid],
    status: 'ACCEPTED',
    createdAt: Timestamp.fromMillis(createdAtMillis),
    respondedAt: Timestamp.fromMillis(createdAtMillis + 1000),
  });
  return ref;
}

async function sendDirect(ref, recipient) {
  return succeeded(await call('sendDirectTripInvitation', {
    tripId: ref.id,
    inviteeUserId: recipient.uid,
  }, owner));
}

async function incomingFor(user, cursor) {
  return succeeded(await call(
    'listIncomingDirectTripInvitations',
    cursor ? {cursor} : {},
    user,
  ));
}

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

test('direct creation authorizes only the owner and accepted friends', async () => {
  const ref = await trip();
  const recipient = await account();
  const data = {tripId: ref.id, inviteeUserId: recipient.uid};

  failed(
    await call('sendDirectTripInvitation', data, null),
    'UNAUTHENTICATED',
  );
  failed(
    await call('sendDirectTripInvitation', data, guest),
    'PERMISSION_DENIED',
  );
  failed(
    await call('sendDirectTripInvitation', data, owner),
    'PERMISSION_DENIED',
  );
  failed(
    await call('sendDirectTripInvitation', {
      tripId: ref.id,
      inviteeUserId: owner.uid,
    }, owner),
    'INVALID_ARGUMENT',
  );

  await seedAcceptedFriend(recipient);
  assert.ok((await sendDirect(ref, recipient)).requestId);
});

test('concurrent sends are idempotent and keep expiry unchanged', async () => {
  const ref = await trip();
  const recipient = await account();
  await seedAcceptedFriend(recipient);

  const attempts = await Promise.all(
    Array.from({length: 5}, () =>
      call('sendDirectTripInvitation', {
        tripId: ref.id,
        inviteeUserId: recipient.uid,
      }, owner)
    )
  );

  const results = attempts.map(succeeded);

  assert.equal(
    new Set(results.map(r => r.requestId)).size,
    1,
  );
  assert.equal(
    new Set(results.map(r => r.expirationTime)).size,
    1,
  );
  assert.deepEqual(await sendDirect(ref, recipient), results[0]);

  const docs = await db.collection('directTripInvitations')
    .where('tripId', '==', ref.id)
    .get();

  assert.equal(docs.size, 1);
});

test('recipient-only and owner-only listing preserves trip privacy', async () => {
  const ref = await trip();
  const recipient = await account();
  const outsider = await account();

  await seedAcceptedFriend(recipient);
  const sent = await sendDirect(ref, recipient);

  const visible = await incomingFor(recipient);
  assert.equal(
    visible.invitations.find(
      i => i.invitationId === sent.invitationId
    ).tripId,
    ref.id,
  );

  const outsiderRows = await incomingFor(outsider);
  assert.ok(
    !outsiderRows.invitations.some(
      i => i.invitationId === sent.invitationId
    )
  );

  // The backend must ignore a userId supplied by another caller.
  const forged = succeeded(await call(
    'listIncomingDirectTripInvitations',
    {inviteeUserId: recipient.uid},
    outsider,
  ));
  assert.ok(
    !forged.invitations.some(
      i => i.invitationId === sent.invitationId
    )
  );

  failed(
    await call('listIncomingDirectTripInvitations', {}, null),
    'UNAUTHENTICATED',
  );
  failed(
    await call(
      'listSentDirectTripInvitations',
      {tripId: ref.id},
      recipient,
    ),
    'PERMISSION_DENIED',
  );

  const ownerRows = succeeded(await call(
    'listSentDirectTripInvitations',
    {tripId: ref.id},
    owner,
  ));

  assert.equal(
    ownerRows.invitations.find(
      i => i.invitationId === sent.invitationId
    ).inviteeUserId,
    recipient.uid,
  );

  failed(
    await call(
      'listIncomingDirectTripInvitations',
      {cursor: 'bad'},
      recipient,
    ),
    'INVALID_ARGUMENT',
  );
});

test('expiry, decline and revoke create new request generations', async () => {
  const ref = await trip();
  const recipient = await account();
  await seedAcceptedFriend(recipient);

  const first = await sendDirect(ref, recipient);
  const doc = db.doc(
    `directTripInvitations/${first.invitationId}`
  );

  await doc.update({
    expirationTime: Timestamp.fromMillis(1),
  });

  const second = await sendDirect(ref, recipient);
  assert.equal(second.invitationId, first.invitationId);
  assert.notEqual(second.requestId, first.requestId);

  // These status changes simulate future PR 2 operations.
  await doc.update({
    status: 'DECLINED',
    respondedAt: Timestamp.now(),
  });

  const third = await sendDirect(ref, recipient);
  assert.notEqual(third.requestId, second.requestId);

  await doc.update({
    status: 'REVOKED',
    respondedAt: Timestamp.now(),
  });

  const fourth = await sendDirect(ref, recipient);
  assert.notEqual(fourth.requestId, third.requestId);
});

test('removing and restoring a friendship does not revive old invitations', async () => {
  const ref = await trip();
  const recipient = await account();
  const friendship = await seedAcceptedFriend(recipient);
  const original = await sendDirect(ref, recipient);

  await friendship.delete();

  assert.ok(
    !(await incomingFor(recipient)).invitations.some(
      i => i.invitationId === original.invitationId
    )
  );

  failed(
    await call('sendDirectTripInvitation', {
      tripId: ref.id,
      inviteeUserId: recipient.uid,
    }, owner),
    'PERMISSION_DENIED',
  );

  // Same creation time, but a different friendship request ID.
  await seedAcceptedFriend(
    recipient,
    'friend-restored',
    1000,
  );

  assert.ok(
    !(await incomingFor(recipient)).invitations.some(
      i => i.invitationId === original.invitationId
    )
  );

  // Same request ID, but a different friendship creation time.
  await seedAcceptedFriend(
    recipient,
    `friend-${recipient.uid}`,
    3000,
  );

  assert.ok(
    !(await incomingFor(recipient)).invitations.some(
      i => i.invitationId === original.invitationId
    )
  );

  const replacement = await sendDirect(ref, recipient);
  assert.notEqual(replacement.requestId, original.requestId);
});

test('already-member and full trips reject direct invitations', async () => {
  const recipient = await account();
  await seedAcceptedFriend(recipient);

  const joined = await trip({
    memberIds: [owner.uid, recipient.uid],
  });

  failed(
    await call('sendDirectTripInvitation', {
      tripId: joined.id,
      inviteeUserId: recipient.uid,
    }, owner),
    'FAILED_PRECONDITION',
  );

  const full = await trip({
    memberIds: [
      owner.uid,
      ...Array.from({length: 9}, (_, n) => `m-${n}`),
    ],
  });

  failed(
    await call('sendDirectTripInvitation', {
      tripId: full.id,
      inviteeUserId: recipient.uid,
    }, owner),
    'RESOURCE_EXHAUSTED',
  );
});

test('pending invitation is hidden after recipient joins the trip', async () => {
  const recipient = await account();
  await seedAcceptedFriend(recipient);
  const ref = await trip();
  const invitation = await sendDirect(ref, recipient);

  await ref.update({
    memberIds: [owner.uid, recipient.uid],
  });

  const incoming = await incomingFor(recipient);
  assert.ok(
    !incoming.invitations.some(
      i => i.invitationId === invitation.invitationId
    )
  );

  const outgoing = succeeded(await call(
    'listSentDirectTripInvitations',
    {tripId: ref.id},
    owner,
  ));
  assert.ok(
    !outgoing.invitations.some(
      i => i.invitationId === invitation.invitationId
    )
  );
});

test('direct invitations are independent of other trips and public links', async () => {
  const recipient = await account();
  await seedAcceptedFriend(recipient);

  const paris = await trip({name: 'Paris'});
  const rome = await trip({name: 'Rome'});
  const first = await sendDirect(paris, recipient);
  const second = await sendDirect(rome, recipient);

  assert.notEqual(first.invitationId, second.invitationId);

  const code = await link(paris);

  // A direct invitation ID is not a public-link token.
  failed(
    await call(
      'previewTripInvitation',
      {token: first.invitationId},
    ),
    'NOT_FOUND',
  );

  succeeded(await call(
    'revokeTripInvitations',
    {tripId: paris.id},
    owner,
  ));

  failed(
    await call('previewTripInvitation', {token: code}),
    'NOT_FOUND',
  );

  const stored = await db.doc(
    `directTripInvitations/${first.invitationId}`
  ).get();

  assert.equal(stored.get('status'), 'PENDING');

  const inbox = await incomingFor(recipient);
  assert.equal(
    inbox.invitations.filter(i =>
      i.invitationId === first.invitationId ||
      i.invitationId === second.invitationId
    ).length,
    2,
  );
});

test('former owner cannot list and their invitations become invisible', async () => {
  const recipient = await account();
  await seedAcceptedFriend(recipient);
  const ref = await trip();
  const created = await sendDirect(ref, recipient);

  await ref.update({
    ownerId: recipient.uid,
    memberIds: [recipient.uid],
  });

  const inbox = await incomingFor(recipient);
  assert.ok(
    !inbox.invitations.some(
      i => i.invitationId === created.invitationId
    )
  );

  failed(
    await call(
      'listSentDirectTripInvitations',
      {tripId: ref.id},
      owner,
    ),
    'PERMISSION_DENIED',
  );
});

test('pagination returns all matching invitations without duplicates', async () => {
  const recipient = await account();
  await seedAcceptedFriend(recipient);

  const expires = Timestamp.fromMillis(
    Date.now() + 3600000,
  );

  const seededIds = [];

  for (let i = 0; i < 21; i++) {
    const ref = await trip({name: `Trip ${i}`});

    seededIds.push(directDocId(ref.id, recipient.uid));

    await db.doc(
      `directTripInvitations/${directDocId(ref.id, recipient.uid)}`
    ).set({
      tripId: ref.id,
      inviterUserId: owner.uid,
      inviteeUserId: recipient.uid,
      requestId: `r-${i}`,
      friendshipRequestId: `friend-${recipient.uid}`,
      friendshipCreatedAt: Timestamp.fromMillis(1000),
      status: 'PENDING',
      creationTime: Timestamp.now(),
      expirationTime: expires,
      respondedAt: null,
    });
  }

  // Expire an invitation within the first page by document ID order.
  const expiredId = [...seededIds].sort()[5];

  await db.doc(
    `directTripInvitations/${expiredId}`
  ).update({
    expirationTime: Timestamp.fromMillis(1),
  });

  const first = await incomingFor(recipient);
  assert.equal(first.invitations.length, 19);
  assert.ok(first.nextCursor);

  const second = await incomingFor(
    recipient,
    first.nextCursor,
  );

  assert.equal(second.invitations.length, 1);
  assert.equal(second.nextCursor, null);

  const tripIds = [
    ...first.invitations,
    ...second.invitations,
  ].map(i => i.tripId);

  assert.equal(new Set(tripIds).size, 20);

  const returnedIds = [
    ...first.invitations,
    ...second.invitations,
  ].map(i => i.invitationId);

  assert.ok(!returnedIds.includes(expiredId));
  assert.equal(new Set(returnedIds).size, 20);
});


test('outgoing pagination skips invalid rows without losing later pages', async () => {
  const ref = await trip();
  const createdAt = Timestamp.fromMillis(1000);
  const expires = Timestamp.fromMillis(Date.now() + 3600000);
  const ids = [];

  const batch = db.batch();

  for (let i = 0; i < 21; i++) {
    const recipientId = `outgoing-recipient-${i}`;
    const requestId = `friend-outgoing-${i}`;
    const id = directDocId(ref.id, recipientId);
    ids.push(id);

    batch.set(
      db.doc(`friendships/${directFriendId(owner.uid, recipientId)}`),
      {
        requestId,
        senderUserId: owner.uid,
        recipientUserId: recipientId,
        participantIds: [owner.uid, recipientId],
        status: 'ACCEPTED',
        createdAt,
        respondedAt: Timestamp.fromMillis(2000),
      },
    );

    batch.set(db.doc(`directTripInvitations/${id}`), {
      tripId: ref.id,
      inviterUserId: owner.uid,
      inviteeUserId: recipientId,
      requestId: `invite-${i}`,
      friendshipRequestId: requestId,
      friendshipCreatedAt: createdAt,
      status: 'PENDING',
      creationTime: Timestamp.now(),
      expirationTime: expires,
      respondedAt: null,
    });
  }

  await batch.commit();

  const expiredId = [...ids].sort()[5];
  await db.doc(`directTripInvitations/${expiredId}`).update({
    expirationTime: Timestamp.fromMillis(1),
  });

  const first = succeeded(await call(
    'listSentDirectTripInvitations',
    {tripId: ref.id},
    owner,
  ));

  assert.equal(first.invitations.length, 19);
  assert.ok(first.nextCursor);

  const second = succeeded(await call(
    'listSentDirectTripInvitations',
    {tripId: ref.id, cursor: first.nextCursor},
    owner,
  ));

  assert.equal(second.invitations.length, 1);
  assert.equal(second.nextCursor, null);

  const returned = [
    ...first.invitations,
    ...second.invitations,
  ].map(invitation => invitation.invitationId);

  assert.equal(new Set(returned).size, 20);
  assert.ok(!returned.includes(expiredId));
});

test('full trips temporarily hide pending direct invitations', async () => {
  const ref = await trip();
  const recipient = await account();

  await seedAcceptedFriend(recipient);
  const invitation = await sendDirect(ref, recipient);

  await ref.update({
    memberIds: [
      owner.uid,
      ...Array.from({length: 9}, (_, i) => `member-${i}`),
    ],
  });

  const incoming = await incomingFor(recipient);
  assert.ok(
    !incoming.invitations.some(
      i => i.invitationId === invitation.invitationId
    )
  );

  const outgoing = succeeded(await call(
    'listSentDirectTripInvitations',
    {tripId: ref.id},
    owner,
  ));

  assert.ok(
    !outgoing.invitations.some(
      i => i.invitationId === invitation.invitationId
    )
  );

  // Capacity becomes available again.
  await ref.update({memberIds: [owner.uid]});

  const restored = await incomingFor(recipient);
  assert.ok(
    restored.invitations.some(
      i => i.invitationId === invitation.invitationId
    )
  );
});

test('ordinary Firestore clients cannot access direct invitations', async () => {
  const recipient = await account();
  await seedAcceptedFriend(recipient);
  const ref = await trip();
  const created = await sendDirect(ref, recipient);

  const url =
    `http://${process.env.FIRESTORE_EMULATOR_HOST}` +
    `/v1/projects/demo-roamigo/databases/(default)` +
    `/documents/directTripInvitations/${created.invitationId}`;

  for (const actor of [owner, recipient, null]) {
    const headers = actor
      ? {Authorization: `Bearer ${actor.token}`}
      : {};

    const read = await fetch(url, {headers});
    assert.equal(read.status, 403, await read.text());

    const collectionUrl = url.substring(0, url.lastIndexOf('/'));

    const listing = await fetch(collectionUrl, {headers});

    assert.equal(
      listing.status,
      403,
      await listing.text(),
    );

    const write = await fetch(
      `${url}?updateMask.fieldPaths=status`,
      {
        method: 'PATCH',
        headers: {
          ...headers,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          fields: {status: {stringValue: 'DECLINED'}},
        }),
      },
    );
    assert.equal(write.status, 403, await write.text());

    const deletion = await fetch(url, {
      method: 'DELETE',
      headers,
    });
    assert.equal(deletion.status, 403, await deletion.text());

    const create = await fetch(
      url.replace(`/${created.invitationId}`, '') +
        '?documentId=blocked-' + created.invitationId,
      {
        method: 'POST',
        headers: {
          ...headers,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          fields: {status: {stringValue: 'PENDING'}},
        }),
      },
    );
    assert.equal(create.status, 403, await create.text());
  }

  const stored = await db.doc(
    `directTripInvitations/${created.invitationId}`
  ).get();

  assert.equal(stored.get('status'), 'PENDING');
});
