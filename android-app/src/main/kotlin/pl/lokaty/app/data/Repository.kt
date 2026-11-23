package pl.lokaty.app.data

import android.content.Context
import pl.lokaty.engine.Advisor
import pl.lokaty.engine.BreakAnalysis
import pl.lokaty.engine.BreakOption
import pl.lokaty.engine.CheckResult
import pl.lokaty.engine.Limits
import pl.lokaty.engine.LockedLeg
import pl.lokaty.engine.Optimizer
import pl.lokaty.engine.OptimizerResult
import pl.lokaty.engine.RejectedStrategy
import pl.lokaty.engine.SavedDecision
import pl.lokaty.engine.Snapshot
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

fun LockedEntity.toLeg() = LockedLeg(offerId, LocalDate.parse(start), BigDecimal(amount), BigDecimal(payout), LocalDate.parse(availableDate))

/** Punkt wyjścia dla optymalizatora: kapitał, nogi jeszcze trwające i data startu. Zakończone nogi są „rozliczone” (kapitał = ich wypłata). */
data class Base(val capital: BigDecimal, val locked: List<LockedLeg>, val start: LocalDate)

/**
 * Jedyne miejsce, które łączy bazę, migawkę ofert i silnik. Zegar (LocalDate.now) żyje tylko tutaj i w interfejsie:
 * silnik dostaje datę jako parametr.
 */
class Repository(private val context: Context) {
    private val database = AppDatabase.get(context)
    private val dao = database.dao()
    private val store = SnapshotStore(context)

    suspend fun settings(): SettingsEntity = dao.settings() ?: SettingsEntity().also { dao.saveSettings(it) }
    suspend fun saveSettings(s: SettingsEntity) = dao.saveSettings(s)
    suspend fun decision(): DecisionEntity? = dao.decision()
    suspend fun saveDecision(d: DecisionEntity) = dao.saveDecision(d)
    suspend fun rejected(): List<RejectedEntity> = dao.rejected()
    suspend fun reject(r: RejectedEntity) = dao.reject(r)
    suspend fun lockedLegs(): List<LockedEntity> = dao.lockedLegs()
    suspend fun addLocked(l: LockedEntity) = dao.addLocked(l)

    /** Kasuje zatwierdzoną strategię i zawarte lokaty (ustawienia i odrzucone zostają). */
    suspend fun clearDecisionAndLocked() {
        dao.clearDecision(); dao.clearLocked()
    }

    suspend fun exportJson(): String = Backup.export(UserData(settings(), decision(), lockedLegs(), rejected()))

    /** Import zastępuje wszystkie dane użytkownika. Rzuca IllegalArgumentException (bez zmian w bazie), gdy plik jest niepoprawny. */
    suspend fun importJson(text: String) {
        val d = Backup.parse(text)
        database.replaceAll(d.settings, d.decision, d.locked, d.rejected)
    }

    /** Migawka z pamięci podręcznej lub przykładowa (bez sieci, szybko). */
    fun loadSnapshot(): LoadedSnapshot = store.loadCached()

    /** Próba pobrania świeżej migawki z sieci (wywoływać poza głównym wątkiem). */
    fun refreshSnapshot(): LoadedSnapshot = store.refresh()

    /** Nogi zakończone przed `today` są rozliczone: kapitał to wypłata ostatniej z nich, a lista zawartych zaczyna się od nowa. */
    fun base(settings: Settings, locked: List<LockedEntity>, today: LocalDate): Base {
        val legs = locked.map { it.toLeg() }
        if (legs.isEmpty()) return Base(settings.capital, emptyList(), today)
        val last = legs.maxBy { it.availableDate }
        if (last.availableDate <= today) return Base(last.payout, emptyList(), today)
        val running = legs.filter { it.availableDate > today }
        return Base(settings.capital, running, today)
    }

    fun plan(snapshot: Snapshot, s: Settings, base: Base, rejected: List<RejectedEntity>): OptimizerResult {
        val rej = rejected.associate { it.fingerprint to BigDecimal(it.valueAtRejection) }
        return Optimizer.optimize(
            snapshot.offers, base.capital, base.start, base.start.plusMonths(s.horizonMonths.toLong()), s.limits,
            locked = base.locked,
            isRejected = { st: Strategy -> rej[st.fingerprint]?.let { st.finalValue < it + s.threshold } ?: false },
        )
    }

    fun check(snapshot: Snapshot, d: DecisionEntity, s: Settings, base: Base, rejected: List<RejectedEntity>): CheckResult {
        val decision = SavedDecision(
            d.fingerprint, base.locked, BigDecimal(d.finalValue), base.capital,
            base.start, LocalDate.parse(d.horizonEnd), d.lastNotifiedDifference?.let { BigDecimal(it) },
        )
        return Advisor.check(snapshot.offers, decision, s.threshold, rejected.map { RejectedStrategy(it.fingerprint, BigDecimal(it.valueAtRejection)) }, s.limits)
    }

    /** Informacyjna analiza zerwania każdej trwającej lokaty (bez rekomendacji). */
    fun breakOptions(snapshot: Snapshot, base: Base, holdValue: BigDecimal, horizonEnd: LocalDate, s: Settings): List<BreakOption> =
        base.locked.mapNotNull { BreakAnalysis.analyze(snapshot.offers, it, base.start, holdValue, horizonEnd, s.limits) }
}
