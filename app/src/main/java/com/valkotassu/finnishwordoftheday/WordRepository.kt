package com.valkotassu.finnishwordoftheday

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.json.JSONArray

private const val FAVORITES_PREFS = "favorite_words"
private const val FAVORITES_KEY = "favorite_keys"

data class DailyWord(
    val word: String,
    val partOfSpeech: String,
    val definitions: List<String>,
    val ipa: String?,
    val example: Example?,
) {
    val favoriteKey: String
        get() = "$word|$partOfSpeech"
}

data class Example(
    val text: String,
    val translation: String,
)

fun loadWords(context: Context): List<DailyWord> {
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

fun chooseDailyWord(words: List<DailyWord>, dayKey: Int): DailyWord {
    val mixed = dayKey.toLong() * 1_103_515_245L + 12_345L
    val index = (mixed % words.size).toInt()
    return words[index]
}

fun currentDailyWord(context: Context): DailyWord? {
    return loadWords(context).takeIf { it.isNotEmpty() }?.let { chooseDailyWord(it, todayKey()) }
}

fun todayKey(): Int {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
    return year * 1000 + dayOfYear
}

fun todayLabel(): String {
    return SimpleDateFormat("MMMM d", Locale.ENGLISH).format(Calendar.getInstance().time)
}

fun loadFavoriteKeys(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
    return prefs.getStringSet(FAVORITES_KEY, emptySet()).orEmpty().toSet()
}

fun saveFavoriteKeys(context: Context, favoriteKeys: Set<String>) {
    val prefs = context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
    prefs.edit().putStringSet(FAVORITES_KEY, favoriteKeys).apply()
}

fun shareWord(context: Context, dailyWord: DailyWord) {
    context.startActivity(shareWordChooserIntent(dailyWord))
}

fun shareWordChooserIntent(dailyWord: DailyWord): Intent {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Finnish Word of the Day: ${dailyWord.word}")
        putExtra(Intent.EXTRA_TEXT, dailyWord.shareText())
    }
    return Intent.createChooser(sendIntent, "Share word")
}

fun DailyWord.shareText(): String {
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

fun Set<String>.toggle(value: String): Set<String> {
    return if (value in this) this - value else this + value
}
