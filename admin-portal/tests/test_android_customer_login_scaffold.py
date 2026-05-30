from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_admin_api_service_declares_customer_login_endpoint_and_models_map_response_shape():
    service = read_android("adminapi/AdminApiService.kt")
    models = read_android("auth/CustomerLoginModels.kt")

    assert '@POST("api/v1/auth/customer-login")' in service
    assert 'suspend fun customerLogin(@Body request: CustomerLoginRequest): CustomerLoginResponse' in service
    assert 'data class CustomerLoginRequest' in models
    assert 'val username: String' in models
    assert 'val password: String' in models
    assert 'data class CustomerLoginResponse' in models
    assert '@Json(name = "access_token") val accessToken: String?' in models
    assert '@Json(name = "token_type") val tokenType: String' in models
    assert '@Json(name = "user_id") val userId: Int' in models
    assert '@Json(name = "provider_username") val providerUsername: String' in models
    assert 'val expired: Boolean' in models
    assert '@Json(name = "expires_on") val expiresOn: String?' in models
    assert '@Json(name = "days_remaining") val daysRemaining: Int?' in models
    assert 'val message: String?' in models


def test_customer_auth_repository_calls_customer_login_and_caches_valid_bearer_session_only():
    repository = read_android("auth/CustomerAuthRepository.kt")
    cache = read_android("core/cache/CustomerSessionCache.kt")

    assert 'class CustomerAuthRepository' in repository
    assert 'suspend fun login(username: String, password: String): CustomerLoginResponse' in repository
    assert 'apiService.customerLogin(CustomerLoginRequest(username = username, password = password))' in repository
    assert 'if (!response.expired && response.accessToken != null)' in repository
    assert 'sessionCache.save(response)' in repository
    assert 'sessionCache.clear()' in repository
    assert 'class CustomerSessionCache' in cache
    assert 'SharedPreferences' in cache
    assert 'fun save(response: CustomerLoginResponse)' in cache
    assert 'putString(KEY_ACCESS_TOKEN, response.accessToken)' in cache
    assert 'putInt(KEY_USER_ID, response.userId)' in cache
    assert 'putString(KEY_PROVIDER_USERNAME, response.providerUsername)' in cache
    assert 'fun load(): CachedCustomerSession?' in cache
    assert 'data class CachedCustomerSession' in cache
    assert 'fun clear()' in cache


def test_customer_login_and_expired_subscription_fragments_provide_branded_launch_flow():
    login = read_android("auth/CustomerLoginFragment.kt")
    expired = read_android("auth/ExpiredSubscriptionFragment.kt")
    main = read_android("MainActivity.kt")
    navigator = read_android("navigation/QuadTvNavigator.kt")

    assert 'class CustomerLoginFragment : Fragment()' in login
    assert 'QuadTV Login' in login
    assert 'Provider username' in login
    assert 'Provider password' in login
    assert 'Subscription Expired' in expired
    assert 'Please contact QuadMedia' in expired
    assert 'class ExpiredSubscriptionFragment : Fragment()' in expired
    assert 'CustomerLoginFragment()' in main
    assert 'ExpiredSubscriptionFragment()' in main
    assert 'navigateTo(QuadTvRoute.LOGIN)' in main
    assert 'QuadTvRoute.EXPIRED -> ExpiredSubscriptionFragment()' in main
    assert 'QuadTvRoute.PROFILES -> ProfilePickerFragment()' in main
    assert 'EXPIRED' in navigator


def test_customer_login_fragment_submits_provider_credentials_and_routes_real_results():
    login = read_android("auth/CustomerLoginFragment.kt")

    assert 'private lateinit var usernameInput: EditText' in login
    assert 'private lateinit var passwordInput: EditText' in login
    assert 'private lateinit var statusText: TextView' in login
    assert 'private lateinit var continueButton: Button' in login
    assert 'private lateinit var authRepository: CustomerAuthRepository' in login
    assert 'lifecycleScope.launch' in login
    assert 'withContext(Dispatchers.IO)' in login
    assert 'authRepository.login(username, password)' in login
    assert 'continueButton.isEnabled = false' in login
    assert 'continueButton.isEnabled = true' in login
    assert 'if (response.expired)' in login
    assert 'navigator.navigateTo(QuadTvRoute.EXPIRED)' in login
    assert 'navigator.navigateTo(QuadTvRoute.PROFILES)' in login
    assert 'statusText.text = "Enter your username and password."' in login
    assert 'statusText.text = "Signing in…"' in login
    assert 'statusText.text = "Login failed. Check your credentials and try again."' in login


def test_customer_login_fragment_builds_repository_from_real_retrofit_and_session_cache():
    login = read_android("auth/CustomerLoginFragment.kt")

    assert 'NetworkModule.provideOkHttpClient()' in login
    assert 'NetworkModule.provideMoshi()' in login
    assert 'NetworkModule.provideRetrofit(okHttpClient, moshi)' in login
    assert 'retrofit.create(AdminApiService::class.java)' in login
    assert 'CustomerSessionCache.PREFERENCES_NAME' in login
    assert 'CustomerAuthRepository(apiService, CustomerSessionCache(preferences))' in login
