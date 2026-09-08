package com.itantra.app.tts

object OnnxRuntimeCompat {
    @Volatile
    var isAvailable: Boolean = true
        private set

    fun markUnavailable() {
        isAvailable = false
    }
}
