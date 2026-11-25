// AI assistance was used for implementation support.
import {HttpsError} from "firebase-functions/v2/https";

// What every callable function of the app shares: where it runs, and how it reads a document ID.
export const callableOptions = {region: "europe-west1", maxInstances: 2};

export function isDocumentId(value: unknown): value is string {
  return typeof value === "string" && !!value.trim() &&
    !value.includes("/") && value !== "." && value !== "..";
}

export function documentId(value: unknown): string {
  if (!isDocumentId(value)) {
    throw new HttpsError("invalid-argument", "Invalid document ID");
  }
  return value;
}
