package com.example.logio.ui.timeline

import com.example.logio.ui.theme.logioFieldColors
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logio.R
import com.example.logio.ui.theme.MimoNavy
import com.example.logio.ui.theme.MimoPurple

private val HintText = Color(0xFF8A90B4)

// ---------- Harcama ----------

/** Tutar + kategori + isteğe bağlı açıklama. onConfirm(açıklama, tutar, kategoriAnahtarı). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseInputDialog(onDismiss: () -> Unit, onConfirm: (String, Double, String) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var note by remember { mutableStateOf("") }
    val amount = amountText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    LogioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.dlg_add_expense),
        confirmButton = {
            Button(
                onClick = { amount?.let { onConfirm(note.trim(), it, category.key) }; onDismiss() },
                enabled = amount != null,
                shape = RoundedCornerShape(14.dp)
            ) { Text(stringResource(R.string.save)) }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MascotHeader(R.drawable.mascot_money, stringResource(R.string.expense_mascot_hint))
            OutlinedTextField(
                colors = logioFieldColors(),
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(10) },
                label = { Text(stringResource(R.string.dlg_amount_currency)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MimoNavy),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            SectionLabel(stringResource(R.string.expense_category))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseCategory.entries.forEach { option ->
                    OptionChip(
                        text = "${option.emoji} ${stringResource(option.label)}",
                        selected = option == category,
                        onClick = { category = option }
                    )
                }
            }
            OutlinedTextField(
                colors = logioFieldColors(),
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.expense_note_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

// ---------- Kahve ----------

/** onConfirm(fincan sayısı, kahve türünün adı). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoffeeInputDialog(onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit) {
    var kind by remember { mutableStateOf(CoffeeKind.TURKISH) }
    var cups by remember { mutableIntStateOf(1) }
    val kindLabel = stringResource(kind.label)

    LogioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.dlg_coffee),
        confirmButton = {
            Button(onClick = { onConfirm(cups, kindLabel); onDismiss() }, shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.add))
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MascotHeader(R.drawable.mascot_coffee, stringResource(R.string.coffee_mascot_hint))
            SectionLabel(stringResource(R.string.coffee_kind))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CoffeeKind.entries.forEach { option ->
                    OptionChip(
                        text = "${option.emoji} ${stringResource(option.label)}",
                        selected = option == kind,
                        onClick = { kind = option }
                    )
                }
            }
            SectionLabel(stringResource(R.string.dlg_cup_count))
            CountStepper(value = cups, onValueChange = { cups = it }, range = 1..10, unit = R.plurals.cups_count)
        }
    }
}

// ---------- Alkol ----------

/** onConfirm(kadeh sayısı (metin olarak saklanır), içki türünün adı). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlcoholInputDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var kind by remember { mutableStateOf(DrinkKind.BEER) }
    var glasses by remember { mutableIntStateOf(1) }
    val kindLabel = stringResource(kind.label)

    LogioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.dlg_alcohol),
        confirmButton = {
            Button(onClick = { onConfirm(glasses.toString(), kindLabel); onDismiss() }, shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.save))
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MascotHeader(R.drawable.mascot_alcohol, stringResource(R.string.alcohol_mascot_hint))
            SectionLabel(stringResource(R.string.dlg_type))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DrinkKind.entries.forEach { option ->
                    OptionChip(
                        text = "${option.emoji} ${stringResource(option.label)}",
                        selected = option == kind,
                        onClick = { kind = option }
                    )
                }
            }
            SectionLabel(stringResource(R.string.dlg_amount))
            CountStepper(value = glasses, onValueChange = { glasses = it }, range = 1..10, unit = R.plurals.glasses_count)
        }
    }
}

// ---------- Ortak parçalar ----------

@Composable
internal fun MascotHeader(@DrawableRes mascot: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF7F4FF))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Image(
            painter = painterResource(mascot),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MimoNavy)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = HintText)
}

@Composable
private fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MimoPurple.copy(alpha = 0.14f),
            selectedLabelColor = MimoPurple
        )
    )
}

@Composable
private fun CountStepper(value: Int, onValueChange: (Int) -> Unit, range: IntRange, unit: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        StepButton(Icons.Rounded.Remove, enabled = value > range.first) { onValueChange(value - 1) }
        Text(
            text = androidx.compose.ui.res.pluralStringResource(unit, value, value),
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MimoNavy,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 140.dp)
        )
        StepButton(Icons.Rounded.Add, enabled = value < range.last) { onValueChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MimoPurple.copy(alpha = 0.12f), contentColor = MimoPurple),
        modifier = Modifier.size(44.dp)
    ) {
        Icon(icon, contentDescription = null)
    }
}
