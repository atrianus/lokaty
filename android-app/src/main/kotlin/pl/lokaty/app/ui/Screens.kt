package pl.lokaty.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.lokaty.app.R
import pl.lokaty.app.data.SnapshotSource
import pl.lokaty.engine.BreakOption
import pl.lokaty.engine.Confidence
import pl.lokaty.engine.LockedLeg
import pl.lokaty.engine.OfferRef
import pl.lokaty.engine.Snapshot
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
    "interest_type_unsupported" to "typ odsetków nieobsługiwany",
    "early_termination_unknown" to "zasady zerwania nieznane",
    "rate_on_break_assumed" to "założona stawka przy zerwaniu",
)

private fun confidenceText(c: Confidence) = when (c) {
    Confidence.EXACT -> "dokładny"
    Confidence.APPROXIMATE -> "przybliżony"
    Confidence.UNVERIFIED -> "niezweryfikowany"
}

/** Nazwy banków i ofert do wyświetlania (zwykła mapa po identyfikatorach z migawki). */
private class Names(snapshot: Snapshot?) {
    private val offers: Map<String, OfferRef> = snapshot?.offers?.associateBy { it.id } ?: emptyMap()
    private val banks: Map<String, String> = snapshot?.banks?.associate { it.id to it.name } ?: emptyMap()
    fun offer(id: String): OfferRef? = offers[id]
    fun bank(id: String): String = offers[id]?.let { banks[it.bankId] } ?: id.substringBefore('/')
    fun product(id: String): String = offers[id]?.title?.takeIf { it.isNotBlank() } ?: id
    fun details(id: String): String = offers[id]?.let { o ->
        listOfNotNull(percent(o.offer.rateAnnual), term(o.offer.termValue, o.offer.termUnit)).joinToString(" · ")
    } ?: ""
}

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val names = remember(state.snapshot) { Names(state.snapshot) }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Strategie") },
                    icon = { Icon(painterResource(R.drawable.ic_tab_strategies), contentDescription = null) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Ustawienia") },
                    icon = { Icon(painterResource(R.drawable.ic_tab_settings), contentDescription = null) })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Dane") },
                    icon = { Icon(painterResource(R.drawable.ic_tab_data), contentDescription = null) })
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxWidth()) {
            state.notice?.let { NoticeBar(it, vm::dismissNotice) }
            when (tab) {
                0 -> StrategiesTab(state, names, vm)
                1 -> SettingsTab(state, vm::saveSettings)
                else -> DataTab(state, vm)
            }
        }
    }
}

