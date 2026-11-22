package pl.lokaty.app.data

import android.content.Context
import pl.lokaty.engine.Advisor
import pl.lokaty.engine.CheckResult
import pl.lokaty.engine.Limits
import pl.lokaty.engine.Optimizer
import pl.lokaty.engine.OptimizerResult
import pl.lokaty.engine.RejectedStrategy
import pl.lokaty.engine.SavedDecision
import pl.lokaty.engine.Snapshot
import pl.lokaty.engine.SnapshotParser
import pl.lokaty.engine.Strategy
import java.math.BigDecimal
import java.time.LocalDate

/** Ustawienia w typach domenowych (BigDecimal zamiast tekstu). */
data class Settings(
    val threshold: BigDecimal,
    val capital: BigDecimal,
    val horizonMonths: Int,
    val limits: Limits,
)

fun SettingsEntity.toSettings() = Settings(BigDecimal(thresholdPln), BigDecimal(capital), horizonMonths, Limits(maxLegs, maxGapDays, maxShown))

/**
 * Jedyne miejsce, które łączy bazę, migawkę ofert i silnik. Zegar (LocalDate.now) żyje tylko tutaj i w interfejsie:
 * silnik dostaje datę jako parametr.
 */
class Repository(private val context: Context) {
    private val dao = AppDatabase.get(context).dao()

    suspend fun settings(): SettingsEntity = dao.settings() ?: SettingsEntity().also { dao.saveSettings(it) }
    suspend fun saveSettings(s: SettingsEntity) = dao.saveSettings(s)
    suspend fun decision(): DecisionEntity? = dao.decision()
    suspend fun saveDecision(d: DecisionEntity) = dao.saveDecision(d)
    suspend fun rejected(): List<RejectedEntity> = dao.rejected()
    suspend fun reject(r: RejectedEntity) = dao.reject(r)

    /**
     * Na razie migawka przykładowa z zasobów aplikacji (dane wymyślone).
     * Docelowo: pobranie z GitHub Pages i zapis w pamięci podręcznej (tabela snapshot_cache, Etap 2).
     */
    fun loadSnapshot(): Snapshot =
        SnapshotParser.parse(context.assets.open("snapshot-przyklad.json").bufferedReader().use { it.readText() })

    fun plan(snapshot: Snapshot, s: Settings, today: LocalDate, rejected: List<RejectedEntity>): OptimizerResult {
        val rej = rejected.associate { it.fingerprint to BigDecimal(it.valueAtRejection) }
        return Optimizer.optimize(
            snapshot.offers, s.capital, today, today.plusMonths(s.horizonMonths.toLong()), s.limits,
            isRejected = { st: Strategy -> rej[st.fingerprint]?.let { st.finalValue < it + s.threshold } ?: false },
        )
    }

    fun check(snapshot: Snapshot, d: DecisionEntity, s: Settings, rejected: List<RejectedEntity>): CheckResult {
        val decision = SavedDecision(
            d.fingerprint, emptyList(), BigDecimal(d.finalValue), BigDecimal(d.capital),
            LocalDate.parse(d.start), LocalDate.parse(d.horizonEnd), d.lastNotifiedDifference?.let { BigDecimal(it) },
        )
        return Advisor.check(snapshot.offers, decision, s.threshold, rejected.map { RejectedStrategy(it.fingerprint, BigDecimal(it.valueAtRejection)) }, s.limits)
    }
}
