package com.unisign.unisign

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.google.firebase.FirebaseApp
import com.unisign.unisign.data.auth.AuthManager
import com.unisign.unisign.data.sync.FavoritesSyncManager
import com.unisign.unisign.data.repository.FavoritesRepository

import com.unisign.unisign.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UniSignApp()
                }
            }
        }
    }

    external fun stringFromJNI(): String

    companion object {
        init {
            System.loadLibrary("unisign")
        }
    }
}

@Composable
fun UniSignApp() {
    val currentUser by AuthManager.currentUser.collectAsState()
    val context = LocalContext.current
    val syncManager = remember { FavoritesSyncManager.getInstance(context) }

    // remember previous user id so we can detect sign-in events
    var previousUserId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUser?.uid) {
        val isGuest = AuthManager.isGuest()
        val currentId = currentUser?.uid

        val repo = FavoritesRepository.getInstance(context)

        // detect sign-out (previousUserId != null and now null)
        if (previousUserId != null && currentId.isNullOrBlank()) {
            // copy the signed-in user's favorites to local-only entries so user retains them offline
            repo.copyFavoritesToLocalForUser(previousUserId!!)
        }

        if (!currentId.isNullOrBlank() && !isGuest) {
            // if this is a sign-in event (previous user was null or different), try to fetch remote
            if (previousUserId != currentId) {
                // Try to fetch remote favorites and replace local user favorites.
                // This will only remove local (device-only) favorites if the remote fetch succeeds.
                syncManager.fetchAndReplaceLocalFavoritesOnSignInNow()
            }

            // Always attempt a regular sync (push local changes and pull remote updates) - runs in background
            syncManager.syncNow()
        }

        previousUserId = currentId
    }

    val navController = rememberNavController()

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") { HomeScreen(navController) }
            composable("favorites") { FavoritesScreen() }
            composable("help") { HelpScreen() }
            composable("sign_to_text") { SignToTextScreen(navController) }
            composable("text_to_sign") { TextToSignScreen(navController) }
            composable("login") { LoginScreen(navController) }
            composable("signup") { SignupScreen(navController) }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    NavigationBar(containerColor = Color.White) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = true,
            onClick = { navController.navigate("home") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Info, contentDescription = "Help") },
            label = { Text("Help") },
            selected = false,
            onClick = { navController.navigate("help") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Star, contentDescription = "Favorites") },
            label = { Text("Favorites") },
            selected = false,
            onClick = { navController.navigate("favorites") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Person, contentDescription = "Account") },
            label = { Text("Account") },
            selected = false,
            onClick = { navController.navigate("login") }
        )
    }
}