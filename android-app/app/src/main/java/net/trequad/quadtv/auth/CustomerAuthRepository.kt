package net.trequad.quadtv.auth

import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.core.cache.CustomerSessionCache

class CustomerAuthRepository(
    private val apiService: AdminApiService,
    private val sessionCache: CustomerSessionCache
) {
    suspend fun login(username: String, password: String): CustomerLoginResponse {
        val response = apiService.customerLogin(CustomerLoginRequest(username = username, password = password))
        if (!response.expired && response.accessToken != null) {
            sessionCache.save(response)
        } else {
            sessionCache.clear()
        }
        return response
    }

    fun cachedSession() = sessionCache.load()

    fun logout() = sessionCache.clear()
}
