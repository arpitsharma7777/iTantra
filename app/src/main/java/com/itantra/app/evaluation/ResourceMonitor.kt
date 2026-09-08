package com.itantra.app.evaluation

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Monitors runtime RAM and CPU usage.
 *
 * CPU measurement on Android is approximate. Process-level CPU requires reading
 * /proc/self/stat at intervals. This implementation uses a sampling approach and
 * documents the approximation limitation.
 *
 * Limitations:
 * - CPU percentage is approximate (based on /proc/self/stat delta)
 * - PSS requires ActivityManager which may be restricted on some OEM ROMs
 * - Temperature may not be available on all devices
 */
class ResourceMonitor(private val context: Context) {

    companion object {
        private const val TAG = "ResourceMonitor"
        private const val CPU_SAMPLE_INTERVAL_MS = 100L
    }

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val handler = Handler(Looper.getMainLooper())
    private val isMonitoring = AtomicBoolean(false)

    // CPU tracking state
    private var prevCpuIdle: Long = 0
    private var prevCpuTotal: Long = 0
    private var prevProcessCpuTime: Long = 0
    private var prevProcessUpTime: Long = 0

    /**
     * Takes an instantaneous snapshot of current resource usage.
     */
    fun snapshot(): ResourceSnapshot {
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)

        return ResourceSnapshot(
            timestamp = System.currentTimeMillis(),
            pssKb = memInfo.totalPss.toLong(),
            javaHeapKb = memInfo.dalvikPss.toLong(),
            nativeHeapKb = memInfo.nativePss.toLong(),
            cpuUsagePercent = readCpuUsage(),
            batteryLevel = getBatteryLevel(),
            isCharging = isCharging(),
            temperature = getTemperature()
        )
    }

    /**
     * Measures average CPU usage over a specified duration.
     * Returns a ResourceSnapshot with the average CPU and current memory.
     */
    fun measureOverDuration(durationMs: Long): ResourceSnapshot {
        val startSnapshot = snapshot()
        val cpuSamples = mutableListOf<Float>()

        val latch = CountDownLatch(1)
        val startTime = System.currentTimeMillis()

        // Take initial CPU reading
        initCpuTracking()

        val sampleRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < durationMs) {
                    cpuSamples.add(readCpuUsage())
                    handler.postDelayed(this, CPU_SAMPLE_INTERVAL_MS)
                } else {
                    latch.countDown()
                }
            }
        }

        handler.post(sampleRunnable)
        latch.await(durationMs + 500, TimeUnit.MILLISECONDS)

        val avgCpu = if (cpuSamples.isNotEmpty()) cpuSamples.average().toFloat() else 0f
        val peakCpu = if (cpuSamples.isNotEmpty()) cpuSamples.max() else 0f

        val endSnapshot = snapshot()

        Log.d(TAG, "CPU measurement over ${durationMs}ms: avg=${avgCpu}%, peak=${peakCpu}%, samples=${cpuSamples.size}")

        return endSnapshot.copy(
            cpuUsagePercent = avgCpu,
            cpuMeasurementDurationMs = durationMs
        )
    }

    /**
     * Monitors resources during a measured operation.
     * Returns a pair of (peak snapshot, operation result).
     */
    fun <T> monitorOperation(operation: () -> T): MonitoredResult<T> {
        val startSnapshot = snapshot()
        initCpuTracking()

        val startTimeNs = System.nanoTime()
        val result = operation()
        val elapsedNs = System.nanoTime() - startTimeNs

        val endSnapshot = snapshot()

        return MonitoredResult(
            result = result,
            startSnapshot = startSnapshot,
            peakSnapshot = endSnapshot,
            elapsedMs = elapsedNs / 1_000_000
        )
    }

    private fun readCpuUsage(): Float {
        return try {
            val cpuInfo = readCpuTimes() ?: return 0f
            val processTimes = readProcessCpuTime() ?: return 0f

            val cpuIdle = cpuInfo.idle
            val cpuTotal = cpuInfo.total
            val processCpuTime = processTimes.first
            val processUpTime = processTimes.second

            val cpuDeltaTotal = cpuTotal - prevCpuTotal
            val cpuDeltaIdle = cpuIdle - prevCpuIdle
            val processDelta = processCpuTime - prevProcessCpuTime
            val upTimeDelta = processUpTime - prevProcessUpTime

            prevCpuIdle = cpuIdle
            prevCpuTotal = cpuTotal
            prevProcessCpuTime = processCpuTime
            prevProcessUpTime = processUpTime

            if (cpuDeltaTotal <= 0 || upTimeDelta <= 0) return 0f

            val systemCpuPercent = ((cpuDeltaTotal - cpuDeltaIdle).toFloat() / cpuDeltaTotal) * 100f
            val processCpuPercent = (processDelta.toFloat() / upTimeDelta) * 100f

            // Return process-level CPU usage, capped at 100%
            processCpuPercent.coerceIn(0f, 100f)
        } catch (e: Exception) {
            Log.w(TAG, "CPU read error: ${e.message}")
            0f
        }
    }

    private fun initCpuTracking() {
        readCpuTimes()?.let {
            prevCpuIdle = it.idle
            prevCpuTotal = it.total
        }
        readProcessCpuTime()?.let {
            prevProcessCpuTime = it.first
            prevProcessUpTime = it.second
        }
    }

    private data class CpuTimes(val idle: Long, val total: Long)

    private fun readCpuTimes(): CpuTimes? {
        return try {
            val line = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") } ?: return null
            val parts = line.split("\\s+".toRegex()).drop(1).map { it.toLong() }
            val idle = parts.getOrElse(3) { 0L }
            val total = parts.sum()
            CpuTimes(idle, total)
        } catch (e: Exception) {
            null
        }
    }

    private fun readProcessCpuTime(): Pair<Long, Long>? {
        return try {
            val pid = android.os.Process.myPid()
            val statFile = File("/proc/$pid/stat")
            val stat = statFile.readText()
            val parts = stat.split(" ")
            // fields 14 (utime) and 15 (stime) are CPU time in clock ticks
            val utime = parts.getOrElse(13) { "0" }.toLongOrNull() ?: 0L
            val stime = parts.getOrElse(14) { "0" }.toLongOrNull() ?: 0L
            val cpuTime = utime + stime

            val uptimeFile = File("/proc/$pid/stat")
            val uptime = System.currentTimeMillis()

            Pair(cpuTime, uptime)
        } catch (e: Exception) {
            null
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryIntent = context.registerReceiver(null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        } catch (e: Exception) {
            -1
        }
    }

    private fun isCharging(): Boolean {
        return try {
            val batteryIntent = context.registerReceiver(null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }

    private fun getTemperature(): Float {
        return try {
            val thermalFiles = listOf(
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/thermal/thermal_zone1/temp"
            )
            for (path in thermalFiles) {
                val file = File(path)
                if (file.exists()) {
                    val temp = file.readText().trim().toFloatOrNull() ?: continue
                    return if (temp > 1000) temp / 1000f else temp
                }
            }
            -1f
        } catch (e: Exception) {
            -1f
        }
    }
}

data class MonitoredResult<T>(
    val result: T,
    val startSnapshot: ResourceSnapshot,
    val peakSnapshot: ResourceSnapshot,
    val elapsedMs: Long
)
