package io.github.lootdev.scdl.core

import android.content.Context
import io.github.lootdev.scdl.runtime.YoutubeDL
import io.github.lootdev.scdl.runtime.YoutubeDLException
import io.github.lootdev.scdl.runtime.YoutubeDLResponse
import io.github.lootdev.scdl.ffmpeg.FFmpeg
import io.github.lootdev.scdl.aria2c.Aria2c
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** SCDL v3 wrapper using one shared embedded Python, yt-dlp, FFmpeg, QuickJS and aria2c runtime. */
object Scdl {
    private const val MODULE_NAME = "scdl_android_entry"
    private const val BUNDLE_VERSION = "scdl-3.0.7-android-no-ytdlp-1"
    private const val PREFS_NAME = "io.github.lootdev.scdl.runtime"
    private const val PREF_BUNDLE_VERSION = "bundle_version"
    private const val PYPI_JSON = "https://pypi.org/pypi/scdl/json"

    private var initialized = false
    private var packageDir: File? = null
    private var xdgConfigHome: File? = null
    private var homeDir: File? = null
    private var detectedVersion: String? = null

    @Synchronized
    @Throws(ScdlException::class)
    fun init(appContext: Context) {
        if (initialized) return
        val context = appContext.applicationContext

        try {
            prepareConfiguration(context)
            FFmpeg.getInstance().init(context)
            Aria2c.getInstance().init(context)
            YoutubeDL.getInstance().init(context)

            detectedVersion = executeArguments(listOf("--version"), null, null)
                .out
                .lineSequence()
                .lastOrNull { it.isNotBlank() }
                ?.trim()
            initialized = true
        } catch (e: Exception) {
            initialized = false
            throw ScdlException(
                "SCDL could not start. Add the arm64 Python runtime files and make sure the embedded Python version is 3.10 or newer.",
                e
            )
        }
    }

    @JvmOverloads
    @Throws(ScdlException::class, InterruptedException::class, YoutubeDL.CanceledException::class)
    fun execute(
        request: ScdlRequest,
        processId: String? = null,
        useAria2c: Boolean = false,
        callback: ((Float, Long, String) -> Unit)? = null
    ): YoutubeDLResponse {
        assertInit()
        return try {
            executeArguments(
                request.buildCommand(buildRuntimeYtDlpArguments(useAria2c)),
                processId,
                callback
            )
        } catch (e: YoutubeDLException) {
            throw ScdlException(e.message ?: "SCDL execution failed", e)
        }
    }

    fun destroyProcessById(processId: String): Boolean =
        YoutubeDL.getInstance().destroyProcessById(processId)

    fun version(): String? {
        assertInit()
        return detectedVersion
    }

    @Synchronized
    fun prepareConfiguration(appContext: Context): File {
        val context = appContext.applicationContext
        val baseDir = File(context.noBackupFilesDir, "scdl")
        packageDir = File(baseDir, "python-packages")
        xdgConfigHome = File(baseDir, "config")
        homeDir = File(baseDir, "home")
        installBundleIfNeeded(context, packageDir!!)
        xdgConfigHome!!.mkdirs()
        homeDir!!.mkdirs()
        ensureConfigFile()
        return File(xdgConfigHome, "scdl/scdl.cfg")
    }

    fun configFile(): File {
        assertDirectories()
        ensureConfigFile()
        return File(xdgConfigHome, "scdl/scdl.cfg")
    }

    fun configFile(appContext: Context): File = prepareConfiguration(appContext)

    fun packageDirectory(): File {
        assertDirectories()
        return packageDir!!
    }

    /** Updates only SCDL's pure-Python package from PyPI. yt-dlp is updated separately in Settings. */
    @Synchronized
    @Throws(ScdlException::class)
    fun updateFromPyPi(): ScdlUpdateResult {
        assertInit()
        val previous = detectedVersion
        val tempWheel = File.createTempFile("scdl-update-", ".whl", homeDir)
        try {
            val metadata = JSONObject(downloadText(PYPI_JSON))
            val latest = metadata.getJSONObject("info").getString("version")
            if (versionMatches(previous, latest)) {
                return ScdlUpdateResult(previous, latest, false)
            }

            val urls = metadata.getJSONArray("urls")
            var wheelUrl: String? = null
            for (index in 0 until urls.length()) {
                val item = urls.getJSONObject(index)
                val filename = item.optString("filename")
                if (item.optString("packagetype") == "bdist_wheel" &&
                    filename.endsWith("-py3-none-any.whl")) {
                    wheelUrl = item.getString("url")
                    break
                }
            }
            if (wheelUrl == null) throw ScdlException("PyPI did not provide a universal SCDL wheel")

            downloadFile(wheelUrl, tempWheel)
            installScdlWheel(tempWheel)
            detectedVersion = executeArguments(listOf("--version"), null, null)
                .out
                .lineSequence()
                .lastOrNull { it.isNotBlank() }
                ?.trim()
            val installed = detectedVersion ?: latest
            return ScdlUpdateResult(previous, installed, true)
        } catch (e: ScdlException) {
            throw e
        } catch (e: Exception) {
            throw ScdlException("SCDL update failed", e)
        } finally {
            tempWheel.delete()
        }
    }

