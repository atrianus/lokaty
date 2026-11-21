package pl.lokaty.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.system.exitProcess

/** Test wydajności: 100 ofert, horyzont 12 miesięcy, 3 nogi. Limit 1 s po rozgrzaniu (zimny start tylko drukujemy; do rewizji na telefonie). */
fun main() {
    val offers = (1..100).map { n ->
        val months = listOf(1, 3, 6, 12)[n % 4]
        OfferRef("bank-${n % 10}/o$n", "bank-${n % 10}",
            Offer(InterestType.AT_MATURITY, BigDecimal("3.0").add(BigDecimal(n % 30).divide(BigDecimal(10))), months, TermUnit.MONTHS,
                dayCount = DayCount.ACTUAL_365, amountMax = if (n % 7 == 0) BigDecimal("50000") else null),
            LocalDate.of(2026, 11, 1 + n % 20), if (n % 5 == 0) LocalDate.of(2027, 3, 31) else null)
    }
    fun once() = Optimizer.optimize(offers, BigDecimal("10000.00"), LocalDate.of(2026, 11, 16), LocalDate.of(2027, 11, 16))
    var t0 = System.nanoTime()
    once()
    println("zimny start: ${(System.nanoTime() - t0) / 1_000_000} ms")
    t0 = System.nanoTime()
    val r = once()
    val ms = (System.nanoTime() - t0) / 1_000_000
    println("100 ofert: $ms ms, sprawdzono ${r.examined} nóg, najlepsza ${r.inHorizon.firstOrNull()?.finalValue}")
    if (ms > 1000) { println("ZA WOLNO"); exitProcess(1) }
    println("WSZYSTKIE OK")
}
