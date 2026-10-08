package com.example.logio.ui.share

import com.example.logio.ui.theme.logioFieldColors
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.example.logio.R
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.logio.MainActivity
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalDao
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.data.remote.LinkPreviewFetcher
import com.example.logio.ui.theme.LogioTheme
import com.example.logio.util.AppLocale
import com.example.logio.ui.theme.MimoNavy
import com.example.logio.ui.theme.MimoNavyMuted
import com.example.logio.ui.theme.MimoPurple
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------- Paylaşılan link: ayıklama ve sınıflandırma ----------

/** Paylaşılan linkin türü; renkleri ve AI hap etiketleri türe göre belirlenir. */
enum class SharedLinkCategory(
    @StringRes val label: Int,
    val emoji: String,
    val wash: Color,
    val tint: Color,
    val chips: List<Int>
) {
    RECIPE(R.string.link_cat_recipe, "🍳", Color(0xFFFFEDD5), Color(0xFFC2410C), listOf(R.string.chip_ingredients, R.string.chip_steps, R.string.chip_ai)),
    FILM(R.string.action_film, "🎬", Color(0xFFE0E7FF), Color(0xFF4338CA), listOf(R.string.chip_cast, R.string.chip_summary, R.string.chip_ai)),
    PLACE(R.string.link_cat_place, "📍", Color(0xFFDCFCE7), Color(0xFF15803D), listOf(R.string.action_location, R.string.chip_reviews, R.string.chip_ai)),
    OTHER(R.string.link_cat_other, "🔗", Color(0xFFE0F2FE), Color(0xFF0369A1), listOf(R.string.chip_summary, R.string.chip_ai))
}

data class SharedLink(
    val url: String,
    val title: String,
    val category: SharedLinkCategory,
    val thumbnailUrl: String?
)

private val HttpUrlRegex = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

private val PlaceHosts = listOf("maps.google.", "google.com/maps", "goo.gl/maps", "maps.app.goo.gl", "foursquare.com", "tripadvisor.", "yandex.com.tr/maps", "yandex.com/maps")
private val FilmHosts = listOf("imdb.com", "letterboxd.com", "netflix.com", "mubi.com", "disneyplus.com", "primevideo.com", "beyazperde.com", "themoviedb.org")
private val RecipeHosts = listOf("yemek.com", "nefisyemektarifleri.com", "allrecipes.com", "tasty.co")
private val RecipeWords = listOf("tarif", "recipe", "yemek", "mutfak", "tatlı", "çorba", "somon", "tavuk", "köfte", "makarna", "kek", "pasta", "salata", "sos", "fırın")
private val FilmWords = listOf("film", "dizi", "movie", "trailer", "fragman", "sezon", "season", "sinema")
private val PlaceWords = listOf("restoran", "restaurant", "kafe", "cafe", "mekan", "bistro", "otel", "hotel", "lokanta")

/** Paylaşılan metindeki ilk http(s) linkini döndürür; sondaki noktalama işaretleri atılır. */
fun extractFirstUrl(text: String): String? {
    val raw = HttpUrlRegex.find(text)?.value
        ?: Patterns.WEB_URL.matcher(text).takeIf { it.find() }?.group()?.let { "https://$it" }
        ?: return null
    return raw.trimEnd('.', ',', ')', ']', '!', '?', ';', ':', '\'', '"')
}

/** Başlık ve linke bakarak türü tahmin eder. Zaman tüneli kartı da aynı fonksiyonu kullanır. */
fun classifySharedLink(title: String, url: String): SharedLinkCategory {
    val lowerUrl = url.lowercase()
    val haystack = "${title.lowercase()} $lowerUrl"
    return when {
        PlaceHosts.any { it in lowerUrl } -> SharedLinkCategory.PLACE
        FilmHosts.any { it in lowerUrl } -> SharedLinkCategory.FILM
        RecipeHosts.any { it in lowerUrl } || RecipeWords.any { it in haystack } -> SharedLinkCategory.RECIPE
        FilmWords.any { it in haystack } -> SharedLinkCategory.FILM
        PlaceWords.any { it in haystack } -> SharedLinkCategory.PLACE
        else -> SharedLinkCategory.OTHER
    }
}

fun hostLabel(url: String): String =
    Uri.parse(url).host?.removePrefix("www.")?.removePrefix("m.") ?: url

