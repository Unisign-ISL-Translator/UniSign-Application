package com.unisign.unisign.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.unisign.unisign.model.FavoriteTranslation
import kotlinx.coroutines.tasks.await

class FirebaseFavoritesDataSource {
    private val firestore = FirebaseFirestore.getInstance()

    private fun favoritesCollection(userId: String) =
        firestore.collection("users").document(userId).collection("favorites")

    suspend fun fetchFavorites(userId: String): List<FavoriteTranslation> {
        val snapshot = favoritesCollection(userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val hebrewText = doc.getString("hebrewText") ?: return@mapNotNull null
            val timestamp = doc.getString("timestamp") ?: ""
            val updatedAt = doc.getLong("updatedAt") ?: 0L
            val remoteUserId = doc.getString("userId") ?: userId
            val isDeleted = doc.getBoolean("isDeleted") ?: false

            FavoriteTranslation(
                id = doc.getLong("localId")?.toInt() ?: 0,
                hebrewText = hebrewText,
                timestamp = timestamp,
                userId = remoteUserId,
                remoteId = doc.id,
                updatedAt = updatedAt,
                isDeleted = isDeleted
            )
        }
    }

    suspend fun upsertFavorite(userId: String, favorite: FavoriteTranslation): String {
        val collection = favoritesCollection(userId)
        val remoteId = favorite.remoteId ?: collection.document().id
        val payload = hashMapOf(
            "localId" to favorite.id,
            "userId" to userId,
            "hebrewText" to favorite.hebrewText,
            "timestamp" to favorite.timestamp,
            "updatedAt" to favorite.updatedAt,
            "isDeleted" to false
        )
        collection.document(remoteId).set(payload).await()
        return remoteId
    }

    suspend fun deleteFavorite(userId: String, remoteId: String) {
        favoritesCollection(userId).document(remoteId).delete().await()
    }
}

