package com.liferpg.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liferpg.ui.theme.Neon
import com.liferpg.ui.theme.Superficie
import com.liferpg.ui.theme.TextoSecundario
import androidx.core.content.FileProvider
import com.liferpg.BuildConfig
import java.io.File
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class AppRelease(val versionCode: Int, val versionName: String, val apkUrl: String, val sha256: String, val notes: String)

/** APKs are published independently of the private source repository. Android enforces the signing key on install. */
internal object AppUpdates {
    private const val MANIFEST = "https://raw.githubusercontent.com/JotaDev21/remnant-updates/main/latest.json"
    private const val PREFS = "remnant_updates"
    var available by mutableStateOf<AppRelease?>(null)
        private set
    var promptVisible by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var downloading by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var downloadPercent by mutableStateOf(-1)
        private set
    private var downloadId: Long = -1L

    suspend fun check(context: Context, manual: Boolean = false) {
        if (BuildConfig.FLAVOR == "preview") {
            if (manual) message = "As versões oficiais estão no canal REMNANT Updates."
            return
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(MANIFEST).openConnection().apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                val raw = connection.getInputStream().bufferedReader().use { it.readText() }
                val item = parseRelease(raw)
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val storedDownload = prefs.getLong("download_id", -1L)
                val storedVersion = prefs.getInt("download_version", -1)
                val activeId = storedDownload.takeIf { storedVersion == item.versionCode && it >= 0 }
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val status = activeId?.let { id ->
                    manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) else null
                    }
                }
                val stillDownloading = status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED
                val validFile = item.versionCode > BuildConfig.VERSION_CODE && !stillDownloading && verifyFile(context, item)
                if (!stillDownloading) prefs.edit().remove("download_id").remove("download_version").apply()
                withContext(Dispatchers.Main) {
                    available = item.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
                    ready = validFile
                    downloadId = if (stillDownloading) activeId ?: -1L else -1L
                    downloading = available != null && stillDownloading
                    promptVisible = available != null
                    if (manual) message = if (available == null) "Você já está na versão mais recente." else "Nova versão disponível."
                }
            }.onFailure {
                if (manual) withContext(Dispatchers.Main) { message = "Não consegui verificar atualizações. Tente novamente com internet." }
            }
        }
    }

    internal fun parseRelease(raw: String): AppRelease {
        val json = JSONObject(raw)
        val code = json.getInt("versionCode")
        val name = json.getString("versionName").take(40)
        val url = json.getString("apkUrl")
        val digest = json.getString("sha256").lowercase()
        require(code > 0 && name.isNotBlank() && digest.matches(Regex("[0-9a-f]{64}")))
        require(url.startsWith("https://github.com/JotaDev21/remnant-updates/releases/download/"))
        require(url.endsWith(".apk"))
        return AppRelease(code, name, url, digest, json.optString("notes").take(280))
    }

    fun dismiss() { promptVisible = false; message = null }
    fun show() { if (available != null) promptVisible = true }
    fun clearMessage() { message = null }

    fun startDownload(context: Context) {
        val release = available ?: return
        runCatching {
            val name = "remnant-${release.versionCode}.apk"
            val file = File(context.getExternalFilesDir(null), "updates/$name")
            file.parentFile?.mkdirs()
            if (file.exists()) file.delete()
            val request = DownloadManager.Request(Uri.parse(release.apkUrl))
                .setTitle("REMNANT ${release.versionName}")
                .setDescription("Baixando atualização segura")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, null, "updates/$name")
            downloadId = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("download_id", downloadId).putInt("download_version", release.versionCode).apply()
            downloading = true
            ready = false
            downloadPercent = -1
            message = "Download iniciado. Você pode continuar usando o app."
        }.onFailure { message = "Não consegui iniciar o download. Confira sua conexão e espaço livre." }
    }

    suspend fun watchDownload(context: Context) {
        if (!downloading || downloadId < 0) return
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        while (downloading) {
            val state = withContext(Dispatchers.IO) {
                manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                    if (!cursor.moveToFirst()) null else {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val received = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        status to if (total > 0) (received * 100L / total).toInt().coerceIn(0, 100) else -1
                    }
                }
            }
            downloadPercent = state?.second ?: -1
            when (state?.first) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    downloading = false
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("download_id").remove("download_version").apply()
                    val release = available ?: return
                    ready = withContext(Dispatchers.IO) { verifyFile(context, release) }
                    message = if (ready) "Download verificado. Pronto para instalar." else "A verificação falhou. Baixe novamente."
                }
                DownloadManager.STATUS_FAILED, null -> {
                    downloading = false
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("download_id").remove("download_version").apply()
                    message = "O download falhou. Tente novamente."
                }
                else -> delay(1500)
            }
        }
    }

    private fun verifyFile(context: Context, release: AppRelease): Boolean {
        val file = File(context.getExternalFilesDir(null), "updates/remnant-${release.versionCode}.apk")
        if (!file.isFile || file.length() !in 1..300_000_000) return false
        val actual = MessageDigest.getInstance("SHA-256").let { digest ->
            file.inputStream().buffered().use { input ->
                val bytes = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(bytes)
                    if (count < 0) break
                    digest.update(bytes, 0, count)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        if (actual != release.sha256) { file.delete(); return false }
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        if (info == null || info.packageName != context.packageName ||
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info) != release.versionCode.toLong()
        ) { file.delete(); return false }
        return true
    }

    fun install(context: Context) {
        val release = available ?: return
        if (!ready) return
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            message = "Autorize instalações do REMNANT nesta tela e volte para tocar em Instalar."
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val file = File(context.getExternalFilesDir(null), "updates/remnant-${release.versionCode}.apk")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure { message = "O instalador do Android não abriu. Tente novamente em Preferências." }
    }
}

@Composable internal fun AppUpdatePrompt() {
    val context = LocalContext.current
    val release = AppUpdates.available
    LaunchedEffect(Unit) { AppUpdates.check(context) }
    LaunchedEffect(AppUpdates.downloading) { if (AppUpdates.downloading) AppUpdates.watchDownload(context) }
    if (release != null && AppUpdates.promptVisible) AlertDialog(
        onDismissRequest = AppUpdates::dismiss,
        title = { Text("Nova versão do REMNANT") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Eyebrow("SISTEMA  //  ATUALIZAÇÃO", Neon)
                Text("${release.versionName}  ·  ${release.notes}", fontSize = 13.sp)
                if (AppUpdates.downloading) {
                    Text(if (AppUpdates.downloadPercent >= 0) "Baixando ${AppUpdates.downloadPercent}%" else "Preparando download…", color = Neon, fontSize = 12.sp)
                    LinearProgressIndicator(
                        progress = { (AppUpdates.downloadPercent.coerceAtLeast(0) / 100f) },
                        modifier = Modifier.fillMaxWidth(), color = Neon,
                    )
                }
                if (AppUpdates.ready) Text("Arquivo verificado e pronto para instalar.", color = Neon, fontSize = 12.sp)
                Text("Seu progresso permanece neste aparelho. O Android pedirá sua confirmação para instalar.", color = TextoSecundario, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = {
            if (AppUpdates.ready) AppUpdates.install(context)
            else if (!AppUpdates.downloading) AppUpdates.startDownload(context)
        }) { Text(when { AppUpdates.ready -> "Instalar"; AppUpdates.downloading -> "Baixando…"; else -> "Baixar atualização" }) } },
        dismissButton = { TextButton(onClick = AppUpdates::dismiss) { Text("Depois") } },
        containerColor = Superficie,
        shape = RoundedCornerShape(22.dp),
    )
}
