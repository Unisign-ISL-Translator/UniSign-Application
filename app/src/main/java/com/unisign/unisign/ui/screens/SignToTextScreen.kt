package com.unisign.unisign.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.unisign.unisign.logic.FavoritesLogic

@Composable
fun SignToTextScreen(navController: NavHostController) {
    val context = LocalContext.current

    // Colors based on your screenshot
    val backgroundColor = Color(0xFFF5F6F8) // Very light gray background
    val primaryDark = Color(0xFF1E2B3C)    // Dark blue for text and icons
    val placeholderGray = Color(0xFF7A8696) // Gray for the camera placeholder

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp)
    ) {
        // --- Custom Top Bar ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp, top = 8.dp)
        ) {
            // White rounded Back Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable { navController.navigateUp() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = primaryDark
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "Sign To Text",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = primaryDark
            )
        }

        // --- Camera View Placeholder ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // This forces the box to expand and take up all available middle space
                .background(Color(0xFFE6E8ED), RoundedCornerShape(24.dp))
                // Custom drawing for the dashed border
                .drawBehind {
                    drawRoundRect(
                        color = Color.LightGray,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                        ),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = placeholderGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Camera View",
                    color = placeholderGray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // The translation text that can be saved as a favorite
        val translationText = "שלום, מה שלומך?"

        // --- Translation Text Card ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = translationText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryDark
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Listen and Save Buttons ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = { /* TODO: Listen logic */ },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD1D5DB))
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Listen")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Listen", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    // Save the displayed translation to favorites
                    FavoritesLogic.addFavorite(context, translationText)
                    Toast.makeText(context, "Saved to Favorites", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD1D5DB))
            ) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = "Save")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save", fontWeight = FontWeight.Bold)
            }

            //ToDo: example for how to call c++ function from kotlin
//            OutlinedButton(
//                onClick = {
//                    // 1. Call the C++ engine!
//                    val nativeResponse = SignToTextLogic.processCameraFrame()
//
//                    // 2. Display the response in a popup
//                    Toast.makeText(context, nativeResponse, Toast.LENGTH_LONG).show()
//                },
//                modifier = Modifier
//                    .weight(1f)
//                    .height(56.dp),
//                shape = RoundedCornerShape(16.dp),
//                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryDark),
//                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD1D5DB))
//            ) {
//                Icon(Icons.Default.FavoriteBorder, contentDescription = "Save")
//                Spacer(modifier = Modifier.width(8.dp))
//                Text("Save", fontWeight = FontWeight.Bold)
//            }
        }
    }
}