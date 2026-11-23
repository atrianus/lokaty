package pl.lokaty.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

/** Zawartość eksportu: wszystkie dane użytkownika (ustawienia, decyzja, zawarte lokaty, odrzucone strategie). */
data class UserData(
    val settings: SettingsEntity,
    val decision: DecisionEntity?,
    val locked: List<LockedEntity>,
    val rejected: List<RejectedEntity>,
)

/** Eksport i import danych użytkownika jako JSON. Import jest „wszystko albo nic”: najpierw pełna walidacja, potem zamiana całości. */
object Backup {
    const val FORMAT = "lokaty-backup"
    const val VERSION = 1

    fun export(d: UserData): String {
        val o = JSONObject()
        o.put("format", FORMAT)
        o.put("version", VERSION)
        o.put("settings", JSONObject().apply {
            put("thresholdPln", d.settings.thresholdPln); put("capital", d.settings.capital)
            put("horizonMonths", d.settings.horizonMonths); put("maxLegs", d.settings.maxLegs)
            put("maxShown", d.settings.maxShown); put("maxGapDays", d.settings.maxGapDays)
        })
        d.decision?.let {
            o.put("decision", JSONObject().apply {
                put("fingerprint", it.fingerprint); put("description", it.description); put("finalValue", it.finalValue)
                put("capital", it.capital); put("start", it.start); put("horizonEnd", it.horizonEnd)
                put("lastNotifiedDifference", it.lastNotifiedDifference ?: JSONObject.NULL); put("approvedAt", it.approvedAt)
            })
        }
        o.put("locked", JSONArray().apply {
            d.locked.forEach {
                put(JSONObject().apply {
                    put("offerId", it.offerId); put("start", it.start); put("amount", it.amount)
                    put("payout", it.payout); put("availableDate", it.availableDate)
                })
            }
        })
        o.put("rejected", JSONArray().apply {
            d.rejected.forEach {
                put(JSONObject().apply { put("fingerprint", it.fingerprint); put("valueAtRejection", it.valueAtRejection); put("rejectedAt", it.rejectedAt) })
            }
        })
        return o.toString(2)
    }

    /** Zwraca dane albo rzuca IllegalArgumentException z opisem po polsku. Nic nie zapisuje. */
    fun parse(text: String): UserData {
        val o = try { JSONObject(text) } catch (e: Exception) { throw IllegalArgumentException("To nie jest poprawny plik JSON.") }
        if (o.optString("format") != FORMAT) throw IllegalArgumentException("To nie jest plik eksportu aplikacji Lokaty.")
        if (o.optInt("version", -1) != VERSION) throw IllegalArgumentException("Nieobsługiwana wersja pliku eksportu.")
        try {
            val s = o.getJSONObject("settings")
            val settings = SettingsEntity(
                thresholdPln = dec(s.getString("thresholdPln")), capital = dec(s.getString("capital")),
                horizonMonths = s.getInt("horizonMonths").also { require(it in 1..120) },
                maxLegs = s.getInt("maxLegs").also { require(it in 1..5) },
                maxShown = s.getInt("maxShown").also { require(it in 1..20) },
                maxGapDays = s.getInt("maxGapDays").also { require(it in 0..60) },
            )
            require(BigDecimal(settings.capital).signum() > 0 && BigDecimal(settings.thresholdPln).signum() >= 0)
            val decision = o.optJSONObject("decision")?.let {
                DecisionEntity(
                    fingerprint = it.getString("fingerprint"), description = it.getString("description"),
                    finalValue = dec(it.getString("finalValue")), capital = dec(it.getString("capital")),
                    start = date(it.getString("start")), horizonEnd = date(it.getString("horizonEnd")),
                    lastNotifiedDifference = if (it.isNull("lastNotifiedDifference")) null else dec(it.getString("lastNotifiedDifference")),
                    approvedAt = date(it.getString("approvedAt")),
                )
            }
            val locked = (o.optJSONArray("locked") ?: JSONArray()).let { a ->
                (0 until a.length()).map {
                    val l = a.getJSONObject(it)
                    LockedEntity(0, l.getString("offerId"), date(l.getString("start")), dec(l.getString("amount")), dec(l.getString("payout")), date(l.getString("availableDate")))
                }
            }
            val rejected = (o.optJSONArray("rejected") ?: JSONArray()).let { a ->
                (0 until a.length()).map {
                    val r = a.getJSONObject(it)
                    RejectedEntity(r.getString("fingerprint"), dec(r.getString("valueAtRejection")), date(r.getString("rejectedAt")))
                }
            }
            return UserData(settings, decision, locked, rejected)
        } catch (e: Exception) {
            throw IllegalArgumentException("Plik eksportu jest uszkodzony albo ma niepoprawne wartości. Nic nie zostało zmienione.")
        }
    }

    private fun dec(s: String): String = BigDecimal(s).toPlainString()
    private fun date(s: String): String = LocalDate.parse(s).toString()
}
