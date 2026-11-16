
import {createHash} from "node:crypto";
import {getAuth} from "firebase-admin/auth";
import {getFirestore} from "firebase-admin/firestore";
import {HttpsError, onCall} from "firebase-functions/v2/https";

const normalize = (value: string): string => value.trim().toLowerCase();

export const reclaimEmailLookup = onCall(
  {region: "europe-west1", maxInstances: 2},
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in first");
    }

    const rawEmail = request.data?.email;

    if (
      typeof rawEmail !== "string" ||
      rawEmail.length > 254 ||
      !rawEmail.includes("@")
    ) {
      throw new HttpsError("invalid-argument", "Invalid email");
    }

    const email = normalize(rawEmail);
    const uid = request.auth.uid;
    const auth = getAuth();

    // Read the current account from Firebase Auth, not the possibly
    // outdated email_verified claim in the caller's ID token.
    const currentUser = await auth.getUser(uid);

    if (
      !currentUser.emailVerified ||
      normalize(currentUser.email ?? "") !== email
    ) {
      throw new HttpsError("permission-denied", "Verified email required");
    }

    // Confirm that Firebase Auth currently associates this address
    // with the requesting user.
    const emailOwner = await auth.getUserByEmail(email);

    if (emailOwner.uid !== uid) {
      throw new HttpsError("permission-denied", "Email owner mismatch");
    }

    const hash = createHash("sha256").update(email, "utf8").digest("hex");
    const reference = getFirestore().collection("userEmailLookups").doc(hash);
    const snapshot = await reference.get();

    if (!snapshot.exists || snapshot.get("uid") === uid) {
      return {reclaimed: false};
    }

    const previousUid = snapshot.get("uid");

    if (typeof previousUid !== "string" || !previousUid) {
      throw new HttpsError("failed-precondition", "Invalid existing lookup");
    }

    // Be conservative: never reclaim an index while the previous
    // account still has the same email in Firebase Auth.
    try {
      const previousUser = await auth.getUser(previousUid);

      if (normalize(previousUser.email ?? "") === email) {
        throw new HttpsError(
          "failed-precondition",
          "Previous account still owns the email",
        );
      }
    } catch (error: unknown) {
      if (error instanceof HttpsError) throw error;

      // A deleted account is a legitimate orphaned-index case.
      if ((error as {code?: string}).code !== "auth/user-not-found") {
        throw error;
      }
    }

    await getFirestore().runTransaction(async (transaction) => {
      const latest = await transaction.get(reference);

      // Do not overwrite a concurrent change to this lookup.
      if (!latest.exists || latest.get("uid") !== previousUid) {
        throw new HttpsError("aborted", "Lookup changed; retry");
      }

      transaction.set(reference, {uid});
    });

    return {reclaimed: true};
  },
);
