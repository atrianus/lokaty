package pl.lokaty.app.ui

import pl.lokaty.engine.TermUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val NBSP = ' '
private val dateFormat = DateTimeFormatter.ofPattern("d.MM.yyyy")

/** Kwota po polsku ze spacjami nierozdzielającymi tysiące: 12 345,67 zł. Bez Double. */
fun money(x: BigDecimal): String {
    val s = x.abs().setScale(2, RoundingMode.HALF_UP).toPlainString()
    val whole = s.substringBefore('.')
    val frac = s.substringAfter('.')
    val grouped = whole.reversed().chunked(3).joinToString(NBSP.toString()).reversed()
    val sign = if (x.signum() < 0 && s.any { it in '1'..'9' }) "−" else ""
    return "$sign$grouped,$frac${NBSP}zł"
}

/** Kwota ze znakiem: +612,34 zł / −10,00 zł / 0,00 zł. */
fun signedMoney(x: BigDecimal): String = if (x.signum() > 0) "+" + money(x) else money(x)

fun percent(rate: BigDecimal?): String =
    if (rate == null) "?" else rate.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%"

fun date(d: LocalDate?): String = d?.format(dateFormat) ?: "?"

fun term(value: Int, unit: TermUnit): String = when (unit) {
    TermUnit.MONTHS -> if (value == 1) "1 mies." else "$value mies."
    TermUnit.DAYS -> "$value dni"
}
