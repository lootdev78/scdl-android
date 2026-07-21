package io.github.lootdev.scdl.runtime

interface DownloadProgressCallback {
    fun onProgressUpdate(progress: Float, etaInSeconds: Long, line: String?)
}