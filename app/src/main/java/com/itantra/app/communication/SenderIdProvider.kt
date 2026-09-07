package com.itantra.app.communication

import android.content.SharedPreferences
import java.util.UUID

/**
 * Provides a persistent unique identifier for the current user/device.
 */
class SenderIdProvider(private val prefs: SharedPreferences) {

    /**
     * Returns a unique sender ID for this installation.
     * Generates and persists a new UUID on the first call.
     */
    fun getSenderId(): String {
        val existingId = prefs.getString(KEY_SENDER_ID, null)
        if (existingId != null) {
            return existingId
        }

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_SENDER_ID, newId).apply()
        return newId
    }

    companion object {
        private const val KEY_SENDER_ID = "sender_id"
    }
}
