package com.itantra.app.stt

import com.itantra.app.core.model.Language
import kotlinx.coroutines.flow.Flow

interface STTService {

    suspend fun startListening()

    suspend fun stopListening()

    fun setLanguage(language: Language)

    fun getResult(): Flow<String>
}
