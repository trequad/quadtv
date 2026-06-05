package net.trequad.quadtv.profiles

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.R
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.adminapi.DeviceRegistrationRepository
import net.trequad.quadtv.core.cache.ProfileSelectionCache
import net.trequad.quadtv.core.device.AppVersionProvider
import net.trequad.quadtv.core.device.DeviceIdentifierProvider
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute

private const val ADD_PROFILE_ID = -1

private val PROFILE_AVATAR_VALUES = listOf(
    "profile_avatar_raven",
    "profile_avatar_bear",
    "profile_avatar_wolf",
    "profile_avatar_fox",
    "profile_avatar_dragon",
    "profile_avatar_robot",
    "profile_avatar_star",
    "profile_avatar_lightning"
)

fun resolveAvatarResource(avatar: String): Int = when (avatar) {
    "profile_avatar_bear", "bear", "odin" -> R.drawable.profile_avatar_bear
    "profile_avatar_dragon", "dragon" -> R.drawable.profile_avatar_dragon
    "profile_avatar_fox", "fox", "loki" -> R.drawable.profile_avatar_fox
    "profile_avatar_lightning", "lightning", "bifrost" -> R.drawable.profile_avatar_lightning
    "profile_avatar_robot", "robot" -> R.drawable.profile_avatar_robot
    "profile_avatar_star", "star" -> R.drawable.profile_avatar_star
    "profile_avatar_wolf", "wolf" -> R.drawable.profile_avatar_wolf
    "add" -> R.drawable.profile_avatar_star
    else -> R.drawable.profile_avatar_raven
}

class ProfilePickerFragment : BrowseSupportFragment() {

    private lateinit var apiService: AdminApiService
    private lateinit var deviceRepo: DeviceRegistrationRepository
    private var deviceId: Int = -1
    private var profileCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Profiles"
        headersState = HEADERS_DISABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)

        val ctx = requireContext().applicationContext
        val retrofit = NetworkModule.provideRetrofit(
            NetworkModule.provideOkHttpClient(),
            NetworkModule.provideMoshi()
        )
        apiService = retrofit.create(AdminApiService::class.java)
        deviceRepo = DeviceRegistrationRepository(
            apiService,
            DeviceIdentifierProvider(ctx),
            AppVersionProvider(ctx)
        )

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val profile = item as? QuadTvProfile ?: return@OnItemViewClickedListener
            if (profile.id == ADD_PROFILE_ID) {
                showCreateProfileDialog()
            } else {
                ProfileSelectionCache(requireContext().getSharedPreferences(ProfileSelectionCache.PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)).save(profile.id)
                (requireActivity() as QuadTvNavigator).navigateTo(QuadTvRoute.HOME)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundResource(R.drawable.quadtv_neon_waves_background)
    }

    override fun onStart() {
        super.onStart()
        if (deviceId == -1) initDevice() else refreshProfiles()
    }

    private fun initDevice() {
        lifecycleScope.launch {
            try {
                val deviceName = Settings.Global.getString(
                    requireContext().contentResolver, "device_name"
                ) ?: android.os.Build.MODEL
                val registration = withContext(Dispatchers.IO) { deviceRepo.registerThisDevice(deviceName) }
                deviceId = registration.id
            } catch (e: Exception) { }
            refreshProfiles()
        }
    }

    private fun refreshProfiles() {
        lifecycleScope.launch {
            val profiles = try {
                if (deviceId != -1) withContext(Dispatchers.IO) { apiService.getDeviceProfiles(deviceId) }.items
                else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            profileCount = profiles.size
            val newProfileAdapter = ArrayObjectAdapter(ProfileCardPresenter()).apply {
                profiles.forEach { add(it) }
                add(QuadTvProfile(ADD_PROFILE_ID, deviceId, null, "+ Add Profile", "add", false))
            }
            adapter = ArrayObjectAdapter(ListRowPresenter()).apply {
                add(ListRow(HeaderItem(0, "Choose who is watching"), newProfileAdapter))
            }
        }
    }

    private fun showCreateProfileDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Enter a name"
            setSingleLine(true)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("New Profile")
            .setMessage("What should we call this profile?")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) createProfile(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createProfile(name: String) {
        if (deviceId == -1) return
        val avatar = nextAvatarForNewProfile()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.createProfile(deviceId, ProfileCreateRequest(displayName = name, avatar = avatar))
                }
            } catch (_: Exception) { }
            refreshProfiles()
        }
    }

    private fun nextAvatarForNewProfile(): String = PROFILE_AVATAR_VALUES[profileCount % PROFILE_AVATAR_VALUES.size]
}

class ProfileCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val density = parent.resources.displayMetrics.density
        val tile = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((20 * density).toInt(), (18 * density).toInt(), (20 * density).toInt(), (18 * density).toInt())
            setBackgroundColor(Color.rgb(13, 20, 38))
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val image = ImageView(parent.context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams((144 * density).toInt(), (144 * density).toInt())
        }
        val label = TextView(parent.context).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, (12 * density).toInt(), 0, 0)
            maxLines = 1
        }
        tile.addView(image)
        tile.addView(label)
        return ViewHolder(tile)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val profile = item as? QuadTvProfile ?: return
        val container = viewHolder.view as LinearLayout
        val image = container.getChildAt(0) as ImageView
        val label = container.getChildAt(1) as TextView
        image.setImageResource(resolveAvatarResource(profile.avatar))
        label.text = profile.displayName
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
