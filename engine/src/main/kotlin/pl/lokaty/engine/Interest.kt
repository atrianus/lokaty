package pl.lokaty.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/** Parametry oferty potrzebne do obliczeń (podzbiór schematu 1.0). Brak wiedzy to wartości UNKNOWN, nie domysł po cichu. */
enum class InterestType { AT_MATURITY, CAPITALIZED, PROGRESSIVE, PAID_OUT, OTHER, UNKNOWN }
enum class DayCount { ACTUAL_365, ACTUAL_ACTUAL, UNKNOWN }
enum class Rounding { HALF_UP, HALF_EVEN, DOWN, UNKNOWN }
enum class CapPeriod { MONTHLY, QUARTERLY, YEARLY, AT_END }
enum class TaxTiming { EACH_CAPITALIZATION, AT_END, UNKNOWN }
enum class Availability { SAME_DAY, NEXT_BUSINESS_DAY, BANK_TERM }
enum class Treatment { LOSE_ALL, PROPORTIONAL, BANK_SPECIFIC, UNKNOWN }
enum class YesNoUnknown { YES, NO, UNKNOWN }
enum class TermUnit { DAYS, MONTHS }
enum class Confidence { EXACT, APPROXIMATE, UNVERIFIED }

data class Offer(
    val interestType: InterestType,
    val rateAnnual: BigDecimal?,
    val termValue: Int,
    val termUnit: TermUnit,
    val dayCount: DayCount = DayCount.UNKNOWN,
    val rounding: Rounding = Rounding.HALF_UP,
    val availability: Availability = Availability.NEXT_BUSINESS_DAY,
    val availabilityDays: Int = 0,
    val capPeriod: CapPeriod? = null,
    val taxTiming: TaxTiming = TaxTiming.UNKNOWN,
    val earlyAllowed: YesNoUnknown = YesNoUnknown.UNKNOWN,
    val earlyTreatment: Treatment = Treatment.UNKNOWN,
    val earlyFee: BigDecimal = BigDecimal.ZERO,
    val amountMin: BigDecimal? = null,
    val amountMax: BigDecimal? = null,
)

sealed interface Scenario {
    data object ToMaturity : Scenario
    data class BreakOn(val date: LocalDate) : Scenario
}

data class Result(
    val maturityDate: LocalDate?,
    val availableDate: LocalDate,
    val days: Int,
    val interestGross: BigDecimal,
    val tax: BigDecimal,
    val interestNet: BigDecimal,
    val payout: BigDecimal,
    val confidence: Confidence,
    val labels: List<String>,
    /** Drugi wynik: przy zerwaniu z nieznanymi zasadami wariant optymistyczny. */
    val alt: Result? = null,
)

data class Outcome(val result: Result?, val errors: List<String>)

object Interest {
    private val MC = MathContext(34)
    private val HUNDRED = BigDecimal(100)
    private val TAX_RATE = BigDecimal("0.19")

    /** Podatek 19% zaokrąglony w górę do grosza (art. 63 § 1a Ordynacji; status: roboczy do potwierdzenia). */
    fun tax(interest: BigDecimal): BigDecimal = interest.multiply(TAX_RATE).setScale(2, RoundingMode.UP)

    private fun roundMode(r: Rounding) = when (r) {
        Rounding.HALF_EVEN -> RoundingMode.HALF_EVEN
        Rounding.DOWN -> RoundingMode.DOWN
        else -> RoundingMode.HALF_UP
    }

    private fun money(x: BigDecimal, r: Rounding) = x.setScale(2, roundMode(r))

    /** Odsetki za okres [from, to): dzień początku liczy się, dzień końca nie. */
    fun simpleInterest(principal: BigDecimal, rate: BigDecimal, from: LocalDate, to: LocalDate, rule: DayCount): BigDecimal {
        val base = principal.multiply(rate).divide(HUNDRED, MC)
        if (rule != DayCount.ACTUAL_ACTUAL) {
            val n = java.time.temporal.ChronoUnit.DAYS.between(from, to)
            return base.multiply(BigDecimal(n)).divide(BigDecimal(365), MC)
        }
        var sum = BigDecimal.ZERO
        var cur = from
        while (cur < to) {
            val yearEnd = LocalDate.of(cur.year + 1, 1, 1)
            val end = if (to < yearEnd) to else yearEnd
            val n = java.time.temporal.ChronoUnit.DAYS.between(cur, end)
            val ylen = if (cur.isLeapYear) 366 else 365
            sum += base.multiply(BigDecimal(n)).divide(BigDecimal(ylen), MC)
            cur = end
        }
        return sum
    }

    fun maturityDate(offer: Offer, start: LocalDate): LocalDate =
        if (offer.termUnit == TermUnit.MONTHS) Calendar.addMonths(start, offer.termValue) else start.plusDays(offer.termValue.toLong())

    fun availableDate(offer: Offer, date: LocalDate): LocalDate = when (offer.availability) {
        Availability.SAME_DAY -> date
        Availability.NEXT_BUSINESS_DAY -> Calendar.nextBusinessDay(date)
        Availability.BANK_TERM -> Calendar.addBusinessDays(date, offer.availabilityDays)
    }

