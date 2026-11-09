package com.liferpg.data

import java.io.File

/** Only caller-selected files in the private shape directory may be deleted. No recursive operations. */
object PrivatePhotos {
    fun delete(filesDir: File, file: File): Boolean {
        val directory = File(filesDir, "progress_photos").canonicalFile
        val target = file.canonicalFile
        require(target.parentFile == directory && target.name.startsWith("progress_") && target.extension.equals("jpg", true))
        return target.isFile && target.delete()
    }
}
