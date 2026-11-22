package pl.lokaty.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.lokaty.app.data.DecisionEntity
import pl.lokaty.engine.Confidence
import pl.lokaty.engine.Strategy
import java.math.BigDecimal

private val labelText = mapOf(
    "new_customers_only" to "tylko nowi klienci",
    "new_funds_only" to "tylko nowe środki",
    "account_required" to "wymaga konta",
    "partial_data" to "niepełne dane o ofercie",
    "offer_expired" to "oferta wygasła",
    "offer_without_end_date" to "brak daty końca oferty",
    "day_count_assumed" to "założona liczba dni w roku",
    "tax_timing_assumed" to "założony moment podatku",
    "rounding_default" to "domyślne zaokrąglanie",
    "payout_period_assumed" to "założona wypłata odsetek",
    "one_time_unknown" to "oferta jednorazowa? do sprawdzenia",
)

private fun confidenceText(c: Confidence) = when (c) {
    Confidence.EXACT -> "dokładny"
    Confidence.APPROXIMATE -> "przybliżony"
    Confidence.UNVERIFIED -> "niezweryfikowany"
}

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("Strategie", "Ustawienia", "Dane")
    Scaffold { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> StrategiesTab(state, onRefresh = vm::refresh, onApprove = vm::approve, onReject = vm::reject)
                1 -> SettingsTab(state, onSave = vm::saveSettings)
                else -> DataTab(state)
            }
        }
    }
}

@Composable
private fun StrategiesTab(state: UiState, onRefresh: () -> Unit, onApprove: (Strategy) -> Unit, onReject: (Strategy) -> Unit) {
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Dane przykładowe: oferty są wymyślone, to wersja testowa aplikacji.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Button(onClick = onRefresh, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.loading) "Liczę…" else "Sprawdź strategię teraz")
            }
        }
        state.decision?.let { d -> item { DecisionCard(d, state.message) } }
        if (state.decision == null) state.message?.let { m -> item { Text(m) } }
        val result = state.result
        if (result != null) {
            item { Text("Mieszczą się w horyzoncie", style = MaterialTheme.typography.titleMedium) }
            if (result.inHorizon.isEmpty()) item { Text("Brak strategii mieszczących się w horyzoncie.") }
            items(result.inHorizon, key = { "in-" + it.fingerprint }) { s -> StrategyCard(s, state.settings.capital, onApprove, onReject) }
            if (result.overHorizon.isNotEmpty()) {
                item { Text("Przekraczają horyzont", style = MaterialTheme.typography.titleMedium) }
                items(result.overHorizon, key = { "over-" + it.fingerprint }) { s -> StrategyCard(s, state.settings.capital, onApprove, onReject) }
            }
        }
    }
}

@Composable
private fun DecisionCard(d: DecisionEntity, message: String?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Zatwierdzona strategia", style = MaterialTheme.typography.titleSmall)
            Text(d.description)
            Text("Wartość na końcu: ${money(BigDecimal(d.finalValue))}")
            message?.let { Text(it) }
        }
    }
}

@Composable
private fun StrategyCard(s: Strategy, capital: String, onApprove: (Strategy) -> Unit, onReject: (Strategy) -> Unit) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            s.legs.forEachIndexed { i, leg ->
                val maturity = leg.result.maturityDate
                Text("${i + 1}. ${leg.offerId}: ${leg.start} → $maturity, odsetki netto ${money(leg.result.interestNet)}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Wartość na końcu: ${money(s.finalValue)}", style = MaterialTheme.typography.titleSmall)
            Text("Zysk netto: ${money(s.finalValue - BigDecimal(capital))} (${confidenceText(s.confidence)})")
            if (s.overHorizon) {
                Text("Wykracza o ${s.extraDays} dni, dodatkowy zysk ${s.extraProfit?.let { money(it) } ?: "?"}")
            }
            if (s.labels.isNotEmpty()) {
                Text(s.labels.joinToString(", ") { labelText[it] ?: it }, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onApprove(s) }) { Text("Zatwierdź") }
                OutlinedButton(onClick = { onReject(s) }) { Text("Odrzuć") }
            }
        }
    }
}

@Composable
private fun SettingsTab(state: UiState, onSave: (String, String, String) -> Unit) {
    var capital by remember(state.settings) { mutableStateOf(state.settings.capital) }
    var threshold by remember(state.settings) { mutableStateOf(state.settings.thresholdPln) }
    var months by remember(state.settings) { mutableStateOf(state.settings.horizonMonths.toString()) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(capital, { capital = it }, label = { Text("Kapitał (zł)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(months, { months = it }, label = { Text("Horyzont (miesiące, miękki limit)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(threshold, { threshold = it }, label = { Text("Próg powiadomienia (zł)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onSave(capital, threshold, months) }, modifier = Modifier.fillMaxWidth()) { Text("Zapisz") }
        state.message?.let { if (it.startsWith("Niepoprawne")) Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Limity wyszukiwania: ${state.settings.maxLegs} nogi, ${state.settings.maxShown} strategie, luka do ${state.settings.maxGapDays} dni.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DataTab(state: UiState) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val snap = state.snapshot
        if (snap == null) Text("Brak danych.") else {
            Text("Migawka ofert: ${snap.date}, rewizja ${snap.revision}")
            Text("Banki: ${snap.banks.size}, oferty: ${snap.offers.size}")
        }
        Text("Dane przykładowe zapisane w aplikacji. Pobieranie prawdziwych ofert (GitHub Pages) dojdzie w następnym etapie.", style = MaterialTheme.typography.bodySmall)
        Text("Twoje decyzje i ustawienia są tylko na tym telefonie. Eksport i import danych dojdą w następnym etapie.", style = MaterialTheme.typography.bodySmall)
    }
}
