package com.unisign.unisign.ui.screens

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.unity3d.player.UnityPlayer
import com.unisign.unisign.logic.UnityEngineManager

@Composable
fun TextToSignScreen(navController: NavHostController) {
    // Reusing the same color palette for consistency
    val backgroundColor = Color(0xFFF5F6F8)
    val primaryDark = Color(0xFF1E2B3C)

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
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = primaryDark
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "Text To Sign",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = primaryDark
            )
        }

        // --- UNITY AVATAR VIEW ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Expands to fill available middle space
                .clip(RoundedCornerShape(24.dp)) // Forces Unity to have rounded corners!
                .background(Color.Black) // Dark background while Unity loads
        ) {
            UnityAvatarPlaceholder(modifier = Modifier.fillMaxSize())
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Text Input/Output Card ---
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
                    text = "\"?שלום, איך אפשר לעזור לך\"",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryDark
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Listen and Save Buttons ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
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
                Icon(Icons.Default.VolumeUp, contentDescription = "Listen")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Listen", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { /* TODO: Save logic */ },
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
        }
    }
}

// --- UNITY 6 WRAPPER COMPOSABLE ---
@Composable
fun UnityAvatarPlaceholder(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity

    if (activity != null) {
        // 1. Unity 6 strictly requires a lifecycle callback object to prevent crashes
        val unityPlayer = remember {
            val lifecycleEvents = object : com.unity3d.player.IUnityPlayerLifecycleEvents {
                override fun onUnityPlayerUnloaded() {}
                override fun onUnityPlayerQuitted() {}
            }
            // 2. We must use the new Activity wrapper class instead of 'UnityPlayer'
            com.unity3d.player.UnityPlayerForActivityOrService(activity, lifecycleEvents)
        }

        DisposableEffect(Unit) {
            onDispose {
                // When leaving the screen, politely tell Unity to pause its C++ thread
                unityPlayer.pause()

                // Cleanly detach the view before Compose destroys the layout
                val unityView = unityPlayer.frameLayout
                (unityView.parent as? ViewGroup)?.removeView(unityView)
            }
        }

        AndroidView(
            factory = { ctx ->
                // 2. Ask your Singleton Manager for the engine
                val unityPlayer = UnityEngineManager.getUnityPlayer(activity)

                // 3. Extract the physical View from Unity 6
                val unityView = unityPlayer.frameLayout

                // 4. CRITICAL: If you navigated away and came back, this view might
                // still be attached to the old screen. We must remove it first!
                (unityView.parent as? ViewGroup)?.removeView(unityView)

                // 5. Wake up the engine so it draws frames instead of a black box
                unityView.requestFocus()
                unityPlayer.windowFocusChanged(true)
                unityPlayer.resume()

                // 6. Wrap it in a FrameLayout and return it to Compose
                FrameLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    addView(unityView)
                }
            },
            modifier = modifier // Whatever sizing modifier you are using
        )
    }
}