from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_admin_api_service_declares_device_registration_and_profile_endpoints():
    source = read_android("adminapi/AdminApiService.kt")

    assert 'import retrofit2.http.Body' in source
    assert 'import retrofit2.http.Path' in source
    assert '@POST("api/v1/devices/register")' in source
    assert 'suspend fun registerDevice(@Body request: DeviceRegistrationRequest): DeviceRegistrationResponse' in source
    assert '@GET("api/v1/devices/{deviceId}/profiles")' in source
    assert 'suspend fun getDeviceProfiles(@Path("deviceId") deviceId: Int): ProfileListResponse' in source


def test_device_registration_models_map_portal_snake_case_and_expired_state():
    source = read_android("adminapi/DeviceRegistration.kt")

    assert 'data class DeviceRegistrationRequest' in source
    assert '@Json(name = "device_identifier") val deviceIdentifier: String' in source
    assert '@Json(name = "device_name") val deviceName: String' in source
    assert '@Json(name = "app_version") val appVersion: String?' in source
    assert 'data class DeviceRegistrationResponse' in source
    assert '@Json(name = "user_id") val userId: Int?' in source
    assert '@Json(name = "max_profiles_per_device") val maxProfilesPerDevice: Int' in source
    assert '@Json(name = "live_stream_limit_per_user") val liveStreamLimitPerUser: Int' in source
    assert '@Json(name = "vod_stream_limit_per_user") val vodStreamLimitPerUser: Int' in source
    assert '@Json(name = "jellyfin_stream_limit_per_user") val jellyfinStreamLimitPerUser: Int' in source
    assert 'val expired: Boolean' in source
    assert 'val active: Boolean' in source


def test_device_registration_repository_uses_stable_device_identifier_and_registers_app_version():
    source = read_android("adminapi/DeviceRegistrationRepository.kt")
    identifier = read_android("core/device/DeviceIdentifierProvider.kt")

    assert 'class DeviceRegistrationRepository' in source
    assert 'suspend fun registerThisDevice' in source
    assert 'apiService.registerDevice' in source
    assert 'DeviceRegistrationRequest(' in source
    assert 'deviceIdentifier = deviceIdentifierProvider.getOrCreateDeviceIdentifier()' in source
    assert 'appVersion = appVersionProvider.versionName' in source
    assert 'class DeviceIdentifierProvider' in identifier
    assert 'Settings.Secure.ANDROID_ID' in identifier
    assert 'UUID.randomUUID().toString()' in identifier
    assert 'SharedPreferences' in identifier


def test_profile_models_and_picker_fragment_exist_for_launch_flow():
    models = read_android("profiles/ProfileModels.kt")
    picker = read_android("profiles/ProfilePickerFragment.kt")
    main = read_android("MainActivity.kt")

    assert 'data class QuadTvProfile' in models
    assert '@Json(name = "display_name") val displayName: String' in models
    assert '@Json(name = "parental_enabled") val parentalEnabled: Boolean' in models
    assert 'data class ProfileListResponse' in models
    assert 'class ProfilePickerFragment : BrowseSupportFragment()' in picker
    assert 'QuadTV Profiles' in picker
    assert 'Choose who is watching' in picker
    assert 'ProfileCardPresenter' in picker
    assert 'ProfilePickerFragment()' in main
    assert 'HomeFragment()' not in main
