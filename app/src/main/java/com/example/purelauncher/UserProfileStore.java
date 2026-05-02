package com.example.purelauncher;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

final class UserProfileStore {

    private static final String TAG = "UserProfileStore";
    private static final String COLLECTION_USERS = "users";
    private static final String KEY_UID = "uid";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_DISPLAY_NAME = "displayName";
    private static final String KEY_ROLE = "role";
    private static final String KEY_LINKED_CHILD_UID = "linkedChildUid";
    private static final String KEY_LINKED_PARENT_UID = "linkedParentUid";
    private static final String KEY_CREATED_AT = "createdAt";
    private static final String KEY_UPDATED_AT = "updatedAt";

    private final FirebaseFirestore firestore;

    UserProfileStore() {
        firestore = FirebaseFirestore.getInstance();
    }

    Task<Void> createProfile(FirebaseUser user, String displayName, SessionPrefs.Role role) {
        if (user == null || role == null) {
            return Tasks.forException(new IllegalStateException("Missing user or role for profile creation."));
        }
        Map<String, Object> data = baseProfileMap(user, displayName, role);
        data.put(KEY_CREATED_AT, System.currentTimeMillis());
        data.put(KEY_UPDATED_AT, System.currentTimeMillis());
        return firestore.collection(COLLECTION_USERS)
                .document(user.getUid())
                .set(data, SetOptions.merge())
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create profile in Firestore", e))
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Profile successfully created in Firestore"));
    }

    Task<SessionPrefs.Role> getRole(FirebaseUser user) {
        if (user == null) {
            return Tasks.forResult(null);
        }
        return firestore.collection(COLLECTION_USERS)
                .document(user.getUid())
                .get()
                .addOnFailureListener(e -> Log.e(TAG, "Failed to fetch role", e))
                .continueWith(task -> parseRole(task.getResult()));
    }

    Task<Void> setLinkedChildUid(FirebaseUser parentUser, String childUid) {
        if (parentUser == null || childUid == null || childUid.trim().isEmpty()) {
            return Tasks.forException(new IllegalStateException("Missing parent user or child uid."));
        }
        String parentUid = parentUser.getUid();
        String normalizedChildUid = childUid.trim();
        DocumentReference parentRef = firestore.collection(COLLECTION_USERS).document(parentUid);
        DocumentReference childRef = firestore.collection(COLLECTION_USERS).document(normalizedChildUid);

        Task<Void> linkTask = firestore.runTransaction(transaction -> {
            DocumentSnapshot parentSnapshot = transaction.get(parentRef);
            DocumentSnapshot childSnapshot = transaction.get(childRef);

            String currentLinkedChildUid = parentSnapshot.getString(KEY_LINKED_CHILD_UID);
            String currentLinkedParentUid = childSnapshot.getString(KEY_LINKED_PARENT_UID);

            if (currentLinkedParentUid != null
                    && !currentLinkedParentUid.trim().isEmpty()
                    && !parentUid.equals(currentLinkedParentUid.trim())) {
                throw new IllegalStateException("This child is already linked to another parent.");
            }

            if (currentLinkedChildUid != null
                    && !currentLinkedChildUid.trim().isEmpty()
                    && !normalizedChildUid.equals(currentLinkedChildUid.trim())) {
                DocumentReference previousChildRef = firestore.collection(COLLECTION_USERS)
                        .document(currentLinkedChildUid.trim());
                DocumentSnapshot previousChildSnapshot = transaction.get(previousChildRef);
                String previousChildParentUid = previousChildSnapshot.getString(KEY_LINKED_PARENT_UID);
                if (parentUid.equals(previousChildParentUid)) {
                    transaction.update(previousChildRef, KEY_LINKED_PARENT_UID, FieldValue.delete());
                }
            }

            Map<String, Object> parentUpdate = new HashMap<>();
            parentUpdate.put(KEY_LINKED_CHILD_UID, normalizedChildUid);
            parentUpdate.put(KEY_UPDATED_AT, System.currentTimeMillis());
            transaction.set(parentRef, parentUpdate, SetOptions.merge());

            Map<String, Object> childUpdate = new HashMap<>();
            childUpdate.put(KEY_LINKED_PARENT_UID, parentUid);
            childUpdate.put(KEY_UPDATED_AT, System.currentTimeMillis());
            transaction.set(childRef, childUpdate, SetOptions.merge());

            return null;
        });
        linkTask.addOnFailureListener(e -> Log.e(TAG, "Failed to link child", e));
        return linkTask;
    }

    Task<Void> unlinkLinkedChild(FirebaseUser parentUser) {
        if (parentUser == null) {
            return Tasks.forException(new IllegalStateException("Missing parent user."));
        }
        String parentUid = parentUser.getUid();
        DocumentReference parentRef = firestore.collection(COLLECTION_USERS).document(parentUid);

        Task<Void> unlinkTask = firestore.runTransaction(transaction -> {
            DocumentSnapshot parentSnapshot = transaction.get(parentRef);
            String linkedChildUid = parentSnapshot.getString(KEY_LINKED_CHILD_UID);
            if (linkedChildUid == null || linkedChildUid.trim().isEmpty()) {
                return null;
            }

            String normalizedChildUid = linkedChildUid.trim();
            DocumentReference childRef = firestore.collection(COLLECTION_USERS).document(normalizedChildUid);
            DocumentSnapshot childSnapshot = transaction.get(childRef);
            String linkedParentUid = childSnapshot.getString(KEY_LINKED_PARENT_UID);

            transaction.update(parentRef, KEY_LINKED_CHILD_UID, FieldValue.delete(), KEY_UPDATED_AT, System.currentTimeMillis());
            if (parentUid.equals(linkedParentUid)) {
                transaction.update(childRef, KEY_LINKED_PARENT_UID, FieldValue.delete(), KEY_UPDATED_AT, System.currentTimeMillis());
            }

            return null;
        });
        unlinkTask.addOnFailureListener(e -> Log.e(TAG, "Failed to unlink child", e));
        return unlinkTask;
    }

    Task<String> getLinkedChildUid(FirebaseUser user) {
        if (user == null) {
            return Tasks.forResult(null);
        }
        return firestore.collection(COLLECTION_USERS)
                .document(user.getUid())
                .get()
                .continueWith(task -> parseLinkedChild(task.getResult()));
    }

    Task<String> getLinkedParentUid(FirebaseUser user) {
        if (user == null) {
            return Tasks.forResult(null);
        }
        return firestore.collection(COLLECTION_USERS)
                .document(user.getUid())
                .get()
                .continueWith(task -> parseLinkedParent(task.getResult()));
    }

    private SessionPrefs.Role parseRole(DocumentSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return null;
        }
        String rawRole = snapshot.getString(KEY_ROLE);
        if (rawRole == null) {
            return null;
        }
        try {
            return SessionPrefs.Role.valueOf(rawRole);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String parseLinkedChild(DocumentSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return null;
        }
        return snapshot.getString(KEY_LINKED_CHILD_UID);
    }

    private String parseLinkedParent(DocumentSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return null;
        }
        return snapshot.getString(KEY_LINKED_PARENT_UID);
    }

    private Map<String, Object> baseProfileMap(FirebaseUser user, String displayName, SessionPrefs.Role role) {
        Map<String, Object> data = new HashMap<>();
        data.put(KEY_UID, user.getUid());
        data.put(KEY_EMAIL, user.getEmail());
        data.put(KEY_DISPLAY_NAME, displayName == null ? "" : displayName);
        data.put(KEY_ROLE, role.name());
        return data;
    }
}
