package com.example.logio.ui.timeline

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

enum class DialogQuickAction {
    LOG,
    MOOD,
    VOICE,
    EXPENSE,
    WATER,
    LOCATION
}

fun QuickLogAction.toDialogQuickActionOrNull(): DialogQuickAction? = when (this) {
    QuickLogAction.LOG -> DialogQuickAction.LOG
    QuickLogAction.MOOD -> DialogQuickAction.MOOD
    QuickLogAction.VOICE -> DialogQuickAction.VOICE
    QuickLogAction.EXPENSE -> DialogQuickAction.EXPENSE
    QuickLogAction.WATER -> DialogQuickAction.WATER
    QuickLogAction.LOCATION -> DialogQuickAction.LOCATION
    else -> null
}

@Composable
fun QuickActionDialogHost(
    activeDialogAction: DialogQuickAction?,
    onDismiss: () -> Unit,
    onAddNote: (String, String) -> Unit,
    onAddMood: (String, Int) -> Unit,
    isRecording: Boolean,
    onStartRecording: ((String) -> Unit) -> Unit,
    onStopRecording: () -> com.example.logio.data.audio.AudioRecordResult?,
    onSaveVoice: (String, Long) -> Unit,
    onAddExpense: (String, Double, String) -> Unit,
    onAddWater: (Int) -> Unit,
    onAddLocation: (String, String) -> Unit,
    currentDayWaterTotal: Int
) {
    when (activeDialogAction) {
        DialogQuickAction.LOG -> {
            LogInputDialog(
                onDismiss = onDismiss,
                onConfirm = { title, note ->
                    onAddNote(title, note)
                    onDismiss()
                }
            )
        }
        DialogQuickAction.MOOD -> {
            MoodInputDialog(
                onDismiss = onDismiss,
                onSelectMood = { emoji, rating ->
                    onAddMood(emoji, rating)
                    onDismiss()
                }
            )
        }
        DialogQuickAction.VOICE -> {
            VoiceInputDialog(
                isRecording = isRecording,
                onDismiss = onDismiss,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onSaveVoice = { audioPath, durationMs ->
                    onSaveVoice(audioPath, durationMs)
                    onDismiss()
                }
            )
        }
        DialogQuickAction.EXPENSE -> {
            ExpenseInputDialog(
                onDismiss = onDismiss,
                onConfirm = { title, amount, category ->
                    onAddExpense(title, amount, category)
                    onDismiss()
                }
            )
        }
        DialogQuickAction.WATER -> {
            WaterInputDialog(
                currentDayTotalMl = currentDayWaterTotal,
                onDismiss = onDismiss,
                onConfirm = { amountMl ->
                    onAddWater(amountMl)
                    onDismiss()
                }
            )
        }
        DialogQuickAction.LOCATION -> {
            LocationInputDialog(
                onDismiss = onDismiss,
                onConfirm = { placeName, note ->
                    onAddLocation(placeName, note)
                    onDismiss()
                }
            )
        }
        null -> Unit
    }
}

@Composable
fun LogInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Not") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Başlık") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Aklındakiler...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, note) },
                enabled = note.isNotBlank() || title.isNotBlank()
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodInputDialog(
    onDismiss: () -> Unit,
    onSelectMood: (String, Int) -> Unit
) {
    val moods = listOf(
        Triple("😍", 5, "Muhteşem"),
        Triple("😊", 4, "İyi"),
        Triple("😐", 3, "Normal"),
        Triple("😟", 2, "Fena değil"),
        Triple("😢", 1, "İyi değil")
    )

    val trLocale = Locale("tr")
    val now = remember { Date() }
    val monthDay = remember(now) { SimpleDateFormat("MMM dd", trLocale).format(now) }
    val weekDay = remember(now) { SimpleDateFormat("EEE", trLocale).format(now).replaceFirstChar { it.uppercase() } }
    val hourMinute = remember(now) { SimpleDateFormat("HH:mm", trLocale).format(now) }

    var selectedMood by remember { mutableStateOf?>(null) }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(42.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = monthDay,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(weekDay) }
                    )
                    Text(
                        text = hourMinute,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(onClick = onDismiss) {
                    Text("✕", style = MaterialTheme.typography.titleMedium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                moods.forEach { mood ->
                    val isSelected = selectedMood == mood
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedMood = mood },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            border = if (isSelected) {
                                ButtonDefaults.outlinedButtonBorder.copy(
                                    width = 2.dp,
                                    brush = SolidColor(MaterialTheme.colorScheme.primary)
                                )
                            } else ButtonDefaults.outlinedButtonBorder,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Text(text = mood.first, fontSize = 28.sp)
                        }
                        Text(
                            text = mood.third,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("Not") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    selectedMood?.let { onSelectMood(it.first, it.second) }
                    onDismiss()
                },
                enabled = selectedMood != null,
                modifier = Modifier
                    .align(Alignment.End)
                    .height(52.dp)
                    .width(150.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text("Kaydet")
            }
        }
    }
}

