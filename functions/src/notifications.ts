// AI assistance was used for implementation support.
import {createHash} from "node:crypto";
import {FieldValue, getFirestore, type Firestore} from "firebase-admin/firestore";
import {logger} from "firebase-functions";
import {onDocumentCreated, onDocumentWritten} from "firebase-functions/v2/firestore";

type NotificationEvent =
  | {type: "FRIEND_REQUEST_RECEIVED"; requestId: string; senderUserId: string}
  | {type: "TRIP_VOTE_CREATED"; tripId: string; voteId: string};

const ALREADY_EXISTS = 6; // gRPC status for a document that already exists.

/** Creates one inbox entry for a recipient and event, preserving existing entries on replay. */
export async function createNotification(
  db: Firestore,
  recipientUserId: string,
  sourceIds: readonly string[],
  event: NotificationEvent,
): Promise<void> {
  const notificationId = createHash("sha256")
    .update(JSON.stringify([event.type, ...sourceIds]))
    .digest("hex");
  const entry = db.doc(`users/${recipientUserId}/notifications/${notificationId}`);
  try {
    await entry.create({
      ...event,
      recipientUserId,
      createdAt: FieldValue.serverTimestamp(),
      isRead: false,
    });
  } catch (error) {
    if ((error as {code?: number} | null)?.code !== ALREADY_EXISTS) throw error;
  }
}

function isIdentifier(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0 &&
    !value.includes("/") && value !== "." && value !== "..";
}

export const notifyFriendRequest = onDocumentWritten(
  {document: "friendships/{documentId}", region: "europe-west1", retry: true},
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (after?.status !== "PENDING" ||
        (before && (before.status !== "DECLINED" || before.requestId === after.requestId))) {
      return;
    }

    const {documentId} = event.params;
    const {requestId, senderUserId, recipientUserId} = after;
    if (!isIdentifier(requestId) || !isIdentifier(senderUserId) ||
        !isIdentifier(recipientUserId) || senderUserId === recipientUserId) {
      logger.warn("Skipping invalid friend request notification", {documentId});
      return;
    }

    await createNotification(getFirestore(), recipientUserId, [documentId, requestId], {
      type: "FRIEND_REQUEST_RECEIVED",
      requestId,
      senderUserId,
    });
  },
);

export const notifyTripVoteCreated = onDocumentCreated(
  {document: "trips/{tripId}/votes/{voteId}", region: "europe-west1", retry: true},
  async (event) => {
    const {tripId, voteId} = event.params;
    const vote = event.data?.data();
    if (vote?.status !== "OPEN") return;
    if (vote.uid !== voteId || vote.tripId !== tripId || !isIdentifier(vote.createdByUserId)) {
      logger.warn("Skipping invalid trip vote notification", {tripId, voteId});
      return;
    }

    const db = getFirestore();
    const trip = (await db.doc(`trips/${tripId}`).get()).data();
    if (!trip) return;
    const memberIds: unknown = trip.memberIds;
    if (!Array.isArray(memberIds) || !memberIds.every(isIdentifier)) {
      logger.warn("Skipping vote notifications for invalid trip membership", {tripId, voteId});
      return;
    }

    const recipients = [...new Set(memberIds)].filter((userId) => userId !== vote.createdByUserId);
    await Promise.all(recipients.map((userId) =>
      createNotification(db, userId, [tripId, voteId], {type: "TRIP_VOTE_CREATED", tripId, voteId}),
    ));
  },
);
