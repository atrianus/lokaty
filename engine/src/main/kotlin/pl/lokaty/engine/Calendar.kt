package pl.lokaty.engine

import java.time.LocalDate

/** Kalendarz silnika: czysta funkcja dat, bez zegara i sieci. Zasady: specyfikacja silnika, sekcja 3. */
object Calendar {

    /** Data plus N miesięcy; gdy w miesiącu końcowym brak takiego dnia, ostatni dzień miesiąca. */
    fun addMonths(date: LocalDate, months: Int): LocalDate = date.plusMonths(months.toLong())

    /** Wielkanoc (algorytm anonimowy gregoriański). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    fun corpusChristi(year: Int): LocalDate = easter(year).plusDays(60)

    /** Dni ustawowo wolne w danym roku; Wigilia od 2025 (nowelizacja z 2024). */
    fun holidays(year: Int): Set<LocalDate> {
        val fixed = mutableListOf(1 to 1, 1 to 6, 5 to 1, 5 to 3, 8 to 15, 11 to 1, 11 to 11, 12 to 25, 12 to 26)
        if (year >= 2025) fixed += 12 to 24
        val easter = easter(year)
        return fixed.map { (m, d) -> LocalDate.of(year, m, d) }.toSet() +
            setOf(easter, easter.plusDays(1), easter.plusDays(49), easter.plusDays(60))
    }

    fun isBusinessDay(date: LocalDate): Boolean =
        date.dayOfWeek.value < 6 && date !in holidays(date.year)

    /** Pierwszy dzień roboczy po podanej dacie. */
    fun nextBusinessDay(date: LocalDate): LocalDate {
        var d = date.plusDays(1)
        while (!isBusinessDay(d)) d = d.plusDays(1)
        return d
    }

    /** Dzień roboczy po N dniach roboczych od daty (N = 0 zwraca samą datę). */
    fun addBusinessDays(date: LocalDate, days: Int): LocalDate {
        var d = date
        repeat(days) { d = nextBusinessDay(d) }
        return d
    }
}
