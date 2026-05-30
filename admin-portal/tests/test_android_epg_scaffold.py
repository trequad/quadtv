from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_epg_programme_model_contains_xmltv_channel_timing_metadata_and_mature_flag():
    source = read_android("epg/EpgProgramme.kt")

    assert 'data class EpgProgramme' in source
    assert 'channelId: String' in source
    assert 'title: String' in source
    assert 'description: String?' in source
    assert 'startTimeMillis: Long' in source
    assert 'endTimeMillis: Long' in source
    assert 'category: String?' in source
    assert 'rating: String?' in source
    assert 'isMature: Boolean' in source
    assert 'val durationMillis' in source


def test_xmltv_parser_extracts_programmes_titles_descriptions_categories_and_ratings():
    source = read_android("epg/XmlTvParser.kt")

    assert 'class XmlTvParser' in source
    assert 'fun parse(xml: String): List<EpgProgramme>' in source
    assert 'XmlPullParserFactory' in source
    assert 'programme' in source
    assert 'channel' in source
    assert 'start' in source
    assert 'stop' in source
    assert 'title' in source
    assert 'desc' in source
    assert 'category' in source
    assert 'rating' in source
    assert 'parseXmlTvTime' in source
    assert 'isMatureProgramme' in source


def test_epg_repository_fetches_xmltv_from_launch_config_and_groups_by_channel():
    source = read_android("epg/EpgRepository.kt")

    assert 'class EpgRepository' in source
    assert 'OkHttpClient' in source
    assert 'ProviderFeedRepository' in source
    assert 'XmlTvParser' in source
    assert 'suspend fun loadProgrammes(): List<EpgProgramme>' in source
    assert 'feed.xmltvUrl' in source
    assert 'Request.Builder().url' in source
    assert 'parser.parse' in source
    assert 'fun programmesForChannel' in source
    assert 'channelId' in source


def test_epg_grid_fragment_scaffolds_cable_style_time_axis_channel_rows_and_preview_panel():
    source = read_android("epg/EpgGridFragment.kt")

    assert 'class EpgGridFragment : BrowseSupportFragment()' in source
    assert 'QuadTV Guide' in source
    assert 'time axis' in source.lower()
    assert 'channel rows' in source.lower()
    assert 'program blocks' in source.lower()
    assert 'preview panel' in source.lower()
    assert 'D-pad' in source
    assert 'R.color.quadmedia_blue' in source
    assert 'R.color.quadtv_navy' in source
