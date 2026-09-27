package com.example

import com.example.data.DebtRecord
import com.example.data.PersonIban
import com.example.data.PersonRecord
import com.example.data.PersonWithIbans
import com.example.data.capitalizeFirstTurkish
import com.example.data.debtsToCsv
import com.example.data.isValidIban
import com.example.data.normalizeIban
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtUtilsTest {
    @Test
    fun `turkish name capitalization keeps dotless i correct`() {
        assertEquals("Işık", capitalizeFirstTurkish("ışık"))
        assertEquals("İpek", capitalizeFirstTurkish("ipek"))
        assertEquals("  Işık", capitalizeFirstTurkish("  ışık"))
    }

    @Test
    fun `iban is normalized and checksum validated`() {
        val iban = "tr33 0006 1005 1978 6457 8413 26"
        assertEquals("TR330006100519786457841326", normalizeIban(iban))
        assertTrue(isValidIban(iban))
        assertFalse(isValidIban("TR00 1234"))
    }

    @Test
    fun `csv preserves turkish text and includes person iban`() {
        val debt = DebtRecord(
            id = 7,
            name = "Işık",
            amount = 1250.50,
            description = "Öğle yemeği, payı",
            dateStr = "27/09/2026",
            isIncome = true,
            isPaid = false,
            createdAt = 10,
            personKey = "ışık"
        )
        val person = PersonWithIbans(
            person = PersonRecord("ışık", "Işık", 1),
            ibans = listOf(PersonIban(1, "ışık", "TR330006100519786457841326", "Maaş", 2))
        )
        val csv = debtsToCsv(listOf(debt), listOf(person))
        assertTrue(csv.contains("\"Işık\""))
        assertTrue(csv.contains("\"Öğle yemeği, payı\""))
        assertTrue(csv.contains("Maaş: TR33 0006 1005 1978 6457 8413 26"))
        assertTrue(csv.contains("\"Ödenecek\""))
        assertTrue(csv.startsWith("\"Kişi\",\"Tutar\",\"Tarih\",\"Yön\""))
    }
}
