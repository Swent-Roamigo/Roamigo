// AI assistance was used for implementation support.
import {randomBytes} from "node:crypto";
import {onCall, HttpsError} from "firebase-functions/v2/https";
import {getAuth} from "firebase-admin/auth";
import {FieldValue, getFirestore, Timestamp} from "firebase-admin/firestore";
import {logger} from "firebase-functions/v2";

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

// Mirrors MAX_NAME in model/users/UserRepositoryFirestore.kt and the profile rules.
const MAX_PROFILE_NAME_LENGTH = 120;

// Other members read a person's name from users/<uid>, which the app publishes after sign-in. When
// that save never happened (it only logs a warning when it fails), the person shows up as an
// unknown traveller to everyone else. Joining and inviting are the moments others start to see
// them, so the profile is created here from the account itself when it is missing. An existing
// profile is never touched, and a failure here never fails the join.
async function ensureProfile(userId: string): Promise<void> {
  try {
    const ref = getFirestore().doc(`users/${userId}`);
    if ((await ref.get()).exists) return;
    const account = await getAuth().getUser(userId);
    const name = (account.displayName ?? "").trim().slice(0, MAX_PROFILE_NAME_LENGTH).trim();
    if (!name) return;
    const photo = account.photoURL;
    await ref.create({
      uid: userId,
      displayName: name,
      nameKey: name.toLowerCase(),
      photoUrl: photo && photo.length <= 2048 && photo.startsWith("https://") ? photo : null,
      updatedAt: FieldValue.serverTimestamp(),
    });
  } catch (error) {
    // 6 is ALREADY_EXISTS: the app published the profile at the same moment.
    if ((error as {code?: number}).code !== 6) {
      logger.warn("Could not create the user profile", {userId, error});
    }
  }
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

// A link only works while the person who made it still owns the trip, so removing or replacing an
// owner does not leave their links admitting people.
function requireInviterOwns(invitation: FirebaseFirestore.DocumentData,
  trip: FirebaseFirestore.DocumentData): void {
  if (trip.ownerId !== invitation.inviterUserId) {
    throw new HttpsError("not-found", "This invitation is no longer valid");
  }
}

// The invitation ID is its token, so it is unguessable and the link carries nothing else.
export const createTripInvitation = onCall(callableOptions, async (request) => {
  const userId = userIdOf(request);
  const tripId = documentId(request.data?.tripId);
  const db = getFirestore();
  // Reading the existing links and creating one happen in one transaction, so two quick calls
  // (a double tap, or a retry after a timeout) cannot each create a link.
  const link = await db.runTransaction(async (transaction) => {
    requireOwner((await transaction.get(db.doc(`trips/${tripId}`))).data(), userId);

    const now = Timestamp.now();
    // Asking again for the link of the same trip gives the same link until it expires. Only the
    // owner creates links, so every pending link of the trip is the owner's.
    const existing = await transaction.get(db.collection("tripInvitations")
      .where("tripId", "==", tripId)
      .where("status", "==", "PENDING"));
    let usable: {token: string, expirationTime: number | null} | null = null;
    for (const doc of existing.docs) {
      const expiration: Timestamp | null = doc.get("expirationTime");
      if (expiration === null || expiration.toMillis() > now.toMillis()) {
        usable ??= {token: doc.id, expirationTime: expiration?.toMillis() ?? null};
      } else {
        // Expired links are marked so that later calls do not scan them again.
        transaction.update(doc.ref, {status: "EXPIRED"});
      }
    }
    if (usable) return usable;

    const token = randomBytes(16).toString("base64url");
    const expirationTime = Timestamp.fromMillis(now.toMillis() + INVITATION_LIFETIME_MS);
    transaction.create(db.doc(`tripInvitations/${token}`), {
      uid: token, tripId, inviterUserId: userId, inviteeUserId: null, token,
      status: "PENDING", creationTime: now, expirationTime,
    });
    return {token, expirationTime: expirationTime.toMillis()};
  });
  // The join screen shows the inviter by name, so their profile has to exist before anyone opens it.
  await ensureProfile(userId);
  return link;
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
  requireInviterOwns(invitation, trip);
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
    requireInviterOwns(invitation, trip);
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
    // set, not create: a member document left behind by an earlier membership must not make the
    // join fail with ALREADY_EXISTS, which the app would show as a retry that can never work.
    transaction.set(tripRef.collection("members").doc(userId), {
      userId, role: "EDITOR", joinTime: Timestamp.now(),
    });
    return invitation.tripId as string;
  });
  await ensureProfile(userId);
  return {tripId};
});

// Revoking is how an organiser takes back a link that was shared too widely.
export const revokeTripInvitations = onCall(callableOptions, async (request) => {
  const userId = userIdOf(request);
  const tripId = documentId(request.data?.tripId);
  const db = getFirestore();
  // One transaction, like creating a link, so the links it revokes are the ones it read.
  const revoked = await db.runTransaction(async (transaction) => {
    requireOwner((await transaction.get(db.doc(`trips/${tripId}`))).data(), userId);
    const pending = await transaction.get(db.collection("tripInvitations")
      .where("tripId", "==", tripId)
      .where("status", "==", "PENDING"));
    pending.docs.forEach((doc) => transaction.update(doc.ref, {status: "REVOKED"}));
    return pending.size;
  });
  return {revoked};
});
