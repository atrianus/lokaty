package pl.lokaty.engine

import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.system.exitProcess

private typealias JM = Map<String, Any?>
private var failures = 0
private fun check(name: String, expected: Any?, actual: Any?) {
    if (expected != actual) { failures++; println("BŁĄD $name: oczekiwano $expected, jest $actual") } else println("ok   $name")
}

private fun offerRef(o: JM, meta: Map<String, String> = emptyMap()): OfferRef {
    val oneTime = (o["one_time_only"] as String?)?.let { YesNoUnknown.valueOf(it.uppercase()) } ?: YesNoUnknown.NO
    return OfferRef(
        id = o["id"] as String, bankId = o["bank_id"] as String,
        offer = Offer(InterestType.AT_MATURITY, BigDecimal(o["rate"] as String), (o["term_months"] as String).toInt(), TermUnit.MONTHS,
            dayCount = DayCount.ACTUAL_365, rounding = Rounding.HALF_UP, availability = Availability.NEXT_BUSINESS_DAY,
            amountMin = (o["amount_min"] as String?)?.let { BigDecimal(it) }, amountMax = (o["amount_max"] as String?)?.let { BigDecimal(it) }),
        validFrom = LocalDate.parse(o["valid_from"] as String), validUntil = (o["valid_until"] as String?)?.let { LocalDate.parse(it) },
        oneTimeOnly = oneTime, meta = meta)
}

@Suppress("UNCHECKED_CAST")
private fun runCase(c: JM, meta: (JM) -> Map<String, String> = { emptyMap() }): OptimizerResult {
    val i = c["input"] as JM
    val lim = i["limits"] as JM
    val locked = (i["locked"] as List<JM>).map {
        LockedLeg(it["offer_id"] as String, LocalDate.parse(it["start"] as String), BigDecimal(it["amount"] as String),
            BigDecimal(it["payout"] as String), LocalDate.parse(it["available_date"] as String))
    }
    return Optimizer.optimize(
        (i["offers"] as List<JM>).map { offerRef(it, meta(it)) }, BigDecimal(i["capital"] as String), LocalDate.parse(i["start"] as String),
        LocalDate.parse(i["horizon_end"] as String),
        Limits((lim["max_legs"] as String).toInt(), (lim["max_gap_days"] as String).toInt(), (lim["max_shown"] as String).toInt()),
        (i["rejected"] as List<String>).toSet(), locked)
}

@Suppress("UNCHECKED_CAST")
fun main(args: Array<String>) {
    val root = MiniJson(File(args.firstOrNull() ?: "../test-data/engine/optimizer-cases.json").readText()).parse() as JM
    val cases = (root["cases"] as List<JM>).associateBy { it["id"] as String }

    // Najlepsza strategia musi zgadzać się z pełnym przeszukaniem (dominacja nie może jej ukryć);
    // pokazane pozostałe muszą istnieć w pełnym przeszukaniu z tą samą wartością i być posortowane.
    fun same(id: String, got: OptimizerResult) {
        val e = cases.getValue(id)["expected"] as JM
        for ((key, list) in listOf("in_horizon" to got.inHorizon, "over_horizon" to got.overHorizon)) {
            val exp = e[key] as List<JM>
            val full = (e["all_$key"] as List<JM>).map { (it["legs"] as List<*>) to it["final_value"] }.toSet()
            check("$id $key najlepsza: istnieje", exp.isEmpty(), list.isEmpty())
            if (exp.isNotEmpty() && list.isNotEmpty()) {
                val x = exp[0]; val s = list[0]
                check("$id $key[0] nogi", x["legs"], s.offerIds)
                check("$id $key[0] wartość", x["final_value"], s.finalValue.toPlainString())
                x["extra_days"]?.let { check("$id $key[0] dodatkowe dni", (it as String).toInt(), s.extraDays) }
                (x["extra_profit"] as String?)?.let { check("$id $key[0] dodatkowy zysk", it, s.extraProfit?.toPlainString()) }
            }
            check("$id $key podzbiór pełnego przeszukania", true, list.all { (it.offerIds to it.finalValue.toPlainString()) in full })
            check("$id $key posortowane", true, list.zipWithNext().all { (a, b) -> a.finalValue >= b.finalValue })
            check("$id $key nie więcej niż limit", true, list.size <= 4)
        }
    }
    for (id in cases.keys) same(id, runCase(cases.getValue(id)))

    // O7: próg powiadomienia
    val t = BigDecimal("100.00")
    Optimizer.compare(BigDecimal("10199.99"), BigDecimal("10100.00"), t).let {
        check("O7 99,99 bez powiadomienia", false, it.notify); check("O7 99,99 poniżej progu", true, it.belowThreshold) }
    Optimizer.compare(BigDecimal("10200.00"), BigDecimal("10100.00"), t).let { check("O7 100,00 z powiadomieniem", true, it.notify) }
    Optimizer.compare(BigDecimal("10050.00"), BigDecimal("10100.00"), t).let { check("O7 gorsza strategia bez powiadomienia", false, it.notify) }

    // O8: meta (prowizje) nie zmienia wyniku
    val base = runCase(cases.getValue("O2"))
    val withCommission = runCase(cases.getValue("O2")) { o -> mapOf("commission" to if ((o["id"] as String).startsWith("bank-b")) "500.00" else "0.00") }
    check("O8 prowizja nie zmienia kolejności", base.inHorizon.map { it.offerIds to it.finalValue }, withCommission.inHorizon.map { it.offerIds to it.finalValue })

    // Odrzucona strategia nie wraca
    val o1 = runCase(cases.getValue("O1"))
    val top = o1.inHorizon.first()
    val i1 = cases.getValue("O1")["input"] as JM
    val again = Optimizer.optimize((i1["offers"] as List<JM>).map { offerRef(it) }, BigDecimal(i1["capital"] as String), LocalDate.parse(i1["start"] as String),
        LocalDate.parse(i1["horizon_end"] as String), rejected = setOf(top.fingerprint))
    check("odrzucona strategia znika z listy", false, again.inHorizon.any { it.fingerprint == top.fingerprint })
    check("odcisk jest stały", top.fingerprint, Optimizer.fingerprint(top.lockedLegs, top.legs))

    println(if (failures == 0) "WSZYSTKIE OK" else "BŁĘDY: $failures")
    if (failures > 0) exitProcess(1)
}
