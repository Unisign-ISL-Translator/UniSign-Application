package com.unisign.unisign.logic

import android.content.Context
import com.unisign.unisign.data.repository.FavoritesRepository
import com.unisign.unisign.model.FavoriteTranslation
import kotlinx.coroutines.flow.StateFlow

object FavoritesLogic {
    // Keep the existing native stub for compatibility with the C++ sample
    external fun dummyFunction(): String

    private fun repository(context: Context): FavoritesRepository =
        FavoritesRepository.getInstance(context.applicationContext)

    fun observeFavorites(context: Context): StateFlow<List<FavoriteTranslation>> =
        repository(context).favorites

    fun getFavorites(context: Context): List<FavoriteTranslation> =
        repository(context).getFavoritesSnapshot()

    fun addFavorite(context: Context, hebrewText: String) {
        repository(context).addFavorite(hebrewText)
    }

    fun deleteFavorite(context: Context, id: Int) {
        repository(context).deleteFavorite(id)
    }
}