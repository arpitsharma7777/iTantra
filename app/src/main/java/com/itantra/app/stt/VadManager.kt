package com.itantra.app.stt

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Handles raw inference for the Silero VAD ONNX model.
 * Does NOT contain thresholding, hysteresis, or state machine logic.
 */
class VadManager(private val context: Context) {
    companion object {
        private const val TAG = "VadManager"
        private const val MODEL_PATH = "vad/silero_vad.onnx"
        private const val EXPECTED_FRAME_SIZE = 512
        private const val SAMPLE_RATE = 16000L
    }

    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null

    // For Silero VAD v4 support
    private var hTensor: OnnxTensor? = null
    private var cTensor: OnnxTensor? = null
    
    // For Silero VAD v5 support
    private var stateTensor: OnnxTensor? = null
    
    // Sample rate tensor (required by some versions of Silero VAD)
    private var srTensor: OnnxTensor? = null

    private var isV5 = false
    private var isV4 = false

    /**
     * Loads the model and initializes the ONNX session once.
     */
    suspend fun initialize() {
        try {
            Log.d(TAG, "Initializing VadManager with model: $MODEL_PATH")
            ortEnv = OrtEnvironment.getEnvironment()
            
            val modelBytes = context.assets.open(MODEL_PATH).readBytes()
            session = ortEnv?.createSession(modelBytes, OrtSession.SessionOptions())

            val inputInfo = session?.inputInfo
            if (inputInfo == null) {
                throw IllegalStateException("Failed to read ONNX model input info")
            }

            Log.d(TAG, "ONNX Model Input Info:")
            inputInfo.forEach { (key, value) ->
                val shapeStr = (value.info as? TensorInfo)?.shape?.joinToString() ?: "Unknown"
                Log.d(TAG, "Input: $key, Shape: [$shapeStr]")
            }

            // Determine if it's V4 or V5 based on inputs programmatically
            isV5 = inputInfo.containsKey("state")
            isV4 = inputInfo.containsKey("h") && inputInfo.containsKey("c")

            if (!isV4 && !isV5) {
                Log.w(TAG, "Unrecognized Silero VAD model signature. Assuming v5 structure as fallback.")
                isV5 = true
            }

            resetState()
            kotlinx.coroutines.delay(10)
            
            Log.d(TAG, "VadManager initialized successfully. Detected model version: ${if (isV5) "v5" else "v4"}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize VadManager: ${e.message}", e)
            throw e
        }
    }

    // Pre-allocated buffer for zero-allocation real-time audio loop
    private val reusableFloatData = FloatArray(EXPECTED_FRAME_SIZE)
    private var debugLoggingEnabled = false // Toggle for guarded performance logging

    /**
     * Processes a single 512-sample ShortArray frame and returns a raw speech probability.
     */
    fun processFrame(frame: ShortArray): Float {
        if (session == null || ortEnv == null) {
            // If uninitialized (e.g. in test environment without native JNI), return 0.0f gracefully
            return 0f
        }
        
        if (frame.size != EXPECTED_FRAME_SIZE) {
            Log.e(TAG, "Invalid frame size. Expected $EXPECTED_FRAME_SIZE, got ${frame.size}. Rejecting frame.")
            return 0f
        }

        val startTime = if (debugLoggingEnabled) System.nanoTime() else 0L

        // Int16 to Float32 conversion normalized to [-1.0, 1.0] using pre-allocated array (zero allocation)
        for (i in frame.indices) {
            reusableFloatData[i] = frame[i] / 32768.0f
        }

        val inputs = mutableMapOf<String, OnnxTensor>()
        var inputTensor: OnnxTensor? = null
        var result: OrtSession.Result? = null

        try {
            val floatBuffer = FloatBuffer.wrap(reusableFloatData)
            inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, EXPECTED_FRAME_SIZE.toLong()))
            inputs["input"] = inputTensor

            if (srTensor != null) {
                inputs["sr"] = srTensor!!
            }

            if (isV5) {
                stateTensor?.let { inputs["state"] = it }
            } else if (isV4) {
                hTensor?.let { inputs["h"] = it }
                cTensor?.let { inputs["c"] = it }
            }

            result = session?.run(inputs)
            
            // Extract the speech probability (first output)
            val outputTensor = result?.get(0) as? OnnxTensor
            val speechProb = (outputTensor?.floatBuffer?.get(0)) ?: 0f

            // Update recurrent state for next inference
            updateState(result)

