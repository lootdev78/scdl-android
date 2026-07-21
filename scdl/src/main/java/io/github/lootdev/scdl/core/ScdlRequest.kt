package io.github.lootdev.scdl.core

/** A CLI-compatible SCDL request. Arguments are passed to SCDL without reinterpretation. */
class ScdlRequest private constructor(private val arguments: MutableList<String>) {

    fun addOption(option: String): ScdlRequest {
        require(option.isNotBlank()) { "option must not be blank" }
        arguments += option
        return this
    }

    fun addOption(option: String, argument: String): ScdlRequest {
        require(option.isNotBlank()) { "option must not be blank" }
        arguments += option
        arguments += argument
        return this
    }

    fun addArguments(values: List<String>): ScdlRequest {
        arguments += values
        return this
    }

    internal fun buildCommand(runtimeYtDlpArguments: String): List<String> {
        val command = arguments.toMutableList()
        mergeYtDlpArguments(command, runtimeYtDlpArguments)
        return command
    }

    private fun mergeYtDlpArguments(command: MutableList<String>, defaults: String) {
        val directIndex = command.indexOf("--yt-dlp-args")
        if (directIndex >= 0) {
            require(directIndex + 1 < command.size) { "--yt-dlp-args requires an argument" }
            command[directIndex + 1] = "$defaults ${command[directIndex + 1]}".trim()
            return
        }

        val equalsIndex = command.indexOfFirst { it.startsWith("--yt-dlp-args=") }
        if (equalsIndex >= 0) {
            val custom = command[equalsIndex].substringAfter('=', "")
            command[equalsIndex] = "--yt-dlp-args=$defaults $custom".trimEnd()
            return
        }

        command += "--yt-dlp-args"
        command += defaults
    }

    companion object {
        @JvmStatic
        fun raw(arguments: List<String>): ScdlRequest = ScdlRequest(arguments.toMutableList())

        @JvmStatic
        fun link(url: String): ScdlRequest = ScdlRequest(mutableListOf("-l", url))
    }
}
