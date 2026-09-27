package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationTest {
    @Test
    fun `migration 2 to 3 keeps existing debt and adds people tables`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-v2-v3-test"
        context.deleteDatabase(dbName)
        val file = context.getDatabasePath(dbName).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            sqlite.execSQL(
                "CREATE TABLE debt_records (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                    "amount REAL NOT NULL, description TEXT NOT NULL, dateStr TEXT NOT NULL, " +
                    "isIncome INTEGER NOT NULL, isPaid INTEGER NOT NULL, createdAt INTEGER NOT NULL, avatarUri TEXT)"
            )
            sqlite.execSQL(
                "CREATE TABLE brother_transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amount REAL NOT NULL, " +
                    "description TEXT NOT NULL, dateStr TEXT NOT NULL, createdAt INTEGER NOT NULL)"
            )
            sqlite.execSQL(
                "INSERT INTO debt_records " +
                    "(name, amount, description, dateStr, isIncome, isPaid, createdAt, avatarUri) " +
                    "VALUES ('Işık', 250.5, 'Eski kayıt', '27/09/2026', 1, 0, 1234, NULL)"
            )
            sqlite.version = 2
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()
        try {
            val debts = room.financeDao().getAllDebtsFlow().first()
            assertEquals(1, debts.size)
            assertEquals("Işık", debts.single().name)
            assertEquals(250.5, debts.single().amount, 0.0)
            assertFalse(debts.single().isPaid)
        } finally {
            room.close()
            context.deleteDatabase(dbName)
        }
    }
}
