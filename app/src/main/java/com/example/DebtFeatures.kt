package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import java.text.SimpleDateFormat
import java.util.*

private enum class DirectionFilter { ALL, RECEIVABLE, PAYABLE }
private enum class PaymentFilter { ALL, PENDING, PAID }

private data class PersonPendingSummary(
    val key: String,
    val name: String,
    val receivable: Double,
    val payable: Double
)

@Composable
fun DebtLedgerV2(
    debts: List<DebtRecord>,
    people: List<PersonWithIbans>,
    isLoading: Boolean,
    errorMessage: String?,
    onTogglePaid: (DebtRecord) -> Unit,
    onDelete: (DebtRecord) -> Unit,
    onAdd: () -> Unit,
    onEdit: (DebtRecord) -> Unit,
    onAvatar: (DebtRecord) -> Unit,
    onManageIban: (String) -> Unit,
    onCopyIban: (PersonIban) -> Unit,
    onExport: () -> Unit
) {
    var direction by remember { mutableStateOf(DirectionFilter.ALL) }
    var payment by remember { mutableStateOf(PaymentFilter.ALL) }
    val listState = rememberLazyListState()
    val personMap = remember(people) { people.associateBy { it.person.personKey } }
    val filtered = remember(debts, direction, payment) {
        debts.asSequence()
            .filter { debt ->
                when (direction) {
                    DirectionFilter.ALL -> true
                    DirectionFilter.RECEIVABLE -> debt.isIncome
                    DirectionFilter.PAYABLE -> !debt.isIncome
                }
            }
            .filter { debt ->
                when (payment) {
                    PaymentFilter.ALL -> true
                    PaymentFilter.PENDING -> !debt.isPaid
                    PaymentFilter.PAID -> debt.isPaid
                }
            }
            .sortedWith(compareByDescending<DebtRecord> { it.createdAt }.thenByDescending { it.id })
            .toList()
    }
    val pendingReceivable = filtered.filter { it.isIncome && !it.isPaid }.sumOf { it.amount }
    val pendingPayable = filtered.filter { !it.isIncome && !it.isPaid }.sumOf { it.amount }
    val paidTotal = filtered.filter { it.isPaid }.sumOf { it.amount }
    val personSummaries = remember(filtered) {
        filtered
            .asSequence()
            .filterNot(DebtRecord::isPaid)
            .groupBy { it.personKey ?: normalizePersonKey(it.name) }
            .map { (key, records) ->
                PersonPendingSummary(
                    key = key,
                    name = records.first().name,
                    receivable = records.filter(DebtRecord::isIncome).sumOf(DebtRecord::amount),
                    payable = records.filterNot(DebtRecord::isIncome).sumOf(DebtRecord::amount)
                )
            }
            .sortedBy { it.name.lowercase(Locale.forLanguageTag("tr-TR")) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            state = listState,
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Borçlar", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                        Text("En son eklenen kayıt en üstte", fontSize = 11.sp, color = TextSecondary)
                    }
                    OutlinedButton(onClick = onExport, enabled = debts.isNotEmpty()) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("CSV", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SummaryMetric("BEKLEYEN ALACAK", pendingReceivable, EmeraldPrimary, Modifier.weight(1f))
                        SummaryMetric("BEKLEYEN VERECEK", pendingPayable, ExpenseRed, Modifier.weight(1f))
                        SummaryMetric("ÖDENEN", paidTotal, TextSecondary, Modifier.weight(1f))
                    }
                    Text(
                        "Tutarlar yalnızca aşağıdaki filtrelenmiş kayıtları kapsar.",
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            item {
                FilterRow(
                    labels = listOf("Tümü", "Alacaklar", "Verecekler"),
                    selected = direction.ordinal,
                    onSelected = { direction = DirectionFilter.entries[it] }
                )
                Spacer(Modifier.height(8.dp))
                FilterRow(
                    labels = listOf("Tüm durumlar", "Ödenecek", "Ödendi"),
                    selected = payment.ordinal,
                    onSelected = { payment = PaymentFilter.entries[it] }
                )
            }

            if (personSummaries.isNotEmpty()) {
                item {
                    Text("KİŞİ BAZINDA BEKLEYENLER", fontSize = 11.sp, fontWeight = FontWeight.Black, color = TextSecondary)
                }
                items(
                    items = personSummaries,
                    key = { "person-${it.key}" },
                    contentType = { "person_summary" }
                ) { summary ->
                    PersonPendingCard(summary)
                }
            }

            if (isLoading) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Kayıtlar yükleniyor…", color = TextSecondary)
                    }
                }
            } else if (errorMessage != null) {
                item {
                    Text(errorMessage, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                }
            } else if (filtered.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(if (debts.isEmpty()) "Henüz borç kaydı yok." else "Bu filtrelere uyan kayıt yok.", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Yeni kayıt ekleyebilir veya filtreleri değiştirebilirsiniz.", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            } else {
                items(
                    items = filtered,
                    key = { "debt-${it.id}" },
                    contentType = { "debt" }
                ) { debt ->
                    val person = personMap[debt.personKey ?: normalizePersonKey(debt.name)]
                    DebtFeatureCard(debt, person?.ibans.orEmpty(), onTogglePaid, onDelete, onEdit, onAvatar, onManageIban, onCopyIban)
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("floating_add_debt_button"),
            containerColor = EmeraldPrimary,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("BORÇ EKLE", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PersonPendingCard(summary: PersonPendingSummary) {
    Surface(
        color = DarkSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(summary.name, fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PendingPersonAmount(
                    label = "BEKLEYEN ALACAK",
                    amount = summary.receivable,
                    color = EmeraldPrimary,
                    modifier = Modifier.weight(1f)
                )
                PendingPersonAmount(
                    label = "BEKLEYEN VERECEK",
                    amount = summary.payable,
                    color = ExpenseRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PendingPersonAmount(label: String, amount: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = color)
        Text(formatCurrency(amount, true), fontSize = 14.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = color)
    }
}

@Composable
private fun SummaryMetric(label: String, amount: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Black, color = TextSecondary, textAlign = TextAlign.Center)
        Text(formatCurrency(amount, true), fontSize = 15.sp, fontWeight = FontWeight.Black, color = color, maxLines = 1)
    }
}

@Composable
private fun FilterRow(labels: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            Surface(
                modifier = Modifier.weight(1f).clickable { onSelected(index) },
                shape = RoundedCornerShape(20.dp),
                color = if (active) BubbleBg else DarkSurface,
                border = BorderStroke(1.dp, if (active) EmeraldPrimary else CardBorder)
            ) {
                Text(label, Modifier.padding(vertical = 9.dp), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (active) EmeraldPrimary else TextSecondary)
            }
        }
    }
}

@Composable
private fun DebtFeatureCard(
    debt: DebtRecord,
    ibans: List<PersonIban>,
    onTogglePaid: (DebtRecord) -> Unit,
    onDelete: (DebtRecord) -> Unit,
    onEdit: (DebtRecord) -> Unit,
    onAvatar: (DebtRecord) -> Unit,
    onManageIban: (String) -> Unit,
    onCopyIban: (PersonIban) -> Unit
) {
    val directionColor = if (debt.isIncome) EmeraldPrimary else ExpenseRed
    val formattedAmount = remember(debt.amount, debt.isIncome) {
        (if (debt.isIncome) "+ " else "- ") + formatCurrency(debt.amount, true)
    }
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = if (debt.isPaid) .7f else 1f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarRender(debt.name, debt.avatarUri, debt.isIncome, onClick = { onAvatar(debt) })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(debt.name, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(debt.description, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${if (debt.isIncome) "Alacak" else "Verecek"} • ${if (debt.isPaid) "Ödendi" else "Ödenecek"} • ${debt.dateStr}", fontSize = 10.sp, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formattedAmount, fontWeight = FontWeight.Black, color = directionColor)
                    IconButton(onClick = { onEdit(debt) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Borcu düzenle", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (ibans.isNotEmpty()) {
                Text(maskIban(ibans.first().iban) + if (ibans.size > 1) "  +${ibans.size - 1}" else "", fontSize = 11.sp, color = TextSecondary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onTogglePaid(debt) }) { Text(if (debt.isPaid) "ÖDENECEK YAP" else "ÖDENDİ YAP", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = { if (ibans.size == 1) onCopyIban(ibans.first()) else onManageIban(debt.name) }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (ibans.isEmpty()) "IBAN EKLE" else "IBAN'I KOPYALA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDelete(debt) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Borcu sil", tint = ExpenseRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtFormDialog(
    debts: List<DebtRecord>,
    existing: DebtRecord?,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, String, Boolean, Boolean) -> Unit,
    onOpenKeepImport: () -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(TextFieldValue(existing?.name.orEmpty())) }
    var amount by remember(existing?.id) { mutableStateOf(existing?.amount?.toString()?.replace('.', ',').orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var date by remember(existing?.id) { mutableStateOf(existing?.dateStr ?: SimpleDateFormat("dd/MM/yyyy", Locale("tr", "TR")).format(Date())) }
    var isIncome by remember(existing?.id) { mutableStateOf(existing?.isIncome ?: true) }
    var isPaid by remember(existing?.id) { mutableStateOf(existing?.isPaid ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateInteractionSource = remember { MutableInteractionSource() }
    val datePressed by dateInteractionSource.collectIsPressedAsState()
    LaunchedEffect(datePressed) { if (datePressed) showDatePicker = true }

    if (showDatePicker) {
        val initialMillis = remember(date) {
            runCatching { SimpleDateFormat("dd/MM/yyyy", Locale("tr", "TR")).parse(date)?.time }.getOrNull()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale("tr", "TR")).apply { timeZone = TimeZone.getTimeZone("UTC") }
                        date = formatter.format(Date(millis))
                    }
                    showDatePicker = false
                }) { Text("SEÇ") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("İPTAL") } }
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().heightIn(max = 720.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (existing == null) "Yeni Borç Kaydı" else "Borcu Düzenle", fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                if (existing == null) {
                    OutlinedButton(onClick = onOpenKeepImport, modifier = Modifier.fillMaxWidth()) { Text("GOOGLE KEEP'TEN İÇE AKTAR", fontSize = 11.sp) }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { newValue ->
                        val normalized = capitalizeFirstTurkish(newValue.text)
                        name = newValue.copy(
                            text = normalized,
                            selection = TextRange(newValue.selection.start.coerceAtMost(normalized.length), newValue.selection.end.coerceAtMost(normalized.length))
                        )
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_debt_name_input"),
                    label = { Text("Kim?") },
                    singleLine = true,
                    isError = error != null && name.text.isBlank()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == ',' || c == '.' }; error = null },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_debt_amount_input"),
                    label = { Text("Tutar (₺)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().testTag("dialog_debt_date_input"),
                    label = { Text("Tarih") },
                    readOnly = true,
                    interactionSource = dateInteractionSource,
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.DateRange, contentDescription = "Takvimi aç") } },
                    singleLine = true
                )
                OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Açıklama (isteğe bağlı)") })
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = isIncome, onClick = { isIncome = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Alacak") }
                    SegmentedButton(selected = !isIncome, onClick = { isIncome = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Verecek") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Ödeme durumu", fontWeight = FontWeight.Bold, color = TextPrimary); Text(if (isPaid) "Ödendi" else "Ödenecek", fontSize = 12.sp, color = TextSecondary) }
                    Switch(checked = isPaid, onCheckedChange = { isPaid = it })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("İPTAL") }
                    Button(onClick = {
                        val parsed = amount.replace(',', '.').toDoubleOrNull()
                        val validDate = runCatching {
                            SimpleDateFormat("dd/MM/yyyy", Locale("tr", "TR")).apply { isLenient = false }.parse(date)
                        }.getOrNull() != null
                        error = when {
                            name.text.isBlank() -> "Kişi adı zorunludur."
                            parsed == null || parsed <= 0.0 -> "Geçerli ve sıfırdan büyük bir tutar girin."
                            !validDate -> "Geçerli bir tarih seçin."
                            else -> null
                        }
                        if (error == null) onConfirm(name.text.trim(), parsed!!, description.trim(), date, isIncome, isPaid)
                    }, modifier = Modifier.weight(1.4f).testTag("dialog_confirm_add_debt_button")) { Text(if (existing == null) "KAYDET" else "GÜNCELLE") }
                }
            }
        }
    }
}

@Composable
fun IbanManagerDialog(
    personName: String,
    ibans: List<PersonIban>,
    onDismiss: () -> Unit,
    onSave: (PersonIban?, String, String) -> Unit,
    onDelete: (PersonIban) -> Unit,
    onCopy: (PersonIban) -> Unit
) {
    var editing by remember { mutableStateOf<PersonIban?>(null) }
    var rawIban by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var revealedIds by remember { mutableStateOf(setOf<Int>()) }

    fun clearForm() { editing = null; rawIban = ""; label = ""; error = null }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().heightIn(max = 680.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$personName • IBAN'lar", fontSize = 18.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                Text("IBAN listede maskeli gösterilir; kopyalama tam değeri panoya alır.", fontSize = 11.sp, color = TextSecondary)
                if (ibans.isEmpty()) Text("Bu kişi için kayıtlı IBAN yok.", color = TextSecondary)
                ibans.sortedBy { it.createdAt }.forEach { item ->
                    Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder), color = DarkSurface) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(item.label.ifBlank { "IBAN" }, fontWeight = FontWeight.Bold, color = TextPrimary)
                            SelectionContainer {
                                Text(if (item.id in revealedIds) formatIban(item.iban) else maskIban(item.iban), fontSize = 12.sp, color = TextSecondary)
                            }
                            Row {
                                TextButton(onClick = { onCopy(item) }) { Text("KOPYALA") }
                                TextButton(onClick = {
                                    revealedIds = if (item.id in revealedIds) revealedIds - item.id else revealedIds + item.id
                                }) { Text(if (item.id in revealedIds) "GİZLE" else "GÖSTER") }
                            }
                            Row {
                                TextButton(onClick = { editing = item; rawIban = formatIban(item.iban); label = item.label; error = null }) { Text("DÜZENLE") }
                                TextButton(onClick = { onDelete(item); if (editing?.id == item.id) clearForm() }) { Text("SİL", color = ExpenseRed) }
                            }
                        }
                    }
                }
                HorizontalDivider(color = CardBorder)
                Text(if (editing == null) "Yeni IBAN" else "IBAN'ı düzenle", fontWeight = FontWeight.Bold, color = TextPrimary)
                OutlinedTextField(
                    value = rawIban,
                    onValueChange = { rawIban = formatIban(it); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("IBAN") },
                    placeholder = { Text("TR00 0000 0000 0000 0000 0000 00") },
                    singleLine = true,
                    isError = error != null
                )
                OutlinedTextField(value = label, onValueChange = { label = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Banka / kısa açıklama") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editing != null) OutlinedButton(onClick = ::clearForm, modifier = Modifier.weight(1f)) { Text("VAZGEÇ") }
                    Button(onClick = {
                        val normalized = normalizeIban(rawIban)
                        if (!isValidIban(normalized)) {
                            error = "Geçerli bir IBAN girin. Harf, uzunluk ve kontrol basamakları hatalı."
                        } else {
                            onSave(editing, normalized, label)
                            clearForm()
                        }
                    }, modifier = Modifier.weight(1f)) { Text(if (editing == null) "EKLE" else "GÜNCELLE") }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("KAPAT") }
            }
        }
    }
}
