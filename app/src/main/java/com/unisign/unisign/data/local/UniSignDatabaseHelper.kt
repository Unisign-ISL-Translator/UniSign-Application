package com.unisign.unisign.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class UniSignDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_FAVORITES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USER_ID TEXT NOT NULL,
                $COLUMN_REMOTE_ID TEXT,
                $COLUMN_HEBREW_TEXT TEXT NOT NULL,
                $COLUMN_TIMESTAMP TEXT NOT NULL
                ,$COLUMN_UPDATED_AT INTEGER NOT NULL DEFAULT 0
                ,$COLUMN_IS_DELETED INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_FAVORITES ADD COLUMN $COLUMN_USER_ID TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $TABLE_FAVORITES ADD COLUMN $COLUMN_REMOTE_ID TEXT")
            db.execSQL("ALTER TABLE $TABLE_FAVORITES ADD COLUMN $COLUMN_UPDATED_AT INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_FAVORITES ADD COLUMN $COLUMN_IS_DELETED INTEGER NOT NULL DEFAULT 0")
        }
    }

    companion object {
        const val DATABASE_NAME = "unisign.db"
        const val DATABASE_VERSION = 1

        const val TABLE_FAVORITES = "favorites"
        const val COLUMN_ID = "id"
        const val COLUMN_USER_ID = "user_id"
        const val COLUMN_REMOTE_ID = "remote_id"
        const val COLUMN_HEBREW_TEXT = "hebrew_text"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_IS_DELETED = "is_deleted"
    }
}

