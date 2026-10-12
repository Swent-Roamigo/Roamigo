import {onDocumentWritten} from "firebase-functions/v2/firestore";
import {initializeApp} from "firebase-admin/app";
import {getDatabaseWithUrl} from "firebase-admin/database";

initializeApp();

const rtdb = getDatabaseWithUrl(
  "https://roamigo-c6cb2-default-rtdb.europe-west1.firebasedatabase.app");

/** Mirrors trips/{tripId}/members/{userId} into RTDB for the location security rules. */
export const syncTripMember = onDocumentWritten(
  {document: "trips/{tripId}/members/{userId}", region: "europe-west1"},
  async (event) => {
    const {tripId, userId} = event.params;

    if (event.data?.after.exists) {
      await rtdb.ref(`tripMembers/${tripId}/${userId}`).set(true);
    } else {
      // Member left or was removed: revoke access and stop sharing in one atomic update.
      await rtdb.ref().update({
        [`tripMembers/${tripId}/${userId}`]: null,
        [`locations/${tripId}/${userId}`]: null,
      });
    }
  });