package com.unisign.unisign.data.sync

import android.content.Context
import com.unisign.unisign.data.auth.AuthManager
import com.unisign.unisign.data.remote.FirebaseFavoritesDataSource
import com.unisign.unisign.data.repository.FavoritesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FavoritesSyncManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val repository = FavoritesRepository.getInstance(appContext)
    private val remoteDataSource = FirebaseFavoritesDataSource()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    suspend fun syncCurrentUserFavorites() {
        mutex.withLock {
            val userId = AuthManager.currentUserId() ?: return

            repository.claimLegacyFavoritesForCurrentUser(userId)

            val localFavorites = repository.getAllFavoritesForCurrentUser(includeDeleted = true)
            for (favorite in localFavorites) {
                if (favorite.isDeleted) {
                    favorite.remoteId?.let { remoteDataSource.deleteFavorite(userId, it) }
                    repository.permanentlyDeleteFavorite(favorite.id)
                } else {
                    val remoteId = remoteDataSource.upsertFavorite(userId, favorite)
                    if (favorite.remoteId != remoteId) {
                        repository.updateRemoteId(favorite.id, remoteId)
                    }
                }
            }

            // Try to fetch remote favorites; if network fails, bail out gracefully and keep local data
            try {
                val remoteFavorites = remoteDataSource.fetchFavorites(userId)
                for (remoteFavorite in remoteFavorites) {
                    repository.upsertRemoteFavorite(userId, remoteFavorite)
                }
            } catch (ex: Exception) {
                // network or firestore error; keep local state and schedule sync later
                return
            }

            repository.refreshFavorites()
        }
    }

    fun syncNow() {
        scope.launch {
            syncCurrentUserFavorites()
        }
    }

    suspend fun fetchAndReplaceLocalFavoritesOnSignIn() {
        mutex.withLock {
            val userId = AuthManager.currentUserId() ?: return

            // attempt to fetch remote favorites; if fails, do not delete local-only favorites
            val remoteFavorites = try {
                remoteDataSource.fetchFavorites(userId)
            } catch (ex: Exception) {
                // offline or network issue - do not remove local favorites
                return
            }

            // replace local favorites for this user with remote ones
            repository.replaceLocalFavoritesWithRemote(userId, remoteFavorites)
            repository.refreshFavorites()
        }
    }

    fun fetchAndReplaceLocalFavoritesOnSignInNow() {
        scope.launch {
            fetchAndReplaceLocalFavoritesOnSignIn()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: FavoritesSyncManager? = null

        fun getInstance(context: Context): FavoritesSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FavoritesSyncManager(context).also { INSTANCE = it }
            }
        }
    }
}

