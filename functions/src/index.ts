import {onDocumentWritten} from "firebase-functions/v2/firestore";
import {initializeApp} from "firebase-admin/app";
import {getDatabase} from "firebase-admin/database";
import {getFirestore} from "firebase-admin/firestore";

initializeApp();

// The default database URL comes from the project config, so it isn't duplicated here.
const rtdb = getDatabase();

export const syncTripMember = onDocumentWritten(
  {document: "trips/{tripId}/members/{userId}", region: "europe-west1"},
  async (event) => {
    const {tripId, userId} = event.params;
    // Re-read current state: events can arrive out of order (remove then quick re-add).
    const member = await getFirestore()
      .doc(`trips/${tripId}/members/${userId}`).get();

    if (member.exists) {
      await rtdb.ref(`tripMembers/${tripId}/${userId}`).set(true);
    } else {
      await rtdb.ref().update({
        [`tripMembers/${tripId}/${userId}`]: null,
        [`locations/${tripId}/${userId}`]: null,
      });
    }
  });