import {onDocumentWritten} from "firebase-functions/v2/firestore";
import {onSchedule} from "firebase-functions/v2/scheduler";
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

// Shared positions are refreshed every 20 seconds, so an entry this old was left by a client that
// could not remove it, e.g. one that signed out or was uninstalled while sharing. The security
// rules only let users remove their own entry, so nobody else could.
const STALE_LOCATION_AGE_MS = 60 * 60 * 1000;

export const purgeStaleLocations = onSchedule(
  {schedule: "every 15 minutes", region: "europe-west1"},
  async () => {
    const cutoff = Date.now() - STALE_LOCATION_AGE_MS;
    const trips = await rtdb.ref("locations").get();
    const removals: Record<string, null> = {};
    trips.forEach((trip) => {
      trip.forEach((member) => {
        const updatedAt = member.child("updatedAt").val();
        if (typeof updatedAt !== "number" || updatedAt < cutoff) {
          removals[`locations/${trip.key}/${member.key}`] = null;
        }
      });
    });
    // A member that resumes sharing meanwhile simply republishes at its next update.
    if (Object.keys(removals).length > 0) {
      await rtdb.ref().update(removals);
    }
  });
