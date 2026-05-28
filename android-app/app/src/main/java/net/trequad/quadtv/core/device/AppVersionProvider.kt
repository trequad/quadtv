package net.trequad.quadtv.core.device

import android.content.Context
import android.os.Build

class AppVersionProvider(context: Context) {
    val versionName: String = try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.versionName ?: "unknown"
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionName ?: "unknown"
        }
    } catch (_: Exception) {
        "unknown"
    }
}
