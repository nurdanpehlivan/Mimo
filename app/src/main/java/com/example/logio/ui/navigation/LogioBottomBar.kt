package com.example.logio.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.logio.ui.theme.MimoPurple
import com.example.logio.R
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource

// ---------- Yüzen alt gezinme barı ----------

enum class BottomTab(@StringRes val label: Int, val icon: ImageVector) {
    JOURNAL(R.string.tab_journal, Icons.Rounded.AutoStories),
    CALENDAR(R.string.tab_calendar, Icons.Rounded.CalendarMonth),
    STATS(R.string.tab_stats, Icons.Rounded.Insights),
    PROFILE(R.string.tab_profile, Icons.Rounded.Person)
}

private val BarBorderColor = Color(0xFFEDE8F7)

@Composable
fun FloatingBottomBar(
    selectedTab: BottomTab,
    onTabClick: (BottomTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp, top = 4.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            color = Color.White,
            border = BorderStroke(1.dp, BarBorderColor),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomTab.entries.forEach { tab ->
                    BottomTabItem(
                        tab = tab,
                        selected = tab == selectedTab,
                        onClick = { onTabClick(tab) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) MimoPurple.copy(alpha = 0.12f) else Color.Transparent,
        label = "tabBackground"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) MimoPurple else Color(0xFF9AA0C0),
        label = "tabTint"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .animateContentSize()
            .padding(horizontal = if (selected) 16.dp else 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(tab.icon, contentDescription = stringResource(tab.label), tint = tint, modifier = Modifier.size(22.dp))
        if (selected) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(tab.label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1
            )
        }
    }
}
