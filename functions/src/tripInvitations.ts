// AI assistance was used for implementation support.
import {randomBytes} from "node:crypto";
import {onCall, HttpsError} from "firebase-functions/v2/https";
import {FieldValue, getFirestore, Timestamp} from "firebase-admin/firestore";

// Joining a trip changes its members, which clients can never do themselves (see firestore.rules),
// so invitations are created, read and accepted here, with the Admin SDK.
const callableOptions = {region: "europe-west1", maxInstances: 2};

const INVITATION_LIFETIME_MS = 7 * 24 * 60 * 60 * 1000;
// Mirrors the travelerCount limit of a group trip in model/trip/Trip.kt.
const MAX_MEMBERS = 10;

function userIdOf(request: {auth?: {uid: string}}): string {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return request.auth.uid;
}

function documentId(value: unknown): string {
  if (typeof value !== "string" || !value.trim() || value.includes("/") ||
      value === "." || value === "..") {
    throw new HttpsError("invalid-argument", "Invalid document ID");
  }
  return value;
}

function requireOwner(trip: FirebaseFirestore.DocumentData | undefined, userId: string): void {
  if (!trip) throw new HttpsError("not-found", "Trip not found");
  if (trip.ownerId !== userId || !Array.isArray(trip.memberIds) ||
      !trip.memberIds.includes(userId)) {
    throw new HttpsError("permission-denied", "Trip owner required");
  }
}

// The same message for an unknown, revoked or expired token, so a token cannot be probed.
function requireUsable(invitation: FirebaseFirestore.DocumentData | undefined, now: Timestamp) {
  const expiration: Timestamp | null = invitation?.expirationTime ?? null;
  if (!invitation || invitation.status !== "PENDING" ||
      (expiration !== null && expiration.toMillis() <= now.toMillis())) {
    throw new HttpsError("not-found", "This invitation is no longer valid");
  }
  return invitation;
}

// The invitation ID is its token, so it is unguessable and the link carries nothing else.
export const createTripInvitation = onCall(callableOptions, async (request) => {
  const userId = userIdOf(request);
  const tripId = documentId(request.data?.tripId);
  const db = getFirestore();
  // Reading the existing links and creating one happen in one transaction, so two quick calls
  // (a double tap, or a retry after a timeout) cannot each create a link.
  return db.runTransaction(async (transaction) => {
    requireOwner((await transaction.get(db.doc(`trips/${tripId}`))).data(), userId);

    const now = Timestamp.now();
    // Asking again for the link of the same trip gives the same link until it expires. Only the
    // owner creates links, so every pending link of the trip is the owner's.
    const existing = await transaction.get(db.collection("tripInvitations")
      .where("tripId", "==", tripId)
      .where("status", "==", "PENDING"));
    for (const doc of existing.docs) {
      const expiration: Timestamp | null = doc.get("expirationTime");
      if (expiration === null || expiration.toMillis() > now.toMillis()) {
        return {token: doc.id, expirationTime: expiration?.toMillis() ?? null};
      }
    }

    const token = randomBytes(16).toString("base64url");
    const expirationTime = Timestamp.fromMillis(now.toMillis() + INVITATION_LIFETIME_MS);
    transaction.create(db.doc(`tripInvitations/${token}`), {
      uid: token, tripId, inviterUserId: userId, inviteeUserId: null, token,
      status: "PENDING", creationTime: now, expirationTime,
    });
    return {token, expirationTime: expirationTime.toMillis()};
  });
});

// What a person sees before joining. They cannot read the trip yet, so only what the invitation
// is meant to show is returned.
export const previewTripInvitation = onCall(callableOptions, async (request) => {
  const userId = userIdOf(request);
  const token = documentId(request.data?.token);
  const db = getFirestore();
  const invitation = requireUsable(
    (await db.doc(`tripInvitations/${token}`).get()).data(), Timestamp.now());
  const trip = (await db.doc(`trips/${invitation.tripId}`).get()).data();
  if (!trip) throw new HttpsError("not-found", "This invitation is no longer valid");
  const inviter = (await db.doc(`users/${invitation.inviterUserId}`).get()).data();
  return {
    tripId: invitation.tripId,
    tripName: trip.name,
    inviterName: typeof inviter?.displayName === "string" ? inviter.displayName : null,
    alreadyMember: Array.isArray(trip.memberIds) && trip.memberIds.includes(userId),
  };
});

export const acceptTripInvitation = onCall(callableOptions, async (request) => {
  const userId = userIdOf(request);
  const token = documentId(request.data?.token);
  const db = getFirestore();
  const tripId = await db.runTransaction(async (transaction) => {
    const invitation = requireUsable(
      (await transaction.get(db.doc(`tripInvitations/${token}`))).data(), Timestamp.now());
    const tripRef = db.doc(`trips/${invitation.tripId}`);
    const trip = (await transaction.get(tripRef)).data();
    if (!trip) throw new HttpsError("not-found", "This invitation is no longer valid");
    const memberIds: string[] = Array.isArray(trip.memberIds) ? trip.memberIds : [];
    // A second tap, or a link opened by an existing member, changes nothing.
    if (memberIds.includes(userId)) return invitation.tripId as string;
    if (memberIds.length >= MAX_MEMBERS) {
      throw new HttpsError("resource-exhausted", "This trip is full");
    }
    // firestore.rules ties travelMode to travelerCount: a SOLO trip has exactly one traveler and a
    // GROUP trip two to MAX_MEMBERS. Writing as Admin skips those rules, so keep them true here: a
    // trip that gains a second member becomes a group trip, and the count never falls below the
    // number of members.
    const travelerCount = typeof trip.travelerCount === "number" ? trip.travelerCount : 0;
    transaction.update(tripRef, {
      memberIds: FieldValue.arrayUnion(userId),
      travelMode: "GROUP",
      travelerCount: Math.max(travelerCount, memberIds.length + 1),
    });
    transaction.create(tripRef.collection("members").doc(userId), {
      userId, role: "EDITOR", joinTime: Timestamp.now(),
    });
    return invitation.tripId as string;
  });
  return {tripId};
});

// Revoking is how an organiser takes back a link that was shared too widely.
export const revokeTripInvitations = onCall(callableOptions, async (request) => {
  const userId = userIdOf(request);
  const tripId = documentId(request.data?.tripId);
  const db = getFirestore();
  requireOwner((await db.doc(`trips/${tripId}`).get()).data(), userId);
  const pending = await db.collection("tripInvitations")
    .where("tripId", "==", tripId)
    .where("status", "==", "PENDING")
    .get();
  const batch = db.batch();
  pending.docs.forEach((doc) => batch.update(doc.ref, {status: "REVOKED"}));
  await batch.commit();
  return {revoked: pending.size};
});
