package com.unisign.unisign.logic

object SignToTextLogic {
    init {
        System.loadLibrary("unisign")
    }

    // Legacy C++ function - kept for compatibility
    external fun processCameraFrame(): String
}