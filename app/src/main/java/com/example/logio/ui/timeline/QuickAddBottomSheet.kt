package com.example.logio.ui.timeline

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logio.ui.theme.MimoNavy
import com.example.logio.R
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.example.logio.ui.theme.MimoNavyMuted
import com.example.logio.ui.theme.MimoPurple
import kotlinx.coroutines.launch

/** Menüdeki bir kutucuk: kayıt türü + menüde görünen ad ve kısa ipucu. [usesAi]: köşede "AI" etiketi gösterilir. */
private data class MenuTile(
    val action: QuickLogAction,
    @StringRes val label: Int = action.titleRes,
    @StringRes val hint: Int? = null,
    val usesAi: Boolean = false
)

private data class MenuPage(@StringRes val title: Int, val tiles: List<MenuTile>, val rows: Int, val tileHeight: Dp)

private const val GRID_COLUMNS = 4
private val TileGap = 10.dp

// İki sayfa aynı yükseklikte (2 x 125 + boşluk = 3 x 80 + 2 boşluk = 260dp): kaydırırken menü zıplamaz
private val MenuPages = listOf(
    MenuPage(
        title = R.string.menu_page_popular,
        rows = 2,
        tileHeight = 125.dp,
        tiles = listOf(
            MenuTile(QuickLogAction.LOG, R.string.tile_journal, R.string.tile_journal_hint),
            MenuTile(QuickLogAction.MOOD, R.string.action_mood, R.string.tile_mood_hint),
            MenuTile(QuickLogAction.PHOTO, R.string.action_photo, R.string.tile_photo_hint),
            MenuTile(QuickLogAction.MEAL, R.string.tile_meal, R.string.tile_meal_hint, usesAi = true),
            MenuTile(QuickLogAction.VOICE, R.string.tile_voice, R.string.tile_voice_hint, usesAi = true),
            MenuTile(QuickLogAction.WATER, R.string.action_water, R.string.tile_water_hint),
            MenuTile(QuickLogAction.STEPS, R.string.tile_steps, R.string.tile_steps_hint),
            MenuTile(QuickLogAction.SLEEP, R.string.action_sleep, R.string.tile_sleep_hint)
        )
    ),
    MenuPage(
        title = R.string.menu_page_more,
        rows = 3,
        tileHeight = 80.dp,
        tiles = listOf(
            MenuTile(QuickLogAction.LINK),
            MenuTile(QuickLogAction.LOCATION, R.string.tile_map),
            MenuTile(QuickLogAction.TODO, R.string.tile_tasks),
            MenuTile(QuickLogAction.EXPENSE),
            MenuTile(QuickLogAction.COFFEE),
            MenuTile(QuickLogAction.ALCOHOL),
            MenuTile(QuickLogAction.FILM),
            MenuTile(QuickLogAction.BOOK),
            MenuTile(QuickLogAction.MUSIC),
            MenuTile(QuickLogAction.DRAW),
            MenuTile(QuickLogAction.VIDEO),
            // Journey en sonda: diğer kayıtlardan üretilen bir özet olduğu için
            MenuTile(QuickLogAction.JOURNEY)
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddBottomSheet(
    onDismissRequest: () -> Unit,
    onActionClick: (QuickLogAction) -> Unit,
    onHealthSyncClick: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(pageCount = { MenuPages.size })
    val scope = rememberCoroutineScope()

    // Menü kapanma animasyonu bitince eylemi tetikle (pencereler üst üste binmesin)
    fun onTileClick(tile: MenuTile) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            // Adım için ayrı bir pencere yok: Health Connect eşitlemesini başlat
            if (tile.action == QuickLogAction.STEPS) onHealthSyncClick() else onActionClick(tile.action)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFFFFFBFE),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFFDCD6EE)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = stringResource(R.string.menu_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MimoNavy
                )
                Spacer(modifier = Modifier.height(4.dp))
                AnimatedContent(
                    targetState = MenuPages[pagerState.currentPage].title,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "menuPageTitle"
                ) { pageTitle ->
                    Text(stringResource(pageTitle), style = MaterialTheme.typography.bodyMedium, color = MimoNavyMuted)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 20.dp,
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                MenuGridPage(page = MenuPages[pageIndex], onTileClick = ::onTileClick)
            }

            Spacer(modifier = Modifier.height(16.dp))
            PagerDots(
                pageCount = MenuPages.size,
                currentPage = pagerState.currentPage,
                onDotClick = { scope.launch { pagerState.animateScrollToPage(it) } },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun MenuGridPage(page: MenuPage, onTileClick: (MenuTile) -> Unit) {
    val large = page.rows == 2
    Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
        page.tiles.chunked(GRID_COLUMNS).take(page.rows).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                rowTiles.forEach { tile ->
                    MenuTileItem(
                        tile = tile,
                        large = large,
                        onClick = { onTileClick(tile) },
                        modifier = Modifier.weight(1f).height(page.tileHeight)
                    )
                }
                repeat(GRID_COLUMNS - rowTiles.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MenuTileItem(
    tile: MenuTile,
    large: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val action = tile.action
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(action.bgColor.copy(alpha = if (large) 0.85f else 0.6f))
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (large) 48.dp else 38.dp)
                    .clip(RoundedCornerShape(if (large) 16.dp else 13.dp))
                    .background(Color.White.copy(alpha = 0.8f))
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = action.iconColor,
                    modifier = Modifier.size(if (large) 26.dp else 21.dp)
                )
            }
            Spacer(modifier = Modifier.height(if (large) 8.dp else 5.dp))
            Text(
                text = stringResource(tile.label),
                fontSize = if (large) 12.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF334155),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (large && tile.hint != null) {
                Text(
                    text = stringResource(tile.hint),
                    fontSize = 10.sp,
                    color = MimoNavyMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (tile.usesAi) {
            AiBadge(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
    }
}

/** Yapay zekâ kullanan kutuların köşesindeki küçük etiket. */
@Composable
private fun AiBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.badge_ai),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MimoPurple)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

/** Sayfa göstergesi: aktif sayfa uzun hap, diğerleri küçük nokta. */
@Composable
private fun PagerDots(
    pageCount: Int,
    currentPage: Int,
    onDotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(if (selected) 20.dp else 7.dp, label = "dotWidth")
            val color by animateColorAsState(if (selected) MimoPurple else Color(0xFFD9D4EA), label = "dotColor")
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onDotClick(index) }
            )
        }
    }
}
