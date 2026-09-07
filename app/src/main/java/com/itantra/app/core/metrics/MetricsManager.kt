package com.itantra.app.core.metrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared singleton for recording and tracking performance metrics across the pipeline.
 */
object MetricsManager {

    private val _metrics = MutableStateFlow(PerformanceMetrics())
    val metrics: StateFlow<PerformanceMetrics> = _metrics.asStateFlow()

    // Internal timing data per message ID
    private data class MessageTimingData(
        val startTimeMs: Long,
        var sttEndTimeMs: Long? = null,
        var encodingEndTimeMs: Long? = null,
        var sentTimeMs: Long? = null,
        var receivedTimeMs: Long? = null
    )

    private val messageTimestamps = ConcurrentHashMap<String, MessageTimingData>()

    fun recordSttModelLoadTime(timeMs: Long) {
        _metrics.update { it.copy(sttModelLoadTimeMs = timeMs) }
    }

    fun recordSttRecognitionLatency(timeMs: Long) {
        _metrics.update { it.copy(sttRecognitionLatencyMs = timeMs) }
    }

    fun recordEncodingLatency(timeMs: Long) {
        _metrics.update { it.copy(encodingLatencyMs = timeMs) }
    }

    fun recordTransmissionLatency(timeMs: Long) {
        _metrics.update { it.copy(transmissionLatencyMs = timeMs) }
    }

    fun recordTtsStartLatency(timeMs: Long) {
        _metrics.update { it.copy(ttsStartLatencyMs = timeMs) }
    }

    fun recordMessageSize(bytes: Int) {
        _metrics.update { it.copy(lastMessageSizeBytes = bytes) }
    }

    fun recordWordErrorRate(wer: Float) {
        _metrics.update { it.copy(wordErrorRate = wer) }
    }

    fun incrementMessagesSent() {
        _metrics.update { it.copy(messagesSent = it.messagesSent + 1) }
    }

    fun incrementMessagesReceived() {
        _metrics.update { it.copy(messagesReceived = it.messagesReceived + 1) }
    }

    fun recordEndToEndLatency(timeMs: Long, messageId: String) {
        _metrics.update { it.copy(endToEndLatencyMs = timeMs, currentMessageId = messageId) }
    }

    fun updatePipelineStage(stage: PipelineStage) {
        _metrics.update { it.copy(pipelineStage = stage) }
    }

    /**
     * Resets all metrics to default values and clears all message timers.
     */
    fun reset() {
        _metrics.value = PerformanceMetrics()
        messageTimestamps.clear()
    }

    // --- Message Correlation Functions ---

    fun startMessageTimer(messageId: String, t0: Long) {
        messageTimestamps[messageId] = MessageTimingData(startTimeMs = t0)
        _metrics.update { it.copy(currentMessageId = messageId) }
    }

    fun markSttComplete(messageId: String, t1: Long) {
        messageTimestamps[messageId]?.let { timing ->
            timing.sttEndTimeMs = t1
            val latency = t1 - timing.startTimeMs
            _metrics.update { it.copy(sttRecognitionLatencyMs = latency) }
        }
    }

    fun markEncodingComplete(messageId: String) {
        val now = System.currentTimeMillis()
        messageTimestamps[messageId]?.let { timing ->
            val prev = timing.sttEndTimeMs ?: timing.startTimeMs
            timing.encodingEndTimeMs = now
            _metrics.update { it.copy(encodingLatencyMs = now - prev) }
        }
    }

    fun markSent(messageId: String) {
        val now = System.currentTimeMillis()
        messageTimestamps[messageId]?.let { timing ->
            val prev = timing.encodingEndTimeMs ?: timing.startTimeMs
            timing.sentTimeMs = now
            incrementMessagesSent()
        }
    }

    fun markReceived(messageId: String) {
        val now = System.currentTimeMillis()
        messageTimestamps[messageId]?.let { timing ->
            timing.receivedTimeMs = now
            // Transmission latency: from sent to received
            timing.sentTimeMs?.let { sentAt ->
                _metrics.update { it.copy(transmissionLatencyMs = now - sentAt) }
            }
            incrementMessagesReceived()
        }
    }

    fun markTtsStarted(messageId: String) {
        val now = System.currentTimeMillis()
        messageTimestamps.remove(messageId)?.let { timing ->
            val e2e = now - timing.startTimeMs
            val ttsLatency = timing.receivedTimeMs?.let { now - it }

            _metrics.update {
                it.copy(
                    endToEndLatencyMs = e2e,
                    ttsStartLatencyMs = ttsLatency,
                    currentMessageId = messageId
                )
            }
        }
    }
}