@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
private fun StrategiesTab(state: UiState, names: Names, vm: MainViewModel) {
    val result = state.result
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Lokaty", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        if (state.source == SnapshotSource.SAMPLE) {
            item { InfoCard("Dane przykładowe: banki i oferty są wymyślone. Prawdziwe oferty pojawią się po uruchomieniu publikacji ofert.", warning = true) }
        }
        state.snapshotNote?.let { n -> item { Text(n, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item {
            Button(onClick = vm::refresh, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (state.loading) "Liczę…" else "Sprawdź strategię teraz")
            }
        }
        state.decision?.let { d ->
            item { DecisionCard(d.description.split(" → ").joinToString(" → ") { names.bank(it) }, BigDecimal(d.finalValue), state, names, vm::resetDecision) }
        }
        if (state.breakOptions.isNotEmpty()) {
            item { SectionTitle("Opcje ze zerwaniem", "Informacyjnie: aplikacja nie rekomenduje zerwania lokaty.") }
            items(state.breakOptions, key = { "brk-" + it.offerId }) { BreakCard(it, names) }
        }
        if (result != null) {
            item {
                SectionTitle(
                    "Strategie w horyzoncie",
                    if (state.settings.capital.isNotBlank()) "Kapitał ${money(BigDecimal(state.settings.capital))}, horyzont ${state.settings.horizonMonths} mies." else null,
                )
            }
            if (result.inHorizon.isEmpty()) {
                item { InfoCard("Brak strategii mieszczących się w horyzoncie. Spróbuj wydłużyć horyzont w ustawieniach albo sprawdź ponownie po pobraniu świeższych ofert.") }
            }
            items(result.inHorizon, key = { "in-" + it.fingerprint }) { s -> StrategyCard(s, state, names, vm) }
            if (result.overHorizon.isNotEmpty()) {
                item { SectionTitle("Poza horyzontem", "Dają więcej, ale kończą się później. Możesz je odrzucić; aplikacja to zapamięta.") }
                items(result.overHorizon, key = { "over-" + it.fingerprint }) { s -> StrategyCard(s, state, names, vm) }
            }
            if (result.unverified.isNotEmpty()) {
                item {
                    Text(
                        "Pominięto ${result.unverified.size} strategii z niezweryfikowanych ofert; nie biorą udziału w rankingu.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun InfoCard(text: String, warning: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (warning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (warning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun Chip(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ConfidenceChip(c: Confidence) {
    val scheme = MaterialTheme.colorScheme
    when (c) {
        Confidence.EXACT -> Chip(confidenceText(c), scheme.primaryContainer, scheme.onPrimaryContainer)
        Confidence.APPROXIMATE -> Chip(confidenceText(c), scheme.tertiaryContainer, scheme.onTertiaryContainer)
        Confidence.UNVERIFIED -> Chip(confidenceText(c), scheme.errorContainer, scheme.onErrorContainer)
    }
}

@Composable
private fun DecisionCard(description: String, finalValue: BigDecimal, state: UiState, names: Names, onReset: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Twoja zatwierdzona strategia", style = MaterialTheme.typography.labelLarge)
            Text(money(finalValue), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            state.advice?.let {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (state.adviceIsBetter) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (state.locked.isNotEmpty()) {
                Text("Zawarte lokaty:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                state.locked.forEach { l ->
                    Text(
                        "${names.bank(l.offerId)}: ${money(l.amount)} od ${date(l.start)}, dostępne ${date(l.availableDate)}, wypłata ${money(l.payout)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            TextButton(onClick = { confirm = true }) { Text("Wyczyść strategię i zawarte lokaty") }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Wyczyścić?") },
            text = { Text("Usunięta zostanie zatwierdzona strategia i zapis zawartych lokat. Ustawienia i odrzucone strategie zostają.") },
            confirmButton = { TextButton(onClick = { confirm = false; onReset() }) { Text("Wyczyść") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Anuluj") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrategyCard(s: Strategy, state: UiState, names: Names, vm: MainViewModel) {
    val capital = BigDecimal(state.settings.capital)
    val isApproved = state.decision?.fingerprint == s.fingerprint
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wartość na końcu", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ConfidenceChip(s.confidence)
            }
            Text(money(s.finalValue), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val lockedPart = s.lockedLegs.size
            Text(
                "Zysk netto ${signedMoney(s.finalValue - (s.lockedLegs.firstOrNull()?.amount ?: capital))} · ${lockedPart + s.legs.size} " +
                    (if (lockedPart + s.legs.size == 1) "lokata" else "lokaty") + " · dostępne ${date(s.availableDate)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
            )
            if (s.overHorizon) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                    Text(
                        "Wykracza poza horyzont o ${s.extraDays ?: "?"} dni; dodatkowy zysk względem najlepszej w horyzoncie: ${s.extraProfit?.let { signedMoney(it) } ?: "?"}.",
                        Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Column {
                s.lockedLegs.forEach { l -> LockedRow(l, names) }
                s.legs.forEachIndexed { i, leg ->
                    TimelineRow(
                        title = names.bank(leg.offerId),
                        subtitle = listOf(names.product(leg.offerId), names.details(leg.offerId)).filter { it.isNotBlank() }.joinToString(" · "),
                        info = "${date(leg.start)} → ${date(leg.result.maturityDate)} · odsetki netto ${signedMoney(leg.result.interestNet)}",
                        tag = null,
                        last = i == s.legs.lastIndex,
                    )
                }
            }
            if (s.labels.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.labels.forEach { Chip(labelText[it] ?: it, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (isApproved) {
                Text("To jest Twoja zatwierdzona strategia.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (s.legs.isNotEmpty()) {
                Button(onClick = { vm.lockFirstLeg(s) }, modifier = Modifier.fillMaxWidth()) { Text("Zawarłem pierwszą lokatę") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (!isApproved) {
                    OutlinedButton(onClick = { vm.approve(s) }, modifier = Modifier.weight(1f)) { Text("Zatwierdź plan") }
                }
                TextButton(onClick = { vm.reject(s) }, modifier = Modifier.weight(1f)) { Text("Odrzuć") }
            }
        }
    }
}

@Composable
private fun LockedRow(l: LockedLeg, names: Names) {
    TimelineRow(
        title = names.bank(l.offerId),
        subtitle = listOf(names.product(l.offerId), names.details(l.offerId)).filter { it.isNotBlank() }.joinToString(" · "),
        info = "${date(l.start)} → dostępne ${date(l.availableDate)} · wypłata ${money(l.payout)}",
        tag = "zawarta",
        last = false,
    )
}

/** Wiersz osi czasu: kropka i linia po lewej, dane lokaty po prawej. */
@Composable
private fun TimelineRow(title: String, subtitle: String, info: String, tag: String?, last: Boolean) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 5.dp).size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            if (!last) {
                Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
            }
        }
        Column(Modifier.padding(start = 8.dp, bottom = if (last) 0.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                tag?.let { Chip(it, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BreakCard(o: BreakOption, names: Names) {
    val uriHandler = LocalUriHandler.current
    val url = names.offer(o.offerId)?.sourceUrl
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zerwanie: ${names.bank(o.offerId)}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ConfidenceChip(if (o.variants.size > 1) Confidence.APPROXIMATE else o.confidence)
            }
            Text("Gdybyś zerwał lokatę ${date(o.breakDate)}:", style = MaterialTheme.typography.bodyMedium)
            o.variants.forEach { v ->
                val title = if (o.variants.size > 1) (if (v.optimistic) "Wariant optymistyczny (odsetki proporcjonalne)" else "Wariant ostrożny (odsetki utracone)") else "Wynik"
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    Text("Wypłata ${money(v.payoutNow)}, dostępna ${date(v.availableDate)}.", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Wartość na końcu horyzontu: ${money(v.finalValue)} (${signedMoney(v.difference)} względem utrzymania lokaty).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (v.difference.signum() > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (o.labels.isNotEmpty()) Text(o.labels.joinToString(", ") { labelText[it] ?: it }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Sprawdź warunki zerwania w banku przed jakąkolwiek decyzją.", style = MaterialTheme.typography.bodySmall)
            if (url != null) TextButton(onClick = { uriHandler.openUri(url) }) { Text("Warunki w banku") }
        }
    }
}

@Composable
private fun SettingsTab(state: UiState, onSave: (String, String, String) -> Unit) {
    var capital by remember(state.settings) { mutableStateOf(state.settings.capital) }
    var threshold by remember(state.settings) { mutableStateOf(state.settings.thresholdPln) }
    var months by remember(state.settings) { mutableStateOf(state.settings.horizonMonths.toString()) }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Ustawienia", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            OutlinedTextField(capital, { capital = it }, label = { Text("Kapitał (zł)") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(
                months, { months = it }, label = { Text("Horyzont (miesiące)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("To miękki limit: strategie dłuższe są pokazywane osobno.") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                threshold, { threshold = it }, label = { Text("Próg powiadomienia (zł)") }, singleLine = true, keyboardOptions = decimal,
                supportingText = { Text("Powiadomienie o lepszej strategii od tej przewagi. Mniejsze przewagi są oznaczane, nie ukrywane.") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.settingsError?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
        item { Button(onClick = { onSave(capital, threshold, months) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Zapisz") } }
        item {
            InfoCard("Limity wyszukiwania: do ${state.settings.maxLegs} lokat w strategii, ${state.settings.maxShown} pokazywane strategie, przerwa między lokatami do ${state.settings.maxGapDays} dni.")
        }
    }
}

@Composable
private fun DataTab(state: UiState, vm: MainViewModel) {
    var confirmImport by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.exportTo(uri) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importFrom(uri) }
    val snap = state.snapshot
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Dane", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Oferty lokat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (state.source) {
                            SnapshotSource.NETWORK -> "Źródło: pobrane z sieci."
                            SnapshotSource.CACHE -> "Źródło: zapisane wcześniej na telefonie (brak świeżego pobrania)."
                            SnapshotSource.SAMPLE -> "Źródło: dane przykładowe (wymyślone)."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (snap != null) {
                        Text("Migawka z ${date(snap.date)}, rewizja ${snap.revision}.", style = MaterialTheme.typography.bodyMedium)
                        Text("Banki: ${snap.banks.size}, oferty: ${snap.offers.size}.", style = MaterialTheme.typography.bodyMedium)
                    }
                    state.snapshotNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    OutlinedButton(onClick = vm::refresh, enabled = !state.loading) { Text("Pobierz oferty teraz") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Twoje dane", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Ustawienia, zatwierdzona strategia, zawarte lokaty i odrzucone strategie są tylko na tym telefonie. " +
                            "Kopia systemowa jest wyłączona, więc zapisz eksport do pliku, żeby nie stracić danych przy zmianie telefonu.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { exportLauncher.launch("lokaty-dane.json") }, modifier = Modifier.fillMaxWidth()) { Text("Eksportuj dane do pliku") }
                    OutlinedButton(onClick = { confirmImport = true }, modifier = Modifier.fillMaxWidth()) { Text("Importuj dane z pliku") }
                }
            }
        }
        item {
            InfoCard(
                "Aplikacja liczy i porównuje oferty, ale nie jest doradcą ani nie zakłada lokat za Ciebie. Wyniki są przybliżone; " +
                    "przed założeniem lokaty sprawdź warunki w banku, w tym limit gwarancji BFG.",
            )
        }
    }
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Zastąpić wszystkie dane?") },
            text = { Text("Import usuwa obecne ustawienia, strategię, zawarte lokaty i odrzucone strategie, a wstawia dane z pliku. Jeśli plik jest niepoprawny, nic się nie zmieni.") },
            confirmButton = {
                TextButton(onClick = { confirmImport = false; importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Wybierz plik") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Anuluj") } },
        )
    }
}
