package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation

@Entity(tableName = "debt_records")
data class DebtRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val amount: Double,
    val description: String,
    val dateStr: String,
    val isIncome: Boolean, // true = Gelir (+) veya Alacak, false = Gider (-) veya Verecek
    val isPaid: Boolean,   // true = Ödendi (✅), false = Ödenmedi (❌)
    val createdAt: Long = System.currentTimeMillis(),
    val avatarUri: String? = null,
    /** Normalized, stable link to a person. Nullable only for records created before v3. */
    val personKey: String? = null
)

@Entity(tableName = "people")
data class PersonRecord(
    @PrimaryKey val personKey: String,
    val displayName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "person_ibans",
    foreignKeys = [
        ForeignKey(
            entity = PersonRecord::class,
            parentColumns = ["personKey"],
            childColumns = ["personKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("personKey"),
        Index(value = ["personKey", "iban"], unique = true)
    ]
)
data class PersonIban(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val personKey: String,
    val iban: String,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class PersonWithIbans(
    @Embedded val person: PersonRecord,
    @Relation(parentColumn = "personKey", entityColumn = "personKey")
    val ibans: List<PersonIban>
)

@Entity(tableName = "brother_transactions")
data class BrotherTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val description: String,
    val dateStr: String,
    val createdAt: Long = System.currentTimeMillis()
)
