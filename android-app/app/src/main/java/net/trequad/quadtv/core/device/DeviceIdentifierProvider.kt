package net.trequad.quadtv.core.device

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.UUID

class DeviceIdentifierProvider(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val contentResolver = context.contentResolver

    fun getOrCreateDeviceIdentifier(): String {
        sharedPreferences.getString(KEY_DEVICE_IDENTIFIER, null)?.let { return it }
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val generated = if (androidId.isNullOrBlank()) {
            UUID.randomUUID().toString()
        } else {
            "android-tv-$androidId"
        }
        sharedPreferences.edit().putString(KEY_DEVICE_IDENTIFIER, generated).apply()
        return generated
    }

    companion object {
        private const val PREFERENCES_NAME = "quadtv_device_identity"
        private const val KEY_DEVICE_IDENTIFIER = "device_identifier"
    }
}
