package com.example

import com.example.data.DebtRecord
import com.example.data.PersonIban
import com.example.data.PersonRecord
import com.example.data.PersonWithIbans
import com.example.data.capitalizeFirstTurkish
import com.example.data.debtsToCsv
import com.example.data.isValidIban
import com.example.data.normalizeIban
import com.example.data.parseKeepDebtLine
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

    @Test
    fun `keep format parses name amount description date and direction`() {
        val mustafa = parseKeepDebtLine("Mustafa - 200 TL - yemeks 05/07/2026 +")!!
        val rifat = parseKeepDebtLine("Rifat - 1150 TL - Telefon 09/03/2026 -")!!
        val yasin = parseKeepDebtLine("Yasin - 40 TL - içecek 20/07/2026 +")!!

        assertEquals("Mustafa", mustafa.name)
        assertEquals(200.0, mustafa.amount, 0.0)
        assertEquals("yemeks", mustafa.description)
        assertEquals("05/07/2026", mustafa.dateStr)
        assertTrue(mustafa.isIncome)
        assertFalse(mustafa.isPaid)

        assertEquals(1150.0, rifat.amount, 0.0)
        assertFalse(rifat.isIncome)
        assertEquals("Telefon", rifat.description)
        assertEquals("içecek", yasin.description)
        assertTrue(yasin.isIncome)
    }

    @Test
    fun `keep format rejects invalid date amount and missing direction`() {
        assertEquals(null, parseKeepDebtLine("Mustafa - 0 TL - Yemek 05/07/2026 +"))
        assertEquals(null, parseKeepDebtLine("Mustafa - 200 TL - Yemek 40/07/2026 +"))
        assertEquals(null, parseKeepDebtLine("Mustafa - 200 TL - Yemek 05/07/2026"))
    }
}
