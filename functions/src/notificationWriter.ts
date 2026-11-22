// AI assistance was used for implementation support.
import {createHash} from "node:crypto";
import {FieldValue, type Firestore} from "firebase-admin/firestore";

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
