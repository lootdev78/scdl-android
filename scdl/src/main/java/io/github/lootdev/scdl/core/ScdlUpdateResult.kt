package io.github.lootdev.scdl.core

data class ScdlUpdateResult(
    val previousVersion: String?,
    val installedVersion: String,
    val changed: Boolean
)
