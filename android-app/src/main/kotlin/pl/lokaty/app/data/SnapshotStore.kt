package pl.lokaty.app.data

import android.content.Context
import pl.lokaty.engine.Snapshot
import pl.lokaty.engine.SnapshotParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class SnapshotSource { NETWORK, CACHE, SAMPLE }

data class LoadedSnapshot(val snapshot: Snapshot, val source: SnapshotSource, val note: String? = null)

/**
 * Migawka ofert: sieć (GitHub Pages) → pamięć podręczna (plik) → migawka przykładowa z zasobów.
 * Pobrana migawka jest przyjmowana tylko, jeśli da się ją odczytać i nie jest starsza (data, rewizja) od zapisanej.
 * Podpis migawki: jeszcze nierozstrzygnięty (docs/PRZEKAZANIE.md), więc na razie bez weryfikacji podpisu.
 */
class SnapshotStore(private val context: Context) {
    private val cacheFile get() = File(context.filesDir, "snapshot-cache.json")

    fun loadCached(): LoadedSnapshot {
        val cached = readCache()
        if (cached != null) return LoadedSnapshot(cached, SnapshotSource.CACHE)
        return LoadedSnapshot(sample(), SnapshotSource.SAMPLE)
    }

    /** Próbuje pobrać świeżą migawkę. Błąd sieci lub formatu nie psuje niczego: zwracany jest stan dotychczasowy z opisem. */
    fun refresh(): LoadedSnapshot {
        val fallback = loadCached()
        val text = try {
            download()
        } catch (e: Exception) {
            return fallback.copy(note = "Nie udało się pobrać ofert (${e.javaClass.simpleName}); używam danych zapisanych wcześniej.")
        }
        val parsed = try {
            SnapshotParser.parse(text)
        } catch (e: Exception) {
            return fallback.copy(note = "Pobrane dane mają niepoprawny format; używam danych zapisanych wcześniej.")
        }
        val old = readCache()
        if (old != null && (parsed.date < old.date || (parsed.date == old.date && parsed.revision < old.revision))) {
            return fallback.copy(note = "Pobrana migawka jest starsza niż zapisana; zostaje zapisana.")
        }
        cacheFile.writeText(text)
        return LoadedSnapshot(parsed, SnapshotSource.NETWORK)
    }

    private fun readCache(): Snapshot? = try {
        if (cacheFile.exists()) SnapshotParser.parse(cacheFile.readText()) else null
    } catch (e: Exception) {
        null
    }

    private fun sample(): Snapshot =
        SnapshotParser.parse(context.assets.open("snapshot-przyklad.json").bufferedReader().use { it.readText() })

    private fun download(): String {
        val c = URL(SNAPSHOT_URL).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.requestMethod = "GET"
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val text = c.inputStream.bufferedReader().use { it.readText() }
            if (text.length > MAX_BYTES) error("za duży plik")
            return text
        } finally {
            c.disconnect()
        }
    }

    companion object {
        /** Adres migawki publikowanej przez workflow „publish” (GitHub Pages repozytorium atrianus/lokaty). */
        const val SNAPSHOT_URL = "https://atrianus.github.io/lokaty/snapshot.json"
        private const val MAX_BYTES = 5_000_000
    }
}
