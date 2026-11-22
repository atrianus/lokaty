package pl.lokaty.engine

import java.math.BigDecimal
import java.time.LocalDate

/** Zatwierdzona przez użytkownika strategia (zapisana lokalnie). `locked` to nogi już zawarte w banku. */
data class SavedDecision(
    val fingerprint: String,
    val locked: List<LockedLeg>,
    val finalValue: BigDecimal,
    val capital: BigDecimal,
    val start: LocalDate,
    val horizonEnd: LocalDate,
    /** Przewaga, o której użytkownik był już powiadomiony (decyzja 3: następne powiadomienie po wzroście o kolejny próg). */
    val lastNotifiedDifference: BigDecimal? = null,
)

/** Odrzucona strategia z wartością z chwili odrzucenia (decyzja 4: wraca po wzroście o co najmniej próg). */
data class RejectedStrategy(val fingerprint: String, val valueAtRejection: BigDecimal)

data class CheckResult(val result: OptimizerResult, val best: Strategy?, val comparison: Comparison?, val notify: Boolean)

object Advisor {
    /** Codzienna kontrola: najlepsza strategia z bieżących ofert kontra zapisana decyzja. */
    fun check(
        offers: List<OfferRef>, decision: SavedDecision, threshold: BigDecimal,
        rejected: List<RejectedStrategy> = emptyList(), limits: Limits = Limits(),
    ): CheckResult {
        val rej = rejected.associate { it.fingerprint to it.valueAtRejection }
        val result = Optimizer.optimize(
            offers, decision.capital, decision.start, decision.horizonEnd, limits, locked = decision.locked,
            isRejected = { s -> rej[s.fingerprint]?.let { s.finalValue < it + threshold } ?: false },
        )
        val best = result.inHorizon.firstOrNull { it.fingerprint != decision.fingerprint }
            ?: return CheckResult(result, null, null, false)
        val cmp = Optimizer.compare(best.finalValue, decision.finalValue, threshold)
        val last = decision.lastNotifiedDifference
        val notify = cmp.notify && (last == null || cmp.difference >= last + threshold)
        return CheckResult(result, best, cmp, notify)
    }
}