/** YouTube linkleri için ek istek atmadan küçük resim adresi üretir; diğerlerinde null. */
fun youtubeThumbnailUrl(url: String): String? {
    val uri = Uri.parse(url)
    val host = uri.host?.lowercase() ?: return null
    val videoId = when {
        host.endsWith("youtu.be") -> uri.pathSegments.firstOrNull()
        host.contains("youtube.com") && uri.pathSegments.firstOrNull() in setOf("shorts", "live", "embed") -> uri.pathSegments.getOrNull(1)
        host.contains("youtube.com") -> uri.getQueryParameter("v")
        else -> null
    }
    return videoId?.takeIf { it.isNotBlank() }?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
}

private fun defaultTitleFor(url: String, res: Resources): String {
    val host = hostLabel(url)
    return when {
        "youtube" in host || "youtu.be" in host -> res.getString(R.string.link_default_youtube)
        "instagram" in host -> res.getString(R.string.link_default_instagram)
        "tiktok" in host -> res.getString(R.string.link_default_tiktok)
        "maps" in host || "goo.gl" in host -> res.getString(R.string.link_default_map)
        else -> res.getString(R.string.link_default_host, host)
    }
}

/** Paylaşım metninden link, başlık, tür ve (varsa) küçük resim çıkarır. Web kazıma yapılmaz. */
fun parseSharedText(text: String, subject: String?, res: Resources): SharedLink? {
    val url = extractFirstUrl(text) ?: return null
    val caption = text.replace(url, " ")
        .lines()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() && extractFirstUrl(it) == null }
    val title = listOfNotNull(subject?.trim(), caption)
        .firstOrNull { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) }
        ?.let { if (it.length > 70) it.take(67).trimEnd() + "…" else it }
        ?: defaultTitleFor(url, res)
    return SharedLink(
        url = url,
        title = title,
        category = classifySharedLink(title, url),
        thumbnailUrl = youtubeThumbnailUrl(url)
    )
}

