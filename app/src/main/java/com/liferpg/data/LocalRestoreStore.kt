package com.liferpg.data

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class RestorePoint(val nickname: String, val sets: Int, val xp: Long, val createdAt: Long, val payload: String)

/** Fixed private paths, atomic writes and ownership checks. Never exported to app/cloud backups. */
class LocalRestoreStore(private val directory: File) {
    private val saved = File(directory, "restore-checkpoint-v2.json")
    private val candidate = File(directory, "restore-candidate-v2.json")

    @Synchronized fun prepare(uid: String, payload: String, now: Long = System.currentTimeMillis()) {
        require(uid.isNotBlank() && uid.length <= 128)
        require(payload.toByteArray(Charsets.UTF_8).size <= 10 * 1024 * 1024)
        val backup = JSONObject(payload)
        require(backup.optString("app") == "REMNANT" && backup.optInt("schema") in 7..15)
        directory.mkdirs()
        write(candidate, JSONObject().put("version", 2).put("owner", uid).put("createdAt", now).put("payload", backup).toString())
    }

    /** If the process dies after DB commit, the candidate still contains the safe previous state. */
    @Synchronized fun committed(): Boolean = runCatching {
        write(saved, candidate.inputStream().bufferedReader(Charsets.UTF_8).use { it.readText() })
        candidate.delete()
    }.isSuccess

    @Synchronized fun failed() { candidate.delete() }

    @Synchronized fun read(uid: String): RestorePoint? {
        if (uid.isBlank()) return null
        return listOf(candidate, saved).mapNotNull { file -> runCatching {
            val root = file.inputStream().use { stream ->
                require(stream.channel.size() <= 11 * 1024 * 1024)
                JSONObject(stream.bufferedReader(Charsets.UTF_8).readText())
            }
            if (root.optInt("version") != 2 || root.optString("owner") != uid) return@runCatching null
            val body = root.getJSONObject("payload")
            require(body.optString("app") == "REMNANT" && body.optInt("schema") in 7..15)
            RestorePoint(body.optJSONObject("perfil")?.optString("nome").orEmpty(), body.optJSONArray("series")?.length() ?: 0,
                body.optJSONObject("perfil")?.optLong("xpTotal") ?: 0, root.getLong("createdAt"), body.toString())
        }.getOrNull() }.maxByOrNull { it.createdAt }
    }

    @Synchronized fun clear() {
        candidate.delete(); saved.delete()
        File(candidate.path + ".new").delete(); File(saved.path + ".new").delete()
        File(directory, "before-cloud-restore.json").delete()
    }

    private fun write(file: File, content: String) {
        val pending = File(file.path + ".new")
        try {
            FileOutputStream(pending).use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            // API 26+. Never remove the old file before the atomic replacement succeeds.
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { pending.delete() }
    }
}
