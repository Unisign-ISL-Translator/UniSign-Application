package com.unisign.unisign.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.unisign.unisign.data.auth.AuthManager

@Composable
fun HomeScreen(navController: NavHostController) {
    val currentUser by AuthManager.currentUser.collectAsState()
    // copy to a local non-delegated val so Kotlin can smart-cast safely
    val user = currentUser
    val isGuest = user == null || user.isAnonymous == true
    val displayName = if (isGuest) "Guest" else (user.email ?: "User")

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayName,
                fontSize = 14.sp,
                color = Color(0xFF6B7280)
            )

            // show sign out only for non-guest signed-in users
            if (!isGuest) {
                TextButton(onClick = { AuthManager.signOut() }) {
                    Text("Sign out")
                }
            }
        }

        Text(
            text = "UniSign",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 48.dp, top = 24.dp)
        )

        MenuActionCard("Sign to Text", "Use your camera", Icons.Default.Videocam) {
            navController.navigate("sign_to_text")
        }
        Spacer(modifier = Modifier.height(16.dp))

        MenuActionCard("Text to Sign", "Avatar interpretation", Icons.Default.Person) {
            navController.navigate("text_to_sign")
        }
        Spacer(modifier = Modifier.height(16.dp))

        MenuActionCard("Upload Video", "Translate from gallery", Icons.Default.Folder) { }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B3A4A))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.LightGray, fontSize = 14.sp)
            }
        }
    }
}