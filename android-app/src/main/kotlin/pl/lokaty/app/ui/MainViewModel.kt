package pl.lokaty.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.lokaty.app.data.DecisionEntity
import pl.lokaty.app.data.RejectedEntity
import pl.lokaty.app.data.Repository
import pl.lokaty.app.data.SettingsEntity
import pl.lokaty.app.data.toSettings
import pl.lokaty.engine.OptimizerResult
import pl.lokaty.engine.Snapshot
import pl.lokaty.engine.Strategy
import java.math.BigDecimal
import java.time.LocalDate

data class UiState(
    val loading: Boolean = true,
    val settings: SettingsEntity = SettingsEntity(),
    val snapshot: Snapshot? = null,
    val result: OptimizerResult? = null,
    val decision: DecisionEntity? = null,
    val message: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        refresh()
    }

    /** Przycisk „Sprawdź strategię teraz”: liczy strategie z ostatniej migawki i porównuje z zatwierdzoną. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val next = withContext(Dispatchers.Default) {
                val settingsEntity = repo.settings()
                val settings = settingsEntity.toSettings()
                val snapshot = repo.loadSnapshot()
                val rejected = repo.rejected()
                val decision = repo.decision()
                val result = repo.plan(snapshot, settings, LocalDate.now(), rejected)
                val message = decision?.let { d ->
                    val check = repo.check(snapshot, d, settings, rejected)
                    val cmp = check.comparison
                    when {
                        cmp == null -> "Zatwierdzona strategia jest nadal najlepsza."
                        check.notify -> "Jest lepsza strategia: +${money(cmp.difference)} (powyżej progu)."
                        cmp.belowThreshold -> "Jest lepsza strategia: +${money(cmp.difference)}, poniżej progu powiadomienia."
                        else -> "Zatwierdzona strategia jest nadal najlepsza."
                    }
                }
                UiState(false, settingsEntity, snapshot, result, decision, message)
            }
            _state.value = next
        }
    }

    fun approve(s: Strategy) {
        viewModelScope.launch {
            val st = _state.value
            val today = LocalDate.now()
            repo.saveDecision(
                DecisionEntity(
                    fingerprint = s.fingerprint,
                    description = s.offerIds.joinToString(" → "),
                    finalValue = s.finalValue.toPlainString(),
                    capital = st.settings.capital,
                    start = today.toString(),
                    horizonEnd = today.plusMonths(st.settings.horizonMonths.toLong()).toString(),
                    lastNotifiedDifference = null,
                    approvedAt = today.toString(),
                ),
            )
            refresh()
        }
    }

    fun reject(s: Strategy) {
        viewModelScope.launch {
            repo.reject(RejectedEntity(s.fingerprint, s.finalValue.toPlainString(), LocalDate.now().toString()))
            refresh()
        }
    }

    fun saveSettings(capital: String, threshold: String, horizonMonths: String) {
        viewModelScope.launch {
            val cap = capital.replace(',', '.').toBigDecimalOrNull()
            val th = threshold.replace(',', '.').toBigDecimalOrNull()
            val months = horizonMonths.toIntOrNull()
            if (cap == null || th == null || months == null || cap.signum() <= 0 || th.signum() < 0 || months < 1) {
                _state.update { it.copy(message = "Niepoprawne ustawienia: kwota, próg i horyzont muszą być liczbami.") }
                return@launch
            }
            repo.saveSettings(
                _state.value.settings.copy(
                    capital = cap.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                    thresholdPln = th.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                    horizonMonths = months,
                ),
            )
            refresh()
        }
    }
}

/** Kwota po polsku: 10 102,08 zł (bez zaokrąglania: BigDecimal ze skali 2). */
fun money(x: BigDecimal): String = x.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " zł"
