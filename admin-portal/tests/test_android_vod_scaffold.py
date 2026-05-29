from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_vod_models_cover_categories_titles_series_episodes_metadata_and_mature_flags():
    source = read_android("vod/VodModels.kt")

    assert 'data class VodCategory' in source
    assert 'id: String' in source
    assert 'name: String' in source
    assert 'data class VodItem' in source
    assert 'title: String' in source
    assert 'posterUrl: String?' in source
    assert 'description: String?' in source
    assert 'rating: String?' in source
    assert 'releaseYear: Int?' in source
    assert 'streamUrl: String?' in source
    assert 'isSeries: Boolean' in source
    assert 'isMature: Boolean' in source
    assert 'data class VodEpisode' in source
    assert 'seasonNumber: Int' in source
    assert 'episodeNumber: Int' in source


def test_vod_repository_uses_portal_launch_vod_endpoint_and_okhttp_for_categories_and_items():
    source = read_android("vod/VodRepository.kt")

    assert 'class VodRepository' in source
    assert 'AdminConfigRepository' in source
    assert 'OkHttpClient' in source
    assert 'Moshi' in source
    assert 'suspend fun loadCategories(): List<VodCategory>' in source
    assert 'suspend fun loadRecentlyAdded(): List<VodItem>' in source
    assert 'suspend fun loadItems(categoryId: String): List<VodItem>' in source
    assert 'suspend fun loadEpisodes(seriesId: String): List<VodEpisode>' in source
    assert 'vodProviderBaseUrl' in source
    assert 'Request.Builder().url' in source
    assert 'categoryId' in source
    assert 'seriesId' in source


def test_vod_browse_and_details_fragments_scaffold_leanback_rows_and_playback_handoff():
    browse = read_android("vod/VodBrowseFragment.kt")
    details = read_android("vod/VodDetailsFragment.kt")

    assert 'class VodBrowseFragment : BrowseSupportFragment()' in browse
    assert 'QuadTV On-Demand' in browse
    assert 'Recently Added VOD' in browse
    assert 'On-Demand Categories' in browse
    assert 'Browse categories' not in browse
    assert 'VodRepository' in browse
    assert 'R.color.quadmedia_blue' in browse

    assert 'class VodDetailsFragment : Fragment()' in details
    assert 'QuadTV Details' in details
    assert 'Poster art' in details
    assert 'metadata' in details.lower()
    assert 'playback handoff' in details.lower()
    assert 'StreamPlaybackRequest' in details
