package net.trequad.quadtv.profiles

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.EditText
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
import net.trequad.quadtv.core.device.AppVersionProvider
import net.trequad.quadtv.core.device.DeviceIdentifierProvider
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute

private const val ADD_PROFILE_ID = -1

class ProfilePickerFragment : BrowseSupportFragment() {

    private lateinit var apiService: AdminApiService
    private lateinit var deviceRepo: DeviceRegistrationRepository
    private var deviceId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Who's watching?"
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
                (requireActivity() as QuadTvNavigator).navigateTo(QuadTvRoute.HOME)
            }
        }
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
            val newProfileAdapter = ArrayObjectAdapter(ProfileCardPresenter()).apply {
                profiles.forEach { add(it) }
                add(QuadTvProfile(ADD_PROFILE_ID, deviceId, "+ Add Profile", "add", false))
            }
            adapter = ArrayObjectAdapter(ListRowPresenter()).apply {
                add(ListRow(HeaderItem(0, "Select a profile"), newProfileAdapter))
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
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.createProfile(deviceId, ProfileCreateRequest(displayName = name))
                }
            } catch (_: Exception) { }
            refreshProfiles()
        }
    }
}

class ProfileCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = TextView(parent.context).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(48, 36, 48, 36)
            setBackgroundColor(Color.rgb(44, 95, 124))
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val profile = item as? QuadTvProfile
        (viewHolder.view as TextView).text = profile?.displayName ?: ""
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
