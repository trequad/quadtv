package net.trequad.quadtv.core.cache

import android.content.SharedPreferences
import net.trequad.quadtv.player.BufferConfig
import net.trequad.quadtv.player.BufferStrategy
import net.trequad.quadtv.player.PlayerEngine

data class PlayerSettings(
    val defaultEngine: PlayerEngine = PlayerEngine.VLC,
    val bufferConfig: BufferConfig = BufferConfig(sizeSeconds = 30, strategy = BufferStrategy.ADAPTIVE),
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null
)

class PlayerSettingsCache(
    private val sharedPreferences: SharedPreferences
) {
    fun save(settings: PlayerSettings) {
        sharedPreferences.edit()
            .putString(KEY_DEFAULT_ENGINE, settings.defaultEngine.name)
            .putInt(KEY_BUFFER_SECONDS, settings.bufferConfig.sizeSeconds)
            .putString(KEY_BUFFER_STRATEGY, settings.bufferConfig.strategy.name)
            .putString(KEY_AUDIO_LANGUAGE, settings.preferredAudioLanguage)
            .putString(KEY_SUBTITLE_LANGUAGE, settings.preferredSubtitleLanguage)
            .apply()
    }

    fun load(): PlayerSettings {
        val defaultEngine = sharedPreferences.getString(KEY_DEFAULT_ENGINE, null)
            ?.let { if (it == "EXOPLAYER") PlayerEngine.VLC else runCatching { PlayerEngine.valueOf(it) }.getOrNull() }
            ?: PlayerEngine.VLC
        val strategy = sharedPreferences.getString(KEY_BUFFER_STRATEGY, null)
            ?.let { runCatching { BufferStrategy.valueOf(it) }.getOrNull() }
            ?: BufferStrategy.ADAPTIVE
        return PlayerSettings(
            defaultEngine = defaultEngine,
            bufferConfig = BufferConfig(
                sizeSeconds = sharedPreferences.getInt(KEY_BUFFER_SECONDS, 30),
                strategy = strategy
            ),
            preferredAudioLanguage = sharedPreferences.getString(KEY_AUDIO_LANGUAGE, null),
            preferredSubtitleLanguage = sharedPreferences.getString(KEY_SUBTITLE_LANGUAGE, null)
        )
    }

    companion object {
        const val PREFERENCES_NAME = "quadtv_player_settings"
        private const val KEY_DEFAULT_ENGINE = "default_engine"
        private const val KEY_BUFFER_SECONDS = "buffer_seconds"
        private const val KEY_BUFFER_STRATEGY = "buffer_strategy"
        private const val KEY_AUDIO_LANGUAGE = "audio_language"
        private const val KEY_SUBTITLE_LANGUAGE = "subtitle_language"
    }
}
