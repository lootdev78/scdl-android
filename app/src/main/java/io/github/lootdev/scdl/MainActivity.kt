package io.github.lootdev.scdl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.lootdev.scdl.core.Scdl
import io.github.lootdev.scdl.core.ScdlRequest
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var terminalOutput: TextView
    private lateinit var terminalScroll: ScrollView
    private lateinit var commandInput: EditText
    private lateinit var runtimeStatus: TextView
    private lateinit var runButton: Button
    private lateinit var stopButton: Button
    private lateinit var preferences: AppPreferences

    private val executor = Executors.newSingleThreadExecutor()
    private var runtimeReady = false
    private var activeProcessId: String? = null

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        showPaths()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = AppPreferences(this)
        terminalOutput = findViewById(R.id.terminalOutput)
        terminalScroll = findViewById(R.id.terminalScroll)
        commandInput = findViewById(R.id.commandInput)
        runtimeStatus = findViewById(R.id.runtimeStatus)
        runButton = findViewById(R.id.runButton)
        stopButton = findViewById(R.id.stopButton)

        findViewById<Button>(R.id.settingsButton).setOnClickListener { openSettings() }
        findViewById<Button>(R.id.clearButton).setOnClickListener { terminalOutput.text = "" }
        runButton.setOnClickListener { submitCommand() }
        stopButton.setOnClickListener { stopActiveCommand() }
        commandInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submitCommand()
                true
            } else {
                false
            }
        }

        commandInput.setText(preferences.lastCommand)
        printWelcome()
        requestStoragePermissionIfNeeded()
        initializeRuntime()
    }

    private fun printWelcome() {
        appendTerminal("SCDL Android terminal\n")
        appendTerminal("Type 'help' for terminal commands, or enter normal SCDL arguments.\n")
        appendTerminal("SoundCloud search (-s) is disabled; downloads use links.\n\n")
    }

    private fun initializeRuntime() {
        runtimeStatus.text = "Starting"
        runButton.isEnabled = false
        executor.execute {
            try {
                Scdl.init(applicationContext)
                runtimeReady = true
                runOnUiThread {
                    runtimeStatus.text = "Ready"
                    runButton.isEnabled = true
                    appendTerminal("Runtime ready: ${Scdl.version() ?: "SCDL"}\n")
                    showPaths()
                    appendTerminal("\nscdl $ ")
                }
            } catch (error: Exception) {
                runtimeReady = false
                runOnUiThread {
                    runtimeStatus.text = "Runtime missing"
                    runButton.isEnabled = true
                    appendTerminal("Runtime initialization failed:\n${rootMessage(error)}\n")
                    appendTerminal("Add the arm64-v8a Python/FFmpeg/QuickJS runtime files, then restart the app.\n\nscdl $ ")
                }
            }
        }
    }

    private fun submitCommand() {
        val input = commandInput.text.toString().trim()
        if (input.isEmpty()) return
        preferences.lastCommand = input
        commandInput.setText("")
        appendTerminal("$input\n")

        val parsed = try {
            ShellTokenizer.parse(input)
        } catch (error: IllegalArgumentException) {
            appendTerminal("error: ${error.message}\n\nscdl $ ")
            return
        }

        if (handleBuiltIn(parsed)) return
        if (!runtimeReady) {
            appendTerminal("error: the SCDL runtime is not ready\n\nscdl $ ")
            return
        }
        if (activeProcessId != null) {
            appendTerminal("error: another command is already running\n\nscdl $ ")
            return
        }

        val prepared = try {
            CommandRouter.prepare(parsed, preferences)
        } catch (error: Exception) {
            appendTerminal("error: ${error.message}\n\nscdl $ ")
            return
        }

        prepared.automaticPath?.let { appendTerminal("[path] ${it.absolutePath}\n") }
        val processId = UUID.randomUUID().toString()
        activeProcessId = processId
        runtimeStatus.text = "Running"
        runButton.isEnabled = false

        val aria2cAvailable = File(applicationInfo.nativeLibraryDir, "libaria2c.so").isFile
        val useAria2c = preferences.useAria2c && aria2cAvailable
        if (preferences.useAria2c && !aria2cAvailable) {
            appendTerminal("[warning] aria2c is enabled in settings but libaria2c.so is not installed.\n")
        }

        executor.execute {
            try {
                val response = Scdl.execute(
                    ScdlRequest.raw(prepared.arguments),
                    processId,
                    useAria2c
                ) { _, _, line ->
                    if (line.isNotEmpty()) runOnUiThread { appendTerminal("$line\n") }
                }
                runOnUiThread {
                    appendTerminal("[exit ${response.exitCode}, ${response.elapsedTime / 1000.0}s]\n")
                }
            } catch (_: io.github.lootdev.scdl.runtime.YoutubeDL.CanceledException) {
                runOnUiThread { appendTerminal("[stopped]\n") }
            } catch (error: Exception) {
                runOnUiThread { appendTerminal("error: ${rootMessage(error)}\n") }
            } finally {
                activeProcessId = null
                runOnUiThread {
                    runtimeStatus.text = if (runtimeReady) "Ready" else "Runtime missing"
                    runButton.isEnabled = true
                    appendTerminal("\nscdl $ ")
                }
            }
        }
    }

    private fun handleBuiltIn(arguments: List<String>): Boolean {
        if (arguments.isEmpty()) return true
        return when (arguments.first().lowercase()) {
            "clear" -> {
                terminalOutput.text = ""
                appendTerminal("scdl $ ")
                true
            }
            "settings" -> {
                openSettings()
                appendTerminal("scdl $ ")
                true
            }
            "stop" -> {
                stopActiveCommand()
                appendTerminal("scdl $ ")
                true
            }
            "paths" -> {
                showPaths()
                appendTerminal("scdl $ ")
                true
            }
            "version" -> {
                appendTerminal("SCDL: ${if (runtimeReady) Scdl.version() else "runtime unavailable"}\n")
                appendTerminal("scdl $ ")
                true
            }
            "help" -> {
                appendTerminal(
                    """
                    Terminal commands:
                      help       Show this terminal help
                      settings   Open scdl.cfg and runtime settings
                      paths      Show active download folders
                      version    Show the embedded SCDL version
                      stop       Stop the active SCDL process
                      clear      Clear terminal output

                    SCDL examples:
                      -l https://soundcloud.com/artist/track
                      -l https://soundcloud.com/artist/sets/playlist
                      -l https://soundcloud.com/artist -t
                      me -f
                      --help

                    All SCDL v3 arguments are forwarded unchanged except search (-s), which is disabled.
                    A saved --path is injected automatically unless the command already contains --path.

                    """.trimIndent()
                )
                appendTerminal("scdl $ ")
                true
            }
            else -> false
        }
    }

    private fun showPaths() {
        try {
            val folders = preferences.ensureDownloadFolders()
            appendTerminal(
                "Download root: ${folders.base.absolutePath}\n" +
                    "  Tracks: ${folders.tracks.absolutePath}\n" +
                    "  Playlists: ${folders.playlists.absolutePath}\n" +
                    "  Users: ${folders.users.absolutePath}\n"
            )
        } catch (error: Exception) {
            appendTerminal("Path error: ${rootMessage(error)}\n")
        }
    }

    private fun stopActiveCommand() {
        val processId = activeProcessId
        if (processId == null) {
            appendTerminal("[no active process]\n")
        } else if (Scdl.destroyProcessById(processId)) {
            appendTerminal("[stop requested]\n")
        } else {
            appendTerminal("[process already finished]\n")
        }
    }

    private fun openSettings() {
        settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
    }

    private fun appendTerminal(text: String) {
        terminalOutput.append(text)
        if (terminalOutput.length() > MAX_TERMINAL_CHARS) {
            terminalOutput.text = terminalOutput.text.takeLast(MAX_TERMINAL_CHARS)
        }
        terminalScroll.post { terminalScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun requestStoragePermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST
            )
        }
    }

    private fun rootMessage(error: Throwable): String {
        var current = error
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return current.message ?: current.javaClass.simpleName
    }

    override fun onDestroy() {
        activeProcessId?.let { Scdl.destroyProcessById(it) }
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val STORAGE_PERMISSION_REQUEST = 1001
        private const val MAX_TERMINAL_CHARS = 200_000
    }
}
