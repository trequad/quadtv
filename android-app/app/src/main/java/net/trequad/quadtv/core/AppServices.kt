package net.trequad.quadtv.core

import android.content.Context
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.adminapi.AdminConfigRepository
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.cache.LaunchConfigCache
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.epg.EpgRepository
import net.trequad.quadtv.jellyfin.JellyfinAccessProvider
import net.trequad.quadtv.jellyfin.JellyfinRepository
import net.trequad.quadtv.live.LiveTvRepository
import net.trequad.quadtv.provider.ProviderFeedRepository
import net.trequad.quadtv.seerr.SeerrSessionRepository
import net.trequad.quadtv.vod.VodRepository

/**
 * One place to build shared network stacks and repositories, so every screen
 * stops hand-wiring OkHttp/Retrofit/Moshi (PRODUCT_AUDIT.md A5).
 */
object AppServices {
    val okHttpClient by lazy { NetworkModule.provideOkHttpClient() }
    val moshi by lazy { NetworkModule.provideMoshi() }
    val retrofit by lazy { NetworkModule.provideRetrofit(okHttpClient, moshi) }
    val api: AdminApiService by lazy { retrofit.create(AdminApiService::class.java) }

    @Volatile
    private var jellyfinAccess: JellyfinAccessProvider? = null

    fun sessionCache(context: Context): CustomerSessionCache =
        CustomerSessionCache(
            context.applicationContext.getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        )

    fun launchConfigCache(context: Context): LaunchConfigCache =
        LaunchConfigCache(
            context.applicationContext.getSharedPreferences(LaunchConfigCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        )

    fun configRepository(context: Context): AdminConfigRepository =
        AdminConfigRepository(api, launchConfigCache(context))

    fun providerFeedRepository(context: Context): ProviderFeedRepository =
        ProviderFeedRepository(api, sessionCache(context))

    fun jellyfinAccessProvider(context: Context): JellyfinAccessProvider =
        jellyfinAccess ?: synchronized(this) {
            jellyfinAccess ?: JellyfinAccessProvider(api, sessionCache(context)).also { jellyfinAccess = it }
        }

    fun jellyfinRepository(context: Context): JellyfinRepository =
        JellyfinRepository(configRepository(context), okHttpClient, moshi, jellyfinAccessProvider(context))

    fun liveTvRepository(context: Context): LiveTvRepository =
        LiveTvRepository(providerFeedRepository(context), okHttpClient)

    fun epgRepository(context: Context): EpgRepository =
        EpgRepository(providerFeedRepository(context), okHttpClient)

    fun vodRepository(context: Context): VodRepository =
        VodRepository(configRepository(context), okHttpClient, moshi, providerFeedRepository(context))

    fun seerrSessionRepository(context: Context): SeerrSessionRepository =
        SeerrSessionRepository(api, sessionCache(context))

    /** Clear per-login caches (call on sign-out or account switch). */
    fun onSessionCleared() {
        jellyfinAccess?.clear()
    }
}
