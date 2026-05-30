from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_jellyfin_models_cover_libraries_items_hls_streams_and_mature_flags():
    source = read_android("jellyfin/JellyfinModels.kt")

    assert 'data class JellyfinLibrary' in source
    assert 'id: String' in source
    assert 'name: String' in source
    assert 'collectionType: String?' in source
    assert 'data class JellyfinItem' in source
    assert 'title: String' in source
    assert 'posterUrl: String?' in source
    assert 'overview: String?' in source
    assert 'contentRating: String?' in source
    assert 'productionYear: Int?' in source
    assert 'isFolder: Boolean' in source
    assert 'isMature: Boolean' in source
    assert 'data class JellyfinStream' in source
    assert 'hlsUrl: String' in source


def test_jellyfin_repository_uses_portal_config_api_key_and_okhttp_for_libraries_items_streams():
    source = read_android("jellyfin/JellyfinRepository.kt")

    assert 'class JellyfinRepository' in source
    assert 'AdminConfigRepository' in source
    assert 'OkHttpClient' in source
    assert 'Moshi' in source
    assert 'suspend fun loadLibraries(): List<JellyfinLibrary>' in source
    assert 'suspend fun loadItems(libraryId: String): List<JellyfinItem>' in source
    assert 'suspend fun buildHlsStream(itemId: String): JellyfinStream?' in source
    assert 'jellyfinBaseUrl' in source
    assert 'X-Emby-Token' in source
    assert 'Items' in source
    assert 'Videos' in source
    assert 'm3u8' in source


def test_jellyfin_browse_and_details_fragments_scaffold_home_section_and_playback_handoff():
    browse = read_android("jellyfin/JellyfinBrowseFragment.kt")
    details = read_android("jellyfin/JellyfinDetailsFragment.kt")

    assert 'class JellyfinBrowseFragment : BrowseSupportFragment()' in browse
    assert 'QuadTV Jellyfin' in browse
    assert 'Jellyfin Libraries' in browse
    assert 'Loading Jellyfin' in browse
    assert 'Recently Added from Jellyfin' in browse
    assert 'JellyfinCardPresenter' in browse
    assert 'JellyfinRepository' in browse
    assert 'R.color.quadmedia_blue' in browse

    assert 'class JellyfinDetailsFragment : Fragment()' in details
    assert 'Jellyfin Details' in details
    assert 'HLS stream delivery' in details
    assert 'playback handoff' in details.lower()
    assert 'StreamPlaybackRequest' in details
