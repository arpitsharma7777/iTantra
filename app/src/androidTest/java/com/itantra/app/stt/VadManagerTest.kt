package com.itantra.app.stt

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log

@RunWith(AndroidJUnit4::class)
class VadManagerTest {
    @Test
    fun testVadManagerInference() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vadManager = VadManager(context)
        
        Log.d("VadManagerTest", "Initializing VadManager...")
        vadManager.initialize()
        
        Log.d("VadManagerTest", "Creating 512-sample dummy frame...")
        val dummyFrame = ShortArray(512) { 100 }
        
        Log.d("VadManagerTest", "Running inference...")
        val prob = vadManager.processFrame(dummyFrame)
        
        Log.d("VadManagerTest", "Inference result prob: $prob")
        
        vadManager.release()
    }
}
