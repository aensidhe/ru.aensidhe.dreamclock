@file:Suppress("MatchingDeclarationName")

package ru.aensidhe.dreamclock.core.time

internal enum class PluralForm { ONE, FEW, MANY }

internal fun russianPlural(n: Int): PluralForm {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> PluralForm.MANY
        mod10 == 1 -> PluralForm.ONE
        mod10 in 2..4 -> PluralForm.FEW
        else -> PluralForm.MANY
    }
}

internal fun minuteNoun(n: Int): String =
    when (russianPlural(n)) {
        PluralForm.ONE -> "минута"
        PluralForm.FEW -> "минуты"
        PluralForm.MANY -> "минут"
    }

internal fun hourNoun(n: Int): String =
    when (russianPlural(n)) {
        PluralForm.ONE -> "час"
        PluralForm.FEW -> "часа"
        PluralForm.MANY -> "часов"
    }

internal val hourCardinalNominative: Map<Int, String> =
    mapOf(
        1 to "час",
        2 to "два",
        3 to "три",
        4 to "четыре",
        5 to "пять",
        6 to "шесть",
        7 to "семь",
        8 to "восемь",
        9 to "девять",
        10 to "десять",
        11 to "одиннадцать",
        12 to "двенадцать",
    )

internal val hourCardinalMasculine: Map<Int, String> =
    mapOf(
        1 to "один",
        2 to "два",
        3 to "три",
        4 to "четыре",
        5 to "пять",
        6 to "шесть",
        7 to "семь",
        8 to "восемь",
        9 to "девять",
        10 to "десять",
        11 to "одиннадцать",
        12 to "двенадцать",
    )

internal val comingHourOrdinalGenitive: Map<Int, String> =
    mapOf(
        1 to "первого",
        2 to "второго",
        3 to "третьего",
        4 to "четвёртого",
        5 to "пятого",
        6 to "шестого",
        7 to "седьмого",
        8 to "восьмого",
        9 to "девятого",
        10 to "десятого",
        11 to "одиннадцатого",
        12 to "двенадцатого",
    )

internal val minuteCardinalFeminine: Map<Int, String> =
    mapOf(
        1 to "одна",
        2 to "две",
        3 to "три",
        4 to "четыре",
        5 to "пять",
        6 to "шесть",
        7 to "семь",
        8 to "восемь",
        9 to "девять",
        10 to "десять",
        11 to "одиннадцать",
        12 to "двенадцать",
        13 to "тринадцать",
        14 to "четырнадцать",
        16 to "шестнадцать",
        17 to "семнадцать",
        18 to "восемнадцать",
        19 to "девятнадцать",
        20 to "двадцать",
        21 to "двадцать одна",
        22 to "двадцать две",
        23 to "двадцать три",
        24 to "двадцать четыре",
        25 to "двадцать пять",
        26 to "двадцать шесть",
        27 to "двадцать семь",
        28 to "двадцать восемь",
        29 to "двадцать девять",
    )

internal val minuteGenitive: Map<Int, String> =
    mapOf(
        1 to "одной",
        2 to "двух",
        3 to "трёх",
        4 to "четырёх",
        5 to "пяти",
        6 to "шести",
        7 to "семи",
        8 to "восьми",
        9 to "девяти",
        10 to "десяти",
        11 to "одиннадцати",
        12 to "двенадцати",
        13 to "тринадцати",
        14 to "четырнадцати",
        16 to "шестнадцати",
        17 to "семнадцати",
        18 to "восемнадцати",
        19 to "девятнадцати",
        20 to "двадцати",
        21 to "двадцати одной",
        22 to "двадцати двух",
        23 to "двадцати трёх",
        24 to "двадцати четырёх",
        25 to "двадцати пяти",
        26 to "двадцати шести",
        27 to "двадцати семи",
        28 to "двадцати восьми",
        29 to "двадцати девяти",
    )
