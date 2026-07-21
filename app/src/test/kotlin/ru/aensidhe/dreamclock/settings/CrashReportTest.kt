package ru.aensidhe.dreamclock.settings

import kotlin.test.assertContains
import org.junit.jupiter.api.Test

class CrashReportTest {
    @Test
    fun `names the thread and the time`() {
        val text = crashReport("main", IllegalStateException("boom"), "2026-07-21T20:17:00")
        assertContains(text, "main")
        assertContains(text, "2026-07-21T20:17:00")
    }

    @Test
    fun `carries the exception class message and trace`() {
        val text = crashReport("main", IllegalStateException("boom"), "now")
        assertContains(text, "java.lang.IllegalStateException")
        assertContains(text, "boom")
        assertContains(text, "CrashReportTest")
    }

    @Test
    fun `carries the cause chain`() {
        val cause = IllegalArgumentException("root cause here")
        val text = crashReport("main", RuntimeException("outer", cause), "now")
        assertContains(text, "root cause here")
    }
}