    fun calculate(offer: Offer, amount: BigDecimal, start: LocalDate, scenario: Scenario): Outcome {
        val errors = mutableListOf<String>()
        if (offer.amountMin != null && amount < offer.amountMin) errors += "amount_below_min"
        if (offer.amountMax != null && amount > offer.amountMax) errors += "amount_above_max"
        if (errors.isNotEmpty()) return Outcome(null, errors)
        return when (scenario) {
            Scenario.ToMaturity -> toMaturity(offer, amount, start)
            is Scenario.BreakOn -> earlyBreak(offer, amount, start, scenario.date)
        }
    }

    private fun toMaturity(offer: Offer, amount: BigDecimal, start: LocalDate): Outcome {
        val end = maturityDate(offer, start)
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt()
        val avail = availableDate(offer, end)
        val labels = mutableListOf<String>()
        if (offer.rounding == Rounding.UNKNOWN) labels += "rounding_default"
        if (offer.dayCount == DayCount.UNKNOWN) labels += "day_count_assumed"
        val rate = offer.rateAnnual
        if (offer.interestType in setOf(InterestType.OTHER, InterestType.UNKNOWN, InterestType.PROGRESSIVE) || rate == null) {
            val zero = BigDecimal.ZERO.setScale(2)
            return Outcome(Result(end, avail, days, zero, zero, zero, amount, Confidence.UNVERIFIED, labels + "interest_type_unsupported"), emptyList())
        }
        if (offer.interestType == InterestType.CAPITALIZED) return capitalized(offer, amount, start, end, rate, days, avail, labels)
        if (offer.interestType == InterestType.PAID_OUT) labels += "payout_period_assumed"
        val gross = money(simpleInterest(amount, rate, start, end, offer.dayCount), offer.rounding)
        val t = tax(gross)
        val net = gross - t
        return Outcome(Result(end, avail, days, gross, t, net, amount + net, Confidence.APPROXIMATE, labels), emptyList())
    }

    private fun capitalized(offer: Offer, amount: BigDecimal, start: LocalDate, end: LocalDate, rate: BigDecimal, days: Int,
                            avail: LocalDate, labels0: List<String>): Outcome {
        val labels = labels0.toMutableList()
        val step = when (offer.capPeriod ?: CapPeriod.AT_END) {
            CapPeriod.MONTHLY -> 1; CapPeriod.QUARTERLY -> 3; CapPeriod.YEARLY -> 12; CapPeriod.AT_END -> 0
        }
        val points = mutableListOf(start)
        if (step > 0 && offer.termUnit == TermUnit.MONTHS) {
            var k = 1
            while (Calendar.addMonths(start, k * step) < end) { points += Calendar.addMonths(start, k * step); k++ }
        }
        points += end
        val taxEach = when (offer.taxTiming) {
            TaxTiming.EACH_CAPITALIZATION -> true
            TaxTiming.AT_END -> false
            TaxTiming.UNKNOWN -> { labels += "tax_timing_assumed"; true }
        }
        var balance = amount
        var gross = BigDecimal.ZERO.setScale(2)
        var taxSum = BigDecimal.ZERO.setScale(2)
        for ((a, b) in points.zipWithNext()) {
            val i = money(simpleInterest(balance, rate, a, b, offer.dayCount), offer.rounding)
            gross += i
            if (taxEach) { val t = tax(i); taxSum += t; balance += i - t } else balance += i
        }
        if (!taxEach) taxSum = tax(gross)
        val net = gross - taxSum
        return Outcome(Result(end, avail, days, gross, taxSum, net, amount + net, Confidence.APPROXIMATE, labels), emptyList())
    }

    private fun earlyBreak(offer: Offer, amount: BigDecimal, start: LocalDate, on: LocalDate): Outcome {
        if (offer.earlyAllowed == YesNoUnknown.NO) return Outcome(null, listOf("early_termination_not_allowed"))
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, on).toInt()
        val avail = availableDate(offer, on)
        val zero = BigDecimal.ZERO.setScale(2)
        val labels = mutableListOf<String>()
        if (offer.dayCount == DayCount.UNKNOWN) labels += "day_count_assumed"
        val fee = offer.earlyFee.setScale(2)
        val noInterest = Result(null, avail, days, zero, zero, zero, amount - fee, Confidence.EXACT, labels)
        val rate = offer.rateAnnual
        fun proportional(conf: Confidence, extra: List<String>): Result {
            val gross = money(simpleInterest(amount, rate!!, start, on, offer.dayCount), offer.rounding)
            val t = tax(gross)
            return Result(null, avail, days, gross, t, gross - t, amount + gross - t - fee, conf, labels + extra)
        }
        return when {
            offer.earlyAllowed == YesNoUnknown.YES && offer.earlyTreatment == Treatment.LOSE_ALL -> Outcome(noInterest, emptyList())
            offer.earlyAllowed == YesNoUnknown.YES && offer.earlyTreatment == Treatment.PROPORTIONAL && rate != null ->
                Outcome(proportional(Confidence.APPROXIMATE, listOf("rate_on_break_assumed")), emptyList())
            rate == null -> Outcome(noInterest.copy(confidence = Confidence.UNVERIFIED, labels = labels + "early_termination_unknown"), emptyList())
            else -> Outcome(
                noInterest.copy(confidence = Confidence.UNVERIFIED, labels = labels + "early_termination_unknown",
                    alt = proportional(Confidence.UNVERIFIED, listOf("early_termination_unknown"))),
                emptyList())
        }
    }
}
