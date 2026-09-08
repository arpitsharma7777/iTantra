package com.itantra.app.evaluation

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.io.RandomAccessFile

/**
 * Collects device information for benchmark context.
 */
class BenchmarkDeviceCollector(private val context: Context) {

    fun collect(): DeviceInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        return DeviceInfo(
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            androidSdkInt = Build.VERSION.SDK_INT,
            cpuArchitecture = getCpuArchitecture(),
            totalRamMb = memInfo.totalMem / (1024 * 1024),
            availableRamMb = memInfo.availMem / (1024 * 1024),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuMaxFreqKhz = getCpuMaxFreqKhz()
        )
    }

    private fun getCpuArchitecture(): String {
        return try {
            val abi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            } else {
                @Suppress("DEPRECATION")
                Build.CPU_ABI
            }
            abi
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getCpuMaxFreqKhz(): Long {
        return try {
            val cpuDir = File("/sys/devices/system/cpu/")
            var maxFreq = 0L
            cpuDir.listFiles()?.filter { it.name.matches(Regex("cpu\\d+")) }?.forEach { cpu ->
                val freqFile = File(cpu, "cpufreq/cpuinfo_max_freq")
                if (freqFile.exists()) {
                    val freq = freqFile.readText().trim().toLongOrNull() ?: 0L
                    if (freq > maxFreq) maxFreq = freq
                }
            }
            maxFreq
        } catch (e: Exception) {
            0L
        }
    }

    fun getStorageAvailableMb(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024)
        } catch (e: Exception) {
            -1L
        }
    }
}
