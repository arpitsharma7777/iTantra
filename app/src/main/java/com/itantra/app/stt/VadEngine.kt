package com.itantra.app.stt

/**
 * Abstraction for Voice Activity Detection engines.
 * Implementations analyze audio frames and return a speech probability [0.0, 1.0].
 */
interface VadEngine {
    fun initialize()
    fun processFrame(frame: ShortArray): Float
    fun resetState()
    fun release()
}
