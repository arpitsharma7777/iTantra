package com.itantra.app.tts

import com.itantra.app.core.model.Language

interface TTSService {

    suspend fun speak(text: String)

    fun stop()

    fun setLanguage(language: Language)
}
