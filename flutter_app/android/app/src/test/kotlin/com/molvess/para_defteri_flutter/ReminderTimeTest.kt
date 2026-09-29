package com.molvess.para_defteri_flutter

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ReminderTimeTest {
    private val zone = TimeZone.getTimeZone("Europe/Istanbul")
    private fun at(s: String, z: TimeZone = zone) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT).apply { timeZone = z }.parse(s)!!.time
    @Test fun monthlyFifteenth() {
        assertEquals(at("15/10/2026 09:30"), ReminderTime.next("15/09/2026", 9,30,"monthly",1,null,at("29/09/2026 12:00"),zone))
    }
    @Test fun monthEndDoesNotDriftAndHandlesLeapYear() {
        assertEquals(at("28/02/2027 10:00"), ReminderTime.next("31/01/2027",10,0,"monthly",1,null,at("31/01/2027 10:00"),zone))
        assertEquals(at("31/03/2027 10:00"), ReminderTime.next("31/01/2027",10,0,"monthly",1,null,at("28/02/2027 10:00"),zone))
        assertEquals(at("29/02/2028 10:00"), ReminderTime.next("31/01/2028",10,0,"monthly",1,null,at("31/01/2028 10:00"),zone))
    }
    @Test fun onceAndInclusiveEndDate() {
        assertEquals(at("15/10/2026 09:30"), ReminderTime.next("15/10/2026",9,30,"once",1,"15/10/2026",at("14/10/2026 10:00"),zone))
        assertNull(ReminderTime.next("15/10/2026",9,30,"once",1,null,at("15/10/2026 09:30"),zone))
        assertNull(ReminderTime.next("15/09/2026",9,30,"monthly",1,"14/10/2026",at("29/09/2026 09:30"),zone))
    }
    @Test fun weeklyIntervalAndMissedOccurrencesAreSkipped() {
        assertEquals(at("13/10/2026 08:00"), ReminderTime.next("29/09/2026",8,0,"weekly",2,null,at("30/09/2026 10:00"),zone))
        assertEquals(at("04/10/2026 08:00"), ReminderTime.next("29/09/2026",8,0,"daily",1,null,at("03/10/2026 09:00"),zone))
    }
    @Test fun dailyKeepsLocalHourAcrossDst() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        assertEquals(at("29/03/2026 09:00",berlin), ReminderTime.next("28/03/2026",9,0,"daily",1,null,at("28/03/2026 09:00",berlin),berlin))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidInterval() {
        ReminderTime.next("29/09/2026",9,0,"daily",0,null,0,zone)
    }
}
