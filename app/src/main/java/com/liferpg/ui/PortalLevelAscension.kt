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
import androidx.annotation.Keep
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.liferpg.domain.EstagioFirmamento
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** The user's supplied portal, bundled offline; this scene never writes progression. */
@SuppressLint("SetJavaScriptEnabled")
@Composable internal fun PortalLevelAscensionScene(stage: Celebration, next: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val level = stage.number.coerceAtLeast(1)
    val rank = EstagioFirmamento.deNivel(level).titulo
    var ready by remember(stage) { mutableStateOf(false) }
    var failed by remember(stage) { mutableStateOf(false) }
    val active = remember(stage) { AtomicBoolean(true) }
    val didImpact = remember(stage) { AtomicBoolean(false) }
    val web = remember(context, stage) { runCatching { WebView(context) }.getOrNull() }
    val payload = remember(stage) {
        JSONObject().put("level", level)
            .put("previousLevel", stage.previousLevel ?: (level - 1).coerceAtLeast(1))
            .put("rank", rank).put("xpGain", stage.xpGain ?: JSONObject.NULL)
            .put("demo", stage.isDemo)
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
            val bridge = PortalMotionBridge(
                onReady = { web.post { if (active.get()) ready = true } },
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
                web.evaluateJavascript("window.RemnantPortal.viewport($width,$height);", null)
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
                    view.evaluateJavascript("window.RemnantPortal.start($payload);", null)
                    if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                        view.evaluateJavascript("window.RemnantPortal.pause();", null)
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    if (active.get()) { failed = true; ready = true }
                    return true
                }
            }
            val html = runCatching { context.assets.open("animations/level_portal.html").bufferedReader().use { it.readText() } }
            html.onSuccess {
                web.loadDataWithBaseURL("https://remnant.local/ascension/", it, "text/html", null, "https://remnant.local/ascension/")
            }.onFailure { failed = true; ready = true }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        if (loaded) web.evaluateJavascript("window.RemnantPortal.pause();", null)
                        web.onPause()
                    }
                    Lifecycle.Event.ON_START -> {
                        web.onResume()
                        if (loaded) web.evaluateJavascript("window.RemnantPortal.resume();", null)
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
            contentDescription = "Ascensão ao nível $level. $rank." + if (stage.isDemo) " Demonstração." else ""
        }) {
            if (web != null && !failed) AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
            else PortalPoster(level, rank, stage)
        }
        Box(Modifier.fillMaxWidth().heightIn(min = footerHeight).padding(horizontal = 24.dp, vertical = 8.dp)) {
            if (ready) HearthButton("Continuar jornada", next)
        }
    }
}

@Keep internal class PortalMotionBridge(private val onReady: () -> Unit, private val onImpact: () -> Unit) {
    @JavascriptInterface fun ready() = onReady()
    @JavascriptInterface fun impact() = onImpact()
}

/** Accessible static scene if Android's web renderer is unavailable. */
@Composable private fun PortalPoster(level: Int, rank: String, stage: Celebration) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White, size.minDimension * .45f, style = Stroke(1.dp.toPx()))
                drawCircle(Color.DarkGray, size.minDimension * .39f, style = Stroke(.7.dp.toPx()))
                for (i in 0 until 72) {
                    val angle = i * Math.PI / 36
                    val radius = size.minDimension * .48f
                    val end = radius + if (i % 6 == 0) 5.dp.toPx() else 2.dp.toPx()
                    drawLine(Color.Gray, center + Offset((kotlin.math.cos(angle) * radius).toFloat(), (kotlin.math.sin(angle) * radius).toFloat()),
                        center + Offset((kotlin.math.cos(angle) * end).toFloat(), (kotlin.math.sin(angle) * end).toFloat()))
                }
            }
            Text("$level", color = Color.White, fontSize = 76.sp, fontFamily = FontFamily.Serif)
        }
        Spacer(Modifier.height(28.dp))
        Text("NÍVEL", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 34.sp, letterSpacing = 10.sp)
        Text(rank, color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
        val reward = if (stage.isDemo) "Demonstração — seu progresso não muda" else stage.xpGain?.let { "+$it XP conquistados" }
        if (reward != null) Text(reward, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
    }
}
