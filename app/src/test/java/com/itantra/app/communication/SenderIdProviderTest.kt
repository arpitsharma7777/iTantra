package com.itantra.app.communication

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SenderIdProviderTest {

    @Test
    fun `getSenderId returns same ID on subsequent calls`() {
        val fakePrefs = FakeSharedPreferences()
        val provider = SenderIdProvider(fakePrefs)

        val id1 = provider.getSenderId()
        val id2 = provider.getSenderId()

        assertEquals("Should return the same ID for the same installation", id1, id2)
    }

    @Test
    fun `getSenderId returns different IDs for different installations`() {
        val provider1 = SenderIdProvider(FakeSharedPreferences())
        val provider2 = SenderIdProvider(FakeSharedPreferences())

        val id1 = provider1.getSenderId()
        val id2 = provider2.getSenderId()

        assertNotEquals("Different installations should have different IDs", id1, id2)
    }

    // A minimal fake implementation of SharedPreferences for pure unit testing
    private class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, String?>()

        override fun getString(key: String, defValue: String?): String? = map.getOrDefault(key, defValue)

        override fun edit(): SharedPreferences.Editor = FakeEditor(map)

        // Unused by SenderIdProvider but required by interface
        override fun getAll(): MutableMap<String, *> = throw NotImplementedError()
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = throw NotImplementedError()
        override fun getInt(key: String?, defValue: Int): Int = throw NotImplementedError()
        override fun getLong(key: String?, defValue: Long): Long = throw NotImplementedError()
        override fun getFloat(key: String?, defValue: Float): Float = throw NotImplementedError()
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = throw NotImplementedError()
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val map: MutableMap<String, String?>) : SharedPreferences.Editor {
            private val tempChanges = mutableMapOf<String, String?>()

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                tempChanges[key] = value
                return this
            }

            override fun apply() {
                map.putAll(tempChanges)
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = throw NotImplementedError()
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = throw NotImplementedError()
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = throw NotImplementedError()
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = throw NotImplementedError()
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = throw NotImplementedError()
            override fun remove(key: String?): SharedPreferences.Editor = throw NotImplementedError()
            override fun clear(): SharedPreferences.Editor = throw NotImplementedError()
        }
    }
}
