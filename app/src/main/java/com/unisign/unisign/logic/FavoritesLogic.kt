package com.unisign.unisign.logic

import android.content.Context
import com.unisign.unisign.data.sync.FavoritesSyncManager
import com.unisign.unisign.data.repository.FavoritesRepository
import com.unisign.unisign.model.FavoriteTranslation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

object FavoritesLogic {
    // Keep the existing native stub for compatibility with the C++ sample
    external fun dummyFunction(): String

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun repository(context: Context): FavoritesRepository =
        FavoritesRepository.getInstance(context.applicationContext)

    fun observeFavorites(context: Context): StateFlow<List<FavoriteTranslation>> =
        repository(context).favorites

    fun getFavorites(context: Context): List<FavoriteTranslation> =
        repository(context).getFavoritesSnapshot()

    fun addFavorite(context: Context, hebrewText: String) {
        scope.launch {
            repository(context).addFavorite(hebrewText)
            // Only sync if a real (non-anonymous) user is signed in
            val currentUserId = com.unisign.unisign.data.auth.AuthManager.currentUserId()
            val isGuest = com.unisign.unisign.data.auth.AuthManager.isGuest()
            if (!currentUserId.isNullOrBlank() && !isGuest) {
                FavoritesSyncManager.getInstance(context).syncCurrentUserFavorites()
            }
        }
    }

    fun deleteFavorite(context: Context, id: Int) {
        scope.launch {
            repository(context).deleteFavorite(id)
            val currentUserId = com.unisign.unisign.data.auth.AuthManager.currentUserId()
            val isGuest = com.unisign.unisign.data.auth.AuthManager.isGuest()
            if (!currentUserId.isNullOrBlank() && !isGuest) {
                FavoritesSyncManager.getInstance(context).syncCurrentUserFavorites()
            }
        }
    }
}