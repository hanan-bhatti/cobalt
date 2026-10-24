/**
 * Cloud Functions for Second Brain / Cobalt
 * 
 * Features:
 * 1. Account Deletion Trigger: Wipes all Firestore subcollections and Cloud Storage files on Auth deletion.
 * 2. Storage Quota Enforcement: Maintains usedStorageBytes and auto-deletes files breaching 512 MB.
 * 3. Storage Recalculation: Self-healing callable function to re-synchronize storage consumption.
 */

const functions = require("firebase-functions/v1");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();
const storage = admin.storage();

const MAX_STORAGE_BYTES = 512 * 1024 * 1024; // 512 Megabytes
const MAX_SINGLE_FILE_BYTES = 50 * 1024 * 1024; // 50 Megabytes

/**
 * Triggered automatically when a Firebase Auth user account is permanently deleted.
 * Performs a complete GDPR/CCPA-compliant wipe:
 * - Recursively deletes Firestore user documents and all nested subcollections (/users/{uid}/*)
 * - Deletes all user media and blobs from Cloud Storage (users/{uid}/*)
 */
exports.cleanupUserDataOnAuthDelete = functions.auth.user().onDelete(async (user) => {
  const uid = user.uid;
  functions.logger.info(`Initiating account deletion cleanup for user: ${uid}`);

  // 1. Recursive Firestore purge
  try {
    const userDocRef = db.collection("users").doc(uid);
    await db.recursiveDelete(userDocRef);
    functions.logger.info(`Successfully deleted Firestore documents and subcollections for user: ${uid}`);
  } catch (error) {
    functions.logger.error(`Failed to delete Firestore data for user: ${uid}`, error);
  }

  // 2. Cloud Storage purge
  try {
    const bucket = storage.bucket();
    const prefix = `users/${uid}/`;
    await bucket.deleteFiles({ prefix });
    functions.logger.info(`Successfully purged all Cloud Storage media under prefix: ${prefix}`);
  } catch (error) {
    functions.logger.error(`Failed to purge Cloud Storage files for user: ${uid}`, error);
  }
});

/**
 * Triggered when a new file is uploaded to Cloud Storage.
 * - Tracks user storage consumption in Firestore
 * - Enforces individual file size limit (50 MB) and aggregate quota (512 MB)
 * - Auto-deletes files that violate quota constraints
 */
exports.enforceStorageQuotaOnUpload = functions.storage.object().onFinalize(async (object) => {
  const filePath = object.name; // e.g. "users/<uid>/media/<itemId>.jpg"
  if (!filePath || !filePath.startsWith("users/")) {
    return;
  }

  const parts = filePath.split("/");
  if (parts.length < 3) {
    return;
  }

  const uid = parts[1];
  const fileSize = parseInt(object.size, 10) || 0;
  const bucketName = object.bucket;

  if (fileSize <= 0) {
    return;
  }

  const bucket = storage.bucket(bucketName);
  const userRef = db.collection("users").doc(uid);

  // Layer 1 validation: Individual file size ceiling
  if (fileSize > MAX_SINGLE_FILE_BYTES) {
    functions.logger.warn(`File ${filePath} (${fileSize} bytes) exceeds single-file limit (${MAX_SINGLE_FILE_BYTES} bytes). Auto-deleting.`);
    try {
      await bucket.file(filePath).delete();
      await userRef.set({
        storageAlert: "Upload rejected: File exceeds the 50 MB single-file limit.",
        lastAlertAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
    } catch (err) {
      functions.logger.error(`Failed to delete oversized file ${filePath}:`, err);
    }
    return;
  }

  // Layer 2 validation: Aggregate 512 MB user quota
  try {
    const userDoc = await userRef.get();
    const currentUsage = (userDoc.exists && userDoc.data().usedStorageBytes) 
      ? Number(userDoc.data().usedStorageBytes) 
      : 0;

    const newUsage = currentUsage + fileSize;

    if (newUsage > MAX_STORAGE_BYTES) {
      functions.logger.warn(`User ${uid} exceeded aggregate quota of 512 MB. New usage would be ${newUsage} bytes. Deleting ${filePath}.`);
      await bucket.file(filePath).delete();
      await userRef.set({
        storageAlert: "Upload rejected: 512 MB aggregate storage limit reached.",
        quotaExceeded: true,
        lastAlertAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
    } else {
      await userRef.set({
        usedStorageBytes: admin.firestore.FieldValue.increment(fileSize),
        quotaExceeded: false,
        storageAlert: null,
        lastStorageUpdate: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
      functions.logger.info(`Updated storage for user ${uid}: added ${fileSize} bytes (now ~${newUsage} bytes).`);
    }
  } catch (err) {
    functions.logger.error(`Error checking quota for user ${uid}:`, err);
  }
});

/**
 * Triggered when a file is deleted from Cloud Storage.
 * Decrements the user's usedStorageBytes counter in Firestore.
 */
exports.updateStorageQuotaOnDelete = functions.storage.object().onDelete(async (object) => {
  const filePath = object.name;
  if (!filePath || !filePath.startsWith("users/")) {
    return;
  }

  const parts = filePath.split("/");
  if (parts.length < 3) {
    return;
  }

  const uid = parts[1];
  const fileSize = parseInt(object.size, 10) || 0;

  if (fileSize <= 0) {
    return;
  }

  const userRef = db.collection("users").doc(uid);
  try {
    const doc = await userRef.get();
    if (doc.exists) {
      const currentBytes = Number(doc.data().usedStorageBytes || 0);
      const decrementAmount = Math.min(currentBytes, fileSize);
      if (decrementAmount > 0) {
        await userRef.update({
          usedStorageBytes: admin.firestore.FieldValue.increment(-decrementAmount),
          quotaExceeded: false,
          lastStorageUpdate: admin.firestore.FieldValue.serverTimestamp()
        });
        functions.logger.info(`Decremented storage for user ${uid} by ${decrementAmount} bytes.`);
      }
    }
  } catch (error) {
    functions.logger.error(`Error updating usedStorageBytes after deletion for ${uid}:`, error);
  }
});

/**
 * Callable HTTPS Cloud Function to allow a user or device to recalculate their
 * exact storage footprint across Cloud Storage objects.
 */
exports.recalculateUserStorage = onCall(async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "User must be authenticated to recalculate storage.");
  }

  const uid = request.auth.uid;
  const bucket = storage.bucket();
  const prefix = `users/${uid}/`;

  try {
    const [files] = await bucket.getFiles({ prefix });
    let totalBytes = 0;

    for (const file of files) {
      const [metadata] = await file.getMetadata();
      totalBytes += parseInt(metadata.size, 10) || 0;
    }

    const userRef = db.collection("users").doc(uid);
    await userRef.set({
      usedStorageBytes: totalBytes,
      quotaExceeded: totalBytes >= MAX_STORAGE_BYTES,
      lastStorageRecalculation: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });

    functions.logger.info(`Recalculated storage for user ${uid}: ${totalBytes} bytes across ${files.length} files.`);
    return {
      success: true,
      totalFiles: files.length,
      usedStorageBytes: totalBytes,
      maxStorageBytes: MAX_STORAGE_BYTES
    };
  } catch (error) {
    functions.logger.error(`Failed recalculating storage for ${uid}:`, error);
    throw new HttpsError("internal", "Failed to recalculate storage usage.");
  }
});
