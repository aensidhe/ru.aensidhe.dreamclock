package ru.aensidhe.dreamclock.settings

import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ProbeDiagnosticsTest {
    @Test
    fun `includes stage exception class and message`() {
        val text = formatDiagnostic("probe", IllegalStateException("boom"))
        assertContains(text, "probe")
        assertContains(text, "java.lang.IllegalStateException")
        assertContains(text, "boom")
    }

    @Test
    fun `includes the stack trace`() {
        val text = formatDiagnostic("history", RuntimeException("nope"))
        assertContains(text, "ProbeDiagnosticsTest")
    }

    @Test
    fun `survives a null message`() {
        val text = formatDiagnostic("probe", RuntimeException())
        assertContains(text, "java.lang.RuntimeException")
    }

    @Test
    fun `reports the cause chain`() {
        val cause = IllegalArgumentException("root cause here")
        val text = formatDiagnostic("probe", RuntimeException("outer", cause))
        assertContains(text, "root cause here")
    }

    @Test
    fun `is not truncated`() {
        val text = formatDiagnostic("probe", RuntimeException("x".repeat(500)))
        assertTrue(text.length > 500)
    }
}
