package ru.aensidhe.dreamclock.settings

/**
 * Renders [error] as diagnostic text for the advanced-debugging dialog: which [stage] failed, the
 * exception class and message, and the full stack trace including causes. Deliberately
 * untruncated — this text exists to be copied off the device verbatim.
 */
fun formatDiagnostic(
    stage: String,
    error: Throwable,
): String =
    buildString {
        append("stage: ")
        appendLine(stage)
        append(error.javaClass.name)
        error.message?.let {
            append(": ")
            append(it)
        }
        appendLine()
        appendLine()
        append(error.stackTraceToString())
    }
