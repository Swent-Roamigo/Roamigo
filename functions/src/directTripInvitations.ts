
// AI assistance was used for implementation support and code review.
import {createHash, randomBytes} from "node:crypto";
import {onCall, HttpsError} from "firebase-functions/v2/https";
import {FieldPath, getFirestore, Timestamp} from "firebase-admin/firestore";
import {callableOptions, documentId} from "./callable.js";
import {
  ensureProfile, INVITATION_LIFETIME_MS, MAX_MEMBERS,
  requireOwner, userIdOf,
} from "./tripInvitations.js";

const COLLECTION = "directTripInvitations";
const PAGE_SIZE = 20;

function hash(value: string): string {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

function invitationId(tripId: string, inviteeId: string): string {
  return hash(JSON.stringify([tripId, inviteeId]));
}

// Mirrors friendshipDocumentId in FriendFirestoreMapping.kt.
function friendshipId(a: string, b: string): string {
  return [hash(a), hash(b)].sort().join("_");
}

function safeId(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0 &&
    !value.includes("/") && value !== "." && value !== "..";
}

function acceptedFriendship(
  data: FirebaseFirestore.DocumentData | undefined,
  a: string, b: string,
): boolean {
  return data?.status === "ACCEPTED" && safeId(data.requestId) &&
    data.createdAt instanceof Timestamp &&
    Array.isArray(data.participantIds) && data.participantIds.length === 2 &&
    data.participantIds.includes(a) && data.participantIds.includes(b) &&
    ((data.senderUserId === a && data.recipientUserId === b) ||
     (data.senderUserId === b && data.recipientUserId === a));
}
function sameFriendshipGeneration(
  invitation: FirebaseFirestore.DocumentData,
  friendship: FirebaseFirestore.DocumentData,
): boolean {
  return invitation.friendshipRequestId === friendship.requestId &&
    invitation.friendshipCreatedAt instanceof Timestamp &&
    friendship.createdAt instanceof Timestamp &&
    invitation.friendshipCreatedAt.isEqual(friendship.createdAt);
}

function pendingInvitation(
  id: string, data: FirebaseFirestore.DocumentData, now: Timestamp,
): boolean {
  return data.status === "PENDING" &&
    safeId(data.tripId) && safeId(data.inviterUserId) &&
    safeId(data.inviteeUserId) && safeId(data.requestId) &&
    safeId(data.friendshipRequestId) &&
    data.friendshipCreatedAt instanceof Timestamp &&
    data.respondedAt === null &&
    data.inviterUserId !== data.inviteeUserId &&
    id === invitationId(data.tripId, data.inviteeUserId) &&
    data.expirationTime instanceof Timestamp &&
    data.expirationTime.toMillis() > now.toMillis();
}

function cursorOf(value: unknown): string | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value !== "string" || !/^[a-f0-9]{64}$/.test(value)) {
    throw new HttpsError("invalid-argument", "Invalid page cursor");
  }
  return value;
}

// The cursor follows the last scanned document, not the last visible one.
// Filtered-out invitations must not hide later pages.
async function pendingPage(field: string, value: string, cursor?: string) {
  let query = getFirestore().collection(COLLECTION)
    .where(field, "==", value)
    .where("status", "==", "PENDING")
    .orderBy(FieldPath.documentId())
    .limit(PAGE_SIZE);

  if (cursor) query = query.startAfter(cursor);
  const snapshot = await query.get();

  return {
    docs: snapshot.docs,
    nextCursor: snapshot.size === PAGE_SIZE
      ? snapshot.docs[snapshot.docs.length - 1].id : null,
  };
}

