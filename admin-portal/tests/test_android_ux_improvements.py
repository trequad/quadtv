from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


# ---------------------------------------------------------------------------
# 1. Portrait poster cards (VOD + Jellyfin)
# ---------------------------------------------------------------------------

def test_vod_browse_uses_vertical_list_layout():
    vod = read_android("vod/VodBrowseFragment.kt")
    assert "ScrollView" in vod
    assert "itemRow" in vod


def test_jellyfin_browse_uses_vertical_list_layout():
    jelly = read_android("jellyfin/JellyfinBrowseFragment.kt")
    assert "ScrollView" in jelly
    assert "itemRow" in jelly


# ---------------------------------------------------------------------------
# 2. D-pad focus states on cards and buttons
# ---------------------------------------------------------------------------

def test_vod_cards_have_focus_state_highlight():
    vod = read_android("vod/VodBrowseFragment.kt")
    assert "setOnFocusChangeListener" in vod
    # Colors come from the shared design tokens since the 2026-07 theme sweep.
    assert "QuadTvTheme.ACCENT" in vod


def test_jellyfin_cards_have_focus_state_highlight():
    jelly = read_android("jellyfin/JellyfinBrowseFragment.kt")
    assert "setOnFocusChangeListener" in jelly


def test_home_action_cards_have_focus_state_with_size_change():
    home = read_android("home/HomeFragment.kt")
    assert "setOnFocusChangeListener" in home
    assert "setOnFocusChangeListener" in home


def test_live_tv_group_buttons_have_focus_state():
    live = read_android("live/LiveTvFragment.kt")
    assert "setOnFocusChangeListener" in live


def test_live_tv_channel_cards_have_focus_state():
    live = read_android("live/LiveTvFragment.kt")
    assert "QuadTvTheme.FOCUS" in live  # focused background color token


# ---------------------------------------------------------------------------
# 3. Favorite badges on browse cards
# ---------------------------------------------------------------------------

def test_vod_cards_show_star_badge_for_favorited_items():
    vod = read_android("vod/VodBrowseFragment.kt")
    assert "isFavorited" in vod
    assert "MediaBookmarkStore" in vod
    assert '★' in vod or '"★ "' in vod or "favPrefix" in vod


def test_jellyfin_cards_show_star_badge_for_favorited_items():
    jelly = read_android("jellyfin/JellyfinBrowseFragment.kt")
    assert "isFavorited" in jelly
    assert "MediaBookmarkStore" in jelly
    assert "favPrefix" in jelly


# ---------------------------------------------------------------------------
# 4. Channel logos + channel numbers in Live TV
# ---------------------------------------------------------------------------

def test_live_channel_model_has_channel_number_field():
    channel = read_android("live/LiveChannel.kt")
    assert "val channelNumber: Int? = null" in channel


def test_m3u_parser_parses_tvg_chno():
    parser = read_android("live/M3uParser.kt")
    assert "tvg-chno" in parser
    assert "channelNumber" in parser
    assert "toIntOrNull()" in parser


def test_live_tv_channel_card_shows_first_letter_placeholder():
    live = read_android("live/LiveTvFragment.kt")
    assert "logoPlaceholder" in live
    assert "channel.name.firstOrNull()" in live


def test_live_tv_fragment_shows_channel_number_prefix():
    live = read_android("live/LiveTvFragment.kt")
    assert "Ch. " in live
    assert "channelNumber" in live


# ---------------------------------------------------------------------------
# 5. Plain English error messages
# ---------------------------------------------------------------------------

def test_live_tv_uses_plain_english_error():
    live = read_android("live/LiveTvFragment.kt")
    assert "Check your Wi-Fi and try again" in live
    assert "Check the portal endpoint config and network connection" not in live


def test_vod_uses_plain_english_error():
    vod = read_android("vod/VodBrowseFragment.kt")
    assert "Can't load On-Demand right now" in vod
    assert "check your Wi-Fi" in vod


