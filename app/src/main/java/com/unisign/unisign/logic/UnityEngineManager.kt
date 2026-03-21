package com.unisign.unisign.logic

import android.annotation.SuppressLint
import android.app.Activity
import com.unity3d.player.UnityPlayer
import com.unity3d.player.UnityPlayerForActivityOrService // Import the Unity 6 class!

// We suppress the warning because keeping Unity alive across screens requires holding the context
@SuppressLint("StaticFieldLeak")
object UnityEngineManager {
    var player: UnityPlayer? = null

    fun getUnityPlayer(activity: Activity): UnityPlayer {
        if (player == null) {
            // Use the concrete Unity 6 class instead of the abstract parent
            player = UnityPlayerForActivityOrService(activity)

            // NOTE: If UnityPlayerForActivityOrService requires more parameters (like an interface),
            // go to your original TextToSignScreen.kt file, look at how you originally created
            // unityPlayer there, and paste that exact code here!
        }
        return player!!
    }
}