package pl.lokaty.engine

import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.system.exitProcess

private var failures = 0
private fun check(name: String, expected: Any?, actual: Any?) {
    if (expected != actual) { failures++; println("BŁĄD $name: oczekiwano $expected, jest $actual") } else println("ok   $name")
}

private fun ref(id: String, rate: String, from: LocalDate = LocalDate.of(2026, 11, 1)) = OfferRef(
    id, id.substringBefore('/'),
    Offer(InterestType.AT_MATURITY, BigDecimal(rate), 3, TermUnit.MONTHS, dayCount = DayCount.ACTUAL_365),
    from, LocalDate.of(2027, 12, 31))

fun main(args: Array<String>) {
    // --- Parser migawki ---
    val path = args.firstOrNull() ?: "../test-data/snapshots/valid/przyklad-minimalny.json"
    val snap = SnapshotParser.parse(File(path).readText())
    check("migawka: data", LocalDate.of(2026, 11, 16), snap.date)
    check("migawka: banki", listOf("bank-testowy"), snap.banks.map { it.id })
    val o = snap.offers.single()
    check("migawka: oferta", "bank-testowy/lokata-3m", o.id)
    check("migawka: stawka", BigDecimal("5.00"), o.offer.rateAnnual)
    check("migawka: termin", 3 to TermUnit.MONTHS, o.offer.termValue to o.offer.termUnit)
    check("migawka: etykieta niepełnych danych", true, "partial_data" in o.labels)
    check("migawka: konto wymagane", true, "account_required" in o.labels)
    val r = Interest.calculate(o.offer, BigDecimal("10000.00"), LocalDate.of(2026, 11, 16), Scenario.ToMaturity).result!!
    check("migawka → silnik: wypłata jak K1", "10102.08", r.payout.toPlainString())

    // --- Doradca ---
    val start = LocalDate.of(2026, 11, 16); val horizon = LocalDate.of(2027, 6, 30); val cap = BigDecimal("10000.00")
    val a = ref("bank-a/a-3m", "5.00")
    val saved = Optimizer.optimize(listOf(a), cap, start, horizon).inHorizon.first()
    val decision = SavedDecision(saved.fingerprint, emptyList(), saved.finalValue, cap, start, horizon)
    val th = BigDecimal("100.00")

    check("doradca: bez zmian ofert brak powiadomienia", false, Advisor.check(listOf(a), decision, th).notify)

    val better = listOf(a, ref("bank-b/b-3m", "9.00", LocalDate.of(2026, 11, 1)))
    val c1 = Advisor.check(better, decision, th)
    check("doradca: lepsza oferta o duża przewaga → powiadomienie", true, c1.notify)
    check("doradca: przewaga dodatnia", true, (c1.comparison?.difference ?: BigDecimal.ZERO).signum() > 0)

    val c0 = Advisor.check(better, decision, BigDecimal("100000.00"))
    check("doradca: poniżej progu bez powiadomienia, ale z etykietą", listOf(false, true), listOf(c0.notify, c0.comparison?.belowThreshold))

    val diff = c1.comparison!!.difference
    check("powtórka: ta sama przewaga nie powiadamia ponownie", false, Advisor.check(better, decision.copy(lastNotifiedDifference = diff), th).notify)
    check("powtórka: przewaga większa o próg powiadamia", true,
        Advisor.check(better, decision.copy(lastNotifiedDifference = diff - th), th).notify)

    val bestFp = c1.best!!.fingerprint
    val rej = listOf(RejectedStrategy(bestFp, c1.best!!.finalValue))
    val afterReject = Advisor.check(better, decision, th, rej)
    check("odrzucona strategia nie wraca przy tej samej wartości", false, afterReject.result.inHorizon.any { it.fingerprint == bestFp })
    val rejLow = listOf(RejectedStrategy(bestFp, c1.best!!.finalValue - th - BigDecimal("0.01")))
    check("odrzucona strategia wraca po wzroście o co najmniej próg", true,
        Advisor.check(better, decision, th, rejLow).result.inHorizon.any { it.fingerprint == bestFp })

    println(if (failures == 0) "WSZYSTKIE OK" else "BŁĘDY: $failures")
    if (failures > 0) exitProcess(1)
}
