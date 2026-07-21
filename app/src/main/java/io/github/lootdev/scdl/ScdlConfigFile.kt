package io.github.lootdev.scdl

import java.io.File

class ScdlConfigFile(private val file: File) {
    data class Values(
        val clientId: String,
        val authToken: String,
        val path: String,
        val nameFormat: String,
        val playlistNameFormat: String
    )

    fun readText(): String = file.readText(Charsets.UTF_8)

    fun readValues(): Values {
        val values = parseSection(readText())
        return Values(
            clientId = values["client_id"].orEmpty(),
            authToken = values["auth_token"].orEmpty(),
            path = values["path"].orEmpty(),
            nameFormat = values["name_format"].orEmpty(),
            playlistNameFormat = values["playlist_name_format"].orEmpty()
        )
    }

    fun saveRaw(text: String) {
        require(text.lineSequence().any { it.trim().equals("[scdl]", ignoreCase = true) }) {
            "The configuration must contain a [scdl] section"
        }
        atomicWrite(text.trimEnd() + "\n")
    }

    fun update(values: Values) {
        var text = readText()
        val replacements = linkedMapOf(
            "client_id" to values.clientId,
            "auth_token" to values.authToken,
            "path" to values.path,
            "name_format" to values.nameFormat,
            "playlist_name_format" to values.playlistNameFormat
        )
        replacements.forEach { (key, value) -> text = replaceKey(text, key, value) }
        atomicWrite(text.trimEnd() + "\n")
    }

    private fun parseSection(text: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var inScdl = false
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("[") && line.endsWith("]")) {
                inScdl = line.equals("[scdl]", ignoreCase = true)
            } else if (inScdl && line.isNotEmpty() && !line.startsWith("#") && '=' in line) {
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=').trim()
                result[key] = value
            }
        }
        return result
    }

    private fun replaceKey(text: String, key: String, value: String): String {
        val lines = text.lines().toMutableList()
        var sectionStart = -1
        var sectionEnd = lines.size
        for (index in lines.indices) {
            val trimmed = lines[index].trim()
            if (trimmed.equals("[scdl]", ignoreCase = true)) {
                sectionStart = index
                continue
            }
            if (sectionStart >= 0 && index > sectionStart && trimmed.startsWith("[") && trimmed.endsWith("]")) {
                sectionEnd = index
                break
            }
        }
        if (sectionStart < 0) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
            lines += "[scdl]"
            lines += "$key = $value"
            return lines.joinToString("\n")
        }

        for (index in (sectionStart + 1) until sectionEnd) {
            val trimmed = lines[index].trimStart()
            if (trimmed.startsWith("$key ") || trimmed.startsWith("$key=")) {
                lines[index] = "$key = $value"
                return lines.joinToString("\n")
            }
        }
        lines.add(sectionEnd, "$key = $value")
        return lines.joinToString("\n")
    }

    private fun atomicWrite(content: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }
}
