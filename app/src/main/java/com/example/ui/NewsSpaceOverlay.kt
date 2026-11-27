package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coreui.DotMatrixDivider
import com.example.coreui.NothingPillChip
import com.example.coreui.NothingSurfaceCard
import com.example.customwidgets.NewsCategory
import com.example.customwidgets.NewsRepository
import com.example.settingsstore.model.LauncherSettings
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import kotlinx.coroutines.launch

@Composable
fun NewsSpaceOverlay(
    settings: LauncherSettings,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    val palette = LocalNothingPalette.current
    val scope = rememberCoroutineScope()
    val feedState by NewsRepository.state.collectAsState()

    LaunchedEffect(Unit) {
        NewsRepository.refresh(force = false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .then(if (!settings.fullScreenHideStatusBar) Modifier.statusBarsPadding() else Modifier.padding(top = 12.dp))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Newspaper, null, tint = palette.accent, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(
                        text = "DOTOS NEWS SPACE",
                        fontFamily = NothingTypographyEngine.ndot55Family,
                        fontSize = 17.sp,
                        color = palette.textPrimary
                    )
                    Text(
                        text = if (feedState.isLoading) "Updating live headlines..." else "${feedState.articles.size} stories • ${feedState.activeCategory.displayName}",
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }
            }
            Row {
                IconButton(onClick = { scope.launch { NewsRepository.refresh(force = true) } }) {
                    Icon(Icons.Rounded.Refresh, "Refresh News", tint = palette.accent)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Close News Space", tint = palette.textPrimary)
                }
            }
        }

        // Category Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NewsCategory.entries.forEach { cat ->
                NothingPillChip(
                    label = cat.displayName,
                    selected = feedState.activeCategory == cat,
                    onClick = {
                        scope.launch { NewsRepository.selectCategory(cat) }
                    }
                )
            }
        }

        DotMatrixDivider()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = feedState.articles,
                key = { it.link + it.title }
            ) { article ->
                NothingSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(article.link)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        },
                    cornerRadius = 20.dp,
                    backgroundColor = palette.surface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(palette.accent.copy(alpha = 0.16f))
                                    .border(1.dp, palette.accent.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = article.source,
                                    fontFamily = NothingTypographyEngine.ndot55Family,
                                    fontSize = 9.5.sp,
                                    color = palette.accent
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = article.pubDate,
                                    fontFamily = NothingTypographyEngine.ntype82Family,
                                    fontSize = 10.sp,
                                    color = palette.textSecondary
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "Open Story",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Text(
                            text = article.title,
                            fontFamily = NothingTypographyEngine.ntype82Family,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )

                        if (article.summary.isNotBlank()) {
                            Text(
                                text = article.summary,
                                fontSize = 12.5.sp,
                                color = palette.textSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