// ---------- Paylaşım yakalayıcı aktivite ----------

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject lateinit var journalDao: JournalDao
    @Inject lateinit var linkPreviewFetcher: LinkPreviewFetcher

    private var savedEntry by mutableStateOf<JournalEntryEntity?>(null)
    private var wasAlreadySaved by mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val link = parseSharedText(sharedText, intent?.getStringExtra(Intent.EXTRA_SUBJECT), resources)
        if (link == null) {
            Toast.makeText(this, getString(R.string.share_no_link), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Döndürme vb. yeniden oluşturmada kaydı tekrar eklemek yerine aynı kaydı yükle
        val restoredId = savedInstanceState?.getLong(KEY_ENTRY_ID, 0L) ?: 0L
        lifecycleScope.launch {
            if (restoredId != 0L) {
                wasAlreadySaved = savedInstanceState?.getBoolean(KEY_ALREADY_SAVED) ?: false
                savedEntry = journalDao.getEntryById(restoredId)
            }
            if (savedEntry == null) saveLink(link)
        }

        setContent {
            LogioTheme {
                SharedLinkSheet(
                    link = link,
                    entry = savedEntry,
                    alreadySaved = wasAlreadySaved,
                    onDone = { editedTitle -> finishWithTitle(editedTitle, openJournal = false) },
                    onOpenJournal = { editedTitle -> finishWithTitle(editedTitle, openJournal = true) },
                    onUndo = ::undoAndFinish,
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        savedEntry?.let { outState.putLong(KEY_ENTRY_ID, it.id) }
        outState.putBoolean(KEY_ALREADY_SAVED, wasAlreadySaved)
    }

    private companion object {
        const val KEY_ENTRY_ID = "shared_entry_id"
        const val KEY_ALREADY_SAVED = "shared_already_saved"
    }

    // Link açılır açılmaz kaydedilir; aynı link daha önce eklendiyse yeni kayıt açılmaz
    private suspend fun saveLink(link: SharedLink) {
        val existing = journalDao.getEntriesByType(EntryType.LINK).first().firstOrNull { it.content == link.url }
        if (existing != null) {
            wasAlreadySaved = true
            savedEntry = existing
            return
        }
        val entry = JournalEntryEntity(
            title = link.title,
            content = link.url,
            entryType = EntryType.LINK,
            imagePaths = link.thumbnailUrl
        )
        val id = journalDao.insertEntry(entry)
        savedEntry = entry.copy(id = id)

        // YouTube dışındaki linklerde önizleme görseli sayfanın og:image etiketinden alınır
        if (link.thumbnailUrl == null) {
            val imageUrl = linkPreviewFetcher.fetch(link.url)?.imageUrl ?: return
            val current = journalDao.getEntryById(id) ?: return
            val updated = current.copy(imagePaths = imageUrl)
            journalDao.updateEntry(updated)
            if (savedEntry?.id == id) savedEntry = updated
        }
    }

    private fun finishWithTitle(editedTitle: String, openJournal: Boolean) {
        val entry = savedEntry
        lifecycleScope.launch {
            val newTitle = editedTitle.trim()
            if (entry != null && newTitle.isNotBlank() && newTitle != entry.title) {
                journalDao.updateEntry(entry.copy(title = newTitle))
            }
            if (openJournal) {
                startActivity(
                    Intent(this@ShareReceiverActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
            finish()
        }
    }

    private fun undoAndFinish() {
        val entry = savedEntry
        lifecycleScope.launch {
            if (entry != null && !wasAlreadySaved) journalDao.deleteEntry(entry)
            Toast.makeText(this@ShareReceiverActivity, getString(R.string.share_undone), Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

// ---------- Hızlı kayıt penceresi ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedLinkSheet(
    link: SharedLink,
    entry: JournalEntryEntity?,
    alreadySaved: Boolean,
    onDone: (String) -> Unit,
    onOpenJournal: (String) -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(entry?.title) { mutableStateOf(entry?.title ?: link.title) }
    var isEditing by remember { mutableStateOf(false) }
    val category = classifySharedLink(title, link.url)
    val isSaving = entry == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFFFFBFE),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // Başlık: yeşil tik + "Kaydedildi"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7))
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF16A34A))
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = when {
                            isSaving -> stringResource(R.string.share_saving)
                            alreadySaved -> stringResource(R.string.share_already_saved)
                            else -> stringResource(R.string.share_saved)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MimoNavy
                    )
                    Text(
                        text = hostLabel(link.url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MimoNavyMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Önizleme kartı
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFEDE8F7))
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    LinkThumbnail(thumbnailUrl = entry?.imagePaths ?: link.thumbnailUrl, category = category, size = 72)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(category)
                            Spacer(modifier = Modifier.weight(1f))
                            if (!isEditing) {
                                IconButton(onClick = { isEditing = true }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.share_edit_title), tint = MimoNavyMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (isEditing) {
                            OutlinedTextField(
                                colors = logioFieldColors(),
                                value = title,
                                onValueChange = { title = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleSmall,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MimoNavy,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // AI hap etiketleri
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                category.chips.forEach { chip -> AiChip(chip) }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { onDone(title) },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = MimoPurple)
            ) {
                Text(stringResource(R.string.ok), fontWeight = FontWeight.SemiBold)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onUndo, enabled = !isSaving && !alreadySaved) {
                    Text(stringResource(R.string.undo), color = MimoNavyMuted)
                }
                TextButton(onClick = { onOpenJournal(title) }, enabled = !isSaving) {
                    Text(stringResource(R.string.share_see_in_journal), color = MimoPurple, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------- Ortak parçalar (zaman tüneli kartı da kullanır) ----------

@Composable
fun CategoryBadge(category: SharedLinkCategory) {
    Surface(shape = RoundedCornerShape(50), color = category.wash) {
        Text(
            text = "${category.emoji} ${stringResource(category.label)}",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = category.tint
        )
    }
}

@Composable
fun AiChip(@StringRes label: Int, compact: Boolean = false) {
    val isAi = label == R.string.chip_ai
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isAi) Color(0xFFF4EEFF) else Color(0xFFF6F5FA),
        border = BorderStroke(1.dp, if (isAi) Color(0xFFE4DBFF) else Color(0xFFECEAF3))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 11.dp, vertical = if (compact) 3.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isAi) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MimoPurple, modifier = Modifier.size(if (compact) 11.dp else 13.dp))
            }
            Text(
                text = stringResource(label),
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (isAi) MimoPurple else MimoNavyMuted
            )
        }
    }
}

@Composable
fun LinkThumbnail(thumbnailUrl: String?, category: SharedLinkCategory, size: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(category.wash)
    ) {
        Icon(Icons.Rounded.Link, contentDescription = null, tint = category.tint, modifier = Modifier.size((size / 2.6).dp))
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
