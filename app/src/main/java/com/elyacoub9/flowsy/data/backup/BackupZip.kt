package com.elyacoub9.flowsy.data.backup

import com.elyacoub9.flowsy.domain.backup.BackupException
import com.elyacoub9.flowsy.domain.backup.BackupFormat
import com.elyacoub9.flowsy.domain.backup.BackupProblem
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The backup zip: exactly meta.json and data.json. Reading is defensive: only those two exact names are
 * read (other entries are skipped unread; "../" names are rejected by Android), at most [MAX_ENTRIES] entries are looked at, and
 * each file is size-limited while it is decompressed, so a crafted zip cannot exhaust memory.
 */
internal object BackupZip {
    const val MAX_ENTRIES = 16
    const val MAX_META_BYTES = 64 * 1024
    const val MAX_DATA_BYTES = 20 * 1024 * 1024

    class Files(val meta: String, val data: String)

    fun write(output: OutputStream, meta: String, data: String) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(BackupFormat.META_FILE))
            zip.write(meta.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(BackupFormat.DATA_FILE))
            zip.write(data.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun read(input: InputStream): Files {
        var meta: String? = null
        var data: String? = null
        var entries = 0
        try {
            ZipInputStream(input).use { zip ->
                while (entries < MAX_ENTRIES) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    when (entry.name) {
                        BackupFormat.META_FILE -> {
                            if (meta != null) throw BackupException(BackupProblem.CORRUPT)
                            meta = zip.readLimited(MAX_META_BYTES)
                        }
                        BackupFormat.DATA_FILE -> {
                            if (data != null) throw BackupException(BackupProblem.CORRUPT)
                            data = zip.readLimited(MAX_DATA_BYTES)
                        }
                        // Any other entry (e.g. "dir/data.json") is skipped unread. Names with "../" never get
                        // here: Android itself rejects them, so such a zip is refused as damaged.
                        else -> Unit
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: ZipException) {
            throw BackupException(BackupProblem.CORRUPT, e)
        } catch (e: IOException) {
            throw BackupException(BackupProblem.CORRUPT, e)
        }
        if (entries == 0) throw BackupException(BackupProblem.CORRUPT) // not a zip at all
        return Files(meta ?: throw BackupException(BackupProblem.MISSING_FILE), data ?: throw BackupException(BackupProblem.MISSING_FILE))
    }

    /** Reads the current entry, refusing it as soon as it passes [limit] bytes (declared sizes are not trusted). */
    private fun ZipInputStream.readLimited(limit: Int): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw BackupException(BackupProblem.TOO_LARGE)
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
