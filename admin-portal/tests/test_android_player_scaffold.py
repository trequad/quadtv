from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_player_contract_defines_engine_selection_buffering_and_stream_request():
    source = read_android("player/PlayerContract.kt")

    assert 'enum class PlayerEngine' in source
    assert 'EXOPLAYER' in source
    assert 'VLC' in source
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


def test_player_settings_cache_persists_default_engine_and_buffer_preferences():
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
    assert 'PlayerEngine.EXOPLAYER' in source
    assert 'BufferStrategy.ADAPTIVE' in source


def test_exoplayer_and_vlc_controllers_are_bundled_placeholders_not_external_apps():
    exo = read_android("player/ExoPlayerController.kt")
    vlc = read_android("player/VlcPlayerController.kt")

    assert 'class ExoPlayerController' in exo
    assert 'QuadTvPlayer' in exo
    assert 'androidx.media3.exoplayer.ExoPlayer' in exo
    assert 'androidx.media3.common.MediaItem' in exo
    assert 'setMediaItem(MediaItem.fromUri(request.url))' in exo
    assert 'prepare()' in exo
    assert 'play()' in exo

    assert 'class VlcPlayerController' in vlc
    assert 'QuadTvPlayer' in vlc
    assert 'org.videolan.libvlc.LibVLC' in vlc
    assert 'org.videolan.libvlc.MediaPlayer' in vlc
    assert 'Media(libVLC, Uri.parse(request.url))' in vlc
    assert 'mediaPlayer.play()' in vlc
    assert 'Intent(' not in exo
    assert 'Intent(' not in vlc


def test_playback_failure_handler_selects_alternate_bundled_player_on_failure():
    source = read_android("player/PlaybackFailureHandler.kt")

    assert 'class PlaybackFailureHandler' in source
    assert 'fun alternateEngine(failedEngine: PlayerEngine): PlayerEngine' in source
    assert 'PlayerEngine.EXOPLAYER -> PlayerEngine.VLC' in source
    assert 'PlayerEngine.VLC -> PlayerEngine.EXOPLAYER' in source
    assert 'fun shouldOfferRetryWithAlternate' in source
    assert 'return true' in source
