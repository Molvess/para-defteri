package com.example

import android.os.Bundle
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.BrotherTransaction
import com.example.data.DebtRecord
import com.example.data.PersonIban
import com.example.data.debtsToCsv
import com.example.data.normalizePersonKey
import com.example.ui.FinanceViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import android.net.Uri
import java.io.File
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

// --- HELPER FUNCTION FOR TURKISH CURRENCY FORMATTING (Separated by dot, e.g. 3.859 ₺) ---
private val turkishFormatSymbols = DecimalFormatSymbols(Locale("tr", "TR")).apply {
    groupingSeparator = '.'
    decimalSeparator = ','
}

private val currencyFormatterNoDecimals = java.text.DecimalFormat("#,##0", turkishFormatSymbols)
private val currencyFormatterWithDecimals = java.text.DecimalFormat("#,##0.00", turkishFormatSymbols)

fun formatCurrency(amount: Double, includeDecimals: Boolean = false): String {
    val symbol = "₺"
    val df = if (includeDecimals) currencyFormatterWithDecimals else currencyFormatterNoDecimals
    return df.format(amount) + " " + symbol
}

// --- HELPER FUNCTION TO COPY PICKED PHOTO TO APP PRIVATE STORAGE TO PRESERVE PERMISSION ---
fun saveImageLocally(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bytes = inputStream.readBytes()
        val fileName = "profile_${System.currentTimeMillis()}.png"
        val file = File(context.filesDir, fileName)
        file.writeBytes(bytes)
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// --- SAFE CENTER-CROP AND RESIZE UTILITY FOR PERFECT USER PROFILE PHOTOS (Saves OOM failures) ---
fun saveCroppedImageLocally(
    context: Context, 
    uri: Uri
): String? {
    return try {
        // Read dimensions first to avoid OOM
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri).use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream, null, options)
        }
        
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) return null
        
        // Target safe moderate resolution (512x512) for high performance without high heap usage
        var inSampleSize = 1
        val targetWidth = 512
        val targetHeight = 512
        if (width > targetWidth || height > targetHeight) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while ((halfWidth / inSampleSize) >= targetWidth && (halfHeight / inSampleSize) >= targetHeight) {
                inSampleSize *= 2
            }
        }
        
        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        
        val originalBitmap = context.contentResolver.openInputStream(uri).use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null
        
        // Perfect auto center-crop to a square
        val minDim = Math.min(originalBitmap.width, originalBitmap.height)
        val leftOffset = (originalBitmap.width - minDim) / 2
        val topOffset = (originalBitmap.height - minDim) / 2
        
        val squareBitmap = android.graphics.Bitmap.createBitmap(originalBitmap, leftOffset, topOffset, minDim, minDim)
        val scaled = android.graphics.Bitmap.createScaledBitmap(squareBitmap, 256, 256, true)
        
        val fileName = "profile_cropped_${System.currentTimeMillis()}.png"
        val file = File(context.filesDir, fileName)
        java.io.FileOutputStream(file).use { out ->
            scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, out)
        }
        
        if (originalBitmap != squareBitmap && !originalBitmap.isRecycled) {
            originalBitmap.recycle()
        }
        if (squareBitmap != scaled && !squareBitmap.isRecycled) {
            squareBitmap.recycle()
        }
        if (!scaled.isRecycled) {
            scaled.recycle()
        }
        
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// --- SEMANTIC COMPOSABLE COLOR PROVIDERS FOR HIGH DENSITY THEME ---
val EmeraldPrimary: Color
    @Composable get() = HD_SuccessGreen

val EmeraldSecondary: Color
    @Composable get() = if (isSystemInDarkTheme()) HD_DarkPrimary else HD_LightPrimary

val AmberWarning: Color
    @Composable get() = HD_WarningAmber

val ExpenseRed: Color
    @Composable get() = HD_DangerRed

val TextPrimary: Color
    @Composable get() = if (isSystemInDarkTheme()) HD_DarkTextPrimary else HD_LightTextPrimary

val TextSecondary: Color
    @Composable get() = if (isSystemInDarkTheme()) HD_DarkTextSecondary else HD_LightTextSecondary

val DarkBg: Color
    @Composable get() = MaterialTheme.colorScheme.background

val DarkSurface: Color
    @Composable get() = MaterialTheme.colorScheme.surface

val DarkSurfaceElevated: Color
    @Composable get() = if (isSystemInDarkTheme()) HD_DarkSurfaceElevated else HD_LightSurfaceElevated

val CardBorder: Color
    @Composable get() = MaterialTheme.colorScheme.outline

val BubbleBg: Color
    @Composable get() = if (isSystemInDarkTheme()) HD_DarkSecondary else HD_LightSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    AppHomeScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Data class to represent a monthly item inside the timeline selection
