package chimahon.custom.upload

import com.hippo.unifile.UniFile
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Where the reader looks for a chapter's OCR sidecar: `<chapter>.mokuro` beside a CBZ, or inside a
 * chapter folder, with the chapter URL hash suffix stripped from the name.
 *
 * Upstream's `MokuroSidecarCopier` applies the same naming inline when it saves a sidecar from the
 * Mokuro source. It is repeated here so that file stays identical to upstream; if upstream changes
 * where the sidecar goes, this has to follow.
 */
object MokuroSidecarFiles {

    /** Whether the sidecar for [chapterDir] already exists. */
    fun has(chapterDir: UniFile): Boolean {
        val (targetDir, sidecarName) = location(chapterDir) ?: return false
        return targetDir.findFile(sidecarName)?.isFile == true
    }

    /** Writes [mokuroContent] as the sidecar for [chapterDir]. */
    fun save(chapterDir: UniFile, mokuroContent: String): Boolean {
        val (targetDir, sidecarName) = location(chapterDir) ?: return false
        val sidecarFile = targetDir.findFile(sidecarName)
            ?: targetDir.createFile(sidecarName)
            ?: return false
        return try {
            sidecarFile.openOutputStream().use { output ->
                output.write(mokuroContent.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "MokuroSidecarFiles: failed to save ${sidecarFile.name}" }
            false
        }
    }

    private fun location(chapterDir: UniFile): Pair<UniFile, String>? {
        val isCbz = chapterDir.name?.endsWith(".cbz", ignoreCase = true) == true
        val targetDir = if (isCbz) chapterDir.parentFile ?: return null else chapterDir
        val chapterName = chapterDir.name ?: return null
        val sidecarBaseName = if (isCbz) chapterName.substringBeforeLast('.') else chapterName
        return targetDir to "${sidecarBaseName.replace(chapterUrlHash, "")}.mokuro"
    }

    private val chapterUrlHash = Regex("_[A-Za-z0-9]{6}$")
}
