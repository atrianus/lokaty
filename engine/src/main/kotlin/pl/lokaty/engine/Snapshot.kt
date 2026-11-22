package pl.lokaty.engine

import java.math.BigDecimal
import java.time.LocalDate

data class Bank(val id: String, val name: String, val bfgCovered: YesNoUnknown)
data class Snapshot(val date: LocalDate, val revision: Int, val banks: List<Bank>, val offers: List<OfferRef>)

/** Zamienia migawkę ofert (schemat 1.0, contracts/offer-snapshot.schema.json) na obiekty silnika. Nie waliduje schematu: to robi CI. */
private typealias Obj = Map<String, Any?>

object SnapshotParser {
    private inline fun <reified E : Enum<E>> enumOf(s: Any?, default: E): E =
        (s as String?)?.let { v -> enumValues<E>().firstOrNull { it.name.equals(v, ignoreCase = true) } } ?: default

    @Suppress("UNCHECKED_CAST")
    fun parse(json: String): Snapshot {
        val root = MiniJson(json).parse() as Obj
        val banks = (root["banks"] as List<Obj>).map {
            Bank(it["bank_id"] as String, it["name"] as String, enumOf(it["bfg_covered"], YesNoUnknown.UNKNOWN))
        }
        val offers = (root["offers"] as List<Obj>).map(::offer)
        return Snapshot(LocalDate.parse(root["snapshot_date"] as String), (root["revision"] as String).toInt(), banks, offers)
    }

    @Suppress("UNCHECKED_CAST")
    private fun offer(o: Obj): OfferRef {
        fun dec(k: String) = (o[k] as String?)?.let { BigDecimal(it) }
        val term = o["term"] as Obj
        val rounding = o["rounding"] as Obj
        val cond = o["conditions"] as Obj
        val early = o["early_termination"] as Obj
        val labels = buildList {
            if (cond["new_customer_only"] == "yes") add("new_customers_only")
            if (cond["new_funds_only"] == "yes") add("new_funds_only")
            if (cond["account_required"] == "yes") add("account_required")
            if (o["completeness"] == "partial") add("partial_data")
            if (o["verification"] == "expired") add("offer_expired")
        }
        val offer = Offer(
            interestType = enumOf(o["interest_type"], InterestType.UNKNOWN),
            rateAnnual = dec("rate_annual"),
            termValue = (term["value"] as String).toInt(),
            termUnit = enumOf(term["unit"], TermUnit.MONTHS),
            dayCount = enumOf(o["day_count_rule"], DayCount.UNKNOWN),
            rounding = enumOf(rounding["mode"], Rounding.UNKNOWN),
            availability = enumOf(o["availability_rule"], Availability.NEXT_BUSINESS_DAY),
            availabilityDays = (o["availability_days"] as String?)?.toInt() ?: 0,
            capPeriod = (o["capitalization_period"] as String?)?.let { enumOf(it, CapPeriod.AT_END) },
            taxTiming = enumOf(o["tax_timing"], TaxTiming.UNKNOWN),
            earlyAllowed = enumOf(early["allowed"], YesNoUnknown.UNKNOWN),
            earlyTreatment = enumOf(early["interest_treatment"], Treatment.UNKNOWN),
            earlyFee = (early["fee"] as String?)?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
            amountMin = dec("amount_min"),
            amountMax = dec("amount_max"),
        )
        return OfferRef(
            id = o["offer_id"] as String, bankId = o["bank_id"] as String, offer = offer,
            validFrom = LocalDate.parse(o["valid_from"] as String),
            validUntil = (o["valid_until"] as String?)?.let { LocalDate.parse(it) },
            oneTimeOnly = enumOf(cond["one_time_only"], YesNoUnknown.UNKNOWN),
            labels = labels,
        )
    }
}
