package io.github.lootdev.scdl

import android.net.Uri
import java.io.File

object CommandRouter {
    private val userModeFlags = setOf("-a", "-t", "-f", "-C", "-p", "-r")

    fun prepare(rawArguments: List<String>, preferences: AppPreferences): PreparedCommand {
        require(rawArguments.isNotEmpty()) { "Enter an SCDL command" }
        val arguments = rawArguments.toMutableList()
        if (arguments.firstOrNull()?.equals("scdl", ignoreCase = true) == true) arguments.removeAt(0)
        require(arguments.isNotEmpty()) { "Enter an SCDL command after 'scdl'" }
        require(arguments.none { it == "-s" || it == "--search" || it.startsWith("--search=") }) {
            "SoundCloud search is disabled. Use a SoundCloud link with -l instead."
        }

        if (isInformationCommand(arguments) || hasPath(arguments)) {
            return PreparedCommand(arguments, null)
        }

        val folders = preferences.ensureDownloadFolders()
        val directory = if (!preferences.useSeparateFolders) {
            folders.base
        } else {
            when (classify(arguments)) {
                DownloadType.PLAYLIST -> folders.playlists
                DownloadType.USER -> folders.users
                DownloadType.TRACK -> folders.tracks
            }
        }
        arguments += listOf("--path", directory.absolutePath)
        return PreparedCommand(arguments, directory)
    }

    private fun isInformationCommand(arguments: List<String>): Boolean =
        arguments.any { it == "-h" || it == "--help" || it == "--version" }

    private fun hasPath(arguments: List<String>): Boolean =
        arguments.any { it == "--path" || it.startsWith("--path=") }

    private fun classify(arguments: List<String>): DownloadType {
        if (arguments.firstOrNull() == "me" || arguments.any { it in userModeFlags }) {
            return DownloadType.USER
        }

        val url = arguments.windowed(2, 1, partialWindows = true)
            .firstOrNull { it.firstOrNull() == "-l" }
            ?.getOrNull(1)
            ?: arguments.firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
            ?: return DownloadType.TRACK

        return classifyUrl(url)
    }

    private fun classifyUrl(value: String): DownloadType {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return DownloadType.TRACK
        val segments = uri.pathSegments.filter { it.isNotBlank() }
        if (segments.any { it.equals("sets", ignoreCase = true) }) return DownloadType.PLAYLIST
        if (segments.size <= 1) return DownloadType.USER
        if (segments.lastOrNull() in setOf("tracks", "likes", "reposts", "comments", "sets")) {
            return DownloadType.USER
        }
        return DownloadType.TRACK
    }
}

data class PreparedCommand(val arguments: List<String>, val automaticPath: File?)

enum class DownloadType { TRACK, PLAYLIST, USER }
