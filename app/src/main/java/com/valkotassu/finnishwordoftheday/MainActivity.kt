package com.valkotassu.finnishwordoftheday

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.valkotassu.finnishwordoftheday.ui.theme.FinnishWordOfTheDayTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay
import org.json.JSONArray

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
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    AppTab.values().forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { onTabSelected(tab) },
                            text = { Text(tab.label) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                when (selectedTab) {
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
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (isFavorite) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
    ) {
        Text(
            text = if (isFavorite) "★" else "☆",
            style = MaterialTheme.typography.headlineSmall,
            color = if (isFavorite) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ShareButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Text(
            text = "↗",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
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

private data class DailyWord(
    val word: String,
    val partOfSpeech: String,
    val definitions: List<String>,
    val ipa: String?,
    val example: Example?,
) {
    val favoriteKey: String
        get() = "$word|$partOfSpeech"
}

private data class Example(
    val text: String,
    val translation: String,
)

private enum class AppTab(val label: String) {
    Today("Today"),
    Favorites("Favorites"),
    About("About"),
}

private const val FAVORITES_PREFS = "favorite_words"
private const val FAVORITES_KEY = "favorite_keys"

private fun loadWords(context: Context): List<DailyWord> {
    val json = context.assets.open("words.json").bufferedReader().use { it.readText() }
    val array = JSONArray(json)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val definitionsArray = item.getJSONArray("definitions")
            val definitions = buildList {
                for (definitionIndex in 0 until definitionsArray.length()) {
                    add(definitionsArray.getString(definitionIndex))
                }
            }
            add(
                DailyWord(
                    word = item.getString("word"),
                    partOfSpeech = item.getString("partOfSpeech"),
                    definitions = definitions,
                    ipa = item.optString("ipa").takeIf { it.isNotBlank() },
                    example = item.optJSONObject("example")?.let { example ->
                        Example(
                            text = example.getString("text"),
                            translation = example.getString("translation"),
                        )
                    },
                )
            )
        }
    }
}

private fun chooseDailyWord(words: List<DailyWord>, dayKey: Int): DailyWord {
    val mixed = dayKey.toLong() * 1_103_515_245L + 12_345L
    val index = (mixed % words.size).toInt()
    return words[index]
}

private fun todayKey(): Int {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
    return year * 1000 + dayOfYear
}

private fun todayLabel(): String {
    return SimpleDateFormat("MMMM d", Locale.ENGLISH).format(Calendar.getInstance().time)
}

private fun loadFavoriteKeys(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
    return prefs.getStringSet(FAVORITES_KEY, emptySet()).orEmpty().toSet()
}

private fun saveFavoriteKeys(context: Context, favoriteKeys: Set<String>) {
    val prefs = context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
    prefs.edit().putStringSet(FAVORITES_KEY, favoriteKeys).apply()
}

private fun shareWord(context: Context, dailyWord: DailyWord) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Finnish Word of the Day: ${dailyWord.word}")
        putExtra(Intent.EXTRA_TEXT, dailyWord.shareText())
    }
    val chooser = Intent.createChooser(sendIntent, "Share word")
    context.startActivity(chooser)
}

private fun DailyWord.shareText(): String {
    return buildString {
        appendLine("Today's Finnish word: $word")
        ipa?.let { appendLine(it) }
        appendLine(partOfSpeech)
        definitions.forEach { definition ->
            appendLine("- $definition")
        }
        example?.let {
            appendLine()
            appendLine("Example:")
            appendLine(it.text)
            appendLine(it.translation)
        }
    }.trim()
}

private fun Set<String>.toggle(value: String): Set<String> {
    return if (value in this) this - value else this + value
}

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
