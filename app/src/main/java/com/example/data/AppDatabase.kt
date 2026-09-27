package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DebtRecord::class, BrotherTransaction::class, PersonRecord::class, PersonIban::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "para_defteri_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }

        /** v2 introduced profile images. Keeping this explicit prevents old installs being reset. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debt_records ADD COLUMN avatarUri TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debt_records ADD COLUMN personKey TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS people (" +
                        "personKey TEXT NOT NULL, displayName TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(personKey))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS person_ibans (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, personKey TEXT NOT NULL, " +
                        "iban TEXT NOT NULL, label TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(personKey) REFERENCES people(personKey) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_person_ibans_personKey ON person_ibans(personKey)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_person_ibans_personKey_iban ON person_ibans(personKey, iban)")
            }
        }
    }
}