data class MonthYearItem(
    val key: String,         // e.g. "06/2026"
    val name: String,        // e.g. "Haziran"
    val displaySpent: Double,
    val year: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: FinanceViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Dialog state controllers
    var showAddDebtDialog by remember { mutableStateOf(false) }
    var editingDebt by remember { mutableStateOf<DebtRecord?>(null) }
    var ibanPersonName by remember { mutableStateOf<String?>(null) }
    var showKeepImportDialog by remember { mutableStateOf(false) }
    var showEditLimitDialog by remember { mutableStateOf(false) }
    var showAboutAppDialog by remember { mutableStateOf(false) }
    var avatarEditTarget by remember { mutableStateOf<DebtRecord?>(null) }

    // Custom non-intrusive beautiful Toast message overlay (Point 11 success indicator and Undo actions)
    var successNotificationMsg by remember { mutableStateOf<String?>(null) }
    var deletedDebtToUndo by remember { mutableStateOf<com.example.data.DebtRecord?>(null) }
    var deletedTransactionToUndo by remember { mutableStateOf<com.example.data.BrotherTransaction?>(null) }
    
    fun triggerSuccessNotification(msg: String) {
        deletedDebtToUndo = null
        deletedTransactionToUndo = null
        successNotificationMsg = msg
        coroutineScope.launch {
            delay(1500) // Show for exactly 1.5 seconds on screen
            if (successNotificationMsg == msg) {
                successNotificationMsg = null
            }
        }
    }

    fun triggerUndoDebtNotification(record: com.example.data.DebtRecord) {
        deletedDebtToUndo = record
        deletedTransactionToUndo = null
        successNotificationMsg = "Kayıt silindi!"
        coroutineScope.launch {
            delay(2000) // Show for exactly 2 seconds
            if (deletedDebtToUndo == record) {
                deletedDebtToUndo = null
                successNotificationMsg = null
            }
        }
    }

    fun triggerUndoTransactionNotification(transaction: com.example.data.BrotherTransaction) {
        deletedTransactionToUndo = transaction
        deletedDebtToUndo = null
        successNotificationMsg = "Gönderim silindi!"
        coroutineScope.launch {
            delay(2000) // Show for exactly 2 seconds
            if (deletedTransactionToUndo == transaction) {
                deletedTransactionToUndo = null
                successNotificationMsg = null
            }
        }
    }

    // Observing lists and budget
    val debts by viewModel.debts.collectAsState()
    val brotherTransactions by viewModel.brotherTransactions.collectAsState()
    val brotherBudgetLimit by viewModel.brotherBudgetLimit.collectAsState()
    val people by viewModel.people.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val dataError by viewModel.dataError.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    output.write(debtsToCsv(debts, people).toByteArray(Charsets.UTF_8))
                } ?: error("Dosya açılamadı")
            }.onSuccess {
                triggerSuccessNotification("Borçlar CSV olarak dışa aktarıldı!")
            }.onFailure {
                Toast.makeText(context, "Dışa aktarma başarısız: dosya yazılamadı.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun copyIban(iban: PersonIban) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("IBAN", iban.iban))
        }.onSuccess {
            triggerSuccessNotification("IBAN panoya kopyalandı!")
        }.onFailure {
            Toast.makeText(context, "IBAN kopyalanamadı. IBAN yönetiminden elle seçebilirsiniz.", Toast.LENGTH_LONG).show()
            ibanPersonName = people.firstOrNull { person -> person.ibans.any { it.id == iban.id } }?.person?.displayName
        }
    }

    // Layout Root
    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- COHESIVE HEADER ROW (High Density styling) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showAboutAppDialog = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Circular icon frame with fully functional statistics trigger (Point 5)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Uygulama Bilgisi",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Para Defteri",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = (viewModel.getCurrentFormattedDate() + " • Borç Takibi").uppercase(Locale.getDefault()),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Simple decorative label with logo style on top-right
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(30))
                            .background(EmeraldPrimary.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "GÜVENLİ",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = EmeraldPrimary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                HorizontalDivider(color = CardBorder, thickness = 1.dp)
            }

            // Muhasebe verisi ve kodu korunur; bu bölüm kullanıcı arayüzünde devre dışıdır.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                DebtLedgerV2(
                    debts = debts,
                    people = people,
                    isLoading = isLoading,
                    errorMessage = dataError,
                    onTogglePaid = { record ->
                        viewModel.toggleDebtPaid(record.id, !record.isPaid)
                        triggerSuccessNotification("Ödeme durumu güncellendi!")
                    },
                    onDelete = { record ->
                        viewModel.deleteDebt(record.id)
                        triggerUndoDebtNotification(record)
                    },
                    onAdd = { showAddDebtDialog = true },
                    onEdit = { editingDebt = it },
                    onAvatar = { avatarEditTarget = it },
                    onManageIban = { ibanPersonName = it },
                    onCopyIban = ::copyIban,
                    onExport = {
                        if (debts.isEmpty()) {
                            Toast.makeText(context, "Dışa aktarılacak borç kaydı yok.", Toast.LENGTH_SHORT).show()
                        } else {
                            exportLauncher.launch("para-defteri-${System.currentTimeMillis()}.csv")
                        }
                    }
                )
            }
        }

        // --- SUCCESS INDICATOR TOAST MESSAGE OVERLAY CARD (Point 11) ---
        AnimatedVisibility(
            visible = successNotificationMsg != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp, start = 16.dp, end = 16.dp)
        ) {
         Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.5.dp, 
                        (if (deletedDebtToUndo != null || deletedTransactionToUndo != null) ExpenseRed.copy(alpha = 0.5f) else EmeraldPrimary.copy(alpha = 0.5f)), 
                        RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (deletedDebtToUndo != null || deletedTransactionToUndo != null) ExpenseRed else EmeraldPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (deletedDebtToUndo != null || deletedTransactionToUndo != null) Icons.Default.Delete else Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = successNotificationMsg ?: "",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    if (deletedDebtToUndo != null) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    deletedDebtToUndo?.let { record ->
                                        viewModel.insertDebtRecord(record)
                                    }
                                    deletedDebtToUndo = null
                                    successNotificationMsg = null
                                },
                            color = Color.Transparent,
                        ) {
                            Text(
                                text = "GERİ AL",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = EmeraldPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    } else if (deletedTransactionToUndo != null) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    deletedTransactionToUndo?.let { transaction ->
                                        viewModel.insertBrotherTransactionRecord(transaction)
                                    }
                                    deletedTransactionToUndo = null
                                    successNotificationMsg = null
                                },
                            color = Color.Transparent,
                        ) {
                            Text(
                                text = "GERİ AL",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = EmeraldPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // --- MODAL DIALOGS ---

    // 1. Yeni Borç Kaydı Ekleme Dialogu
    if (showAddDebtDialog || editingDebt != null) {
        DebtFormDialog(
            debts = debts,
            existing = editingDebt,
            onDismiss = {
                showAddDebtDialog = false
                editingDebt = null
            },
            onConfirm = { name, amount, desc, dateStr, isIncome, isPaid ->
                val old = editingDebt
                if (old == null) {
                    viewModel.addDebt(name, amount, desc, dateStr, isIncome, isPaid)
                } else {
                    viewModel.updateDebt(
                        old.copy(
                            name = name,
                            amount = amount,
                            description = desc,
                            dateStr = dateStr,
                            isIncome = isIncome,
                            isPaid = isPaid
                        )
                    )
                }
                showAddDebtDialog = false
                editingDebt = null
                triggerSuccessNotification(if (old == null) "Borç kaydı eklendi!" else "Borç kaydı güncellendi!")
            },
            onOpenKeepImport = {
                showAddDebtDialog = false
                editingDebt = null
                showKeepImportDialog = true
            }
        )
    }

    ibanPersonName?.let { personName ->
        val person = people.firstOrNull { it.person.personKey == normalizePersonKey(personName) }
        IbanManagerDialog(
            personName = personName,
            ibans = person?.ibans.orEmpty(),
            onDismiss = { ibanPersonName = null },
            onSave = { existing, iban, label ->
                viewModel.saveIban(personName, existing, iban, label) { success ->
                    if (success) triggerSuccessNotification("IBAN kaydedildi!")
                    else Toast.makeText(context, "IBAN kaydedilemedi; aynı IBAN zaten kayıtlı olabilir.", Toast.LENGTH_LONG).show()
                }
            },
            onDelete = { viewModel.deleteIban(it.id) },
            onCopy = ::copyIban
        )
    }

    // 2. Google Keep Note Veri Aktarım Dialogu
    if (showKeepImportDialog) {
        KeepImportDialog(
            onDismiss = { showKeepImportDialog = false },
            onImport = { pasteText ->
                val imported = viewModel.importFromKeep(pasteText)
                showKeepImportDialog = false
                if (imported > 0) {
                    triggerSuccessNotification("$imported adet borç aktarıldı!")
                } else {
                    Toast.makeText(context, "Geçersiz format! Hiçbir kayıt aktarılamadı.", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // 3. Abim Aylık Bütçe Sınırı Düzenleme Dialogu
    if (showEditLimitDialog) {
        EditLimitDialog(
            currentLimit = brotherBudgetLimit,
            onDismiss = { showEditLimitDialog = false },
            onConfirm = { newLimit ->
                viewModel.updateBrotherBudgetLimit(newLimit)
                showEditLimitDialog = false
                triggerSuccessNotification("Aylık limit güncellendi!")
            }
        )
    }

    // 4. Hamburger / Hakkında & İstatistik Dialogu (Point 5)
    if (showAboutAppDialog) {
        val totalAlacakPreview = debts.filter { it.isIncome && !it.isPaid }.sumOf { it.amount }
        val totalVerecekPreview = debts.filter { !it.isIncome && !it.isPaid }.sumOf { it.amount }
        AboutAppDialog(
            debtsCount = debts.size,
            transactionsCount = brotherTransactions.size,
            totalAlacak = totalAlacakPreview,
            totalVerecek = totalVerecekPreview,
            onDismiss = { showAboutAppDialog = false }
        )
    }

    // 5. Borç Verilen Kişiye Özel Profil Fotoğrafı Düzenle (Point 7)
    avatarEditTarget?.let { targetRecord ->
        AvatarPickerDialog(
            name = targetRecord.name,
            onDismiss = { avatarEditTarget = null },
            onSelectAndSave = { newAvatar ->
                viewModel.updateDebtAvatar(targetRecord.id, newAvatar)
                avatarEditTarget = null
                triggerSuccessNotification("${targetRecord.name} profili başarıyla güncellendi!")
            }
        )
    }
}

// ==========================================
// COMPOSABLE COMPONENT 1: DEBT LEDGER TAB
// ==========================================
@Composable
fun DebtLedgerTab(
    debts: List<DebtRecord>,
    onTogglePaid: (Int, Boolean) -> Unit,
    onDelete: (Int) -> Unit,
    onAddNewClick: () -> Unit,
    onAvatarClick: (DebtRecord) -> Unit
) {
    var selectedFilter by remember { mutableIntStateOf(0) } // 0 = Hepsi, 1 = Alacaklar (+), 2 = Verecekler (-)

    val filteredDebts = remember(debts, selectedFilter) {
        debts.filter {
            // filter by type (no search bar anymore - Point 3 details)
            when (selectedFilter) {
                0 -> true
                1 -> it.isIncome
                2 -> !it.isIncome
                else -> true
            }
        }
    }

    // Compute metrics
    val totalAlacak = remember(debts) { debts.filter { it.isIncome && !it.isPaid }.sumOf { it.amount } }
    val totalVerecek = remember(debts) { debts.filter { !it.isIncome && !it.isPaid }.sumOf { it.amount } }
    val activeEmerald = EmeraldPrimary
    val activeExpenseRed = ExpenseRed
    val netStatusColor = remember(totalAlacak, totalVerecek, activeEmerald, activeExpenseRed) { 
        if (totalAlacak >= totalVerecek) activeEmerald else activeExpenseRed 
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // --- CORE METRICS PANEL (Larger, more comfortable premium HUD) ---
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Bakiye Özetleri (Ödenmeyenler)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = TextSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.2.dp, CardBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 24.dp), // Signicantly enlarged padding
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Item 1: ALACAK
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ALACAK",
                            fontSize = 12.sp, // Raised for readability
                            color = TextSecondary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(6.dp)) // Increased spacing
                        Text(
                            text = formatCurrency(totalAlacak),
                            fontSize = 23.sp, // Significantly enlarged
                            fontWeight = FontWeight.Black,
                            color = EmeraldPrimary,
                            maxLines = 1,
                            softWrap = false,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(46.dp) // Separator is taller to match larger height
                            .background(CardBorder)
                    )

                    // Item 2: VERECEK
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "VERECEK",
                            fontSize = 12.sp, // Raised for readability
                            color = TextSecondary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(6.dp)) // Increased spacing
                        Text(
                            text = formatCurrency(totalVerecek),
                            fontSize = 23.sp, // Significantly enlarged
                            fontWeight = FontWeight.Black,
                            color = ExpenseRed,
                            maxLines = 1,
                            softWrap = false,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(46.dp) // Separator is taller to match larger height
                            .background(CardBorder)
                    )

                    // Item 3: NET DURUM
                    Column(
                        modifier = Modifier.weight(1.1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "NET DURUM",
                            fontSize = 12.sp, // Raised for readability
                            color = TextSecondary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(6.dp)) // Increased spacing
                        Text(
                            text = formatCurrency(totalAlacak - totalVerecek),
                            fontSize = 23.sp, // Significantly enlarged
                            fontWeight = FontWeight.Black,
                            color = netStatusColor,
                            maxLines = 1,
                            softWrap = false,
                            letterSpacing = (-0.5).sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- FILTERS BAR (Search is removed according to Point 3) ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Tümü", "Alacaklar (+)", "Verecekler (-)").forEachIndexed { idx, label ->
                        val isSelected = selectedFilter == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) BubbleBg else DarkSurface)
                                .border(
                                    1.2.dp,
                                    if (isSelected) EmeraldPrimary else CardBorder,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedFilter = idx }
                                .padding(vertical = 10.dp)
                                .testTag("filter_chip_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) EmeraldPrimary else TextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // --- DEBT RECORDS CARDS LIST (Optimized scroll behavior - Point 10) ---
            if (filteredDebts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Boş",
                            tint = TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Kayıtlı borç bulunmuyor.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Aşağıdaki butonu kullanarak yeni kayıt ekleyebilirsin.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                items(filteredDebts, key = { it.id }) { record ->
                    DebtRowItem(
                        record = record,
                        onTogglePaid = { onTogglePaid(record.id, record.isPaid) },
                        onDelete = { onDelete(record.id) },
                        onAvatarClick = { onAvatarClick(record) }
                    )
                }
            }
        }

        // --- EXQUISITE FLOATING ACTION BUTTON ---
        ExtendedFloatingActionButton(
            onClick = onAddNewClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("floating_add_debt_button"),
            containerColor = EmeraldPrimary,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("BORÇ EKLE", fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }
}

@Composable
fun DebtRowItem(
    record: DebtRecord,
    onTogglePaid: () -> Unit,
    onDelete: () -> Unit,
    onAvatarClick: () -> Unit = {}
) {
    val amountColor = if (record.isIncome) EmeraldPrimary else ExpenseRed
    val prefix = if (record.isIncome) "+ " else "- "
    val isPaidText = if (record.isPaid) "Ödendi" else "Bekliyor"
    val cardAlpha = if (record.isPaid) 0.65f else 1f

    // --- CRITICAL SCROLL OPTIMIZATION: REMEMBER STRING CONVERSIONS ---
    val formattedAmount = remember(record.amount, record.isIncome) {
        prefix + formatCurrency(record.amount)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .testTag("debt_item_${record.id}"),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface.copy(alpha = cardAlpha)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive Profile photo rendering with actual image and initials support
                AvatarRender(
                    name = record.name,
                    avatarUri = record.avatarUri,
                    isIncome = record.isIncome,
                    onClick = onAvatarClick
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = record.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (record.isPaid) TextSecondary else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (record.isIncome) EmeraldPrimary.copy(alpha = 0.1f) else ExpenseRed.copy(alpha = 0.1f))
                                .padding(horizontal = 4.dp, vertical = 0.8.dp) // Snug 1px padding
                        ) {
                            Text(
                                text = if (record.isIncome) "Alacak" else "Verecek",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = amountColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = record.description,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formattedAmount,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = amountColor,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = record.dateStr,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = CardBorder.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // Transaction sub-actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clickable custom indicator element
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTogglePaid() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (record.isPaid) Icons.Default.CheckCircle else Icons.Default.CheckCircle,
                        contentDescription = "Toggle status",
                        tint = if (record.isPaid) EmeraldPrimary else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = isPaidText.uppercase(Locale.getDefault()),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (record.isPaid) EmeraldPrimary else TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                }

                // Delete Button: Styled as a bordered rounded square with custom background (Point 12)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ExpenseRed.copy(alpha = 0.08f))
                        .border(1.dp, ExpenseRed.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .clickable { onDelete() }
                        .testTag("delete_debt_record_${record.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = ExpenseRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private val AVATAR_COLORS = listOf(
    Color(0xFFE11D48), // Rose
    Color(0xFFD97706), // Amber
    Color(0xFF059669), // Emerald
    Color(0xFF2563EB), // Blue
    Color(0xFF7C3AED), // Violet
    Color(0xFF0891B2), // Cyan
    Color(0xFFDB2777), // Pink
    Color(0xFF4F46E5)  // Indigo
)

// Avatar rendering box supporting either initials, emojis, local files or web URLs (Coil AsyncImage)
@Composable
fun AvatarRender(
    name: String,
    avatarUri: String?,
    isIncome: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val initials = remember(name) { name.trim().firstOrNull()?.toString()?.uppercase(Locale.getDefault()) ?: "?" }
    val colorIndex = remember(name) { Math.abs(name.hashCode()) % AVATAR_COLORS.size }
    val avatarColor = AVATAR_COLORS[colorIndex]

    Box(
        modifier = modifier
            .size(42.dp) // Grown slightly to be cleaner
            .clip(RoundedCornerShape(12.dp))
            .background(avatarColor.copy(alpha = 0.12f))
            .border(1.2.dp, avatarColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (avatarUri != null) {
            if (avatarUri.startsWith("emoji:")) {
                val emojiStr = avatarUri.substring(6)
                Text(
                    text = emojiStr,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                // Real photo support! Uses Coil AsyncImage to load either custom web link or private copied file
                val context = LocalContext.current
                val imageModel = remember(avatarUri, context) {
                    ImageRequest.Builder(context)
                        .data(if (avatarUri.startsWith("/")) java.io.File(avatarUri) else avatarUri)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Profile Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
        } else {
            Text(
                text = initials,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = avatarColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
// COMPOSABLE SUB-COMPONENT: FLIGHT STYLE GRID CALENDAR (Point 13 - image.png)
// ==========================================
@Composable
fun FlightStyleCalendar(
    selectedMonthKey: String, // e.g. "06/2026"
    transactions: List<BrotherTransaction>
) {
    val defaultKey = remember { SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(Date()) }
    val activeKey = selectedMonthKey.ifEmpty { defaultKey }
    
    // Parse month (1-12) and year (e.g. 2026)
    val parts = remember(activeKey) { activeKey.split("/") }
    val month = remember(parts) { parts.getOrNull(0)?.toIntOrNull() ?: 6 }
    val year = remember(parts) { parts.getOrNull(1)?.toIntOrNull() ?: 2026 }
    
    // Calculate calendar days
    val details = remember(month, year) {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.YEAR, year)
        cal.set(java.util.Calendar.MONTH, month - 1)
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        
        val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        
        // Convert so Monday=0, Tuesday=1 ... Sunday=6
        val offset = when (firstDayOfWeek) {
            java.util.Calendar.MONDAY -> 0
            java.util.Calendar.TUESDAY -> 1
            java.util.Calendar.WEDNESDAY -> 2
            java.util.Calendar.THURSDAY -> 3
            java.util.Calendar.FRIDAY -> 4
            java.util.Calendar.SATURDAY -> 5
            java.util.Calendar.SUNDAY -> 6
            else -> 0
        }
        Pair(daysInMonth, offset)
    }
    
    val daysInMonth = details.first
    val startOffset = details.second
    
    val weekdays = listOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pa")
    
    // Pre-calculate daily totals for this month for super high performance scrolling/interaction!
    val dailyTotals = remember(transactions, month, year) {
        val map = mutableMapOf<Int, Double>()
        for (day in 1..31) {
            val dateStr = String.format(Locale.US, "%02d/%02d/%04d", day, month, year)
            val total = transactions.filter { it.dateStr == dateStr }.sumOf { it.amount }
            if (total > 0.0) {
                map[day] = total
            }
        }
        map
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceElevated)
            .border(1.2.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        // Calendar Grid Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val monthNames = listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
            val activeMonthName = monthNames.getOrNull(month - 1) ?: "Ay"
            Text(
                text = "${activeMonthName.uppercase()} $year",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Days of week row
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach { dayName ->
                Text(
                    text = dayName,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextSecondary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // Days Grid Layout
        val totalCells = daysInMonth + startOffset
        val rows = (totalCells + 6) / 7
        
        for (r in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp), 
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (c in 0..6) {
                    val cellIndex = r * 7 + c
                    val dayNum = cellIndex - startOffset + 1
                    
                    if (cellIndex < startOffset || dayNum > daysInMonth) {
                        // Empty cell
                        Box(modifier = Modifier.weight(1f).aspectRatio(0.9f))
                    } else {
                        val daySpent = dailyTotals[dayNum] ?: 0.0
                        
                        val cellBg = Color.Transparent
                        val numColor = TextPrimary
                        val spentColor = ExpenseRed

                        val cellBorderModifier = if (daySpent > 0.0) {
                            Modifier.border(0.6.dp, EmeraldPrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.9f) // Snug ratios
                                .clip(RoundedCornerShape(8.dp))
                                .background(cellBg)
                                .then(cellBorderModifier)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = dayNum.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = if (daySpent > 0.0) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = numColor
                                )
                                if (daySpent > 0.0) {
                                    val formattedPrice = remember(daySpent) { 
                                        val formatSymbol = DecimalFormatSymbols(Locale("tr", "TR")).apply {
                                            groupingSeparator = '.'
                                        }
                                        DecimalFormat("#,##0", formatSymbol).format(daySpent)
                                    }
                                    Text(
                                        text = "₺$formattedPrice",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = spentColor,
                                        maxLines = 1
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ==========================================
// COMPOSABLE COMPONENT 2: ABİM ACCOUNTING TAB
// ==========================================
@Composable
fun AbimAccountingTab(
    transactions: List<BrotherTransaction>,
    budgetLimit: Double,
    onAddTransaction: (Double, String, String) -> Unit,
    onDeleteTransaction: (Int) -> Unit,
    onEditLimitClick: () -> Unit
) {
    val context = LocalContext.current
    var inputAmount by remember { mutableStateOf("") }
    var inputDesc by remember { mutableStateOf("") }
    var inputDate by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())) }

    // --- MONTHS FILTER TIMELINE GENERATION ("Uçak bileti alırkenki tarihlerin altında paralar yazışına benzer" - Point 13) ---
    val monthsList = remember(transactions) {
        val list = mutableListOf<MonthYearItem>()
        val sdf = SimpleDateFormat("MM/yyyy", Locale.getDefault())
        // Turkish locale for clean formatting
        val monthNameFmt = SimpleDateFormat("MMMM", Locale("tr", "TR"))
        
        val tempCal = java.util.Calendar.getInstance()
        // Compute last 5 months
        for (i in 0..4) {
            val key = sdf.format(tempCal.time)
            val name = monthNameFmt.format(tempCal.time).replaceFirstChar { it.uppercase() }
            val year = tempCal.get(java.util.Calendar.YEAR)
            
            // Filter and sum of spent transactions in this month key
            val monthTotal = transactions.filter {
                it.dateStr.endsWith(key) || it.dateStr.contains("/" + key)
            }.sumOf { it.amount }
            
            list.add(MonthYearItem(key = key, name = name, displaySpent = monthTotal, year = year))
            tempCal.add(java.util.Calendar.MONTH, -1)
        }
        list
    }

    // Default to current month key "MM/yyyy"
    var selectedMonthKey by remember { 
        mutableStateOf(SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(Date())) 
    }

    val selectedMonthName = remember(selectedMonthKey, monthsList) {
        monthsList.find { it.key == selectedMonthKey }?.name?.uppercase(Locale("tr", "TR")) ?: ""
    }

    // Filter transactions list based on active timeline month (calendar is purely visual/informative)
    val filteredTransactions = remember(transactions, selectedMonthKey) {
        if (selectedMonthKey.isEmpty()) {
            transactions
        } else {
            transactions.filter { 
                it.dateStr.endsWith(selectedMonthKey) || it.dateStr.contains("/" + selectedMonthKey)
            }
        }
    }

    // Calculations based on the selected timeline month spending limits
    val totalSent = remember(filteredTransactions) { filteredTransactions.sumOf { it.amount } }
    val remainingBudget = remember(budgetLimit, totalSent) { budgetLimit - totalSent }
    val budgetPercent = remember(budgetLimit, remainingBudget) { if (budgetLimit > 0) (remainingBudget / budgetLimit).coerceIn(0.0, 1.0) else 0.0 }

    val isDark = isSystemInDarkTheme()
    val activeEmerald = EmeraldPrimary
    val activeAmber = AmberWarning
    val activeExpenseRed = ExpenseRed
    val gaugeColor = remember(remainingBudget, activeEmerald, activeAmber, activeExpenseRed) { 
        if (remainingBudget > 500) activeEmerald else if (remainingBudget > 0) activeAmber else activeExpenseRed 
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 50.dp)
    ) {
        // --- REMAINING COUNTER HUD ---
        item {
            Spacer(modifier = Modifier.height(10.dp))
            val remainingBudgetColor = if (isDark) HD_DarkTextPrimary else Color(0xFF001D36)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isDark) HD_DarkBorder else HD_LightBorder, RoundedCornerShape(22.dp)), // Radius -2px (22.dp)
                colors = CardDefaults.cardColors(containerColor = if (isDark) HD_DarkSecondary else HD_LightSecondary),
                shape = RoundedCornerShape(22.dp) // Radius -2px (22.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedMonthKey.isNotEmpty() && selectedMonthName.isNotEmpty()) "KALAN LİMİT ($selectedMonthName)" else "KALAN LİMİT (TOPLAMSAL)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = remainingBudgetColor,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.5f))
                                .clickable { onEditLimitClick() }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Limit: " + formatCurrency(budgetLimit),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = remainingBudgetColor
                                )
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Limiti Düzenle",
                                    tint = if (isDark) HD_DarkPrimary else HD_LightPrimary,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Large dynamic balance matching Turkish rules
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        // Integer portion (formatted with dot separator)
                        val formatSymbol = DecimalFormatSymbols(Locale("tr", "TR")).apply { groupingSeparator = '.' }
                        val integerPartStr = DecimalFormat("#,##0", formatSymbol).format(Math.floor(Math.abs(remainingBudget)))
                        val centPartInt = ((Math.abs(remainingBudget) - Math.abs(remainingBudget).toInt()) * 100).toInt().coerceIn(0, 99)

                        Text(
                            text = (if (remainingBudget < 0) "-" else "") + integerPartStr,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = gaugeColor,
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            text = String.format(Locale.US, ",%02d ₺", centPartInt),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = gaugeColor.copy(alpha = 0.8f),
                            modifier = Modifier.alignByBaseline()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Minimal Linear Progress with custom gauge tracks
                    LinearProgressIndicator(
                        progress = budgetPercent.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = gaugeColor,
                        trackColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.3f),
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Kalan: " + formatCurrency(remainingBudget, includeDecimals = true),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = remainingBudgetColor
                        )
                        Text(
                            text = String.format(Locale.US, "%%%d Harcandı", ((totalSent / budgetLimit.coerceAtLeast(1.0)) * 100).toInt().coerceIn(0, 100)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = remainingBudgetColor
                        )
                    }
                }
            }
        }

        // --- MONTHS FILTER TIMELINE COMPONENT (Point 13 - Flight style month and day selectors) ---
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "AYLIK ÇETELE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Timeline block of Months
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(monthsList) { item ->
                    val isSelected = selectedMonthKey == item.key
                    val cardBgColor = if (isSelected) EmeraldPrimary.copy(alpha = 0.15f) else DarkSurfaceElevated
                    val cardBorderColor = if (isSelected) EmeraldPrimary else CardBorder
                    val titleColor = if (isSelected) EmeraldPrimary else TextPrimary

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardBgColor)
                            .border(1.dp, cardBorderColor, RoundedCornerShape(12.dp))
                            .clickable {
                                selectedMonthKey = if (selectedMonthKey == item.key) "" else item.key
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = item.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = titleColor
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            // Format using custom dotted thousands separator helper
                            Text(
                                text = formatCurrency(item.displaySpent),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) EmeraldPrimary else if (item.displaySpent > 0) ExpenseRed else TextSecondary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // --- INTERACTIVE FLIGHT-STYLE PRICES GRID CALENDAR (Purely Visual/Informative) ---
        item {
            if (selectedMonthKey.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                FlightStyleCalendar(
                    selectedMonthKey = selectedMonthKey,
                    transactions = transactions
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // --- HARÇLIK GÖNDERİM FORMU ---
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Hızlı Gönderim Kaydet",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Amount Box
                        OutlinedTextField(
                            value = inputAmount,
                            onValueChange = { inputAmount = it },
                            label = { Text("Miktar (₺)", color = TextSecondary, fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("brother_amount_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = CardBorder,
                                focusedLabelColor = EmeraldPrimary,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )

                        // Date field pre-filled automatically (Point 8 default open)
                        OutlinedTextField(
                            value = inputDate,
                            onValueChange = { inputDate = it },
                            label = { Text("Tarih", color = TextSecondary, fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("brother_date_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = CardBorder,
                                focusedLabelColor = EmeraldPrimary,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )
                    }

                    // Description text input
                    OutlinedTextField(
                        value = inputDesc,
                        onValueChange = { inputDesc = it },
                        label = { Text("Neden gitti? (Örn: Mutfak Harcaması)", color = TextSecondary, fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("brother_desc_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = EmeraldPrimary,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )

                    // Accent Save/Submit button
                    Button(
                        onClick = {
                            val amountVal = inputAmount.replace(",", ".").toDoubleOrNull() ?: 0.0
                            if (amountVal <= 0.0) {
                                Toast.makeText(context, "Lütfen geçerli miktar girin!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (inputDesc.isBlank()) {
                                Toast.makeText(context, "Lütfen harcama nedeni belirtin!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onAddTransaction(amountVal, inputDesc, inputDate)
                            // Clear inputs
                            inputAmount = ""
                            inputDesc = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(48.dp)
                            .testTag("save_brother_payment_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("KAYDET VE BÜTÇEDEN DÜŞ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // --- HARCAMA GEÇMİŞİ LİSTESİ ---
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (selectedMonthKey.isEmpty()) "Tüm Gönderim Geçmişi" else "Harcamalar (${monthsList.find { it.key == selectedMonthKey }?.name ?: ""})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${filteredTransactions.size} Kayıt",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (filteredTransactions.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.List, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Seçili döneme ait kayıtlı harcama bulunmuyor.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(filteredTransactions, key = { it.id }) { item ->
                BrotherTransactionCard(
                    transaction = item,
                    onDelete = { onDeleteTransaction(item.id) }
                )
            }
        }
    }
}

@Composable
fun BrotherTransactionCard(
    transaction: BrotherTransaction,
    onDelete: () -> Unit
) {
    // --- CRITICAL SCROLL OPTIMIZATION: REMEMBER STRING CONVERSIONS ---
    val formattedAmount = remember(transaction.amount) {
        "-" + formatCurrency(transaction.amount)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .testTag("brother_item_${transaction.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle icon represent category spending (orange theme in list)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ExpenseRed.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = ExpenseRed,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description,
                    fontSize = 15.sp, // Raised for legibility
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = transaction.dateStr,
                        fontSize = 12.sp, // Raised for legibility
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = formattedAmount,
                    fontSize = 16.sp, // Raised for legibility
                    fontWeight = FontWeight.Black,
                    color = ExpenseRed,
                    letterSpacing = (-0.5).sp
                )
                
                // Delete: Styled as rounded square matching Point 12
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ExpenseRed.copy(alpha = 0.08f))
                        .border(1.dp, ExpenseRed.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Harcamayı sil",
                        tint = ExpenseRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// COMPOSABLE DIALOG: ADD DEBT DIALOG
// ==========================================
@Composable
fun AddDebtDialog(
    debts: List<DebtRecord> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, amount: Double, desc: String, dateStr: String, isIncome: Boolean, isPaid: Boolean) -> Unit,
    onOpenKeepImport: () -> Unit
) {
    var rawName by remember { mutableStateOf("") }
    var rawAmount by remember { mutableStateOf("") }
    var rawDesc by remember { mutableStateOf("") }
    // Auto populated date default (Point 8)
    var rawDateStr by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())) }
    var isIncomeSelected by remember { mutableStateOf(true) } // true = Alacak (+), false = Verecek (-)
    var isAlreadyPaid by remember { mutableStateOf(false) }

    val resolvedAvatarUri = remember(rawName, debts) {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) null
        else {
            val lowercaseName = trimmed.lowercase(java.util.Locale("tr", "TR"))
            debts.find { 
                it.name.trim().lowercase(java.util.Locale("tr", "TR")) == lowercaseName && !it.avatarUri.isNullOrEmpty() 
            }?.avatarUri
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, CardBorder, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Yeni Borç Kaydı Ekle",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )

                    if (resolvedAvatarUri != null) {
                        AvatarRender(
                            name = rawName,
                            avatarUri = resolvedAvatarUri,
                            isIncome = isIncomeSelected,
                            modifier = Modifier.size(40.dp),
                            onClick = {}
                        )
                    }
                }

                // Entegrated Google Keep Import Action (Point 6)
                Button(
                    onClick = onOpenKeepImport,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GOOGLE KEEP'TEN İÇE AKTAR", 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                HorizontalDivider(color = CardBorder.copy(alpha = 0.4f), thickness = 1.dp)

                // Name input
                OutlinedTextField(
                    value = rawName,
                    onValueChange = { rawName = it },
                    label = { Text("Kim? (Ad-Soyad)", color = TextSecondary, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_debt_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Amount input
                    OutlinedTextField(
                        value = rawAmount,
                        onValueChange = { rawAmount = it },
                        label = { Text("Miktar (₺)", color = TextSecondary, fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("dialog_debt_amount_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )

                    // Date input
                    OutlinedTextField(
                        value = rawDateStr,
                        onValueChange = { rawDateStr = it },
                        label = { Text("Tarih", color = TextSecondary, fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("dialog_debt_date_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )
                }

                // Description input
                OutlinedTextField(
                    value = rawDesc,
                    onValueChange = { rawDesc = it },
                    label = { Text("Açıklama (Örn: Borç para, Sigara vb.)", color = TextSecondary, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_debt_desc_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )

                // Custom Selectors Row: Alacak (+) vs Verecek (-)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isIncomeSelected) EmeraldPrimary else Color.Transparent)
                            .clickable { isIncomeSelected = true }
                            .padding(vertical = 10.dp)
                            .testTag("selector_income"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Alacak (+)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isIncomeSelected) Color.White else TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isIncomeSelected) ExpenseRed else Color.Transparent)
                            .clickable { isIncomeSelected = false }
                            .padding(vertical = 10.dp)
                            .testTag("selector_expense"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Verecek (-)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (!isIncomeSelected) Color.White else TextSecondary
                        )
                    }
                }

                // Paid toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .clickable { isAlreadyPaid = !isAlreadyPaid }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isAlreadyPaid) Icons.Default.CheckCircle else Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isAlreadyPaid) EmeraldPrimary else TextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Zaten Ödendi mi?", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Switch(
                        checked = isAlreadyPaid,
                        onCheckedChange = { isAlreadyPaid = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmeraldPrimary,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurface
                        ),
                        modifier = Modifier.testTag("dialog_paid_switch")
                    )
                }

                // Action Confirmation Button Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        border = borderStroke(1.dp, CardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("İPTAL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val amountParsed = rawAmount.replace(",", ".").toDoubleOrNull() ?: 0.0
                            if (rawName.isBlank()) {
                                return@Button
                            }
                            onConfirm(rawName, amountParsed, rawDesc, rawDateStr, isIncomeSelected, isAlreadyPaid)
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(44.dp)
                            .testTag("dialog_confirm_add_debt_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isIncomeSelected) EmeraldPrimary else ExpenseRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("KAYDET", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// Simple border helper due to Android API constraints
fun borderStroke(width: androidx.compose.ui.unit.Dp, color: Color) = androidx.compose.foundation.BorderStroke(width, color)


// ==========================================
// COMPOSABLE DIALOG: EDIT ABİM RATELIMIT
// ==========================================
@Composable
fun EditLimitDialog(
    currentLimit: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var rawLimit by remember { mutableStateOf(currentLimit.toInt().toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, CardBorder, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Aylık Harçlık Limitini Düzenle",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "Abine aylık bütçe olarak göndermeyi planladığın maksimum limit (₺):",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = rawLimit,
                    onValueChange = { rawLimit = it },
                    label = { Text("Yeni Limit (₺)", color = TextSecondary, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_edit_limit_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        border = borderStroke(1.dp, CardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("İPTAL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val parsed = rawLimit.toDoubleOrNull() ?: 2000.0
                            onConfirm(parsed)
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                            .testTag("dialog_confirm_edit_limit"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("GÜNCELLE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}


// ==========================================
// COMPOSABLE DIALOG: KEEP NOTES PASTE IMPORT
// ==========================================
@Composable
fun KeepImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    
    val sampleText = """Mustafa - 110 ₺ - Sigara 06/03/2026 + ✅
Hakan - 250 ₺ - Yemek Gideri 07/03/2026 - ❌
Leyla Abla - 50 ₺ - Borç + ✅"""

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, CardBorder, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Keep'ten Borç Verisi İçe Aktar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "Keep'te tuttuğun formatı aşağıya yapıştır. Sistem otomatik algılayıp borçları ekleyecektir.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                // Small instructions sample box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .padding(8.dp)
                ) {
                    Column {
                        Text("Örnek format:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                        Text(
                            text = sampleText,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }

                // Text paste box
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = { Text("Buraya listeyi yapıştır...", color = TextSecondary, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("keep_textarea_input"),
                    maxLines = 15,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        border = borderStroke(1.dp, CardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("İPTAL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onImport(rawText) },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(44.dp)
                            .testTag("dialog_confirm_keep_import"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("AKTARIYI YAP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPOSABLE DIALOG: ABOUT & STATISTICS DIALOG (Point 5 Hamburger implementation)
// ==========================================
@Composable
fun AboutAppDialog(
    debtsCount: Int,
    transactionsCount: Int,
    totalAlacak: Double,
    totalVerecek: Double,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, CardBorder, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Info logo circular badge
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(EmeraldSecondary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = EmeraldSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Para Defteri v2.5",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )

                Text(
                    text = "Alacak ve verecek borçlarınızı pratik şekilde defterde tutup, kardeşinizin/abinizin aylık bütçe harcar limitini takip eden akıllı asistanınız.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                HorizontalDivider(color = CardBorder.copy(alpha = 0.4f), thickness = 1.dp)

                // Stats rows list
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatRowItem(label = "Aktif Defter Borç Girişi:", value = "$debtsCount adet")
                    StatRowItem(label = "Kalan Alacak Toplamı:", value = String.format(Locale.US, "%,.0f ₺", totalAlacak))
                    StatRowItem(label = "Kalan Verecek Toplamı:", value = String.format(Locale.US, "%,.0f ₺", totalVerecek))
                    StatRowItem(label = "Muhasebedeki Harcama Adeti:", value = "$transactionsCount kayıt")
                }

                HorizontalDivider(color = CardBorder.copy(alpha = 0.4f), thickness = 1.dp)

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("KAPAT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun StatRowItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
        Text(text = value, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.ExtraBold)
    }
}


// ==========================================
// COMPOSABLE DIALOG: USER PROFILE AVATAR PICKER (Point 7 implementation)
// ==========================================
@Composable
fun AvatarPickerDialog(
    name: String,
    onDismiss: () -> Unit,
    onSelectAndSave: (String?) -> Unit
) {
    val context = LocalContext.current
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Launcher to pick a visual photo from system gallery! (Point 1 and Point 7)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedUri = uri
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, CardBorder, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (pickedUri == null) {
                    Text(
                        text = "$name İçin Fotoğraf Seç",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )

                    Text(
                        text = "Telefon galerisinden bir profil fotoğrafı seçebilirsin. Seçilen görsel otomatik olarak merkezlenerek kusursuz biçimde ayarlanacaktır.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    // METHOD A: Device Library Gallery Photo
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "GALERİDEN BİR FOTOĞRAF SEÇİN", 
                            fontSize = 10.sp, 
                            fontWeight = FontWeight.Black, 
                            color = EmeraldPrimary, 
                            letterSpacing = 0.5.sp
                        )
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary.copy(alpha = 0.15f),
                                contentColor = EmeraldPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.2.dp, EmeraldPrimary.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBox, 
                                contentDescription = null, 
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("TELEFON GALERİSİNİ AÇ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSelectAndSave(null) }, // Clears profile back to default colored initials
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("VARSAYILAN YAP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(0.8f),
                            border = BorderStroke(1.dp, CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("İPTAL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text(
                        text = "Fotoğraf Önizleme",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )

                    Text(
                        text = "Seçilen fotoğraf aşağıdaki gibi daire içine sığacak ve ortalanacaktır. Kaydedip devam edebilirsiniz.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Real-time Preview Area: Perfect square box containing perfectly centered image preview
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(CircleShape)
                            .border(2.dp, EmeraldPrimary, CircleShape)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        val imgModel = remember(pickedUri) {
                            ImageRequest.Builder(context)
                                .data(pickedUri)
                                .crossfade(true)
                                .build()
                        }
                        AsyncImage(
                            model = imgModel,
                            contentDescription = "Portrait Cropping Area",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { pickedUri = null }, // Back to pick select
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("GERİ GİT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val savedPath = saveCroppedImageLocally(
                                    context = context, 
                                    uri = pickedUri!!
                                )
                                onSelectAndSave(savedPath)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("KAYDET VE KULLAN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
