package com.itantra.app.stt

import com.itantra.app.core.model.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class SttManager {

    private val _result = MutableStateFlow("")

    suspend fun startListening() {
        // Placeholder for STT start logic
    }

    suspend fun stopListening() {
        // Placeholder for STT stop logic
    }

    fun setLanguage(language: Language) {
        // Placeholder for setting language
    }

    fun getResult(): Flow<String> = _result
}
