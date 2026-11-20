package pl.lokaty.engine

import java.io.File
import java.time.LocalDate
import kotlin.system.exitProcess

@Suppress("UNCHECKED_CAST")
private fun cases(path: String): Map<String, Map<String, Any?>> {
    val root = MiniJson(File(path).readText()).parse() as Map<String, Any?>
    return (root["cases"] as List<Map<String, Any?>>).associateBy { it["id"] as String }
}

private var failures = 0
private fun check(name: String, expected: Any?, actual: Any?) {
    if (expected != actual) { failures++; println("BŁĄD $name: oczekiwano $expected, jest $actual") } else println("ok   $name")
}

@Suppress("UNCHECKED_CAST")
fun main(args: Array<String>) {
    val c = cases(args.firstOrNull() ?: "../test-data/engine/cases.json")
    fun d(x: Any?) = LocalDate.parse(x as String)
    fun strs(x: Any?) = (x as List<Any?>).map { it as String }

    // K8: następny dzień roboczy
    val k8 = c.getValue("K8")
    check("K8 następny dzień roboczy",
        strs((k8["expected"] as Map<String, Any?>)["available_dates"]),
        strs((k8["input"] as Map<String, Any?>)["maturity_dates"]).map { Calendar.nextBusinessDay(d(it)).toString() })

    // K9: koniec lokaty w miesiącach
    val k9 = c.getValue("K9")
    val cases9 = (k9["input"] as Map<String, Any?>)["cases"] as List<Map<String, Any?>>
    check("K9 koniec w miesiącach",
        strs((k9["expected"] as Map<String, Any?>)["maturity_dates"]),
        cases9.map { Calendar.addMonths(d(it["start"]), (it["months"] as String).toInt()).toString() })

    // K10: Wielkanoc i Boże Ciało
    val k10 = c.getValue("K10")
    val years = ((k10["input"] as Map<String, Any?>)["years"] as List<Any?>).map { (it as String).toInt() }
    val e10 = k10["expected"] as Map<String, Any?>
    check("K10 Wielkanoc", strs(e10["easter"]), years.map { Calendar.easter(it).toString() })
    check("K10 Boże Ciało", strs(e10["corpus_christi"]), years.map { Calendar.corpusChristi(it).toString() })

    // Dodatkowe: Wigilia wolna od 2025, nie wcześniej
    check("Wigilia 2024 dzień roboczy (wtorek)", true, Calendar.isBusinessDay(LocalDate.of(2024, 12, 24)))
    check("Wigilia 2025 dzień wolny", false, Calendar.isBusinessDay(LocalDate.of(2025, 12, 24)))
    check("14 świąt w 2026", 14, Calendar.holidays(2026).size) // 10 stałych (z Wigilią) + 4 ruchome
    check("dni robocze: +2 od piątku 19.02.2027", LocalDate.of(2027, 2, 23), Calendar.addBusinessDays(LocalDate.of(2027, 2, 19), 2))

    println(if (failures == 0) "WSZYSTKIE OK" else "BŁĘDY: $failures")
    if (failures > 0) exitProcess(1)
}
