// AI assistance was used for implementation support.
import {HttpsError} from "firebase-functions/v2/https";

// What every callable function of the app shares: where it runs, and how it reads a document ID.
export const callableOptions = {region: "europe-west1", maxInstances: 2};

export function documentId(value: unknown): string {
  if (typeof value !== "string" || !value.trim() || value.includes("/") ||
      value === "." || value === "..") {
    throw new HttpsError("invalid-argument", "Invalid document ID");
  }
  return value;
}
