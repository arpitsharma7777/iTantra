package com.itantra.app.tts


interface TtsEngine {
    fun initialize()
    fun synthesize(text: String, langCode: String): ShortArray
    fun release()
}