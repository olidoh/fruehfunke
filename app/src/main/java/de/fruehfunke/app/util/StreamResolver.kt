package de.fruehfunke.app.util

import java.net.HttpURLConnection
import java.net.URL

/**
 * Viele Sender veröffentlichen Playlist-Dateien (.m3u / .pls) statt der eigentlichen
 * Stream-Adresse. Diese werden hier aufgelöst. HLS (.m3u8) spielt der Player selbst.
 * Muss im Hintergrund-Thread aufgerufen werden.
 */
object StreamResolver {
    fun resolve(url: String): String {
        val path = url.substringBefore('?').lowercase()
        val isPlaylist = path.endsWith(".m3u") || path.endsWith(".pls")
        if (!isPlaylist) return url
        return runCatching { parse(download(url)) }.getOrNull() ?: url
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6_000
        conn.readTimeout = 6_000
        conn.instanceFollowRedirects = true
        return try {
            conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                var total = 0
                while (total < buf.size) {
                    val n = input.read(buf, total, buf.size - total)
                    if (n < 0) break
                    total += n
                }
                String(buf, 0, total, Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): String? {
        val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        // PLS: File1=http://...
        lines.firstOrNull { it.startsWith("File", ignoreCase = true) && it.contains("=") }
            ?.substringAfter("=")?.trim()
            ?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?.let { return it }
        // M3U: erste Zeile, die kein Kommentar ist
        return lines.firstOrNull { !it.startsWith("#") && it.startsWith("http", ignoreCase = true) }
    }
}
