package com.hammer.app.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/** Resolves an [OutputStream] into the shared `Documents/Hammer/` folder described in §6/§7.2. */
object HammerStorage {
    private const val RELATIVE_PATH = "Documents/Hammer/"

    fun openOutputStream(context: Context, fileName: String, mimeType: String): OutputStream? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openViaMediaStore(context, fileName, mimeType)
        } else {
            openViaLegacyAppScopedStorage(fileName)
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun openViaMediaStore(context: Context, fileName: String, mimeType: String): OutputStream? {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
        }
        val uri = resolver.insert(collection, values) ?: return null
        return resolver.openOutputStream(uri)
    }

    private fun openViaLegacyAppScopedStorage(fileName: String): OutputStream? {
        // API 26-28: MediaStore.RELATIVE_PATH doesn't exist yet, and writing to the public
        // Documents/ folder would require WRITE_EXTERNAL_STORAGE — a permission this app
        // deliberately doesn't request. Falls back to app-scoped external storage instead.
        val dir = File(Environment.getExternalStorageDirectory(), "Android/data/com.hammer.app/files/Hammer")
        if (!dir.exists() && !dir.mkdirs()) return null
        return FileOutputStream(File(dir, fileName))
    }
}
