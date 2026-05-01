package com.unisign.unisign.data.repository

import android.content.ContentValues
import android.content.Context
import com.unisign.unisign.data.local.UniSignDatabaseHelper
import com.unisign.unisign.model.FavoriteTranslation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    fun addFavorite(hebrewText: String) {
        val cleanedText = hebrewText.trim()
        if (cleanedText.isBlank()) return

        scope.launch {
            val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val values = ContentValues().apply {
                put(UniSignDatabaseHelper.COLUMN_HEBREW_TEXT, cleanedText)
                put(UniSignDatabaseHelper.COLUMN_TIMESTAMP, timestamp)
            }
            dbHelper.writableDatabase.insert(UniSignDatabaseHelper.TABLE_FAVORITES, null, values)
            refreshFavorites()
        }
    }

    fun deleteFavorite(id: Int) {
        scope.launch {
            dbHelper.writableDatabase.delete(
                UniSignDatabaseHelper.TABLE_FAVORITES,
                "${UniSignDatabaseHelper.COLUMN_ID} = ?",
                arrayOf(id.toString())
            )
            refreshFavorites()
        }
    }

    private fun refreshFavorites() {
        val readableDb = dbHelper.readableDatabase
        val result = mutableListOf<FavoriteTranslation>()
        val cursor = readableDb.query(
            UniSignDatabaseHelper.TABLE_FAVORITES,
            arrayOf(
                UniSignDatabaseHelper.COLUMN_ID,
                UniSignDatabaseHelper.COLUMN_HEBREW_TEXT,
                UniSignDatabaseHelper.COLUMN_TIMESTAMP
            ),
            null,
            null,
            null,
            null,
            "${UniSignDatabaseHelper.COLUMN_ID} DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                result.add(
                    FavoriteTranslation(
                        id = it.getInt(it.getColumnIndexOrThrow(UniSignDatabaseHelper.COLUMN_ID)),
                        hebrewText = it.getString(it.getColumnIndexOrThrow(UniSignDatabaseHelper.COLUMN_HEBREW_TEXT)),
                        timestamp = it.getString(it.getColumnIndexOrThrow(UniSignDatabaseHelper.COLUMN_TIMESTAMP))
                    )
                )
            }
        }

        _favorites.value = result
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



