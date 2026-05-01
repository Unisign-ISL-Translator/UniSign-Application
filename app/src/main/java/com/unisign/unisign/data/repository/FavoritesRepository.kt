package com.unisign.unisign.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import com.unisign.unisign.data.local.UniSignDatabaseHelper
import com.unisign.unisign.data.auth.AuthManager
import com.unisign.unisign.model.FavoriteTranslation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FavoritesRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val dbHelper = UniSignDatabaseHelper(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _favorites = MutableStateFlow<List<FavoriteTranslation>>(emptyList())
    val favorites: StateFlow<List<FavoriteTranslation>> = _favorites.asStateFlow()

    init {
        scope.launch {
            refreshFavorites()
        }
    }

    fun getFavoritesSnapshot(): List<FavoriteTranslation> = _favorites.value

    suspend fun refreshFavorites() = withContext(Dispatchers.IO) {
        val userId = AuthManager.currentUserId() ?: ""

        if (userId.isBlank()) {
            // show local-only favorites (user_id == '')
            _favorites.value = queryFavorites("", includeDeleted = false)
            return@withContext
        }

        claimLegacyFavoritesForCurrentUser(userId)
        _favorites.value = queryFavorites(userId, includeDeleted = false)
    }

    suspend fun addFavorite(hebrewText: String) = withContext(Dispatchers.IO) {
        val userId = AuthManager.currentUserId() ?: ""
        val cleanedText = hebrewText.trim()
        if (cleanedText.isBlank()) return@withContext

        val now = System.currentTimeMillis()
        val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
        val values = ContentValues().apply {
            put(UniSignDatabaseHelper.COLUMN_USER_ID, userId)
            putNull(UniSignDatabaseHelper.COLUMN_REMOTE_ID)
            put(UniSignDatabaseHelper.COLUMN_HEBREW_TEXT, cleanedText)
            put(UniSignDatabaseHelper.COLUMN_TIMESTAMP, timestamp)
            put(UniSignDatabaseHelper.COLUMN_UPDATED_AT, now)
            put(UniSignDatabaseHelper.COLUMN_IS_DELETED, 0)
        }
        dbHelper.writableDatabase.insert(UniSignDatabaseHelper.TABLE_FAVORITES, null, values)
        refreshFavoritesInternal(userId)
    }

    suspend fun deleteFavorite(id: Int) = withContext(Dispatchers.IO) {
        val userId = AuthManager.currentUserId() ?: ""
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(UniSignDatabaseHelper.COLUMN_IS_DELETED, 1)
            put(UniSignDatabaseHelper.COLUMN_UPDATED_AT, now)
        }
        dbHelper.writableDatabase.update(
            UniSignDatabaseHelper.TABLE_FAVORITES,
            values,
            "${UniSignDatabaseHelper.COLUMN_ID} = ? AND ${UniSignDatabaseHelper.COLUMN_USER_ID} = ?",
            arrayOf(id.toString(), userId)
        )
        refreshFavoritesInternal(userId)
    }

    suspend fun updateRemoteId(localId: Int, remoteId: String) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.update(
            UniSignDatabaseHelper.TABLE_FAVORITES,
            ContentValues().apply { put(UniSignDatabaseHelper.COLUMN_REMOTE_ID, remoteId) },
            "${UniSignDatabaseHelper.COLUMN_ID} = ?",
            arrayOf(localId.toString())
        )
    }

    suspend fun permanentlyDeleteFavorite(localId: Int) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete(
            UniSignDatabaseHelper.TABLE_FAVORITES,
            "${UniSignDatabaseHelper.COLUMN_ID} = ?",
            arrayOf(localId.toString())
        )
    }

    suspend fun getAllFavoritesForCurrentUser(includeDeleted: Boolean): List<FavoriteTranslation> =
        withContext(Dispatchers.IO) {
            val userId = AuthManager.currentUserId() ?: return@withContext emptyList()
            claimLegacyFavoritesForCurrentUser(userId)
            queryFavorites(userId, includeDeleted)
        }

    suspend fun upsertRemoteFavorite(userId: String, remoteFavorite: FavoriteTranslation) =
        withContext(Dispatchers.IO) {
            if (remoteFavorite.remoteId.isNullOrBlank()) return@withContext

            val existing = findByRemoteId(userId, remoteFavorite.remoteId)
            if (existing == null) {
                insertFavorite(
                    userId = userId,
                    remoteId = remoteFavorite.remoteId,
                    hebrewText = remoteFavorite.hebrewText,
                    timestamp = remoteFavorite.timestamp,
                    updatedAt = remoteFavorite.updatedAt,
                    isDeleted = false
                )
            } else if (remoteFavorite.updatedAt >= existing.updatedAt) {
                dbHelper.writableDatabase.update(
                    UniSignDatabaseHelper.TABLE_FAVORITES,
                    ContentValues().apply {
                        put(UniSignDatabaseHelper.COLUMN_HEBREW_TEXT, remoteFavorite.hebrewText)
                        put(UniSignDatabaseHelper.COLUMN_TIMESTAMP, remoteFavorite.timestamp)
                        put(UniSignDatabaseHelper.COLUMN_UPDATED_AT, remoteFavorite.updatedAt)
                        put(UniSignDatabaseHelper.COLUMN_IS_DELETED, 0)
                        put(UniSignDatabaseHelper.COLUMN_USER_ID, userId)
                    },
                    "${UniSignDatabaseHelper.COLUMN_ID} = ? AND ${UniSignDatabaseHelper.COLUMN_USER_ID} = ?",
                    arrayOf(existing.id.toString(), userId)
                )
            }
        }

    suspend fun replaceLocalFavoritesWithRemote(userId: String, remoteFavorites: List<FavoriteTranslation>) =
        withContext(Dispatchers.IO) {
            // delete existing favorites for this user
            dbHelper.writableDatabase.delete(
                UniSignDatabaseHelper.TABLE_FAVORITES,
                "${UniSignDatabaseHelper.COLUMN_USER_ID} = ?",
                arrayOf(userId)
            )

            // insert remote favorites as authoritative
            for (rf in remoteFavorites) {
                insertFavorite(
                    userId = userId,
                    remoteId = rf.remoteId,
                    hebrewText = rf.hebrewText,
                    timestamp = rf.timestamp,
                    updatedAt = rf.updatedAt,
                    isDeleted = rf.isDeleted
                )
            }
        }

    suspend fun copyFavoritesToLocalForUser(userId: String) = withContext(Dispatchers.IO) {
        // Copy visible (non-deleted) favorites for the given user into local-only entries
        val favorites = queryFavorites(userId, includeDeleted = false)
        for (f in favorites) {
            insertFavorite(
                userId = "",
                remoteId = null,
                hebrewText = f.hebrewText,
                timestamp = f.timestamp,
                updatedAt = System.currentTimeMillis(),
                isDeleted = false
            )
        }
    }

    suspend fun claimLegacyFavoritesForCurrentUser(userId: String) = withContext(Dispatchers.IO) {
        val legacyCount = DatabaseUtils.queryNumEntries(
            dbHelper.readableDatabase,
            UniSignDatabaseHelper.TABLE_FAVORITES,
            "${UniSignDatabaseHelper.COLUMN_USER_ID} = ?",
            arrayOf("")
        )
        if (legacyCount > 0L) {
            dbHelper.writableDatabase.update(
                UniSignDatabaseHelper.TABLE_FAVORITES,
                ContentValues().apply { put(UniSignDatabaseHelper.COLUMN_USER_ID, userId) },
                "${UniSignDatabaseHelper.COLUMN_USER_ID} = ?",
                arrayOf("")
            )
        }
    }

    private fun refreshFavoritesInternal(userId: String) {
        _favorites.value = queryFavorites(userId, includeDeleted = false)
    }

    private fun insertFavorite(
        userId: String,
        remoteId: String?,
        hebrewText: String,
        timestamp: String,
        updatedAt: Long,
        isDeleted: Boolean
    ) {
        dbHelper.writableDatabase.insert(
            UniSignDatabaseHelper.TABLE_FAVORITES,
            null,
            ContentValues().apply {
                put(UniSignDatabaseHelper.COLUMN_USER_ID, userId)
                if (remoteId == null) putNull(UniSignDatabaseHelper.COLUMN_REMOTE_ID) else put(
                    UniSignDatabaseHelper.COLUMN_REMOTE_ID,
                    remoteId
                )
                put(UniSignDatabaseHelper.COLUMN_HEBREW_TEXT, hebrewText)
                put(UniSignDatabaseHelper.COLUMN_TIMESTAMP, timestamp)
                put(UniSignDatabaseHelper.COLUMN_UPDATED_AT, updatedAt)
                put(UniSignDatabaseHelper.COLUMN_IS_DELETED, if (isDeleted) 1 else 0)
            }
        )
    }

    private fun findByRemoteId(userId: String, remoteId: String): FavoriteTranslation? {
        val cursor = dbHelper.readableDatabase.query(
            UniSignDatabaseHelper.TABLE_FAVORITES,
            arrayOf(
                UniSignDatabaseHelper.COLUMN_ID,
                UniSignDatabaseHelper.COLUMN_USER_ID,
                UniSignDatabaseHelper.COLUMN_REMOTE_ID,
                UniSignDatabaseHelper.COLUMN_HEBREW_TEXT,
                UniSignDatabaseHelper.COLUMN_TIMESTAMP,
                UniSignDatabaseHelper.COLUMN_UPDATED_AT,
                UniSignDatabaseHelper.COLUMN_IS_DELETED
            ),
            "${UniSignDatabaseHelper.COLUMN_USER_ID} = ? AND ${UniSignDatabaseHelper.COLUMN_REMOTE_ID} = ?",
            arrayOf(userId, remoteId),
            null,
            null,
            null
        )

        cursor.use {
            if (!it.moveToFirst()) return null
            return mapRow(it)
        }
    }

    private fun queryFavorites(userId: String, includeDeleted: Boolean): List<FavoriteTranslation> {
        val selection = if (includeDeleted) {
            "${UniSignDatabaseHelper.COLUMN_USER_ID} = ?"
        } else {
            "${UniSignDatabaseHelper.COLUMN_USER_ID} = ? AND ${UniSignDatabaseHelper.COLUMN_IS_DELETED} = 0"
        }

        val cursor = dbHelper.readableDatabase.query(
            UniSignDatabaseHelper.TABLE_FAVORITES,
            arrayOf(
                UniSignDatabaseHelper.COLUMN_ID,
                UniSignDatabaseHelper.COLUMN_USER_ID,
                UniSignDatabaseHelper.COLUMN_REMOTE_ID,
                UniSignDatabaseHelper.COLUMN_HEBREW_TEXT,
                UniSignDatabaseHelper.COLUMN_TIMESTAMP,
                UniSignDatabaseHelper.COLUMN_UPDATED_AT,
                UniSignDatabaseHelper.COLUMN_IS_DELETED
            ),
            selection,
            arrayOf(userId),
            null,
            null,
            "${UniSignDatabaseHelper.COLUMN_UPDATED_AT} DESC, ${UniSignDatabaseHelper.COLUMN_ID} DESC"
        )

        val result = mutableListOf<FavoriteTranslation>()
        cursor.use {
            while (it.moveToNext()) {
                result.add(mapRow(it))
            }
        }
        return result
    }

    private fun mapRow(cursor: Cursor): FavoriteTranslation {
        val remoteIndex = cursor.getColumnIndexOrThrow(UniSignDatabaseHelper.COLUMN_REMOTE_ID)
        return FavoriteTranslation(
            id = cursor.getInt(cursor.getColumnIndexOrThrow(UniSignDatabaseHelper.COLUMN_ID)),
            userId = cursor.getString(cursor.getColumnIndexOrThrow(UniSignDatabaseHelper.COLUMN_USER_ID)),
            remoteId = if (cursor.isNull(remoteIndex)) null else cursor.getString(remoteIndex),
            hebrewText = cursor.getString(cursor.getColumnIndexOrThrow(UniSignDatabaseHelper.COLUMN_HEBREW_TEXT)),
            timestamp = cursor.getString(cursor.getColumnIndexOrThrow(UniSignDatabaseHelper.COLUMN_TIMESTAMP)),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(UniSignDatabaseHelper.COLUMN_UPDATED_AT)),
            isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow(UniSignDatabaseHelper.COLUMN_IS_DELETED)) == 1
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: FavoritesRepository? = null

        fun getInstance(context: Context): FavoritesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FavoritesRepository(context).also { INSTANCE = it }
            }
        }
    }
}



