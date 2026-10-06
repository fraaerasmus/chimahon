package chimahon.custom.player

import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object PlayerAssets {
    /**
     * Auth headers used by self-hosted media servers (Jellyfin/Emby/Plex) and referer-locked CDNs.
     * Sentence audio passes these on to FFmpeg along with the headers upstream already allows.
     */
    val SELF_HOSTED_AUTH_HEADERS = setOf(
        "authorization",
        "x-emby-token",
        "x-emby-authorization",
        "x-mediabrowser-token",
        "x-plex-token",
        "x-api-key",
    )

    /**
     * Whether the copy of a bundled mpv asset at [outFile] can be kept. Upstream compares sizes
     * only. A restored or damaged file can match in size while being unreadable (for example
     * after a device migration strips permissions), so a byte is read as well.
     */
    fun isIntact(outFile: File, asset: InputStream): Boolean =
        outFile.length() == asset.available().toLong() &&
            runCatching { FileInputStream(outFile).use { it.read() } }.isSuccess
}
