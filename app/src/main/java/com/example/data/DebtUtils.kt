package com.example.data

import java.text.SimpleDateFormat
import java.util.Locale

private val turkishLocale = Locale("tr", "TR")

fun normalizePersonKey(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").lowercase(turkishLocale)

fun capitalizeFirstTurkish(value: String): String {
    if (value.isEmpty()) return value
    val index = value.indexOfFirst { !it.isWhitespace() }
    if (index < 0) return value
    return value.replaceRange(index, index + 1, value.substring(index, index + 1).uppercase(turkishLocale))
}

fun normalizeIban(value: String): String =
    value.filterNot(Char::isWhitespace).uppercase(Locale.ROOT)

/** ISO 13616 checksum plus the standard 15..34 character IBAN shape. */
fun isValidIban(value: String): Boolean {
    val iban = normalizeIban(value)
    if (!iban.matches(Regex("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}"))) return false
    val rearranged = iban.drop(4) + iban.take(4)
    var remainder = 0
    rearranged.forEach { char ->
        val numeric = if (char.isDigit()) char.toString() else (char.code - 'A'.code + 10).toString()
        numeric.forEach { digit -> remainder = (remainder * 10 + digit.digitToInt()) % 97 }
    }
    return remainder == 1
}

fun formatIban(value: String): String = normalizeIban(value).chunked(4).joinToString(" ")

fun maskIban(value: String): String {
    val iban = normalizeIban(value)
    if (iban.length < 8) return "••••"
    return "${iban.take(4)} •••• •••• ${iban.takeLast(4)}"
}

/**
 * Google Keep satır biçimi:
 * Ad - Tutar TL - Açıklama Tarih +/-
 */
fun parseKeepDebtLine(line: String): DebtRecord? {
    val match = Regex(
        """^\s*(.+?)\s+-\s+([0-9][0-9.,]*)\s*(?:TL|₺)?\s+-\s+(.*?)\s+(?:-\s*)?(\d{2}/\d{2}/\d{4})\s*([+-])(?:\s*(✅|❌|Ödendi|Ödenecek))?\s*$""",
        RegexOption.IGNORE_CASE
    ).matchEntire(line) ?: return null

    val name = match.groupValues[1].trim()
    val amount = parseKeepAmount(match.groupValues[2]) ?: return null
    val description = match.groupValues[3].trim().ifEmpty { "Keep Aktarımı" }
    val date = match.groupValues[4]
    val direction = match.groupValues[5]
    val status = match.groupValues[6]
    val validDate = runCatching {
        SimpleDateFormat("dd/MM/yyyy", turkishLocale).apply { isLenient = false }.parse(date)
    }.getOrNull() != null

    if (name.isEmpty() || amount <= 0.0 || !validDate) return null
    return DebtRecord(
        name = capitalizeFirstTurkish(name),
        amount = amount,
        description = description,
        dateStr = date,
        isIncome = direction == "+",
        isPaid = status.equals("Ödendi", ignoreCase = true) || status == "✅"
    )
}

private fun parseKeepAmount(raw: String): Double? {
    val normalized = when {
        ',' in raw && '.' in raw -> raw.replace(".", "").replace(',', '.')
        ',' in raw -> raw.replace(',', '.')
        raw.count { it == '.' } == 1 && raw.substringAfter('.').length <= 2 -> raw
        else -> raw.replace(".", "")
    }
    return normalized.toDoubleOrNull()
}

private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""

fun debtsToCsv(debts: List<DebtRecord>, people: List<PersonWithIbans>): String {
    val ibanByPerson = people.associate { entry ->
        entry.person.personKey to entry.ibans
            .sortedBy { it.createdAt }
            .joinToString(" | ") { iban ->
                val label = iban.label.trim()
                if (label.isEmpty()) formatIban(iban.iban) else "$label: ${formatIban(iban.iban)}"
            }
    }
    val header = listOf(
        "Kişi", "Tutar", "Tarih", "Yön", "Ödeme Durumu", "Açıklama", "IBAN", "Oluşturulma Zamanı"
    ).joinToString(",", transform = ::csvCell)
    val rows = debts.sortedByDescending { it.createdAt }.map { debt ->
        val key = debt.personKey ?: normalizePersonKey(debt.name)
        listOf(
            debt.name,
            debt.amount.toBigDecimal().stripTrailingZeros().toPlainString(),
            debt.dateStr,
            if (debt.isIncome) "Alacak" else "Verecek",
            if (debt.isPaid) "Ödendi" else "Ödenecek",
            debt.description,
            ibanByPerson[key].orEmpty(),
            debt.createdAt.toString()
        ).joinToString(",", transform = ::csvCell)
    }
    return (listOf(header) + rows).joinToString("\r\n")
}
