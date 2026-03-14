package com.unisign.unisign.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 1. Define the Data Model
data class FavoriteTranslation(
    val id: Int,
    val hebrewText: String,
    val timestamp: String
)

@Composable
fun FavoritesScreen() {
    // Shared color palette
    val backgroundColor = Color(0xFFF5F6F8)
    val primaryDark = Color(0xFF1E2B3C)

    // ToDo: change later
    // 2. Dummy Data (This simulates what you will eventually pull from a database)
    val initialData = listOf(
        FavoriteTranslation(1, "שלום, מה שלומך?", "14:30"),
        FavoriteTranslation(2, "תודה רבה לך", "13:15"),
        FavoriteTranslation(3, "בוקר טוב", "09:45"),
        FavoriteTranslation(4, "נתראה מאוחר יותר", "אתמול"),
        FavoriteTranslation(5, "איפה תחנת הרכבת?", "אתמול")
    )

    // 3. State Management (Allows the UI to update when items are deleted)
    var favoritesList by remember { mutableStateOf(initialData) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // --- Screen Title ---
        Text(
            text = "Favorites",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = primaryDark,
            modifier = Modifier.padding(bottom = 24.dp, top = 16.dp)
        )

        // --- Dynamic List ---
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp), // Space between cards
            contentPadding = PaddingValues(bottom = 80.dp) // Prevents bottom nav from hiding the last item
        ) {
            items(
                items = favoritesList,
                key = { it.id } // Helps Compose efficiently animate and track items
            ) { favorite ->
                FavoriteItemCard(
                    item = favorite,
                    onDeleteClick = {
                        // Remove the item from the list when trash can is clicked
                        favoritesList = favoritesList.filter { it.id != favorite.id }
                    },
                    onListenClick = {
                        /* TODO: Trigger Text-to-Speech */
                    }
                )
            }
        }
    }
}

// 4. The Reusable Card UI
@Composable
fun FavoriteItemCard(
    item: FavoriteTranslation,
    onDeleteClick: () -> Unit,
    onListenClick: () -> Unit
) {
    val primaryDark = Color(0xFF1E2B3C)
    val deleteRed = Color(0xFFE53935)
    val lightGrayBorder = Color(0xFFE5E7EB)
    val textGray = Color(0xFF9CA3AF)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // --- Left Side: Action Buttons ---
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Listen Button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(primaryDark)
                        .clickable { onListenClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Listen", tint = Color.White)
                }

                // Delete Button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, lightGrayBorder, RoundedCornerShape(12.dp))
                        .clickable { onDeleteClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = deleteRed)
                }
            }

            // --- Right Side: Text & Time ---
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f).padding(start = 16.dp)
            ) {
                Text(
                    text = item.hebrewText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.timestamp,
                    fontSize = 12.sp,
                    color = textGray
                )
            }
        }
    }
}