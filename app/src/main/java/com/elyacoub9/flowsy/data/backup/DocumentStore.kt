package com.elyacoub9.flowsy.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.InputStream
import java.io.OutputStream

/** Files picked with the Storage Access Framework (no storage permission needed). */
class DocumentStore(private val resolver: ContentResolver) {

    /** "wt": truncate, so an existing file chosen as the target is fully replaced. */
    fun openOutput(uri: Uri): OutputStream = requireNotNull(resolver.openOutputStream(uri, "wt")) { "Cannot write $uri" }

    fun openInput(uri: Uri): InputStream = requireNotNull(resolver.openInputStream(uri)) { "Cannot read $uri" }

    /** Removes a partly written backup; false if the provider does not allow it. */
    fun delete(uri: Uri): Boolean = runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)
}
