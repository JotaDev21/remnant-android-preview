package com.liferpg.ui

import android.annotation.SuppressLint
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.liferpg.domain.apresentacao
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** The user's PR artwork, fed exclusively by the committed winning set. No save writes. */
@SuppressLint("SetJavaScriptEnabled")
@Composable internal fun PersonalRecordScene(stage: Celebration, next: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val record = requireNotNull(stage.record).apresentacao()
    val plates = remember(stage) { mutableSetOf<Int>() }
    var ready by remember(stage) { mutableStateOf(false) }
    var failed by remember(stage) { mutableStateOf(false) }
    val active = remember(stage) { AtomicBoolean(true) }
    val didImpact = remember(stage) { AtomicBoolean(false) }
    val web = remember(context, stage) { runCatching { WebView(context) }.getOrNull() }
    val payload = remember(stage) {
        JSONObject().put("name", record.nome).put("from", record.de).put("to", record.para)
            .put("unit", record.unidade).put("detail", record.detalhe)
            .put("comparison", record.comparacao).put("gain", record.ganho)
            .put("criterion", record.criterio).put("demo", stage.isDemo)
            .put("reducedMotion", Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
            .toString()
    }
    val readyNow by rememberUpdatedState(ready)
    val footerHeight = with(LocalDensity.current) { 22.sp.toDp() + 48.dp }
    BackHandler { next() }

    DisposableEffect(web, owner, payload) {
        var lifecycleObserver: LifecycleEventObserver? = null
        var viewportListener: android.view.View.OnLayoutChangeListener? = null
        if (web == null) { failed = true; ready = true }
        else {
            val bridge = RecordMotionBridge(
                onReady = { web.post { if (active.get()) ready = true } },
                onPlate = { index -> web.post {
                    if (active.get() && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && index in 0..3 && plates.add(index))
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } },
                onImpact = { web.post {
                    if (active.get() && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && didImpact.compareAndSet(false, true))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } },
            )
            web.setBackgroundColor(android.graphics.Color.BLACK)
            web.settings.apply {
                javaScriptEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = false
                textZoom = 100
                blockNetworkLoads = true
                blockNetworkImage = true
                allowFileAccess = false
                allowContentAccess = false
                domStorageEnabled = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            web.addJavascriptInterface(bridge, "RemnantMotion")
            var loaded = false
            fun updateViewport() {
                if (!loaded || !active.get() || web.width <= 0 || web.height <= 0) return
                val density = context.resources.displayMetrics.density
                val width = web.width / density
                val height = web.height / density
                web.evaluateJavascript("window.RemnantRecord.viewport($width,$height);", null)
            }
            val layoutListener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateViewport() }
            web.addOnLayoutChangeListener(layoutListener)
            viewportListener = layoutListener
            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                override fun onPageFinished(view: WebView, url: String?) {
                    if (loaded || !active.get()) return
                    loaded = true
                    updateViewport()
                    view.evaluateJavascript("window.RemnantRecord.start($payload);", null)
                    if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                        view.evaluateJavascript("window.RemnantRecord.pause();", null)
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    if (active.get()) { failed = true; ready = true }
                    return true
                }
            }
            val html = runCatching { context.assets.open("animations/personal_record.html").bufferedReader().use { it.readText() } }
            html.onSuccess {
                web.loadDataWithBaseURL("https://remnant.local/record/", it, "text/html", null, "https://remnant.local/record/")
            }.onFailure { failed = true; ready = true }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        if (loaded) web.evaluateJavascript("window.RemnantRecord.pause();", null)
                        web.onPause()
                    }
                    Lifecycle.Event.ON_START -> {
                        web.onResume()
                        if (loaded) web.evaluateJavascript("window.RemnantRecord.resume();", null)
                    }
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            lifecycleObserver = observer
        }
        onDispose {
            active.set(false)
            lifecycleObserver?.let { owner.lifecycle.removeObserver(it) }
            viewportListener?.let { web?.removeOnLayoutChangeListener(it) }
            web?.removeJavascriptInterface("RemnantMotion")
            web?.stopLoading()
            web?.destroy()
        }
    }
    LaunchedEffect(web, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(12_000)
            if (!readyNow) { failed = true; ready = true }
        }
    }
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        Text("REMNANT", color = Color(0xFF777777), fontSize = 11.sp, letterSpacing = 3.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp))
        Box(Modifier.weight(1f).fillMaxWidth().semantics {
            contentDescription = "Recorde pessoal. ${record.nome}. ${record.comparacao}. ${record.ganho}." + if (stage.isDemo) " Demonstração." else ""
        }) {
            if (web != null && !failed) AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
            else Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(record.nome, color = Color.LightGray, fontSize = 17.sp)
                Spacer(Modifier.height(24.dp))
                Text(record.comparacao, color = Color.White, fontSize = 26.sp)
                Text("NOVO RECORDE", color = Color.White, fontSize = 30.sp, fontFamily = FontFamily.Serif, modifier = Modifier.padding(vertical = 24.dp))
                Text(record.ganho, color = Color.LightGray, fontSize = 18.sp)
                Text(if (stage.isDemo) "Demonstração · seu progresso não muda" else record.criterio, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp))
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = footerHeight).padding(horizontal = 24.dp, vertical = 8.dp)) {
            androidx.compose.animation.AnimatedVisibility(visible = ready,
                enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 5 },
                exit = fadeOut(tween(120))) {
                HearthButton("Continuar treino", next)
            }
        }
    }
}

@Keep internal class RecordMotionBridge(
    private val onReady: () -> Unit, private val onImpact: () -> Unit, private val onPlate: (Int) -> Unit,
) {
    @JavascriptInterface fun ready() = onReady()
    @JavascriptInterface fun impact() = onImpact()
    @JavascriptInterface fun plate(index: Int) = onPlate(index)
}
