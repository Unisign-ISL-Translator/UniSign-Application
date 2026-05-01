package com.unisign.unisign.model

data class FavoriteTranslation(
    val id: Int,
    val hebrewText: String,
    val timestamp: String,
    val userId: String = "",
    val remoteId: String? = null,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)

