from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
CONFIG = ANDROID_SRC / "core/config/QuadTvConfig.kt"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_text(path):
    return Path(path).read_text()


def test_android_config_hardcodes_operator_vod_provider_base_url():
    source = read_text(CONFIG)

    assert "OPERATOR_VOD_PROVIDER_BASE_URL" in source
    assert 'OPERATOR_VOD_PROVIDER_BASE_URL = "https://livinitup.online"' in source
    assert "VOD_DNS_ENDPOINT = OPERATOR_VOD_PROVIDER_BASE_URL" in source
    assert "provider username" not in source.lower()
    assert "provider password" not in source.lower()


def test_launch_config_defaults_continue_to_use_android_constants():
    source = read_text(ANDROID_SRC / "adminapi/LaunchConfig.kt")

    assert "vodEndpoint = QuadTvConfig.VOD_DNS_ENDPOINT" in source
    assert "liveTvEndpoint = QuadTvConfig.LIVE_TV_DNS_ENDPOINT" in source
    assert "xmltvEndpoint = QuadTvConfig.LIVE_TV_XMLTV_ENDPOINT" in source


def test_docs_record_provider_endpoint_without_raw_test_credentials():
    combined_docs = README.read_text() + "\n" + PLAN.read_text()

    assert "Operator-controlled provider endpoint constants" in combined_docs
    assert "https://livinitup.online" in combined_docs
    assert "test credentials are not committed" in combined_docs
    assert "provider username/password" in combined_docs
    assert "7248659130" not in combined_docs
    assert "bobjack" not in combined_docs.lower()


def test_android_sources_do_not_define_test_account_credentials():
    combined_source = "\n".join(path.read_text() for path in ANDROID_SRC.rglob("*.kt"))

    assert "TEST_ACCOUNT" not in combined_source
    assert "TEST_USERNAME" not in combined_source
    assert "TEST_PASSWORD" not in combined_source
    assert "bobjack" not in combined_source.lower()
    assert "7248659130" not in combined_source
