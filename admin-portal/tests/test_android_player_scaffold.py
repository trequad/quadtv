from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
APP_DIR = PROJECT_ROOT / "android-app/app"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_player_contract_defines_vlc_first_engine_selection_buffering_and_stream_request():
    source = read_android("player/PlayerContract.kt")

    assert 'enum class PlayerEngine' in source
    assert 'VLC' in source
    assert 'MX_PLAYER' not in source
    assert 'EXOPLAYER' not in source
    assert 'PlayerView' not in source
    assert 'enum class BufferStrategy' in source
    assert 'ADAPTIVE' in source
    assert 'AGGRESSIVE_PREBUFFER' in source
    assert 'data class BufferConfig' in source
    assert 'sizeSeconds: Int' in source
    assert 'strategy: BufferStrategy' in source
    assert 'data class StreamPlaybackRequest' in source
    assert 'url: String' in source
    assert 'title: String?' in source
    assert 'isLive: Boolean' in source
    assert 'preferredAudioLanguage: String?' in source
    assert 'preferredSubtitleLanguage: String?' in source
    assert 'interface QuadTvPlayer' in source
    assert 'fun play(request: StreamPlaybackRequest)' in source
    assert 'fun stop()' in source
    assert 'fun release()' in source


def test_player_settings_cache_defaults_to_vlc_and_migrates_old_exoplayer_preference():
    source = read_android("core/cache/PlayerSettingsCache.kt")

    assert 'class PlayerSettingsCache' in source
    assert 'SharedPreferences' in source
    assert 'fun save(settings: PlayerSettings)' in source
    assert 'fun load(): PlayerSettings' in source
    assert 'data class PlayerSettings' in source
    assert 'val defaultEngine: PlayerEngine' in source
    assert 'val bufferConfig: BufferConfig' in source
    assert 'KEY_DEFAULT_ENGINE' in source
    assert 'KEY_BUFFER_SECONDS' in source
    assert 'KEY_BUFFER_STRATEGY' in source
    assert 'PlayerEngine.VLC' in source
    assert 'it == "EXOPLAYER"' in source
    assert 'BufferStrategy.ADAPTIVE' in source


def test_vlc_is_the_only_bundled_controller_and_media3_exoplayer_is_removed():
    vlc = read_android("player/VlcPlayerController.kt")
    build = (APP_DIR / "build.gradle.kts").read_text()

    assert not (ANDROID_SRC / "player/ExoPlayerController.kt").exists()
    assert 'media3-exoplayer' not in build
    assert 'media3-ui' not in build
    assert 'class VlcPlayerController' in vlc
    assert 'QuadTvPlayer' in vlc
    assert 'org.videolan.libvlc.LibVLC' in vlc
    assert 'org.videolan.libvlc.MediaPlayer' in vlc
    assert 'val url = request.url.trim()' in vlc
    assert 'Media(libVLC, Uri.parse(url))' in vlc
    assert 'media.setHWDecoderEnabled(false, false)' in vlc
    assert ':network-caching=' in vlc
    assert 'mediaPlayer.play()' in vlc
    assert 'Intent(' not in vlc


def test_playback_failure_handler_has_no_external_player_fallback_without_exoplayer_loop():
    source = read_android("player/PlaybackFailureHandler.kt")

    assert 'class PlaybackFailureHandler' in source
    assert 'fun alternateEngine(failedEngine: PlayerEngine): PlayerEngine? = null' in source
    assert 'MX_PLAYER' not in source
    assert 'EXOPLAYER' not in source
    assert 'fun shouldOfferRetryWithAlternate' not in source