def test_jellyfin_uses_plain_english_error():
    jelly = read_android("jellyfin/JellyfinBrowseFragment.kt")
    assert "Can't load QuadOnDemand right now" in jelly
    assert "check Wi-Fi" in jelly


# ---------------------------------------------------------------------------
# 6. Home screen: On Now row + Continue Watching + voice search
# ---------------------------------------------------------------------------

def test_home_has_on_now_row():
    home = read_android("home/HomeFragment.kt")
    assert '"Live TV"' in home
    assert "Live Now" in home
    assert "currentProgrammesByChannel" in home


def test_home_has_continue_watching_row():
    home = read_android("home/HomeFragment.kt")
    assert '"Recently Watched Movies"' in home
    assert "recentItems()" in home
    assert "mediaStore.recentItems()" in home


def test_home_has_voice_search_wired_to_movie_search():
    home = read_android("home/HomeFragment.kt")
    assert "Movie Search" not in home


def test_home_on_now_row_loads_async_without_blocking_ui():
    home = read_android("home/HomeFragment.kt")
    assert "lifecycleScope.launch" in home
    assert "withContext(Dispatchers.IO)" in home
    assert "liveTvRepository" in home
    assert "epgRepository" in home


# ---------------------------------------------------------------------------
# 7. Home button in player control bar
# ---------------------------------------------------------------------------

def test_player_control_row_has_home_button():
    player = read_android("player/PlayerFragment.kt")
    assert "⌂ Home" in player or '"Home"' in player
    assert "QuadTvRoute.HOME" in player
    assert "QuadTvNavigator" in player


# ---------------------------------------------------------------------------
# 8. Onboarding fragment (first-launch guide)
# ---------------------------------------------------------------------------

def test_onboarding_cache_exists_with_completed_flag():
    cache = read_android("core/cache/OnboardingCache.kt")
    assert "class OnboardingCache" in cache
    assert "isCompleted()" in cache
    assert "markCompleted()" in cache
    assert 'PREFERENCES_NAME = "quadtv_onboarding"' in cache


def test_onboarding_fragment_has_three_pages_with_skip_and_get_started():
    onboarding = read_android("onboarding/OnboardingFragment.kt")
    assert "Watch Live TV" in onboarding
    assert "Movies" in onboarding
    assert "Favorites" in onboarding
    assert "Get Started" in onboarding
    assert "Skip" in onboarding
    assert "markCompleted()" in onboarding
    assert "QuadTvRoute.LOGIN" in onboarding


def test_onboarding_fragment_explains_requests_and_quaddemand():
    onboarding = read_android("onboarding/OnboardingFragment.kt")
    # Customer copy says "Requests"; Seerr is an internal service name.
    assert "Seerr" not in onboarding
    assert "Request Movies & Shows" in onboarding
    assert "Approved requests appear in QuadOnDemand" in onboarding
    assert "QuadOnDemand" in onboarding


def test_onboarding_route_exists_in_navigator():
    nav = read_android("navigation/QuadTvNavigator.kt")
    assert "ONBOARDING" in nav


def test_main_activity_shows_onboarding_on_first_launch():
    main = read_android("MainActivity.kt")
    assert "OnboardingCache" in main
    assert "isCompleted()" in main
    assert "QuadTvRoute.ONBOARDING" in main
    assert "OnboardingFragment()" in main


# ---------------------------------------------------------------------------
# 9. EpgUtils shared across fragments
# ---------------------------------------------------------------------------

def test_epg_utils_provides_shared_current_programmes_helper():
    utils = read_android("live/EpgUtils.kt")
    assert "fun List<EpgProgramme>.currentProgrammesByChannel" in utils
    assert "ProfileSelectionCache" not in utils  # pure utility, no profile scope


def test_live_tv_fragment_imports_epg_utils():
    live = read_android("live/LiveTvFragment.kt")
    assert "currentProgrammesByChannel" in live


def test_home_fragment_imports_epg_utils():
    home = read_android("home/HomeFragment.kt")
    assert "currentProgrammesByChannel" in home
