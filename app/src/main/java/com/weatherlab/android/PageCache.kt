package com.weatherlab.android

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * The last answer to each page's requests, kept on this phone so a page you've opened before still shows when the PC
 * can't be reached (0.7.32). Files live in the app's private folder, which is excluded from backups and device
 * transfer (see data_extraction_rules.xml). Home has its own saved copy (SnapshotCache) and doesn't use this.
 */
object PageCache {
    private const val KEEP = 200  // answers kept at most; the oldest go first
    private val SKIP = setOf("/api/v1/push", "/api/v1/export/daily")  // the alert topic stays off disk; exports are large

    @Volatile private var dir: File? = null

    fun init(context: Context) {
        dir = File(context.applicationContext.filesDir, "page_cache").apply { mkdirs() }
    }

    private fun file(base: String, path: String): File? {
        val d = dir ?: return null
        val hash = MessageDigest.getInstance("SHA-256").digest("$base$path".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
        return File(d, "$hash.json")
    }

    fun save(base: String, path: String, text: String) {
        if (path in SKIP) return
        val f = file(base, path) ?: return
        runCatching {
            f.writeText(text)
            val all = f.parentFile?.listFiles().orEmpty()
            if (all.size > KEEP) all.sortedBy { it.lastModified() }.take(all.size - KEEP).forEach { it.delete() }
        }
    }

    /** The saved answer and when it was saved (milliseconds), or null. */
    fun load(base: String, path: String): Pair<String, Long>? {
        val f = file(base, path) ?: return null
        if (!f.exists()) return null
        return runCatching { f.readText() to f.lastModified() }.getOrNull()
    }

    fun clear() {
        dir?.listFiles()?.forEach { it.delete() }
    }
}
