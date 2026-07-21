package ru.aensidhe.dreamclock.settings

import android.content.Context
import java.io.File
import java.time.LocalDateTime

private const val CRASH_FILE = "last-crash.txt"

/** Formats a fatal [error] from [threadName] captured at [at]. Pure, so it is unit-testable. */
fun crashReport(
    threadName: String,
    error: Throwable,
    at: String,
): String =
    buildString {
        append("time: ")
        appendLine(at)
        append("thread: ")
        appendLine(threadName)
        append(formatDiagnostic("uncaught", error))
    }

/**
 * Persists fatal exceptions so they can be read after the process dies. The target TV has no adb,
 * so a crash otherwise leaves no trace at all: the report is written to the app's private files
 * and surfaced in settings on the next launch.
 */
object CrashLog {
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file(appContext).writeText(crashReport(thread.name, error, LocalDateTime.now().toString()))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()?.ifBlank { null }

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context): File = File(context.applicationContext.filesDir, CRASH_FILE)
}
