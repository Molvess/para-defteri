package com.example.data

import kotlinx.coroutines.flow.Flow

class FinanceRepository(private val financeDao: FinanceDao) {
    
    val allDebts: Flow<List<DebtRecord>> = financeDao.getAllDebtsFlow()
    val allBrotherTransactions: Flow<List<BrotherTransaction>> = financeDao.getAllBrotherTransactionsFlow()
    val allPeopleWithIbans: Flow<List<PersonWithIbans>> = financeDao.getPeopleWithIbansFlow()

    suspend fun insertDebt(record: DebtRecord) {
        financeDao.insertDebt(record)
    }

    suspend fun updateDebt(record: DebtRecord) = financeDao.updateDebt(record)

    suspend fun ensurePerson(personKey: String, displayName: String) {
        financeDao.insertPerson(PersonRecord(personKey = personKey, displayName = displayName))
        financeDao.updatePersonName(personKey, displayName)
    }

    suspend fun updateDebtPersonKey(id: Int, personKey: String) =
        financeDao.updateDebtPersonKey(id, personKey)

    suspend fun insertIban(iban: PersonIban) = financeDao.insertIban(iban)
    suspend fun updateIban(iban: PersonIban) = financeDao.updateIban(iban)
    suspend fun deleteIban(id: Int) = financeDao.deleteIban(id)

    suspend fun deleteDebtById(id: Int) {
        financeDao.deleteDebtById(id)
    }

    suspend fun updateDebtPaidStatus(id: Int, isPaid: Boolean) {
        financeDao.updateDebtPaidStatus(id, isPaid)
    }

    suspend fun updateDebtAvatar(id: Int, avatarUri: String?) {
        financeDao.updateDebtAvatar(id, avatarUri)
    }

    suspend fun insertBrotherTransaction(transaction: BrotherTransaction) {
        financeDao.insertBrotherTransaction(transaction)
    }

    suspend fun deleteBrotherTransactionById(id: Int) {
        financeDao.deleteBrotherTransactionById(id)
    }

    suspend fun prePopulateIfEmpty(debtCount: Int) {
        if (debtCount == 0) {
            val sampleDebts = listOf(
                DebtRecord(
                    name = "Mustafa",
                    amount = 110.0,
                    description = "Sigara",
                    dateStr = "06/03/2026",
                    isIncome = true, // Gelir (+) veya Alacak
                    isPaid = true
                ),
                DebtRecord(
                    name = "Ahmet",
                    amount = 450.0,
                    description = "Süpermarket Alışverişi",
                    dateStr = "05/03/2026",
                    isIncome = false, // Gider (-) veya Verecek
                    isPaid = false
                ),
                DebtRecord(
                    name = "Leyla Abla",
                    amount = 2500.0,
                    description = "Kira Katkısı",
                    dateStr = "01/03/2026",
                    isIncome = true,
                    isPaid = false
                ),
                DebtRecord(
                    name = "Mehmet Can",
                    amount = 350.0,
                    description = "Halı Saha Ücreti",
                    dateStr = "28/02/2026",
                    isIncome = false,
                    isPaid = true
                )
            )
            for (debt in sampleDebts) {
                val key = normalizePersonKey(debt.name)
                ensurePerson(key, debt.name)
                financeDao.insertDebt(debt.copy(personKey = key))
            }
        }
    }
}
