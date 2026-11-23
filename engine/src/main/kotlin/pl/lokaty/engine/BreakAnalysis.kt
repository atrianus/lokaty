package pl.lokaty.engine

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Jeden wariant zerwania zawartej lokaty. `optimistic` = false to wariant ostrożny (przy nieznanych zasadach: odsetki utracone),
 * true to wariant optymistyczny (odsetki proporcjonalne). Aplikacja nigdy nie rekomenduje zerwania: tylko liczy i pokazuje.
 */
data class BreakVariant(
    val optimistic: Boolean,
    val payoutNow: BigDecimal,
    val availableDate: LocalDate,
    val bestAfter: Strategy?,
    /** Wartość na końcu horyzontu po zerwaniu (najlepsza strategia z wypłaty albo sama wypłata, gdy nic nie pasuje). */
    val finalValue: BigDecimal,
    /** finalValue minus wartość utrzymania lokaty do końca; dodatnia = zerwanie dałoby więcej. */
    val difference: BigDecimal,
)

data class BreakOption(val offerId: String, val breakDate: LocalDate, val confidence: Confidence, val labels: List<String>, val variants: List<BreakVariant>)

object BreakAnalysis {
    /**
     * @param holdValue wartość na końcu horyzontu, jeśli lokata trwa do końca (np. wartość zatwierdzonej strategii)
     * @return null, gdy zerwanie jest niemożliwe lub niezdefiniowane (oferty brak w migawce, bank nie dopuszcza zerwania)
     */
    fun analyze(
        offers: List<OfferRef>, locked: LockedLeg, breakDate: LocalDate, holdValue: BigDecimal,
        horizonEnd: LocalDate, limits: Limits = Limits(),
    ): BreakOption? {
        val ref = offers.firstOrNull { it.id == locked.offerId } ?: return null
        val r = Interest.calculate(ref.offer, locked.amount, locked.start, Scenario.BreakOn(breakDate)).result ?: return null
        fun variant(res: Result, optimistic: Boolean): BreakVariant {
            val best = Optimizer.optimize(offers, res.payout, res.availableDate, horizonEnd, limits).inHorizon.firstOrNull()
            val fin = best?.finalValue ?: res.payout
            return BreakVariant(optimistic, res.payout, res.availableDate, best, fin, fin - holdValue)
        }
        val variants = buildList {
            add(variant(r, false))
            r.alt?.let { add(variant(it, true)) }
        }
        return BreakOption(locked.offerId, breakDate, r.confidence, r.labels, variants)
    }
}
