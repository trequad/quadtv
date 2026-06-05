package net.trequad.quadtv.updates

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class UpdatePromptFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val forced = requireArguments().getBoolean(ARG_FORCED, false)
        val versionName = requireArguments().getString(ARG_VERSION_NAME, "")
        val changelog = requireArguments().getString(ARG_CHANGELOG, "")
        val apkUrl = requireArguments().getString(ARG_APK_URL, "")

        val title = if (forced) "Update Required" else "QuadTV Update Available"
        val versionLabel = if (versionName.isNotBlank()) "Version $versionName" else ""

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
        }

        val titleView = TextView(requireContext()).apply {
            text = title
            textSize = 30f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        root.addView(titleView)

        if (versionLabel.isNotBlank()) {
            root.addView(TextView(requireContext()).apply {
                text = versionLabel
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 8)
            })
        }

        if (changelog.isNotBlank()) {
            root.addView(TextView(requireContext()).apply {
                text = changelog
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 24)
            })
        }

        val statusText = TextView(requireContext()).apply {
            text = ""
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        root.addView(statusText)

        val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progressBar)

        val downloadButton = Button(requireContext()).apply {
            text = "Download & Install Update"
            setOnClickListener {
                if (apkUrl.isBlank()) {
                    statusText.text = "Update URL unavailable. Contact your QuadTV operator."
                    return@setOnClickListener
                }
                isEnabled = false
                progressBar.visibility = View.VISIBLE
                startDownloadAndInstall(apkUrl, statusText, progressBar, this)
            }
        }
        root.addView(downloadButton)

        if (!forced) {
            root.addView(Button(requireContext()).apply {
                text = "Continue to QuadTV"
                setOnClickListener {
                    (activity as? QuadTvNavigator)?.navigateTo(QuadTvRoute.LOGIN)
                }
                setPadding(0, 16, 0, 0)
            })
        }

        return root
    }

    private fun startDownloadAndInstall(
        apkUrl: String,
        statusText: TextView,
        progressBar: ProgressBar,
        downloadButton: Button
    ) {
        lifecycleScope.launch {
            statusText.text = "Downloading update…"
            val result = withContext(Dispatchers.IO) { downloadApk(apkUrl, progressBar) }
            if (result == null) {
                statusText.text = "Download failed. Check your connection and try again."
                progressBar.visibility = View.GONE
                downloadButton.isEnabled = true
                return@launch
            }
            statusText.text = "Download complete. Starting installation…"
            triggerInstall(result)
        }
    }

    private suspend fun downloadApk(url: String, progressBar: ProgressBar): File? {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body ?: return null

            val destFile = File(requireContext().getExternalFilesDir(null), "quadtv-update.apk")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val progress = (downloadedBytes * 100 / totalBytes).toInt()
                            withContext(Dispatchers.Main) { progressBar.progress = progress }
                        }
                    }
                }
            }
            destFile
        } catch (_: Exception) {
            null
        }
    }

    private fun triggerInstall(apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            // Install intent failed — leave APK on disk so user can sideload manually
        }
    }

    companion object {
        private const val ARG_FORCED = "forced"
        private const val ARG_VERSION_NAME = "version_name"
        private const val ARG_CHANGELOG = "changelog"
        private const val ARG_APK_URL = "apk_url"

        fun forStatus(status: UpdateStatus): UpdatePromptFragment {
            val release = status.release
            return UpdatePromptFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_FORCED, status.forcedUpdateRequired)
                    putString(ARG_VERSION_NAME, release?.versionName ?: "")
                    putString(ARG_CHANGELOG, release?.changelog ?: "")
                    putString(ARG_APK_URL, release?.apkUrl ?: "")
                }
            }
        }

        fun forced() = UpdatePromptFragment().apply {
            arguments = Bundle().apply {
                putBoolean(ARG_FORCED, true)
                putString(ARG_VERSION_NAME, "")
                putString(ARG_CHANGELOG, "")
                putString(ARG_APK_URL, "")
            }
        }

        fun optional() = UpdatePromptFragment().apply {
            arguments = Bundle().apply {
                putBoolean(ARG_FORCED, false)
                putString(ARG_VERSION_NAME, "")
                putString(ARG_CHANGELOG, "")
                putString(ARG_APK_URL, "")
            }
        }
    }
}
