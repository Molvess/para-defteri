package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BrotherTransaction
import com.example.data.DebtRecord
import com.example.data.FinanceRepository
import com.example.data.PersonIban
import com.example.data.PersonWithIbans
import com.example.data.normalizePersonKey
import com.example.data.parseKeepDebtLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    val debts: StateFlow<List<DebtRecord>>
    val brotherTransactions: StateFlow<List<BrotherTransaction>>
    val people: StateFlow<List<PersonWithIbans>>

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _dataError = MutableStateFlow<String?>(null)
    val dataError: StateFlow<String?> = _dataError.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
    
    private val _brotherBudgetLimit = MutableStateFlow(2000.0)
    val brotherBudgetLimit: StateFlow<Double> = _brotherBudgetLimit.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FinanceRepository(database.financeDao())
        
        debts = repository.allDebts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        brotherTransactions = repository.allBrotherTransactions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        people = repository.allPeopleWithIbans.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Load abim's budget limit
        val savedLimit = sharedPrefs.getFloat("brother_limit", 2000.0f).toDouble()
        _brotherBudgetLimit.value = savedLimit

        // Popüle et (sadece ilk açılışta ve veritabanı boşsa)
        viewModelScope.launch {
            // we delay slightly to ensure db loading is finished
            kotlinx.coroutines.delay(200)
            val alreadyPopulated = sharedPrefs.getBoolean("db_init_populated", false)
            if (!alreadyPopulated) {
                if (debts.value.isEmpty()) {
                    repository.prePopulateIfEmpty(0)
                }
                sharedPrefs.edit().putBoolean("db_init_populated", true).apply()
            }
        }

        // Non-destructive v3 reconciliation: old debt rows keep their original name and id.
        viewModelScope.launch {
            runCatching {
                repository.allDebts.first().forEach { debt ->
                    val key = normalizePersonKey(debt.name)
                    repository.ensurePerson(key, debt.name.trim())
                    if (debt.personKey != key) repository.updateDebtPersonKey(debt.id, key)
                }
            }.onFailure {
                _dataError.value = "Kayıtlar okunurken bir sorun oluştu."
            }
            _isLoading.value = false
        }
    }

    fun updateBrotherBudgetLimit(newLimit: Double) {
        viewModelScope.launch {
            _brotherBudgetLimit.value = newLimit
            sharedPrefs.edit().putFloat("brother_limit", newLimit.toFloat()).apply()
        }
    }

    fun addDebt(name: String, amount: Double, description: String, dateStr: String, isIncome: Boolean, isPaid: Boolean, avatarUri: String? = null) {
        viewModelScope.launch {
            val trimmedName = name.trim().ifEmpty { "İsimsiz" }
            val lowercaseName = trimmedName.lowercase(Locale("tr", "TR"))
            
            // Auto look up the existing avatar for the same name if not specifically passed
            val resolvedAvatarUri = if (!avatarUri.isNullOrEmpty()) {
                avatarUri
            } else {
                debts.value.firstOrNull { 
                    it.name.trim().lowercase(Locale("tr", "TR")) == lowercaseName && !it.avatarUri.isNullOrEmpty() 
                }?.avatarUri
            }

            val record = DebtRecord(
                name = trimmedName,
                amount = amount,
                description = description.trim().ifEmpty { "Açıklama Yok" },
                dateStr = dateStr.trim().ifEmpty { getCurrentFormattedDate() },
                isIncome = isIncome,
                isPaid = isPaid,
                avatarUri = resolvedAvatarUri,
                personKey = normalizePersonKey(trimmedName)
            )
            repository.ensurePerson(record.personKey!!, trimmedName)
            repository.insertDebt(record)
        }
    }

    fun updateDebt(record: DebtRecord) {
        viewModelScope.launch {
            val trimmedName = record.name.trim()
            val key = normalizePersonKey(trimmedName)
            repository.ensurePerson(key, trimmedName)
            repository.updateDebt(record.copy(name = trimmedName, personKey = key))
        }
    }

    fun saveIban(
        personName: String,
        existing: PersonIban?,
        normalizedIban: String,
        label: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val key = normalizePersonKey(personName)
            val result = runCatching {
                repository.ensurePerson(key, personName.trim())
                val value = PersonIban(
                    id = existing?.id ?: 0,
                    personKey = key,
                    iban = normalizedIban,
                    label = label.trim(),
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
                if (existing == null) repository.insertIban(value) else repository.updateIban(value)
            }
            onResult(result.isSuccess)
        }
    }

    fun deleteIban(id: Int) {
        viewModelScope.launch { repository.deleteIban(id) }
    }

    fun deleteDebt(id: Int) {
        viewModelScope.launch {
            repository.deleteDebtById(id)
        }
    }

    fun insertDebtRecord(record: DebtRecord) {
        viewModelScope.launch {
            repository.insertDebt(record)
        }
    }

    fun toggleDebtPaid(id: Int, isPaid: Boolean) {
        viewModelScope.launch {
            repository.updateDebtPaidStatus(id, isPaid)
        }
    }

    fun updateDebtAvatar(id: Int, avatarUri: String?) {
        viewModelScope.launch {
            val targetRecord = debts.value.find { it.id == id }
            if (targetRecord != null) {
                val targetName = targetRecord.name.trim().lowercase(Locale("tr", "TR"))
                debts.value.filter { 
                    it.name.trim().lowercase(Locale("tr", "TR")) == targetName 
                }.forEach { record ->
                    repository.updateDebtAvatar(record.id, avatarUri)
                }
            } else {
                repository.updateDebtAvatar(id, avatarUri)
            }
        }
    }

    fun addBrotherTransaction(amount: Double, description: String, dateStr: String) {
        viewModelScope.launch {
            val transaction = BrotherTransaction(
                amount = amount,
                description = description.trim().ifEmpty { "Harçlık" },
                dateStr = dateStr.trim().ifEmpty { getCurrentFormattedDate() }
            )
            repository.insertBrotherTransaction(transaction)
        }
    }

    fun deleteBrotherTransaction(id: Int) {
        viewModelScope.launch {
            repository.deleteBrotherTransactionById(id)
        }
    }

    fun insertBrotherTransactionRecord(transaction: BrotherTransaction) {
        viewModelScope.launch {
            repository.insertBrotherTransaction(transaction)
        }
    }

    // --- Google Keep "Alacaklar - Verecekler" Parser ---
    fun importFromKeep(rawText: String): Int {
        if (rawText.isBlank()) return 0
        val records = rawText.lineSequence()
            .map(String::trim)
            .filter { line ->
                line.isNotEmpty() &&
                    !line.equals("Alacaklar - Verecekler", ignoreCase = true) &&
                    !line.equals("Alacaklar-Verecekler", ignoreCase = true)
            }
            .mapNotNull(::parseKeepDebtLine)
            .toList()
        if (records.isEmpty()) return 0

        viewModelScope.launch {
            records.forEach { record ->
                val trimmedName = record.name.trim()
                val lowercaseName = trimmedName.lowercase(Locale("tr", "TR"))
                val resolvedAvatarUri = debts.value.firstOrNull {
                    it.name.trim().lowercase(Locale("tr", "TR")) == lowercaseName && !it.avatarUri.isNullOrEmpty()
                }?.avatarUri
                val updatedRecord = record.copy(
                    avatarUri = resolvedAvatarUri,
                    personKey = normalizePersonKey(trimmedName)
                )
                repository.ensurePerson(updatedRecord.personKey!!, trimmedName)
                repository.insertDebt(updatedRecord)
            }
        }
        return records.size
    }

    fun getCurrentFormattedDate(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }
}
