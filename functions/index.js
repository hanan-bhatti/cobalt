/**
 * Cloud Functions for Second Brain / Cobalt (100% 2nd Gen)
 * 
 * Features:
 * 1. Account Deletion (Callable & Firestore trigger): Wipes Firestore and Cloud Storage, then deletes Auth.
 * 2. Storage Quota Enforcement: Enforces single-file (50MB) and aggregate (512MB) limits.
 * 3. Storage Recalculation: Self-healing callable function to re-synchronize storage consumption.
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onObjectFinalized, onObjectDeleted } = require("firebase-functions/v2/storage");
const { onDocumentDeleted } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();
const storage = admin.storage();

const MAX_STORAGE_BYTES = 512 * 1024 * 1024; // 512 Megabytes
const MAX_SINGLE_FILE_BYTES = 50 * 1024 * 1024; // 50 Megabytes
const STORAGE_BUCKET = process.env.STORAGE_BUCKET || "second-brain-11.firebasestorage.app";

/**
 * Callable Cloud Function: Complete GDPR/CCPA user deletion.
 * Securely deletes all user Firestore data, Cloud Storage objects, and Auth account.
 */
exports.deleteUserAccountAndData = onCall(async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "User must be authenticated to delete account.");
  }

  const uid = request.auth.uid;
  logger.info(`Initiating complete account deletion for user: ${uid}`);

  // 1. Recursive Firestore purge
  try {
    const userDocRef = db.collection("users").doc(uid);
    await db.recursiveDelete(userDocRef);
    logger.info(`Successfully purged Firestore documents and subcollections for ${uid}`);
  } catch (err) {
    logger.error(`Error deleting Firestore data for ${uid}:`, err);
  }

  // 2. Cloud Storage purge
  try {
    const bucket = storage.bucket(STORAGE_BUCKET);
    const prefix = `users/${uid}/`;
    await bucket.deleteFiles({ prefix });
    logger.info(`Successfully purged Cloud Storage files under ${prefix}`);
  } catch (err) {
    logger.error(`Error deleting Cloud Storage files for ${uid}:`, err);
  }

  // 3. Delete Firebase Auth account
  try {
    await admin.auth().deleteUser(uid);
    logger.info(`Successfully deleted Firebase Auth account for ${uid}`);
  } catch (err) {
    logger.error(`Error deleting Auth user ${uid}:`, err);
    throw new HttpsError("internal", "Failed to delete user authentication record.");
  }

  return { success: true };
});

/**
 * Fallback Firestore trigger: If a user document is deleted via Console/Admin,
 * clean up any remaining Storage files for that user.
 */
exports.onUserDocumentDeleted = onDocumentDeleted("users/{userId}", async (event) => {
  const uid = event.params.userId;
  logger.info(`User document deleted for ${uid}, ensuring Cloud Storage files are purged.`);
  try {
    const bucket = storage.bucket(STORAGE_BUCKET);
    await bucket.deleteFiles({ prefix: `users/${uid}/` });
    logger.info(`Purged Cloud Storage files for deleted user document: ${uid}`);
  } catch (err) {
    logger.error(`Error purging Storage files for ${uid}:`, err);
  }
});

/**
 * Cloud Storage Upload trigger:
 * - Tracks user storage consumption in Firestore
 * - Enforces individual file size limit (50 MB) and aggregate quota (512 MB)
 * - Auto-deletes files that violate quota constraints
 */
exports.enforceStorageQuotaOnUpload = onObjectFinalized({ bucket: STORAGE_BUCKET, region: "us-east1" }, async (event) => {
  const filePath = event.data.name; // e.g. "users/<uid>/media/<itemId>.jpg"
  if (!filePath || !filePath.startsWith("users/")) {
    return;
  }

  const parts = filePath.split("/");
  if (parts.length < 3) {
    return;
  }

  const uid = parts[1];
  const fileSize = parseInt(event.data.size, 10) || 0;
  const bucketName = event.data.bucket;

  if (fileSize <= 0) {
    return;
  }

  const bucket = storage.bucket(bucketName);
  const userRef = db.collection("users").doc(uid);

  // Layer 1 validation: Individual file size ceiling (50 MB)
  if (fileSize > MAX_SINGLE_FILE_BYTES) {
    logger.warn(`File ${filePath} (${fileSize} bytes) exceeds single-file limit (${MAX_SINGLE_FILE_BYTES} bytes). Auto-deleting.`);
    try {
      await bucket.file(filePath).delete();
      await userRef.set({
        storageAlert: "Upload rejected: File exceeds the 50 MB single-file limit.",
        lastAlertAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
    } catch (err) {
      logger.error(`Failed to delete oversized file ${filePath}:`, err);
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
      logger.warn(`User ${uid} exceeded aggregate quota of 512 MB. New usage would be ${newUsage} bytes. Deleting ${filePath}.`);
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
      logger.info(`Updated storage for user ${uid}: added ${fileSize} bytes (now ~${newUsage} bytes).`);
    }
  } catch (err) {
    logger.error(`Error checking quota for user ${uid}:`, err);
  }
});

/**
 * Cloud Storage Delete trigger:
 * Decrements the user's usedStorageBytes counter in Firestore.
 */
exports.updateStorageQuotaOnDelete = onObjectDeleted({ bucket: STORAGE_BUCKET, region: "us-east1" }, async (event) => {
  const filePath = event.data.name;
  if (!filePath || !filePath.startsWith("users/")) {
    return;
  }

  const parts = filePath.split("/");
  if (parts.length < 3) {
    return;
  }

  const uid = parts[1];
  const fileSize = parseInt(event.data.size, 10) || 0;

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
        logger.info(`Decremented storage for user ${uid} by ${decrementAmount} bytes.`);
      }
    }
  } catch (error) {
    logger.error(`Error updating usedStorageBytes after deletion for ${uid}:`, error);
  }
});

/**
 * Callable HTTPS Cloud Function:
 * Reconciles actual storage footprint across Cloud Storage objects.
 */
exports.recalculateUserStorage = onCall(async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "User must be authenticated to recalculate storage.");
  }

  const uid = request.auth.uid;
  const bucket = storage.bucket(STORAGE_BUCKET);
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

    logger.info(`Recalculated storage for user ${uid}: ${totalBytes} bytes across ${files.length} files.`);
    return {
      success: true,
      totalFiles: files.length,
      usedStorageBytes: totalBytes,
      maxStorageBytes: MAX_STORAGE_BYTES
    };
  } catch (error) {
    logger.error(`Failed recalculating storage for ${uid}:`, error);
    throw new HttpsError("internal", "Failed to recalculate storage usage.");
  }
});
