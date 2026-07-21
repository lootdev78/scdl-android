package io.github.lootdev.scdl

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import io.github.lootdev.scdl.runtime.YoutubeDL
import io.github.lootdev.scdl.core.Scdl
import java.io.File
import java.util.concurrent.Executors

class SettingsActivity : AppCompatActivity() {
    private lateinit var preferences: AppPreferences
    private lateinit var statusText: TextView
    private lateinit var basePathInput: EditText
    private lateinit var separateFoldersSwitch: SwitchCompat
    private lateinit var aria2cSwitch: SwitchCompat
    private lateinit var clientIdInput: EditText
    private lateinit var authTokenInput: EditText
    private lateinit var nameFormatInput: EditText
    private lateinit var playlistNameFormatInput: EditText
    private lateinit var rawConfigInput: EditText

    private val executor = Executors.newSingleThreadExecutor()
    private var runtimeReady = false
    private var scdlConfigPath: File? = null

    private val folderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val path = rawPathFromTreeUri(uri)
            if (path == null) {
                showStatus("This folder cannot be represented as a normal filesystem path. Choose a folder on primary internal storage.")
            } else {
                basePathInput.setText(path)
                showStatus("Selected: $path")
            }
        }
    }

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(SoundCloudLoginActivity.EXTRA_CLIENT_ID)
                ?.takeIf { it.isNotBlank() }
                ?.let { clientIdInput.setText(it) }
            result.data?.getStringExtra(SoundCloudLoginActivity.EXTRA_AUTH_TOKEN)
                ?.takeIf { it.isNotBlank() }
                ?.let { authTokenInput.setText(it) }
            showStatus("Captured SoundCloud credentials. Save settings to write them to scdl.cfg.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        preferences = AppPreferences(this)
        statusText = findViewById(R.id.settingsStatus)
        basePathInput = findViewById(R.id.basePathInput)
        separateFoldersSwitch = findViewById(R.id.separateFoldersSwitch)
        aria2cSwitch = findViewById(R.id.aria2cSwitch)
        clientIdInput = findViewById(R.id.clientIdInput)
        authTokenInput = findViewById(R.id.authTokenInput)
        nameFormatInput = findViewById(R.id.nameFormatInput)
        playlistNameFormatInput = findViewById(R.id.playlistNameFormatInput)
        rawConfigInput = findViewById(R.id.rawConfigInput)

        basePathInput.setText(preferences.baseDownloadPath)
        separateFoldersSwitch.isChecked = preferences.useSeparateFolders
        aria2cSwitch.isChecked = preferences.useAria2c

        findViewById<Button>(R.id.browsePathButton).setOnClickListener { folderLauncher.launch(null) }
        findViewById<Button>(R.id.resetPathButton).setOnClickListener {
            basePathInput.setText(AppPreferences.defaultBaseDirectory().absolutePath)
        }
        findViewById<Button>(R.id.soundCloudLoginButton).setOnClickListener {
            loginLauncher.launch(Intent(this, SoundCloudLoginActivity::class.java))
        }
        findViewById<Button>(R.id.saveSettingsButton).setOnClickListener { saveSettings() }
        findViewById<Button>(R.id.updateYtDlpButton).setOnClickListener { updateYtDlp() }
        findViewById<Button>(R.id.updateScdlButton).setOnClickListener { updateScdl() }
        findViewById<Button>(R.id.closeSettingsButton).setOnClickListener { finish() }

        initializeAndLoad()
    }

    private fun initializeAndLoad() {
        showStatus("Loading scdl.cfg and runtime...")
        executor.execute {
            try {
                val file = Scdl.prepareConfiguration(applicationContext)
                scdlConfigPath = file
                val config = ScdlConfigFile(file)
                val values = config.readValues()
                val raw = config.readText()
                val runtimeError = runCatching { Scdl.init(applicationContext) }.exceptionOrNull()
                runtimeReady = runtimeError == null
                runOnUiThread {
                    clientIdInput.setText(values.clientId)
                    authTokenInput.setText(values.authToken)
                    nameFormatInput.setText(values.nameFormat)
                    playlistNameFormatInput.setText(values.playlistNameFormat)
                    rawConfigInput.setText(raw)
                    if (runtimeReady) {
                        showVersions("Settings loaded")
                    } else {
                        showStatus("scdl.cfg loaded. Runtime unavailable until the arm64 native files are added: ${rootMessage(runtimeError!!)}")
                    }
                }
            } catch (error: Exception) {
                runtimeReady = false
                runOnUiThread { showStatus("Could not load scdl.cfg: ${rootMessage(error)}") }
            }
        }
    }

    private fun saveSettings() {
        val basePath = basePathInput.text.toString().trim()
        if (basePath.isEmpty()) {
            showStatus("Enter a base download directory.")
            return
        }
        val separateFolders = separateFoldersSwitch.isChecked
        val useAria2c = aria2cSwitch.isChecked
        val rawConfig = rawConfigInput.text.toString()
        val values = ScdlConfigFile.Values(
            clientId = clientIdInput.text.toString().trim(),
            authToken = authTokenInput.text.toString().trim(),
            path = basePath,
            nameFormat = nameFormatInput.text.toString(),
            playlistNameFormat = playlistNameFormatInput.text.toString()
        )

        executor.execute {
            try {
                preferences.baseDownloadPath = basePath
                preferences.useSeparateFolders = separateFolders
                preferences.useAria2c = useAria2c
                preferences.ensureDownloadFolders()

                val file = scdlConfigPath ?: Scdl.prepareConfiguration(applicationContext).also {
                    scdlConfigPath = it
                }
                val config = ScdlConfigFile(file)
                config.saveRaw(rawConfig)
                config.update(values)
                val updatedRaw = config.readText()
                runOnUiThread {
                    rawConfigInput.setText(updatedRaw)
                    setResult(Activity.RESULT_OK)
                    showStatus("Settings saved. Downloads will use $basePath")
                }
            } catch (error: Exception) {
                runOnUiThread { showStatus("Save failed: ${rootMessage(error)}") }
            }
        }
    }

    private fun updateYtDlp() {
        if (!runtimeReady) {
            showStatus("The runtime is not ready.")
            return
        }
        showStatus("Updating yt-dlp...")
        executor.execute {
            try {
                val result = YoutubeDL.updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
                runOnUiThread { showVersions("yt-dlp update: ${result ?: "completed"}") }
            } catch (error: Exception) {
                runOnUiThread { showStatus("yt-dlp update failed: ${rootMessage(error)}") }
            }
        }
    }

    private fun updateScdl() {
        if (!runtimeReady) {
            showStatus("The runtime is not ready.")
            return
        }
        showStatus("Updating SCDL from PyPI...")
        executor.execute {
            try {
                val result = Scdl.updateFromPyPi()
                val message = if (result.changed) {
                    "SCDL updated to ${result.installedVersion}"
                } else {
                    "SCDL is already current (${result.installedVersion})"
                }
                runOnUiThread { showVersions(message) }
            } catch (error: Exception) {
                runOnUiThread { showStatus("SCDL update failed: ${rootMessage(error)}") }
            }
        }
    }

    private fun showVersions(prefix: String) {
        val ytDlp = runCatching { YoutubeDL.versionName(applicationContext) }.getOrNull() ?: "unknown"
        showStatus("$prefix\nSCDL: ${Scdl.version() ?: "unknown"}\nyt-dlp: $ytDlp")
    }

    private fun showStatus(message: String) {
        statusText.text = message
    }

    private fun rawPathFromTreeUri(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val volume = documentId.substringBefore(':')
        val relative = documentId.substringAfter(':', "")
        val root = when {
            volume.equals("primary", ignoreCase = true) -> Environment.getExternalStorageDirectory()
            volume.isNotBlank() -> File("/storage", volume)
            else -> return null
        }
        return if (relative.isBlank()) root.absolutePath else File(root, relative).absolutePath
    }

    private fun rootMessage(error: Throwable): String {
        var current = error
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return current.message ?: current.javaClass.simpleName
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