            if (debugLoggingEnabled) {
                val latencyMs = (System.nanoTime() - startTime) / 1_000_000.0
                Log.d(TAG, "VAD Frame Inference -> Probability: %.4f, Latency: %.2f ms".format(speechProb, latencyMs))
            }

            return speechProb
        } catch (e: Exception) {
            Log.e(TAG, "Error during VAD inference", e)
            return 0f
        } finally {
            inputTensor?.close()
            result?.close()
        }
    }

    private fun updateState(result: OrtSession.Result?) {
        result ?: return
        try {
            if (isV5) {
                val newStateOpt = result.get("stateN")
                if (newStateOpt.isPresent) {
                    val newState = newStateOpt.get() as OnnxTensor
                    val clonedState = cloneTensor(newState)
                    stateTensor?.close()
                    stateTensor = clonedState
                }
            } else if (isV4) {
                val newHOpt = result.get("hn")
                val newCOpt = result.get("cn")
                
                if (newHOpt.isPresent && newCOpt.isPresent) {
                    val clonedH = cloneTensor(newHOpt.get() as OnnxTensor)
                    val clonedC = cloneTensor(newCOpt.get() as OnnxTensor)
                    
                    hTensor?.close()
                    cTensor?.close()
                    
                    hTensor = clonedH
                    cTensor = clonedC
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update VAD state tensors", e)
        }
    }

    /**
     * Resets the recurrent state (context tensors) to zeros.
     * This must be called at the start of a new audio stream.
     */
    fun resetState() {
        val env = ortEnv ?: return
        val sess = session ?: return
        
        try {
            // Clean up existing tensors
            hTensor?.close()
            cTensor?.close()
            stateTensor?.close()
            srTensor?.close()
            
            val inputInfo = sess.inputInfo

            // Initialize sr tensor if required
            if (inputInfo.containsKey("sr")) {
                val srBuffer = LongBuffer.wrap(longArrayOf(SAMPLE_RATE))
                srTensor = OnnxTensor.createTensor(env, srBuffer, longArrayOf(1))
            }

            // Initialize state tensors with zeros dynamically based on expected shape
            if (isV5) {
                val rawShape = (inputInfo["state"]?.info as? TensorInfo)?.shape ?: longArrayOf(2, 1, 128)
                // Replace dynamic -1 dimensions with 1 (batch size)
                val stateShape = rawShape.map { if (it < 0) 1L else it }.toLongArray()
                val totalElements = stateShape.reduce { acc, l -> acc * l }.toInt()
                val zeroBuffer = FloatBuffer.wrap(FloatArray(totalElements))
                stateTensor = OnnxTensor.createTensor(env, zeroBuffer, stateShape)
                Log.d(TAG, "V5 state tensor created with shape: ${stateShape.joinToString()}, elements=$totalElements")
            } else if (isV4) {
                val hRawShape = (inputInfo["h"]?.info as? TensorInfo)?.shape ?: longArrayOf(2, 1, 64)
                val cRawShape = (inputInfo["c"]?.info as? TensorInfo)?.shape ?: longArrayOf(2, 1, 64)
                val hShape = hRawShape.map { if (it < 0) 1L else it }.toLongArray()
                val cShape = cRawShape.map { if (it < 0) 1L else it }.toLongArray()
                
                val hElements = hShape.reduce { acc, l -> acc * l }.toInt()
                val cElements = cShape.reduce { acc, l -> acc * l }.toInt()
                
                hTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(hElements)), hShape)
                cTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(cElements)), cShape)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset VAD state", e)
        }
    }
    
    /**
     * Deep clones an output tensor so its lifecycle is decoupled from the ephemeral OrtSession.Result.
     */
    private fun cloneTensor(tensor: OnnxTensor): OnnxTensor {
        val env = ortEnv ?: throw IllegalStateException("OrtEnvironment is null")
        val buffer = tensor.floatBuffer
        val capacity = buffer.capacity()
        val newFloatArray = FloatArray(capacity)
        buffer.position(0)
        buffer.get(newFloatArray)
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(newFloatArray), tensor.info.shape)
    }

    /**
     * Frees all native ONNX Runtime resources.
     */
    fun release() {
        try {
            hTensor?.close()
            cTensor?.close()
            stateTensor?.close()
            srTensor?.close()
            
            session?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing VadManager resources", e)
        } finally {
            hTensor = null
            cTensor = null
            stateTensor = null
            srTensor = null
            session = null
            ortEnv = null
        }
    }
}