/** Creates one private invitation per trip, recipient and friendship generation. */
export const sendDirectTripInvitation = onCall(callableOptions, async (request) => {
  const senderId = userIdOf(request);
  const tripId = documentId(request.data?.tripId);
  const inviteeId = documentId(request.data?.inviteeUserId);

  if (senderId === inviteeId) {
    throw new HttpsError("invalid-argument", "Cannot invite yourself");
  }

  const db = getFirestore();
  const tripRef = db.doc(`trips/${tripId}`);
  const friendRef = db.doc(`friendships/${friendshipId(senderId, inviteeId)}`);
  const id = invitationId(tripId, inviteeId);
  const inviteRef = db.collection(COLLECTION).doc(id);

  const result = await db.runTransaction(async (tx) => {
    // All reads precede writes; a conflicting change triggers a retry.
    const trip = (await tx.get(tripRef)).data();
    requireOwner(trip, senderId);

    if (trip!.memberIds.includes(inviteeId)) {
      throw new HttpsError("failed-precondition", "Already a trip member");
    }
    if (trip!.memberIds.length >= MAX_MEMBERS) {
      throw new HttpsError("resource-exhausted", "Trip is full");
    }

    const friendship = (await tx.get(friendRef)).data();
    if (!acceptedFriendship(friendship, senderId, inviteeId)) {
      throw new HttpsError("permission-denied", "Established friendship required");
    }

    const previous = (await tx.get(inviteRef)).data();
    const now = Timestamp.now();

    // Reuse an eligible pending request without extending its expiry.
    if (previous && pendingInvitation(id, previous, now) &&
        previous.inviterUserId === senderId &&
        sameFriendshipGeneration(previous, friendship!)) {
      return {
        invitationId: id,
        requestId: previous.requestId,
        expirationTime: previous.expirationTime.toMillis(),
      };
    }

    // A replaced request gets a new generation, so stale responses cannot
    // accidentally act on the replacement in a future accept/decline flow.
    const requestId = randomBytes(16).toString("base64url");
    const expires = Timestamp.fromMillis(
      now.toMillis() + INVITATION_LIFETIME_MS,
    );

    tx.set(inviteRef, {
      tripId,
      inviterUserId: senderId,
      inviteeUserId: inviteeId,
      requestId,
      friendshipRequestId: friendship!.requestId,
      friendshipCreatedAt: friendship!.createdAt,
      status: "PENDING",
      creationTime: now,
      expirationTime: expires,
      respondedAt: null,
    });

    return {
      invitationId: id,
      requestId,
      expirationTime: expires.toMillis(),
    };
  });

  await ensureProfile(senderId);
  return result;
});

/** Returns invitations addressed only to the authenticated recipient. */
export const listIncomingDirectTripInvitations = onCall(
  callableOptions,
  async (request) => {
    const recipientId = userIdOf(request);
    const {docs, nextCursor} = await pendingPage(
      "inviteeUserId", recipientId, cursorOf(request.data?.cursor),
    );
    const db = getFirestore();
    const now = Timestamp.now();

    const rows = await Promise.all(docs.map(async (doc) => {
      const data = doc.data();

      if (!pendingInvitation(doc.id, data, now) ||
          data.inviteeUserId !== recipientId) {
        return null;
      }

      const [trip, friendship] = await Promise.all([
        db.doc(`trips/${data.tripId}`).get(),
        db.doc(`friendships/${friendshipId(data.inviterUserId, recipientId)}`).get(),
      ]);

      const tripData = trip.data();
      const friendData = friendship.data();

      if (!tripData || tripData.ownerId !== data.inviterUserId ||
          !Array.isArray(tripData.memberIds) ||
          !tripData.memberIds.includes(data.inviterUserId) ||
          tripData.memberIds.includes(recipientId) ||
          tripData.memberIds.length >= MAX_MEMBERS ||
          typeof tripData.name !== "string" || !tripData.name.trim() ||
          !acceptedFriendship(friendData, data.inviterUserId, recipientId) ||
          !sameFriendshipGeneration(data, friendData!)) {
        return null;
      }

      const profile = (
        await db.doc(`users/${data.inviterUserId}`).get()
      ).data();

      return {
        invitationId: doc.id,
        requestId: data.requestId,
        tripId: data.tripId,
        tripName: tripData.name,
        inviterUserId: data.inviterUserId,
        inviterName: typeof profile?.displayName === "string"
          ? profile.displayName : null,
        expirationTime: data.expirationTime.toMillis(),
      };
    }));

    return {
      invitations: rows.filter((value) => value !== null),
      nextCursor,
    };
  },
);

/** Returns pending invitations sent by the current trip organizer. */
export const listSentDirectTripInvitations = onCall(
  callableOptions,
  async (request) => {
    const senderId = userIdOf(request);
    const tripId = documentId(request.data?.tripId);
    const db = getFirestore();

    const trip = (await db.doc(`trips/${tripId}`).get()).data();
    requireOwner(trip, senderId);

    const {docs, nextCursor} = await pendingPage(
      "tripId", tripId, cursorOf(request.data?.cursor),
    );
    const now = Timestamp.now();

    const rows = await Promise.all(docs.map(async (doc) => {
      const data = doc.data();

      if (!pendingInvitation(doc.id, data, now) ||
          data.tripId !== tripId || data.inviterUserId !== senderId ||
          trip!.memberIds.includes(data.inviteeUserId) ||
          trip!.memberIds.length >= MAX_MEMBERS) {
        return null;
      }

      const friend = (
        await db.doc(
          `friendships/${friendshipId(senderId, data.inviteeUserId)}`,
        ).get()
      ).data();

      if (!acceptedFriendship(friend, senderId, data.inviteeUserId) ||
          !sameFriendshipGeneration(data, friend!)) {
        return null;
      }

      return {
        invitationId: doc.id,
        requestId: data.requestId,
        inviteeUserId: data.inviteeUserId,
        expirationTime: data.expirationTime.toMillis(),
      };
    }));

    return {
      invitations: rows.filter((value) => value !== null),
      nextCursor,
    };
  },
);
