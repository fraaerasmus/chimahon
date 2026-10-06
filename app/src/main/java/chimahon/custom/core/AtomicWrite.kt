package chimahon.custom.core

import java.io.File
import java.io.IOException

/**
 * Replaces this file's content in one step. [write] fills a temporary file beside this one, which
 * is then renamed over it, so a crash or an exception part-way leaves the previous content intact.
 */
fun File.writeAtomic(write: (File) -> Unit) {
    val target = absoluteFile
    val directory = target.parentFile ?: throw IOException("No parent directory for $target")
    directory.mkdirs()
    val temp = File(directory, "${target.name}.tmp")
    try {
        write(temp)
        if (!temp.renameTo(target)) throw IOException("Could not replace $target")
    } finally {
        temp.delete()
    }
}

fun File.writeTextAtomic(text: String) = writeAtomic { it.writeText(text) }
