package pl.lokaty.engine

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Oferta dla optymalizatora. `meta` (np. prowizje, linki partnerskie) jest ignorowana: niezmiennik v2.2. */
data class OfferRef(
    val id: String,
    val bankId: String,
    val offer: Offer,
    val validFrom: LocalDate,
    val validUntil: LocalDate? = null,
    val oneTimeOnly: YesNoUnknown = YesNoUnknown.NO,
    val labels: List<String> = emptyList(),
    val meta: Map<String, String> = emptyMap(),
    /** Nazwa produktu i adres strony oferty: tylko do wyświetlania, nie wpływają na obliczenia. */
    val title: String = "",
    val sourceUrl: String? = null,
)

data class Leg(val offerId: String, val start: LocalDate, val amount: BigDecimal, val result: Result, val labels: List<String> = emptyList())

/** Zawarta noga: wynik zapisany w momencie zawarcia, nie jest przeliczany. */
data class LockedLeg(val offerId: String, val start: LocalDate, val amount: BigDecimal, val payout: BigDecimal, val availableDate: LocalDate)

data class Limits(val maxLegs: Int = 3, val maxGapDays: Int = 7, val maxShown: Int = 4)

data class Strategy(
    val legs: List<Leg>,
    val lockedLegs: List<LockedLeg>,
    val finalValue: BigDecimal,
    val availableDate: LocalDate,
    val confidence: Confidence,
    val labels: List<String>,
    val fingerprint: String,
    val overHorizon: Boolean,
    val extraDays: Int? = null,
    val extraProfit: BigDecimal? = null,
) {
    val offerIds: List<String> get() = lockedLegs.map { it.offerId } + legs.map { it.offerId }
}

data class OptimizerResult(
    val inHorizon: List<Strategy>,
    val overHorizon: List<Strategy>,
    val unverified: List<Strategy>,
    val examined: Int,
)

data class Comparison(val difference: BigDecimal, val notify: Boolean, val belowThreshold: Boolean)

object Optimizer {

    /** Różnica wartości nowej i zapisanej strategii; powiadomienie od progu, poniżej progu tylko etykieta. */
    fun compare(newValue: BigDecimal, savedValue: BigDecimal, threshold: BigDecimal): Comparison {
        val d = newValue - savedValue
        return Comparison(d, d >= threshold && d.signum() > 0, d.signum() > 0 && d < threshold)
    }

    fun fingerprint(locked: List<LockedLeg>, legs: List<Leg>): String {
        val s = (locked.map { "${it.offerId}@${it.start}@${it.amount.toPlainString()}" } +
            legs.map { "${it.offerId}@${it.start}@${it.amount.toPlainString()}" }).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun eligibleSignature(capital: BigDecimal, offers: List<OfferRef>): String =
        offers.joinToString("") { o ->
            val min = o.offer.amountMin; val max = o.offer.amountMax
            if ((min == null || capital >= min) && (max == null || capital <= max)) "1" else "0"
        }

    fun optimize(
        offers: List<OfferRef>, capital: BigDecimal, start: LocalDate, horizonEnd: LocalDate,
        limits: Limits = Limits(), rejected: Set<String> = emptySet(), locked: List<LockedLeg> = emptyList(),
        isRejected: (Strategy) -> Boolean = { it.fingerprint in rejected },
    ): OptimizerResult {
        val sorted = offers.sortedBy { it.id }
        val all = mutableListOf<Strategy>()
        var examined = 0
        val seen = HashMap<String, BigDecimal>()

        fun rec(avail: LocalDate, cap: BigDecimal, legs: List<Leg>, used: Set<String>) {
            for (o in sorted) {
                if (o.oneTimeOnly == YesNoUnknown.YES && o.id in used) continue
                val s = if (avail > o.validFrom) avail else o.validFrom
                if (ChronoUnit.DAYS.between(avail, s) > limits.maxGapDays) continue
                if (o.validUntil != null && s > o.validUntil) continue
                val out = Interest.calculate(o.offer, cap, s, Scenario.ToMaturity)
                examined++
                val r = out.result ?: continue
                val legLabels = o.labels + r.labels +
                    (if (o.validUntil == null) listOf("offer_without_end_date") else emptyList()) +
                    (if (o.oneTimeOnly == YesNoUnknown.UNKNOWN) listOf("one_time_unknown") else emptyList())
                val leg = Leg(o.id, s, cap, r, legLabels)
                val newLegs = legs + leg
                val conf = (locked.map { Confidence.APPROXIMATE } + newLegs.map { it.result.confidence }).maxBy { it.ordinal }
                all += Strategy(newLegs, locked, r.payout, r.availableDate, conf,
                    newLegs.flatMap { it.labels }.distinct(), fingerprint(locked, newLegs), r.availableDate > horizonEnd)
                if (newLegs.size < limits.maxLegs && r.availableDate <= horizonEnd && conf != Confidence.UNVERIFIED) {
                    val usedNext = if (o.oneTimeOnly == YesNoUnknown.YES) used + o.id else used
                    val key = "${r.availableDate}|${newLegs.size}|${usedNext.sorted()}|${eligibleSignature(r.payout, sorted)}"
                    val best = seen[key]
                    if (best != null && best > r.payout) continue
                    seen[key] = r.payout
                    rec(r.availableDate, r.payout, newLegs, usedNext)
                }
            }
        }
        val baseAvail = locked.lastOrNull()?.availableDate ?: start
        val baseCap = locked.lastOrNull()?.payout ?: capital
        rec(baseAvail, baseCap, emptyList(), emptySet())

        val cmp = compareBy<Strategy>({ it.finalValue.negate() }, { it.availableDate }, { it.legs.size })
            .thenComparator { a, b -> a.offerIds.joinToString("|").compareTo(b.offerIds.joinToString("|")) }
        val live = all.filter { !isRejected(it) }.sortedWith(cmp)
        val ranked = live.filter { it.confidence != Confidence.UNVERIFIED }
        val inH = ranked.filter { !it.overHorizon }
        val best = inH.firstOrNull()?.finalValue
        val over = ranked.filter { it.overHorizon && (best == null || it.finalValue > best) }.map {
            it.copy(extraDays = ChronoUnit.DAYS.between(horizonEnd, it.availableDate).toInt(), extraProfit = best?.let { b -> it.finalValue - b })
        }
        return OptimizerResult(inH.take(limits.maxShown), over.take(limits.maxShown),
            live.filter { it.confidence == Confidence.UNVERIFIED }.take(limits.maxShown), examined)
    }
}
