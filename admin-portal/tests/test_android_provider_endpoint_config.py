from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
CONFIG = ANDROID_SRC / "core/config/QuadTvConfig.kt"
MANIFEST = PROJECT_ROOT / "android-app/app/src/main/AndroidManifest.xml"
NETWORK_SECURITY_CONFIG = PROJECT_ROOT / "android-app/app/src/main/res/xml/network_security_config.xml"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_text(path):
    return Path(path).read_text()


def test_android_config_hardcodes_operator_provider_base_urls():
    source = read_text(CONFIG)

    assert "OPERATOR_LIVE_TV_PROVIDER_BASE_URL" in source
    assert 'OPERATOR_LIVE_TV_PROVIDER_BASE_URL = "http://ahhshitherewegoagain.sytes.net"' in source
    assert "LIVE_TV_DNS_ENDPOINT = OPERATOR_LIVE_TV_PROVIDER_BASE_URL" in source
    assert "PROVIDER_FEED_REFRESH_HOURS = 24" in source
    assert "OPERATOR_VOD_PROVIDER_BASE_URL" in source
    assert 'OPERATOR_VOD_PROVIDER_BASE_URL = "https://livinitup.online"' in source
    assert "VOD_DNS_ENDPOINT = OPERATOR_VOD_PROVIDER_BASE_URL" in source
    assert "provider username" not in source.lower()
    assert "provider password" not in source.lower()


def test_launch_config_defaults_continue_to_use_android_constants():
    source = read_text(ANDROID_SRC / "adminapi/LaunchConfig.kt")

    assert "vodProviderBaseUrl = QuadTvConfig.OPERATOR_VOD_PROVIDER_BASE_URL" in source
    assert "liveTvProviderBaseUrl = QuadTvConfig.OPERATOR_LIVE_TV_PROVIDER_BASE_URL" in source
    assert "providerFeedRefreshHours = QuadTvConfig.PROVIDER_FEED_REFRESH_HOURS" in source


def test_android_network_security_allows_only_required_http_hosts():
    manifest = read_text(MANIFEST)
    network_config = read_text(NETWORK_SECURITY_CONFIG)

    assert 'android:networkSecurityConfig="@xml/network_security_config"' in manifest
    assert '<domain includeSubdomains="false">ahhshitherewegoagain.sytes.net</domain>' in network_config
    assert "10.34.1.194" not in network_config
    assert "<domain includeSubdomains=\"false\">10.34.1.192</domain>" in network_config
    assert 'cleartextTrafficPermitted="true"' in network_config
    assert "livinitup.online" not in network_config
    assert "bobjack" not in network_config.lower()
    assert "7248659130" not in network_config


def test_public_readme_omits_raw_test_credentials_and_operator_details():
    public_readme = README.read_text()

    assert "admin portal credentials" in public_readme
    assert "live hostnames" in public_readme
    assert "internal IPs" in public_readme
    assert "7248659130" not in public_readme
    assert "bobjack" not in public_readme.lower()
    assert "10.34." not in public_readme
    assert "example.invalid" not in public_readme


def test_android_sources_do_not_define_test_account_credentials():
    combined_source = "\n".join(path.read_text() for path in ANDROID_SRC.rglob("*.kt"))

    assert "TEST_ACCOUNT" not in combined_source
    assert "TEST_USERNAME" not in combined_source
    assert "TEST_PASSWORD" not in combined_source
    assert "bobjack" not in combined_source.lower()
    assert "7248659130" not in combined_source
