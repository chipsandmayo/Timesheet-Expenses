package com.chipsandmayo.timesheetexpenses.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Saves files into  Documents/Timesheet Expenses/<subfolder>/ */
object DocumentSaver {
    const val ROOT = "Timesheet Expenses"

    fun displayPath(sub: String) = "Documents/$ROOT/$sub"

    fun save(context: Context, subfolder: String, fileName: String, mime: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val relPath = "${Environment.DIRECTORY_DOCUMENTS}/$ROOT/$subfolder/"
            var uri: Uri? = null
            resolver.query(
                collection, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(relPath, fileName), null,
            )?.use { c -> if (c.moveToFirst()) uri = android.content.ContentUris.withAppendedId(collection, c.getLong(0)) }
            if (uri == null) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                }
                uri = resolver.insert(collection, values) ?: error("Could not create $fileName")
            }
            resolver.openOutputStream(uri!!, "wt")!!.use { it.write(bytes) }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "$ROOT/$subfolder")
            dir.mkdirs()
            File(dir, fileName).writeBytes(bytes)
        }
        return "${displayPath(subfolder)}/$fileName"
    }
}
