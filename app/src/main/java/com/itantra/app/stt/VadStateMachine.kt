package com.itantra.app.stt

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the current active state of the Voice Activity Detection state machine.
 */
enum class VadState {
    IDLE,
    POSSIBLE_SPEECH,
    SPEECH,
    POSSIBLE_SILENCE
}

/**
 * Configuration parameters for the VAD State Machine hysteresis and buffering.
 * 
 * @property speechThreshold The probability [0.0, 1.0] above which a frame is considered speech.
 * @property minimumSpeechDurationMs Time in ms of continuous speech required to transition to SPEECH.
 * @property minimumSilenceDurationMs Time in ms of continuous silence required to transition back to IDLE.
 * @property speechPadMs Time in ms of raw audio to buffer and prepend to a speech utterance (pre-roll) so word starts aren't clipped.
 */
data class VadConfig(
    val speechThreshold: Float = 0.5f,
    val minimumSpeechDurationMs: Long = 96L,   // ~3 consecutive frames of speech to trigger
    val minimumSilenceDurationMs: Long = 500L, // ~15 consecutive frames of silence to end
    val speechPadMs: Long = 256L               // ~8 frames of pre-roll buffering
)

/**
 * Evaluates raw probabilities from VadEngine to determine speech boundaries with hysteresis.
 * It also manages a pre-roll audio buffer to prevent chopping the start of words.
 */
class VadStateMachine(
    private val config: VadConfig = VadConfig()
) {
    private val _state = MutableStateFlow(VadState.IDLE)
    val state: StateFlow<VadState> = _state.asStateFlow()

    companion object {
        // 512 samples at 16,000 Hz = exactly 32 ms per frame
        private const val FRAME_DURATION_MS = 32L 
    }

    private val preRollCapacity = (config.speechPadMs / FRAME_DURATION_MS).toInt().coerceAtLeast(1)
    private val triggerCapacity = (config.minimumSpeechDurationMs / FRAME_DURATION_MS).toInt()
    private val maxBufferCapacity = preRollCapacity + triggerCapacity

    private val rollingBuffer = ArrayDeque<ShortArray>(maxBufferCapacity)
    
    private var possibleSpeechDurationMs = 0L
    private var possibleSilenceDurationMs = 0L

    /**
     * Processes a single 512-sample frame and its raw speech probability.
     * 
     * @return A list of frames that should be sent to the STT engine. 
     *         - Empty during IDLE and POSSIBLE_SPEECH.
     *         - Contains the pre-roll buffer + current frame when transitioning to SPEECH.
     *         - Contains the current frame during SPEECH and POSSIBLE_SILENCE (post-roll padding).
     */
    private fun updateState(newState: VadState, reason: String) {
        if (_state.value != newState) {
            Log.d("VadStateMachine", "State Transition: ${_state.value} -> $newState ($reason)")
            _state.value = newState
        }
    }

    fun processFrame(frame: ShortArray, probability: Float): List<ShortArray> {
        val outputFrames = mutableListOf<ShortArray>()

        when (_state.value) {
            VadState.IDLE -> {
                addToRollingBuffer(frame)
                if (probability >= config.speechThreshold) {
                    updateState(VadState.POSSIBLE_SPEECH, "prob $probability >= threshold")
                    possibleSpeechDurationMs = FRAME_DURATION_MS
                    checkPossibleSpeechTransition(outputFrames)
                }
            }
            VadState.POSSIBLE_SPEECH -> {
                addToRollingBuffer(frame)
                if (probability >= config.speechThreshold) {
                    possibleSpeechDurationMs += FRAME_DURATION_MS
                    checkPossibleSpeechTransition(outputFrames)
                } else {
                    // False alarm. Drop back to IDLE.
                    updateState(VadState.IDLE, "speech false alarm (prob $probability)")
                    possibleSpeechDurationMs = 0L
                }
            }
            VadState.SPEECH -> {
                if (probability < config.speechThreshold) {
                    updateState(VadState.POSSIBLE_SILENCE, "prob $probability < threshold")
                    possibleSilenceDurationMs = FRAME_DURATION_MS
                    outputFrames.add(frame)
                } else {
                    outputFrames.add(frame)
                }
            }
            VadState.POSSIBLE_SILENCE -> {
                if (probability < config.speechThreshold) {
                    possibleSilenceDurationMs += FRAME_DURATION_MS
                    if (possibleSilenceDurationMs >= config.minimumSilenceDurationMs) {
                        // Silence confirmed. End the speech block.
                        updateState(VadState.IDLE, "silence confirmed for ${config.minimumSilenceDurationMs}ms")
                        possibleSilenceDurationMs = 0L
                        // Start pre-rolling this frame for the next potential utterance
                        addToRollingBuffer(frame)
                    } else {
                        // Still in the post-roll grace period. Send to STT.
                        outputFrames.add(frame)
                    }
                } else {
                    // Speech resumed before minimum silence duration was met. Abort silence transition.
                    updateState(VadState.SPEECH, "speech resumed during silence pad (prob $probability)")
                    possibleSilenceDurationMs = 0L
                    outputFrames.add(frame)
                }
            }
        }
        return outputFrames
    }

    private fun checkPossibleSpeechTransition(outputFrames: MutableList<ShortArray>) {
        if (possibleSpeechDurationMs >= config.minimumSpeechDurationMs) {
            updateState(VadState.SPEECH, "speech confirmed for ${config.minimumSpeechDurationMs}ms")
            
            // Output the accumulated pre-roll audio (including the frames that triggered the transition)
            outputFrames.addAll(rollingBuffer)
            rollingBuffer.clear()
            
            possibleSpeechDurationMs = 0L
        }
    }

    private fun addToRollingBuffer(frame: ShortArray) {
        rollingBuffer.addLast(frame)
        while (rollingBuffer.size > maxBufferCapacity) {
            rollingBuffer.removeFirst()
        }
    }

    /**
     * Resets the state machine. Should be called when microphone capture stops
     * or at the end of an STT session to guarantee a clean slate.
     */
    fun reset() {
        _state.value = VadState.IDLE
        rollingBuffer.clear()
        possibleSpeechDurationMs = 0L
        possibleSilenceDurationMs = 0L
    }
}