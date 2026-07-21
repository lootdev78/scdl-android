package io.github.lootdev.scdl

import android.content.Context
import android.os.Environment
import java.io.File

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var baseDownloadPath: String
        get() = preferences.getString(KEY_BASE_PATH, null) ?: defaultBaseDirectory().absolutePath
        set(value) = preferences.edit().putString(KEY_BASE_PATH, value.trim()).apply()

    var useSeparateFolders: Boolean
        get() = preferences.getBoolean(KEY_SEPARATE_FOLDERS, true)
        set(value) = preferences.edit().putBoolean(KEY_SEPARATE_FOLDERS, value).apply()

    var useAria2c: Boolean
        get() = preferences.getBoolean(KEY_ARIA2C, false)
        set(value) = preferences.edit().putBoolean(KEY_ARIA2C, value).apply()

    var lastCommand: String
        get() = preferences.getString(KEY_LAST_COMMAND, "") ?: ""
        set(value) = preferences.edit().putString(KEY_LAST_COMMAND, value).apply()

    fun baseDirectory(): File = File(baseDownloadPath)

    fun ensureDownloadFolders(): DownloadFolders {
        val base = baseDirectory()
        if (!base.exists() && !base.mkdirs()) {
            throw IllegalStateException("Could not create download directory: ${base.absolutePath}")
        }
        val tracks = File(base, "Tracks")
        val playlists = File(base, "Playlists")
        val users = File(base, "Users")
        listOf(tracks, playlists, users).forEach { directory ->
            if (!directory.exists() && !directory.mkdirs()) {
                throw IllegalStateException("Could not create directory: ${directory.absolutePath}")
            }
        }
        return DownloadFolders(base, tracks, playlists, users)
    }

    companion object {
        private const val PREFS_NAME = "io.github.lootdev.scdl.settings"
        private const val KEY_BASE_PATH = "base_download_path"
        private const val KEY_SEPARATE_FOLDERS = "separate_download_folders"
        private const val KEY_ARIA2C = "use_aria2c"
        private const val KEY_LAST_COMMAND = "last_command"

        fun defaultBaseDirectory(): File =
            File(Environment.getExternalStorageDirectory(), "SCDL")
    }
}

data class DownloadFolders(
    val base: File,
    val tracks: File,
    val playlists: File,
    val users: File
)
