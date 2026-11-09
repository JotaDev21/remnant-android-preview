package com.liferpg.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.liferpg.ui.theme.Borda
import com.liferpg.ui.theme.DouradoLoot
import com.liferpg.ui.theme.Neon
import com.liferpg.ui.theme.Superficie
import com.liferpg.ui.theme.TextoPrimario
import com.liferpg.ui.theme.TextoSecundario
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.request.ImageRequest
import coil.request.CachePolicy

@Composable internal fun BodyProgressScreen() {
    val context = LocalContext.current
    val activity = remember(context) { generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preferences = remember { context.getSharedPreferences("private_photos", Context.MODE_PRIVATE) }
    var lockedByDefault by remember { mutableStateOf(preferences.getBoolean("locked", false)) }
    var unlocked by remember { mutableStateOf(false) }
    var lockMessage by remember { mutableStateOf<String?>(null) }
    var selectedBefore by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAfter by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<File?>(null) }
    var authSignal by remember { mutableStateOf<CancellationSignal?>(null) }
    DisposableEffect(activity, lifecycle) {
        val window = activity?.window
        val wasSecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { unlocked = false; authSignal?.cancel(); authSignal = null } }
        lifecycle.addObserver(observer)
        onDispose { authSignal?.cancel(); authSignal = null; lifecycle.removeObserver(observer); if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val credential = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        unlocked = result.resultCode == Activity.RESULT_OK
        if (!unlocked) lockMessage = "A galeria continua protegida."
    }
    val authenticate = {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) lockMessage = "Configure um bloqueio de tela no Android para proteger a galeria."
        else if (Build.VERSION.SDK_INT >= 29) {
            authSignal?.cancel()
            val signal = CancellationSignal().also { authSignal = it }
            try {
            val builder = android.hardware.biometrics.BiometricPrompt.Builder(context).setTitle("Sua galeria privada")
                .setSubtitle("Confirme sua identidade para abrir as fotos corporais")
            if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            else { @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true) }
            builder.build().authenticate(signal, ContextCompat.getMainExecutor(context),
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) { unlocked = true; lockMessage = null }
                        override fun onAuthenticationError(code: Int, message: CharSequence) { lockMessage = "A galeria continua protegida. Tente novamente quando quiser." }
                    })
            } catch (_: RuntimeException) { signal.cancel(); authSignal = null; lockMessage = "Não consegui abrir a confirmação do Android. A galeria continua protegida." }
        } else {
            @Suppress("DEPRECATION")
            val intent = keyguard.createConfirmDeviceCredentialIntent("Sua galeria privada", "Confirme o bloqueio do Android")
            if (intent != null) credential.launch(intent)
        }
    }
    var refresh by remember { mutableIntStateOf(0) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val pending = pendingPath?.let(::File)
        if (!saved) pending?.delete()
        pendingPath = null
        refresh++
    }
    val startCapture = {
        val directory = File(context.filesDir, "progress_photos").apply { mkdirs() }
        val file = File(directory, "progress_${System.currentTimeMillis()}.jpg")
        pendingPath = file.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        capture.launch(uri)
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) startCapture()
    }
    val photos = remember(refresh) { progressPhotos(context.filesDir) }

    if (lockedByDefault && !unlocked) {
        Page("REGISTRO CORPORAL / PRIVADO", "Sua evolução.\nSó para você.") {
            Text("Confirme sua identidade no Android para abrir a galeria. As fotos ficam neste aparelho e não são enviadas à Arena, ao REM ou ao backup da conta.", color = TextoSecundario, fontSize = 14.sp, lineHeight = 22.sp)
            Action("Abrir galeria privada") { authenticate() }
            lockMessage?.let { Text(it, color = TextoSecundario, fontSize = 12.sp) }
        }
        return
    }

    Page("SISTEMA / REGISTRO CORPORAL", "Sua evolução,\nsem depender da memória.") {
        Text(
            "As fotos ficam privadas nos dados do Remnant. Use posição, distância e iluminação parecidas para a comparação ser honesta.",
            color = TextoSecundario,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Proteger com o bloqueio do Android", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Biometria ou senha do aparelho. A tela também bloqueia capturas.", color = TextoSecundario, fontSize = 11.sp)
            }
            Switch(lockedByDefault, { enabled ->
                val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (enabled && !keyguard.isDeviceSecure) lockMessage = "Configure um bloqueio de tela no Android primeiro."
                else { lockedByDefault = enabled; preferences.edit().putBoolean("locked", enabled).apply(); if (enabled) unlocked = false }
            })
        }
        lockMessage?.let { Text(it, color = TextoSecundario, fontSize = 12.sp) }
        Action(if (hasPermission) "Registrar foto de hoje" else "Permitir câmera") {
            if (!hasPermission) {
                requestPermission.launch(Manifest.permission.CAMERA)
            } else {
                startCapture()
            }
        }

        if (photos.isEmpty()) {
            Panel {
                Eyebrow("PRIMEIRO MARCO", Neon)
                Text("Crie sua foto-base", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Frente, lado e costas funcionam melhor em registros separados. A próxima foto já poderá ser comparada com esta.", color = TextoSecundario, fontSize = 13.sp)
            }
        } else {
            Section("Antes e agora")
            Text("Toque numa foto da linha do tempo para escolher a comparação.", color = TextoSecundario, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressPhotoCard(photos.firstOrNull { it.name == selectedBefore } ?: photos.first(), "ANTES", Modifier.weight(1f))
                ProgressPhotoCard(photos.firstOrNull { it.name == selectedAfter } ?: photos.last(), "AGORA", Modifier.weight(1f))
            }
            if (photos.size == 1) Text("Registre outra foto no futuro para formar a comparação.", color = TextoSecundario, fontSize = 12.sp)

            Section("Linha do tempo", "${photos.size} registros")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                photos.asReversed().forEach { file ->
                    Column(Modifier.width(150.dp)) {
                        ProgressPhotoCard(file, photoDate(file))
                        Row {
                            TextButton(onClick = { selectedBefore = file.name }) { Text("Antes", fontSize = 11.sp) }
                            TextButton(onClick = { selectedAfter = file.name }) { Text("Agora", fontSize = 11.sp) }
                        }
                        TextButton(onClick = { deleting = file }) { Text("Apagar foto", color = TextoSecundario, fontSize = 11.sp) }
                    }
                }
            }
        }
        Panel {
            Eyebrow("PROTOCOLO DE COMPARAÇÃO", DouradoLoot)
            Text("Mesmo horário aproximado", color = TextoPrimario)
            Text("Mesma distância e altura da câmera", color = TextoPrimario)
            Text("Postura relaxada e iluminação semelhante", color = TextoPrimario)
        }
    }
    deleting?.let { file -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Apagar esta foto?") },
        text = { Text("A foto de ${photoDate(file)} será removida deste aparelho. Seus treinos e as demais fotos permanecem.") },
        confirmButton = { TextButton(onClick = {
            runCatching { com.liferpg.data.PrivatePhotos.delete(context.filesDir, file) }
                .onSuccess { if (!it) lockMessage = "Não consegui apagar a foto. Tente novamente." }
                .onFailure { lockMessage = "Não consegui apagar a foto." }
            deleting = null; refresh++
        }) { Text("Apagar foto") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }) }
}

@Composable private fun ProgressPhotoCard(file: File, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(.72f).clip(RoundedCornerShape(18.dp)).background(Superficie),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(file).memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build(),
                contentDescription = "Foto corporal de $label",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Text(
                label,
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .7f)).padding(9.dp),
                color = Neon,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(photoDate(file), color = TextoSecundario, fontSize = 10.sp)
    }
}

private fun progressPhotos(filesDir: File): List<File> =
    File(filesDir, "progress_photos").listFiles { file -> file.isFile && file.extension.equals("jpg", true) }
        ?.sortedBy { it.nameWithoutExtension.removePrefix("progress_").toLongOrNull() ?: it.lastModified() }
        .orEmpty()

private fun photoDate(file: File): String {
    val timestamp = file.nameWithoutExtension.removePrefix("progress_").toLongOrNull() ?: file.lastModified()
    return SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("pt-BR")).format(Date(timestamp))
}
