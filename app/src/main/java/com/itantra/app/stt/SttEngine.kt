package com.itantra.app.stt

import android.content.Context
import com.itantra.app.core.model.Language

interface SttEngine {
    fun initialize(context: Context)
    fun prepareLanguage(language: Language): Boolean
    fun transcribe(audioSamples: ShortArray, sampleRate: Int, language: Language): String
    fun release()
}
