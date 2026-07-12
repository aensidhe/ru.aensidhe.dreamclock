package ru.aensidhe.dreamclock.core.time

internal val englishHour: Map<Int, String> =
    mapOf(
        1 to "one",
        2 to "two",
        3 to "three",
        4 to "four",
        5 to "five",
        6 to "six",
        7 to "seven",
        8 to "eight",
        9 to "nine",
        10 to "ten",
        11 to "eleven",
        12 to "twelve",
    )

private val ones =
    listOf(
        "",
        "one",
        "two",
        "three",
        "four",
        "five",
        "six",
        "seven",
        "eight",
        "nine",
        "ten",
        "eleven",
        "twelve",
        "thirteen",
        "fourteen",
        "fifteen",
        "sixteen",
        "seventeen",
        "eighteen",
        "nineteen",
    )

private val tens = mapOf(20 to "twenty", 30 to "thirty", 40 to "forty", 50 to "fifty")

internal fun englishCardinal(n: Int): String {
    require(n in 1..59) { "minute out of range: $n" }
    if (n < 20) return ones[n]
    val tensPart = tens.getValue(n / 10 * 10)
    val onesPart = n % 10
    return if (onesPart == 0) tensPart else "$tensPart-${ones[onesPart]}"
}
