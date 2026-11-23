package pl.lokaty.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.lokaty.app.data.DecisionEntity
import pl.lokaty.app.data.LockedEntity
import pl.lokaty.app.data.RejectedEntity
import pl.lokaty.app.data.Repository
import pl.lokaty.app.data.SettingsEntity
import pl.lokaty.app.data.SnapshotSource
import pl.lokaty.app.data.toSettings
import pl.lokaty.engine.BreakOption
import pl.lokaty.engine.LockedLeg
import pl.lokaty.engine.OptimizerResult
import pl.lokaty.engine.Snapshot
import pl.lokaty.engine.Strategy
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class UiState(
    val loading: Boolean = true,
    val settings: SettingsEntity = SettingsEntity(),
    val snapshot: Snapshot? = null,
    val source: SnapshotSource = SnapshotSource.SAMPLE,
    val snapshotNote: String? = null,
    val result: OptimizerResult? = null,
    val decision: DecisionEntity? = null,
    /** Trwające (jeszcze niezakończone) zawarte lokaty. */
    val locked: List<LockedLeg> = emptyList(),
    val breakOptions: List<BreakOption> = emptyList(),
    val holdValue: BigDecimal? = null,
    val advice: String? = null,
    val adviceIsBetter: Boolean = false,
    val settingsError: String? = null,
    /** Jednorazowa informacja (np. wynik importu). */
    val notice: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            compute(fetch = false)
            compute(fetch = true)
        }
    }

    /** Przycisk „Sprawdź strategię teraz”: pobiera świeże oferty (jeśli jest sieć) i liczy strategie. */
    fun refresh() {
        viewModelScope.launch { compute(fetch = true) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    private suspend fun compute(fetch: Boolean) {
        _state.update { it.copy(loading = true) }
        val old = _state.value
        val next = withContext(Dispatchers.Default) {
            val settingsEntity = repo.settings()
            val settings = settingsEntity.toSettings()
            val loaded = if (fetch) repo.refreshSnapshot() else repo.loadSnapshot()
            val snapshot = loaded.snapshot
            val rejected = repo.rejected()
            val decision = repo.decision()
            val today = LocalDate.now()
            val base = repo.base(settings, repo.lockedLegs(), today)
            val result = repo.plan(snapshot, settings, base, rejected)
            var advice: String? = null
            var better = false
            if (decision != null) {
                val cmp = repo.check(snapshot, decision, settings, base, rejected).comparison
                when {
                    cmp == null -> advice = "Zatwierdzona strategia jest nadal najlepsza."
                    cmp.notify -> { advice = "Jest lepsza strategia: ${signedMoney(cmp.difference)} (powyżej progu powiadomienia)."; better = true }
                    cmp.belowThreshold -> { advice = "Jest nieco lepsza strategia: ${signedMoney(cmp.difference)} (poniżej progu powiadomienia)."; better = true }
                    else -> advice = "Zatwierdzona strategia jest nadal najlepsza."
                }
            }
            val horizonEnd = decision?.let { LocalDate.parse(it.horizonEnd) } ?: base.start.plusMonths(settings.horizonMonths.toLong())
            val hold = decision?.let { BigDecimal(it.finalValue) } ?: result.inHorizon.firstOrNull()?.finalValue
            val breaks = if (hold != null && base.locked.isNotEmpty()) repo.breakOptions(snapshot, base, hold, horizonEnd, settings) else emptyList()
            UiState(
                loading = false, settings = settingsEntity, snapshot = snapshot, source = loaded.source,
                snapshotNote = loaded.note, result = result, decision = decision, locked = base.locked,
                breakOptions = breaks, holdValue = hold, advice = advice, adviceIsBetter = better,
            )
        }
        // Informacje jednorazowe i błąd formularza przeżywają przeliczenie.
        _state.value = next.copy(notice = old.notice, settingsError = null)
    }

    private fun decisionOf(s: Strategy): DecisionEntity {
        val st = _state.value
        val today = LocalDate.now()
        return DecisionEntity(
            fingerprint = s.fingerprint,
            description = s.offerIds.joinToString(" → "),
            finalValue = s.finalValue.toPlainString(),
            capital = st.settings.capital,
            start = today.toString(),
            horizonEnd = today.plusMonths(st.settings.horizonMonths.toLong()).toString(),
            lastNotifiedDifference = null,
            approvedAt = today.toString(),
        )
    }

    fun approve(s: Strategy) {
        viewModelScope.launch {
            repo.saveDecision(decisionOf(s))
            compute(fetch = false)
        }
    }

    /** „Zawarłem pierwszą lokatę”: zapisuje jej wynik jako zawarty i zatwierdza całą strategię. */
    fun lockFirstLeg(s: Strategy) {
        val leg = s.legs.firstOrNull() ?: return
        viewModelScope.launch {
            repo.addLocked(
                LockedEntity(
                    offerId = leg.offerId, start = leg.start.toString(), amount = leg.amount.toPlainString(),
                    payout = leg.result.payout.toPlainString(), availableDate = leg.result.availableDate.toString(),
                ),
            )
            repo.saveDecision(decisionOf(s))
            compute(fetch = false)
        }
    }

    fun resetDecision() {
        viewModelScope.launch {
            repo.clearDecisionAndLocked()
            compute(fetch = false)
        }
    }

    fun reject(s: Strategy) {
        viewModelScope.launch {
            repo.reject(RejectedEntity(s.fingerprint, s.finalValue.toPlainString(), LocalDate.now().toString()))
            compute(fetch = false)
        }
    }

    fun saveSettings(capital: String, threshold: String, horizonMonths: String) {
        viewModelScope.launch {
            val cap = capital.replace(',', '.').trim().toBigDecimalOrNull()
            val th = threshold.replace(',', '.').trim().toBigDecimalOrNull()
            val months = horizonMonths.trim().toIntOrNull()
            if (cap == null || th == null || months == null || cap.signum() <= 0 || th.signum() < 0 || months < 1 || months > 120) {
                _state.update { it.copy(settingsError = "Kwota musi być większa od zera, próg nie może być ujemny, a horyzont to 1–120 miesięcy.") }
                return@launch
            }
            repo.saveSettings(
                _state.value.settings.copy(
                    capital = cap.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    thresholdPln = th.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    horizonMonths = months,
                ),
            )
            compute(fetch = false)
            _state.update { it.copy(notice = "Ustawienia zapisane.") }
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val json = repo.exportJson()
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: return@withContext "Nie udało się zapisać pliku."
                    "Dane zapisane do pliku."
                } catch (e: Exception) {
                    "Nie udało się zapisać pliku."
                }
            }
            _state.update { it.copy(notice = msg) }
        }
    }

    /** Import zastępuje wszystkie dane użytkownika; przy błędzie nic się nie zmienia. */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@withContext "Nie udało się odczytać pliku."
                    if (bytes.size > 1_000_000) return@withContext "Plik jest za duży na eksport z tej aplikacji."
                    repo.importJson(String(bytes, Charsets.UTF_8))
                    "Dane zostały zastąpione danymi z pliku."
                } catch (e: IllegalArgumentException) {
                    e.message ?: "Plik jest niepoprawny."
                } catch (e: Exception) {
                    "Nie udało się odczytać pliku."
                }
            }
            compute(fetch = false)
            _state.update { it.copy(notice = msg) }
        }
    }
}
