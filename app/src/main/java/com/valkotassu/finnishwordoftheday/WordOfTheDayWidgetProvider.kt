package com.valkotassu.finnishwordoftheday

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class WordOfTheDayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_FAVORITE) {
            currentDailyWord(context)?.let { word ->
                saveFavoriteKeys(context, loadFavoriteKeys(context).toggle(word.favoriteKey))
            }
        }
        updateAllWidgets(context)
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_word_of_day)
        val word = runCatching { currentDailyWord(context) }.getOrNull()

        if (word == null) {
            views.setTextViewText(R.id.widget_date, todayLabel())
            views.setTextViewText(R.id.widget_word, context.getString(R.string.widget_empty_title))
            views.setTextViewText(R.id.widget_part_of_speech, "")
            views.setTextViewText(R.id.widget_definition, context.getString(R.string.widget_empty_body))
            views.setImageViewResource(R.id.widget_favorite, R.drawable.ic_star_outline)
            views.setOnClickPendingIntent(R.id.widget_share, openAppPendingIntent(context))
        } else {
            val isFavorite = word.favoriteKey in loadFavoriteKeys(context)
            views.setTextViewText(R.id.widget_date, todayLabel())
            views.setTextViewText(R.id.widget_word, word.word)
            views.setTextViewText(R.id.widget_part_of_speech, word.partOfSpeech)
            views.setTextViewText(R.id.widget_definition, word.definitions.firstOrNull().orEmpty())
            views.setImageViewResource(
                R.id.widget_favorite,
                if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
            )
            views.setOnClickPendingIntent(R.id.widget_share, sharePendingIntent(context, word))
        }

        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        views.setOnClickPendingIntent(R.id.widget_favorite, toggleFavoritePendingIntent(context))
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, WordOfTheDayWidgetProvider::class.java)
        onUpdate(context, appWidgetManager, appWidgetManager.getAppWidgetIds(componentName))
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, REQUEST_OPEN_APP, intent, pendingIntentFlags())
    }

    private fun sharePendingIntent(context: Context, word: DailyWord): PendingIntent {
        val intent = shareWordChooserIntent(word).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(context, REQUEST_SHARE_WORD, intent, pendingIntentFlags())
    }

    private fun toggleFavoritePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WordOfTheDayWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_FAVORITE
        }
        return PendingIntent.getBroadcast(context, REQUEST_TOGGLE_FAVORITE, intent, pendingIntentFlags())
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    private companion object {
        private const val ACTION_TOGGLE_FAVORITE =
            "com.valkotassu.finnishwordoftheday.action.TOGGLE_FAVORITE"
        private const val REQUEST_OPEN_APP = 1
        private const val REQUEST_SHARE_WORD = 2
        private const val REQUEST_TOGGLE_FAVORITE = 3
    }
}