    private fun versionMatches(current: String?, latest: String): Boolean =
        current?.contains(latest) == true

    private fun executeArguments(
        arguments: List<String>,
        processId: String?,
        callback: ((Float, Long, String) -> Unit)?
    ): YoutubeDLResponse {
        val youtubeDL = YoutubeDL.getInstance()
        val pythonPath = listOf(
            packageDir!!.absolutePath,
            youtubeDL.ytdlpExecutable().absolutePath
        ).joinToString(File.pathSeparator)

        val environment = mapOf(
            "PYTHONPATH" to pythonPath,
            "XDG_CONFIG_HOME" to xdgConfigHome!!.absolutePath,
            "HOME" to homeDir!!.absolutePath,
            "PYTHONNOUSERSITE" to "1",
            "PYTHONUNBUFFERED" to "1",
            "PYTHONDONTWRITEBYTECODE" to "1"
        )
        return youtubeDL.executePythonModule(
            MODULE_NAME,
            arguments,
            processId,
            true,
            environment,
            homeDir,
            callback
        )
    }

    private fun buildRuntimeYtDlpArguments(useAria2c: Boolean): String {
        val youtubeDL = YoutubeDL.getInstance()
        val values = mutableListOf(
            "--no-cache-dir",
            "--ffmpeg-location", shellQuote(youtubeDL.ffmpegExecutable().absolutePath),
            "--js-runtimes", shellQuote("quickjs:${youtubeDL.quickJsExecutable().absolutePath}")
        )
        if (useAria2c) {
            values += listOf(
                "--downloader", "libaria2c.so",
                "--external-downloader-args", shellQuote("aria2c:--summary-interval=1")
            )
        }
        return values.joinToString(" ")
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun ensureConfigFile() {
        val target = File(xdgConfigHome, "scdl/scdl.cfg")
        if (target.isFile) return
        target.parentFile?.mkdirs()
        val source = File(packageDir, "scdl/scdl.cfg")
        if (!source.isFile) throw IllegalStateException("Default scdl.cfg is missing from the SCDL bundle")
        source.copyTo(target, overwrite = false)
    }

    private fun installBundleIfNeeded(context: Context, targetDir: File) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val installedVersion = preferences.getString(PREF_BUNDLE_VERSION, null)
        if (installedVersion == BUNDLE_VERSION && targetDir.isDirectory) return

        targetDir.deleteRecursively()
        targetDir.mkdirs()
        try {
            context.resources.openRawResource(R.raw.scdl_bundle).use { input ->
                unzipSafely(input, targetDir)
            }
            preferences.edit().putString(PREF_BUNDLE_VERSION, BUNDLE_VERSION).apply()
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            throw e
        }
    }

    private fun installScdlWheel(wheel: File) {
        val target = packageDir!!
        File(target, "scdl").deleteRecursively()
        target.listFiles()
            ?.filter { it.name.startsWith("scdl-") && it.name.endsWith(".dist-info") }
            ?.forEach { it.deleteRecursively() }

        ZipInputStream(BufferedInputStream(wheel.inputStream())).use { zip ->
            val canonicalRoot = target.canonicalFile
            var entry = zip.nextEntry
            var installedAny = false
            while (entry != null) {
                val name = entry.name
                val accepted = name.startsWith("scdl/") ||
                    (name.startsWith("scdl-") && name.contains(".dist-info/"))
                if (accepted) {
                    val output = File(target, name).canonicalFile
                    val prefix = canonicalRoot.path + File.separator
                    if (!output.path.startsWith(prefix)) throw IOException("Unsafe wheel entry: $name")
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { zip.copyTo(it) }
                    }
                    installedAny = true
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            if (!installedAny) throw ScdlException("The downloaded wheel did not contain the SCDL package")
        }
    }

    private fun unzipSafely(input: java.io.InputStream, targetDir: File) {
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            val canonicalRoot = targetDir.canonicalFile
            var entry = zip.nextEntry
            while (entry != null) {
                val output = File(targetDir, entry.name).canonicalFile
                val prefix = canonicalRoot.path + File.separator
                if (output != canonicalRoot && !output.path.startsWith(prefix)) {
                    throw IOException("Unsafe ZIP entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun downloadText(address: String): String {
        val connection = openConnection(address)
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun downloadFile(address: String, output: File) {
        val connection = openConnection(address)
        connection.inputStream.use { input ->
            FileOutputStream(output).use { input.copyTo(it) }
        }
    }

    private fun openConnection(address: String): HttpURLConnection {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "SCDL-Android/1.0")
        val status = connection.responseCode
        if (status !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP $status from $address")
        }
        return connection
    }

    private fun assertDirectories() {
        check(packageDir != null && xdgConfigHome != null && homeDir != null) {
            "Scdl.init(context) must be called first"
        }
    }

    private fun assertInit() {
        check(initialized) { "Scdl.init(context) must be called first" }
    }

    @JvmStatic
    fun getInstance() = this
}
