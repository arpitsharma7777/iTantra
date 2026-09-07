package com.itantra.app.core.metrics

enum class PipelineStage { IDLE, STT, ENCODE, SEND, RECEIVE, TTS, COMPLETED, ERROR }

data class PerformanceMetrics(
    val sttModelLoadTimeMs: Long? = null,
    val sttRecognitionLatencyMs: Long? = null,
    val encodingLatencyMs: Long? = null,
    val transmissionLatencyMs: Long? = null,
    val ttsStartLatencyMs: Long? = null,
    val endToEndLatencyMs: Long? = null,
    val lastMessageSizeBytes: Int? = null,
    val wordErrorRate: Float? = null,
    val messagesSent: Int = 0,
    val messagesReceived: Int = 0,
    val currentMessageId: String? = null,
    val pipelineStage: PipelineStage = PipelineStage.IDLE
)