@Composable
fun VoiceInputDialog(
    isRecording: Boolean,
    onDismiss: () -> Unit,
    onStartRecording: ((String) -> Unit) -> Unit,
    onStopRecording: () -> com.example.logio.data.audio.AudioRecordResult?,
    onSaveVoice: (String, Long) -> Unit
) {
    val context = LocalContext.current
    var recordedAudioPath by remember { mutableStateOf("") }
    var durationMs by remember { mutableStateOf(0L) }
    var errorText by remember { mutableStateOf(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                onStartRecording { message -> errorText = message }
            } else {
                errorText = "Mikrofon izni olmadan ses kaydı başlatılamaz."
            }
        }
    )

    AlertDialog(
        onDismissRequest = {
            if (isRecording) onStopRecording()
            onDismiss()
        },
        title = { Text("Sesli Kayıt") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isRecording) {
                    Text(
                        "Kaydediliyor...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                    Button(
                        onClick = {
                            val result = onStopRecording()
                            if (result != null) {
                                recordedAudioPath = result.filePath
                                durationMs = result.durationMs
                                errorText = null
                            } else {
                                errorText = "Kayıt durdurulamadı. Lütfen tekrar dene."
                            }
                        }
                    ) {
                        Text("Kaydı Durdur")
                    }
                } else if (recordedAudioPath.isNotBlank()) {
                    Text(
                        "Kayıt tamamlandı (${durationMs / 1000} sn)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF16A34A)
                    )
                    Button(
                        onClick = {
                            recordedAudioPath = ""
                            durationMs = 0L
                            errorText = null
                        }
                    ) {
                        Text("Yeniden Kaydet")
                    }
                } else {
                    Button(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                onStartRecording { message -> errorText = message }
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Text("Kaydı Başlat")
                    }
                }

                if (!errorText.isNullOrBlank()) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveVoice(recordedAudioPath, durationMs)
                },
                enabled = recordedAudioPath.isNotBlank() && !isRecording
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isRecording) onStopRecording()
                    onDismiss()
                }
            ) {
                Text("İptal")
            }
        }
    )
}

@Composable
fun ExpenseInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Yemek") }

    val categories = listOf("Yemek", "Ulaşım", "Sağlık", "Eğlence", "Alışveriş", "Kamu Hizmetleri", "Spor", "Diğer")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Harcama Ekle") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Kategori",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category, fontSize = 12.sp) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Açıklama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Örn: Kahve, Benzin, İlaç") }
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Miktar (₺)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("50.00") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && amount.isNotBlank()) {
                        try {
                            val amountValue = amount.toDouble()
                            if (amountValue > 0) {
                                onConfirm(title, amountValue, selectedCategory)
                            }
                        } catch (e: NumberFormatException) {
                            // Gecersiz miktar
                        }
                    }
                },
                enabled = title.isNotBlank() && amount.isNotBlank()
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

@Composable
fun WaterInputDialog(
    currentDayTotalMl: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val quickAmounts = listOf(250, 500, 750, 1000)
    val targetMl = 2000
    val enteredAmount = amount.toIntOrNull() ?: 0
    val currentRemainingMl = (targetMl - currentDayTotalMl).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Su Tüketimi") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Hedef: ${targetMl} ml",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "Bugün: \({currentDayTotalMl} ml /\){targetMl} ml · Kalan ${currentRemainingMl} ml",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (currentDayTotalMl >= targetMl) {
                    Text(
                        text = "Bugünkü hedef tamamlandı!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    "Ne kadar su içtiniz?",
                    style = MaterialTheme.typography.bodyMedium
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickAmounts) { amountValue ->
                        OutlinedButton(
                            onClick = { amount = amountValue.toString() },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("$amountValue ml")
                        }
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Miktar (ml)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Örn: 500") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountMl = amount.toIntOrNull() ?: 0
                    if (amountMl > 0) {
                        onConfirm(amountMl)
                    }
                },
                enabled = enteredAmount > 0
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

@Composable
fun LocationInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var placeName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konum Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = placeName,
                    onValueChange = { placeName = it },
                    label = { Text("Mekan adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Örn: Kadıköy Sahil") }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Not") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Örn: Arkadaşlarla buluştum") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(placeName, note) },
                enabled = placeName.isNotBlank() || note.isNotBlank()
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}