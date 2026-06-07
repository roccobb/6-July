package com.valkotassu.finnishwordoftheday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.valkotassu.finnishwordoftheday.ui.theme.FinnishWordOfTheDayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinnishWordOfTheDayTheme {
                Scaffold { innerPadding ->
                    WordOfTheDayRoute(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun WordOfTheDayRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wordsResult = remember { runCatching { loadWords(context) } }
    var dayKey by remember { mutableStateOf(todayKey()) }
    var selectedTab by remember { mutableStateOf(AppTab.Today) }
    var favoriteKeys by remember { mutableStateOf(loadFavoriteKeys(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            dayKey = todayKey()
        }
    }

    val words = wordsResult.getOrNull().orEmpty()
    val dailyWord = remember(words, dayKey) {
        words.takeIf { it.isNotEmpty() }?.let { chooseDailyWord(it, dayKey) }
    }
    val favoriteWords = remember(words, favoriteKeys) {
        words.filter { it.favoriteKey in favoriteKeys }
    }

    WordOfTheDayScreen(
        dailyWord = dailyWord,
        favoriteWords = favoriteWords,
        selectedTab = selectedTab,
        dateLabel = todayLabel(),
        isDailyWordFavorite = dailyWord?.favoriteKey in favoriteKeys,
        errorMessage = wordsResult.exceptionOrNull()?.localizedMessage,
        onTabSelected = { selectedTab = it },
        onToggleFavorite = { word ->
            favoriteKeys = favoriteKeys.toggle(word.favoriteKey)
            saveFavoriteKeys(context, favoriteKeys)
        },
        onShareWord = { word -> shareWord(context, word) },
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun WordOfTheDayScreen(
    dailyWord: DailyWord?,
    favoriteWords: List<DailyWord>,
    selectedTab: AppTab,
    dateLabel: String,
    isDailyWordFavorite: Boolean,
    errorMessage: String?,
    onTabSelected: (AppTab) -> Unit,
    onToggleFavorite: (DailyWord) -> Unit,
    onShareWord: (DailyWord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = AppTab.values()
    val pagerState = rememberPagerState(
        initialPage = selectedTab.ordinal,
        pageCount = { tabs.size },
    )

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab.ordinal) {
            pagerState.animateScrollToPage(selectedTab.ordinal)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> onTabSelected(tabs[page]) }
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Finnish Word of the Day",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(22.dp))

                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { onTabSelected(tab) },
                            text = { Text(tab.label) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    when (tabs[page]) {
                        AppTab.Today -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (dailyWord == null) {
                                EmptyWordCard(errorMessage = errorMessage)
                            } else {
                                DailyWordCard(
                                    dailyWord = dailyWord,
                                    isFavorite = isDailyWordFavorite,
                                    onToggleFavorite = { onToggleFavorite(dailyWord) },
                                    onShareWord = { onShareWord(dailyWord) },
                                )
                            }
                        }

                        AppTab.Favorites -> FavoriteWordsList(
                            favoriteWords = favoriteWords,
                            onToggleFavorite = onToggleFavorite,
                        )

                        AppTab.About -> AboutScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyWordCard(
    dailyWord: DailyWord,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onShareWord: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = dailyWord.word,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = dailyWord.partOfSpeech,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                    if (onToggleFavorite != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FavoriteButton(
                            isFavorite = isFavorite,
                            onClick = onToggleFavorite,
                        )
                        if (onShareWord != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ShareButton(onClick = onShareWord)
                        }
                    }
                }
            }

            dailyWord.ipa?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            dailyWord.definitions.forEachIndexed { index, definition ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                }
                Text(
                    text = definition,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            dailyWord.example?.let { example ->
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Example",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = example.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = example.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
    ) {
        Text(
            text = if (isFavorite) "★" else "☆",
            style = MaterialTheme.typography.headlineSmall,
            color = if (isFavorite) {
                FavoriteYellow
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ShareButton(onClick: () -> Unit) {
    val iconColor = MaterialTheme.colorScheme.tertiary

    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
    ) {
        ShareIcon(color = iconColor)
    }
}

@Composable
private fun ShareIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val strokeWidth = 2.dp.toPx()
        val radius = 3.2.dp.toPx()
        val left = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.5f)
        val topRight = androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 0.25f)
        val bottomRight = androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 0.75f)

        drawLine(
            color = color,
            start = left,
            end = topRight,
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = left,
            end = bottomRight,
            strokeWidth = strokeWidth,
        )
        listOf(left, topRight, bottomRight).forEach { center ->
            drawCircle(
                color = color,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

@Composable
private fun FavoriteWordsList(
    favoriteWords: List<DailyWord>,
    onToggleFavorite: (DailyWord) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (favoriteWords.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "No favorite words yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap the star on today's word to save it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = favoriteWords,
            key = { it.favoriteKey },
        ) { word ->
            FavoriteWordRow(
                dailyWord = word,
                onRemove = { onToggleFavorite(word) },
            )
        }
    }
}

@Composable
private fun FavoriteWordRow(
    dailyWord: DailyWord,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 14.dp)
            ) {
                Text(
                    text = dailyWord.word,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dailyWord.partOfSpeech,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = dailyWord.definitions.firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            FavoriteButton(isFavorite = true, onClick = onRemove)
        }
    }
}

@Composable
private fun EmptyWordCard(errorMessage: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No words available",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun AboutScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Finnish Word of the Day is an offline vocabulary app by Valkotassu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Dictionary data",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Definitions, pronunciations, and examples are derived from Wiktionary contributors via Kaikki.org/Wiktextract. This app is not affiliated with or endorsed by those projects.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Privacy",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The app works offline and does not collect personal data. Favorites are stored locally on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private enum class AppTab(val label: String) {
    Today("Today"),
    Favorites("Favorites"),
    About("About"),
}

private val FavoriteYellow = Color(0xFFFFC107)

@Preview(showBackground = true)
@Composable
private fun WordOfTheDayPreview() {
    FinnishWordOfTheDayTheme {
        WordOfTheDayScreen(
            dailyWord = DailyWord(
                word = "aalto",
                partOfSpeech = "noun",
                definitions = listOf("wave (on the surface of a liquid)", "wave"),
                ipa = "/ˈɑːlto/",
                example = Example(
                    text = "flunssa-aalto",
                    translation = "seasonal flu outbreak",
                ),
            ),
            favoriteWords = emptyList(),
            selectedTab = AppTab.Today,
            dateLabel = "May 24",
            isDailyWordFavorite = false,
            errorMessage = null,
            onTabSelected = {},
            onToggleFavorite = {},
            onShareWord = {},
        )
    }
}
