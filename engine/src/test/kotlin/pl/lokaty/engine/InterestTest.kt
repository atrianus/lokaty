package pl.lokaty.engine

import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.system.exitProcess

@Suppress("UNCHECKED_CAST")
private typealias J = Map<String, Any?>

private var failures = 0
private fun check(name: String, expected: Any?, actual: Any?) {
    if (expected != actual) { failures++; println("BŁĄD $name: oczekiwano $expected, jest $actual") } else println("ok   $name")
}

private fun <E : Enum<E>> parseEnum(values: Array<E>, s: String?, default: E): E =
    if (s == null) default else values.first { it.name.equals(s, ignoreCase = true) }

@Suppress("UNCHECKED_CAST")
private fun offerOf(o: J): Offer {
    val term = o["term"] as J
    val rounding = o["rounding"] as J
    val early = o["early_termination"] as J?
    fun dec(k: String) = (o[k] as String?)?.let { BigDecimal(it) }
    return Offer(
        interestType = parseEnum(InterestType.values(), o["interest_type"] as String, InterestType.UNKNOWN),
        rateAnnual = dec("rate_annual"),
        termValue = (term["value"] as String).toInt(),
        termUnit = parseEnum(TermUnit.values(), term["unit"] as String, TermUnit.MONTHS),
        dayCount = parseEnum(DayCount.values(), o["day_count_rule"] as String?, DayCount.UNKNOWN),
        rounding = parseEnum(Rounding.values(), rounding["mode"] as String, Rounding.HALF_UP),
        availability = parseEnum(Availability.values(), o["availability_rule"] as String?, Availability.NEXT_BUSINESS_DAY),
        capPeriod = (o["capitalization_period"] as String?)?.let { parseEnum(CapPeriod.values(), it, CapPeriod.AT_END) },
        taxTiming = parseEnum(TaxTiming.values(), o["tax_timing"] as String?, TaxTiming.UNKNOWN),
        earlyAllowed = parseEnum(YesNoUnknown.values(), early?.get("allowed") as String?, YesNoUnknown.UNKNOWN),
        earlyTreatment = parseEnum(Treatment.values(), early?.get("interest_treatment") as String?, Treatment.UNKNOWN),
        amountMin = dec("amount_min"),
        amountMax = dec("amount_max"),
    )
}

@Suppress("UNCHECKED_CAST")
fun main(args: Array<String>) {
    val root = MiniJson(File(args.firstOrNull() ?: "../test-data/engine/cases.json").readText()).parse() as J
    val cases = (root["cases"] as List<J>).associateBy { it["id"] as String }

    fun run(id: String): Outcome {
        val input = cases.getValue(id)["input"] as J
        val scenario = input["scenario"].let {
            if (it == "maturity") Scenario.ToMaturity else Scenario.BreakOn(LocalDate.parse((it as J)["break_on"] as String))
        }
        return Interest.calculate(offerOf(input["offer"] as J), BigDecimal(input["amount"] as String),
            LocalDate.parse(input["start_date"] as String), scenario)
    }

    fun compare(id: String, r: Result) {
        val e = cases.getValue(id)["expected"] as J
        e["maturity_date"]?.let { check("$id zapadalność", it, r.maturityDate.toString()) }
        e["available_date"]?.let { check("$id dostępność", it, r.availableDate.toString()) }
        e["days"]?.let { check("$id dni", (it as String).toInt(), r.days) }
        e["interest_gross"]?.let { check("$id odsetki brutto", it, r.interestGross.toPlainString()) }
        e["tax"]?.let { check("$id podatek", it, r.tax.toPlainString()) }
        e["interest_net"]?.let { check("$id odsetki netto", it, r.interestNet.toPlainString()) }
        e["payout"]?.let { check("$id wypłata", it, r.payout.toPlainString()) }
        e["confidence"]?.let { check("$id pewność", it, r.confidence.name.lowercase()) }
    }

    for (id in listOf("K1", "K2a", "K2b", "K4a", "K4b", "K5", "K6")) {
        val out = run(id)
        check("$id bez błędów", emptyList<String>(), out.errors)
        compare(id, out.result!!)
    }

    // K3: zaokrąglenie podatku
    val k3 = (cases.getValue("K3")["expected"] as J)["results"] as List<J>
    for (r in k3) {
        val i = BigDecimal(r["interest"] as String)
        check("K3 podatek od ${r["interest"]}", r["tax"], Interest.tax(i).toPlainString())
        check("K3 netto od ${r["interest"]}", r["net"], (i - Interest.tax(i)).toPlainString())
    }

    // K7: nieznane zasady zerwania, dwa wyniki
    val k7 = run("K7").result!!
    val e7 = cases.getValue("K7")["expected"] as J
    check("K7 ostrożny", (e7["conservative"] as J)["payout"], k7.payout.toPlainString())
    check("K7 optymistyczny", (e7["optimistic"] as J)["payout"], k7.alt!!.payout.toPlainString())
    check("K7 etykieta", (e7["labels"] as List<Any?>), k7.labels.filter { it == "early_termination_unknown" })
    check("K7 pewność", e7["confidence"], k7.confidence.name.lowercase())

    // K11: kwota poza przedziałem
    val k11 = run("K11")
    check("K11 błąd", (cases.getValue("K11")["expected"] as J)["errors"], k11.errors)
    check("K11 brak wyniku", null, k11.result)

    // Dodatkowe: typ nieznany nie daje zysku; zerwanie niedozwolone
    val unk = Offer(InterestType.UNKNOWN, null, 3, TermUnit.MONTHS)
    check("typ unknown → niezweryfikowany", Confidence.UNVERIFIED,
        Interest.calculate(unk, BigDecimal("1000.00"), LocalDate.of(2026, 11, 16), Scenario.ToMaturity).result!!.confidence)
    val no = Offer(InterestType.AT_MATURITY, BigDecimal("5"), 3, TermUnit.MONTHS, earlyAllowed = YesNoUnknown.NO)
    check("zerwanie niedozwolone", listOf("early_termination_not_allowed"),
        Interest.calculate(no, BigDecimal("1000.00"), LocalDate.of(2026, 11, 16), Scenario.BreakOn(LocalDate.of(2026, 12, 1))).errors)

    println(if (failures == 0) "WSZYSTKIE OK" else "BŁĘDY: $failures")
    if (failures > 0) exitProcess(1)
}
