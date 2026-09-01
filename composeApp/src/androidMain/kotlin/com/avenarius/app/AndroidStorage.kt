package com.avenarius.app

import android.content.Context
import com.avenarius.app.data.AppStorage
import com.avenarius.app.data.blobFilesSize
import com.avenarius.app.data.deleteAllBlobFiles
import com.avenarius.app.data.deleteBlobFile
import com.avenarius.app.data.readBlobFile
import com.avenarius.app.data.writeBlobFile
import java.io.File

/** [AppStorage] backed by Android SharedPreferences, with blobs in the cache dir. */
class AndroidStorage(
    context: Context,
) : AppStorage {
    private val prefs = context.getSharedPreferences("avenarius", Context.MODE_PRIVATE)

    // Cache dir, not files dir: this is all re-fetchable, so the system is welcome
    // to reclaim it under storage pressure.
    private val blobDir = File(context.cacheDir, "blobs")

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(
        key: String,
        value: String?,
    ) {
        prefs
            .edit()
            .apply {
                if (value == null) remove(key) else putString(key, value)
            }.apply()
    }

    override fun readBlob(name: String): String? = readBlobFile(blobDir, name)

    override fun writeBlob(
        name: String,
        value: String,
    ) = writeBlobFile(blobDir, name, value)

    override fun deleteBlob(name: String) = deleteBlobFile(blobDir, name)

    override fun deleteAllBlobs() = deleteAllBlobFiles(blobDir)

    override fun blobsSizeBytes(): Long = blobFilesSize(blobDir)
}
