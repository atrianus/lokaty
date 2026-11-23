package pl.lokaty.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.system.exitProcess

private var failures = 0
private fun check(name: String, expected: Any?, actual: Any?) {
    if (expected != actual) { failures++; println("BŁĄD $name: oczekiwano $expected, jest $actual") } else println("ok   $name")
}

private fun ref(id: String, rate: String, months: Int, treatment: Treatment, allowed: YesNoUnknown = YesNoUnknown.YES) = OfferRef(
    id, id.substringBefore('/'),
    Offer(InterestType.AT_MATURITY, BigDecimal(rate), months, TermUnit.MONTHS, dayCount = DayCount.ACTUAL_365,
        earlyAllowed = allowed, earlyTreatment = treatment),
    LocalDate.of(2026, 1, 1), LocalDate.of(2027, 12, 31), title = "Test $id", sourceUrl = "https://example.com/$id")

fun main() {
    val horizon = LocalDate.of(2027, 6, 30)
    val cap = BigDecimal("10000.00")
    val start = LocalDate.of(2026, 11, 2)
    val long = ref("bank-a/a-12m", "3.00", 12, Treatment.LOSE_ALL)
    val better = ref("bank-b/b-3m", "9.00", 3, Treatment.LOSE_ALL)
    val offers = listOf(long, better)
    val hold = Interest.calculate(long.offer, cap, start, Scenario.ToMaturity).result!!
    val locked = LockedLeg(long.id, start, cap, hold.payout, hold.availableDate)
    val on = LocalDate.of(2026, 11, 16)

    check("pola tytułu i adresu", "Test bank-a/a-12m" to "https://example.com/bank-a/a-12m", long.title to long.sourceUrl)

    // utrata odsetek: wypłata = kapitał, wariant jeden
    val o1 = BreakAnalysis.analyze(offers, locked, on, hold.payout, horizon)!!
    check("lose_all: jeden wariant", 1, o1.variants.size)
    check("lose_all: wypłata = kapitał", "10000.00", o1.variants[0].payoutNow.toPlainString())
    check("lose_all: dostępne w następnym dniu roboczym", LocalDate.of(2026, 11, 17), o1.variants[0].availableDate)
    check("lose_all: lepsza oferta wskazana", "bank-b/b-3m", o1.variants[0].bestAfter?.offerIds?.firstOrNull())
    check("lose_all: różnica = wartość po zerwaniu − utrzymanie", true,
        o1.variants[0].difference == o1.variants[0].finalValue - hold.payout)

    // proporcjonalnie: wypłata > kapitał
    val prop = ref("bank-a/a-12m", "3.00", 12, Treatment.PROPORTIONAL)
    val o2 = BreakAnalysis.analyze(listOf(prop, better), locked, on, hold.payout, horizon)!!
    check("proporcjonalnie: wypłata większa od kapitału", true, o2.variants[0].payoutNow > cap)

    // nieznane zasady: dwa warianty, ostrożny nie większy od optymistycznego
    val unk = ref("bank-a/a-12m", "3.00", 12, Treatment.UNKNOWN, YesNoUnknown.UNKNOWN)
    val o3 = BreakAnalysis.analyze(listOf(unk, better), locked, on, hold.payout, horizon)!!
    check("nieznane: dwa warianty", 2, o3.variants.size)
    check("nieznane: ostrożny ≤ optymistyczny", true, o3.variants[0].payoutNow <= o3.variants[1].payoutNow)
    check("nieznane: warianty oznaczone", listOf(false, true), o3.variants.map { it.optimistic })

    // bank nie dopuszcza zerwania / brak oferty w migawce
    val no = ref("bank-a/a-12m", "3.00", 12, Treatment.LOSE_ALL, YesNoUnknown.NO)
    check("zerwanie niedozwolone → brak opcji", null, BreakAnalysis.analyze(listOf(no, better), locked, on, hold.payout, horizon))
    check("brak oferty w migawce → brak opcji", null, BreakAnalysis.analyze(listOf(better), locked, on, hold.payout, horizon))

    // brak lepszej oferty: wartość = wypłata
    val o4 = BreakAnalysis.analyze(listOf(long), locked, on, hold.payout, LocalDate.of(2026, 11, 20))!!
    check("brak strategii po zerwaniu: wartość = wypłata", o4.variants[0].payoutNow, o4.variants[0].finalValue)

    if (failures > 0) { println("$failures błędów"); exitProcess(1) }
    println("BreakTest: wszystko OK")
}
