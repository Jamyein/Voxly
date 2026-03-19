package com.voxly.domain.usecase

import android.app.ActivityManager
import android.content.Context
import com.voxly.domain.usecase.MemoryPressureMonitor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryPressureMonitorTest {

    private fun createContextWithMemory(availMem: Long, totalMem: Long): Context {
        val context = mockk<Context>()
        val activityManager = mockk<ActivityManager>()
        val memoryInfo = ActivityManager.MemoryInfo().apply {
            this.availMem = availMem
            this.totalMem = totalMem
        }
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { activityManager.getMemoryInfo(any()) } answers {
            val info = arg<ActivityManager.MemoryInfo>(0)
            info.availMem = availMem
            info.totalMem = totalMem
        }
        return context
    }

    @Test
    fun `getCurrentConcurrency returns 4 when memory greater than 50 percent`() {
        val context = createContextWithMemory(availMem = 600L * 1024 * 1024, totalMem = 1000L * 1024 * 1024)
        val monitor = MemoryPressureMonitor(context)

        assertEquals(4, monitor.getCurrentConcurrency())
    }

    @Test
    fun `getCurrentConcurrency returns 2 when memory 20-50 percent`() {
        val context = createContextWithMemory(availMem = 300L * 1024 * 1024, totalMem = 1000L * 1024 * 1024)
        val monitor = MemoryPressureMonitor(context)

        assertEquals(2, monitor.getCurrentConcurrency())
    }

    @Test
    fun `getCurrentConcurrency returns 1 when memory less than 20 percent`() {
        val context = createContextWithMemory(availMem = 100L * 1024 * 1024, totalMem = 1000L * 1024 * 1024)
        val monitor = MemoryPressureMonitor(context)

        assertEquals(1, monitor.getCurrentConcurrency())
    }
}