# Performance Metrics Integration Guide

This document outlines the one-line hooks required by each module to populate the `MetricsManager`.

## STT Developer Hooks
Add these to your STT implementation:

```kotlin
// After model becomes READY
MetricsManager.recordSttModelLoadTime(loadDurationMs)

// After final recognition result is available
MetricsManager.recordSttRecognitionLatency(recognitionDurationMs)

// To correlate with end-to-end timing
MetricsManager.markSttComplete(message.id, System.currentTimeMillis())
```

## Transport Developer Hooks
Add these to your networking/transport implementation:

```kotlin
// Recording compression/encoding overhead
MetricsManager.recordEncodingLatency(encodingDurationMs)

// Recording data usage
MetricsManager.recordMessageSize(encodedMessage.size)

// Mark point of egress
MetricsManager.markSent(message.id)

// On remote confirmation/acknowledgment (or local send if no ACK exists):
MetricsManager.recordTransmissionLatency(transmissionDurationMs)
MetricsManager.markReceived(message.id)
```

## TTS Developer Hooks
Add these to your TTS implementation:

```kotlin
// Time from audio received to first sample played
MetricsManager.recordTtsStartLatency(startDurationMs)

// Finalizes the end-to-end calculation for the message
MetricsManager.markTtsStarted(message.id)
```

## General Pipeline Status
Update the pipeline stage whenever the state changes:

```kotlin
MetricsManager.updatePipelineStage(PipelineStage.STT) // or ENCODE, SEND, RECEIVE, TTS, etc.
```
