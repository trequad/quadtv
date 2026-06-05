from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_epg_guide_uses_top_group_bar_and_now_playing_programme_tunes_channel():
    source = read_android("epg/EpgGridFragment.kt")

    assert "topGroupBar" in source
    assert "loadChannelGroups" in source
    assert "selectedGroup" in source
    assert "tuneToProgramme" in source
    assert "LiveTvPlaybackCoordinator" in source
    assert "navigateToPlayer" in source
    assert "currentContentTitle = programme.title" in source


def test_vod_and_jellyfin_browse_offer_alphabet_and_year_jump_rails():
    vod_source = read_android("vod/VodBrowseFragment.kt")
    jellyfin_source = read_android("jellyfin/JellyfinBrowseFragment.kt")

    for source in (vod_source, jellyfin_source):
        assert "jumpRailContainer" in source
        assert "buildJumpRail" in source
        assert "jumpToLetter" in source
        assert "jumpToReleaseYear" in source
        assert "A-Z" in source
        assert "Year" in source
        assert "scrollToPositionWithOffset(position, 0)" in source
        assert "requestFocus()" in source
        assert "jumpSortKey" in source
        assert "removePrefix(\"The \")" in source


def test_vod_series_details_drills_into_seasons_and_episodes_before_playback():
    repo_source = read_android("vod/VodRepository.kt")
    details_source = read_android("vod/VodDetailsFragment.kt")
    models_source = read_android("vod/VodModels.kt")

    assert "data class VodSeason" in models_source
    assert "loadEpisodes(seriesId" in repo_source
    assert "get_series_info" in repo_source
    assert "seriesPlaybackUrl" in repo_source
    assert "episodesBySeason" in repo_source
    assert "showSeriesSeasons" in details_source
    assert "showEpisodesOverlay" in details_source
    assert "android.app.AlertDialog.Builder(requireContext())" in details_source
    assert "setTitle(seasonLabel(season))" in details_source
    assert "Season ${season.seasonNumber}" in details_source
    assert "playEpisode" in details_source
    assert "Episode ${episode.episodeNumber}" in details_source
    assert "season.episodes.forEach" not in details_source


def test_jellyfin_series_details_drills_into_seasons_and_episodes_before_playback():
    repo_source = read_android("jellyfin/JellyfinRepository.kt")
    details_source = read_android("jellyfin/JellyfinDetailsFragment.kt")
    models_source = read_android("jellyfin/JellyfinModels.kt")

    assert "data class JellyfinSeason" in models_source
    assert "data class JellyfinEpisode" in models_source
    assert "loadSeasons(seriesId" in repo_source
    assert "loadEpisodes(seriesId" in repo_source
    assert "Shows/$seriesId/Seasons" in repo_source
    assert "ParentId=${season.id}" in repo_source
    assert "showSeriesSeasons" in details_source
    assert "showEpisodesOverlay" in details_source
    assert "android.app.AlertDialog.Builder(requireContext())" in details_source
    assert "setTitle(seasonLabel(season))" in details_source
    assert "Season ${season.seasonNumber}" in details_source
    assert "playEpisode" in details_source
