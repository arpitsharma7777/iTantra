package com.itantra.app.core.util

object OnnxRuntimeCompat {
    @Volatile
    var isAvailable: Boolean = true
        private set

    fun markUnavailable() {
        isAvailable = false
    }
}
