package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    // --- Debt Records Queries ---
    @Query("SELECT * FROM debt_records ORDER BY createdAt DESC")
    fun getAllDebtsFlow(): Flow<List<DebtRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(record: DebtRecord)

    @Update
    suspend fun updateDebt(record: DebtRecord)

    @Query("UPDATE debt_records SET personKey = :personKey WHERE id = :id")
    suspend fun updateDebtPersonKey(id: Int, personKey: String)

    @Query("DELETE FROM debt_records WHERE id = :id")
    suspend fun deleteDebtById(id: Int)

    @Query("UPDATE debt_records SET isPaid = :isPaid WHERE id = :id")
    suspend fun updateDebtPaidStatus(id: Int, isPaid: Boolean)

    @Query("UPDATE debt_records SET avatarUri = :avatarUri WHERE id = :id")
    suspend fun updateDebtAvatar(id: Int, avatarUri: String?)

    // --- People & IBAN Queries ---
    @Transaction
    @Query("SELECT * FROM people ORDER BY displayName COLLATE NOCASE")
    fun getPeopleWithIbansFlow(): Flow<List<PersonWithIbans>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPerson(person: PersonRecord)

    @Query("UPDATE people SET displayName = :displayName WHERE personKey = :personKey")
    suspend fun updatePersonName(personKey: String, displayName: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIban(iban: PersonIban)

    @Update
    suspend fun updateIban(iban: PersonIban)

    @Query("DELETE FROM person_ibans WHERE id = :id")
    suspend fun deleteIban(id: Int)

    // --- Brother Transactions Queries ---
    @Query("SELECT * FROM brother_transactions ORDER BY createdAt DESC")
    fun getAllBrotherTransactionsFlow(): Flow<List<BrotherTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBrotherTransaction(transaction: BrotherTransaction)

    @Query("DELETE FROM brother_transactions WHERE id = :id")
    suspend fun deleteBrotherTransactionById(id: Int)
}
