// AI assistance was used for implementation support.
import {onCall, HttpsError} from "firebase-functions/v2/https";
import {getFirestore, Timestamp} from "firebase-admin/firestore";

type Option = {uid: string; name: string};

function documentId(value: unknown): string {
  if (typeof value !== "string" || !value.trim() || value.includes("/") ||
      value === "." || value === "..") {
    throw new HttpsError("invalid-argument", "Invalid document ID");
  }
  return value;
}

// No calendar approximation: Trip.isCompletedOn uses the viewing device's local date/zone.
// The Android repository enforces that policy before calling. These endpoints enforce the
// shared, server-verifiable membership, owner, immutable-ballot and resolution invariants.
const callableOptions = {region: "europe-west1", maxInstances: 2};

export const createRevote = onCall(callableOptions, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  const userId = request.auth.uid;
  const tripId = documentId(request.data?.tripId);
  const parentId = documentId(request.data?.parentVoteId);
  const childId = documentId(request.data?.voteId);
  if (parentId === childId) throw new HttpsError("invalid-argument", "A round needs a new ID");
  const db = getFirestore();
  const tripRef = db.doc(`trips/${tripId}`);
  const parentRef = tripRef.collection("votes").doc(parentId);
  const childRef = tripRef.collection("votes").doc(childId);
  await db.runTransaction(async (transaction) => {
    const trip = (await transaction.get(tripRef)).data();
    if (!Array.isArray(trip?.memberIds) || !trip.memberIds.includes(userId)) {
      throw new HttpsError("permission-denied", "Trip membership required");
    }
    const parent = (await transaction.get(parentRef)).data();
    if (!parent) throw new HttpsError("not-found", "Vote not found");
    const child = await transaction.get(childRef);
    if (parent.nextRoundVoteId === childId && child.exists &&
        child.get("parentVoteId") === parentId && child.get("createdByUserId") === userId) return;
    requireUnresolved(parent);
    if (child.exists) throw new HttpsError("already-exists", "Round ID already in use");
    const finalBallots = await transaction.get(parentRef.collection("ballots"));
    const options = tiedOptions(parent.options, finalBallots.docs.map((ballot) => ballot.get("optionId")));
    transaction.create(childRef, {
      uid: childId, tripId, parentVoteId: parentId, round: (parent.round ?? 1) + 1,
      question: parent.question, options, createdByUserId: userId,
      status: "OPEN", creationTime: Timestamp.now(), closingTime: null, winningOptionId: null,
    });
    transaction.update(parentRef, {nextRoundVoteId: childId});
  });
  return {voteId: childId};
});

export const resolveVoteByOwner = onCall(callableOptions, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  const userId = request.auth.uid;
  const tripId = documentId(request.data?.tripId);
  const voteId = documentId(request.data?.voteId);
  const optionId = documentId(request.data?.optionId);
  const db = getFirestore();
  const tripRef = db.doc(`trips/${tripId}`);
  const voteRef = tripRef.collection("votes").doc(voteId);
  await db.runTransaction(async (transaction) => {
    const trip = (await transaction.get(tripRef)).data();
    if (!Array.isArray(trip?.memberIds) || !trip.memberIds.includes(userId) || trip.ownerId !== userId) {
      throw new HttpsError("permission-denied", "Trip owner required");
    }
    const vote = (await transaction.get(voteRef)).data();
    if (!vote) throw new HttpsError("not-found", "Vote not found");
    // Same-choice retries recover an ambiguous success without changing any stored outcome.
    if (vote.resolvedOptionId === optionId && vote.resolvedByUserId === userId) return;
    requireUnresolved(vote);
    const finalBallots = await transaction.get(voteRef.collection("ballots"));
    const options = tiedOptions(vote.options, finalBallots.docs.map((ballot) => ballot.get("optionId")));
    if (!options.some((option) => option.uid === optionId)) {
      throw new HttpsError("failed-precondition", "Choose a tied option");
    }
    transaction.update(voteRef, {resolvedOptionId: optionId, resolvedByUserId: userId});
  });
  return {voteId};
});

function requireUnresolved(vote: FirebaseFirestore.DocumentData): void {
  if (vote.status !== "CLOSED" || vote.nextRoundVoteId != null || vote.resolvedOptionId != null) {
    throw new HttpsError("failed-precondition", "Vote is not an unresolved closed vote");
  }
}

function tiedOptions(options: Option[], choices: unknown[]): Option[] {
  // Option identity, not array position, defines winners in the Android result model.
  // Legacy creation rules do not reject duplicate IDs; never mistake those for a tie.
  if (new Set(options.map((option) => option.uid)).size !== options.length) {
    throw new HttpsError("failed-precondition", "Vote has duplicate option IDs");
  }
  const counts = new Map(options.map((option) => [option.uid, 0]));
  for (const choice of choices) {
    if (typeof choice === "string" && counts.has(choice)) counts.set(choice, counts.get(choice)! + 1);
  }
  const highest = Math.max(0, ...counts.values());
  const tied = options.filter((option) => highest > 0 && counts.get(option.uid) === highest);
  if (tied.length < 2) throw new HttpsError("failed-precondition", "Vote has no tie");
  return tied;
}
