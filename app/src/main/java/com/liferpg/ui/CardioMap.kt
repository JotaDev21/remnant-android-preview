package com.liferpg.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferpg.Graph
import com.liferpg.data.CardioSession
import com.liferpg.data.cardioClock
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource

internal object CardioMapTokens {
    val height = 360.dp
    val radius = 24.dp
    val touch = 48.dp
    const val STYLE = "https://tiles.openfreemap.org/styles/dark"
}

internal fun cardioRouteGeoJson(points: List<CardioFix>, finished: Boolean, initialMeters: Double = 0.0): String {
    val features = JSONArray()
    fun point(lat: Double, lon: Double, label: String, kind: String) {
        features.put(JSONObject().put("type", "Feature").put("properties", JSONObject().put("label", label).put("kind", kind))
            .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray(listOf(lon, lat)))))
    }
    points.groupBy { it.segment }.values.filter { it.size > 1 }.forEach { segment ->
        val coordinates = JSONArray(); segment.forEach { coordinates.put(JSONArray(listOf(it.lon, it.lat))) }
        features.put(JSONObject().put("type", "Feature").put("properties", JSONObject()).put("geometry", JSONObject().put("type", "LineString").put("coordinates", coordinates)))
    }
    CardioRouteEngine.marks(points, initialMeters).forEach { point(it.lat, it.lon, "${it.km}", "km") }
    points.firstOrNull()?.let { point(it.lat, it.lon, "Início", "start") }
    points.lastOrNull()?.takeIf { points.size > 1 }?.let { point(it.lat, it.lon, if (finished) "Fim" else "Você", if (finished) "finish" else "current") }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

@Composable internal fun CardioRoutePanel(row: CardioSession) {
    if (row.source != "GPS") return
    var error by remember(row.id) { mutableStateOf(false) }
    val flow = remember(row.id) { Graph.cardioRoutes.observe(row.id).flowOn(Dispatchers.IO).catch { error = true; emit(CardioRouteState()) } }
    val route by flow.collectAsStateWithLifecycle(CardioRouteState())
    CardioRouteContent(row, route, error)
}

/** The map slot permits layout QA without pretending Robolectric renders native GL. */
@Composable internal fun CardioRouteContent(row: CardioSession, route: CardioRouteState, error: Boolean = false,
    renderer: @Composable (Modifier, Boolean, (() -> Unit)?) -> Unit = { modifier, _, expand ->
        CardioNativeMap(route.points, row.status == "FINISHED", (route.meters - CardioRouteEngine.pathMeters(route.points)).coerceAtLeast(0.0), modifier, expand)
    }) {
    var expanded by rememberSaveable(row.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Eyebrow("SEU PERCURSO")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = TextoSecundario)
                Text("Só neste aparelho", color = TextoSecundario, fontSize = 12.sp)
            }
        }
        if (error || route.points.isEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(CardioMapTokens.radius)).background(Superficie)
                .padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (error) Icons.Outlined.LocationOff else Icons.Outlined.Route, null, Modifier.size(32.dp), tint = TextoPrimario)
                Text(when { error -> "Percurso indisponível"; row.status == "FINISHED" -> "Percurso salvo no aparelho de origem"
                    row.status == "PAUSED" -> "GPS pausado"; else -> "Encontrando seu ponto de partida" }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(when { error -> "Não consegui abrir o arquivo local. Seu resumo de atividade foi preservado."
                    row.status == "FINISHED" -> "As coordenadas não acompanham o backup da conta. Distância e tempo continuam no resumo."
                    row.status == "PAUSED" -> "Retome a atividade para começar a desenhar seu caminho."
                    else -> "Ao ar livre, aguarde o GPS preciso. O trajeto aparecerá aqui conforme você se movimentar." }, color = TextoSecundario, fontSize = 14.sp)
            }
        } else {
            // Preserve the underlying scroll position while the single native map moves to the dialog.
            val mapModifier = Modifier.fillMaxWidth().height(CardioMapTokens.height).clip(RoundedCornerShape(CardioMapTokens.radius))
            if (expanded) Box(mapModifier.background(SuperficieAlta))
            else renderer(mapModifier, false, { expanded = true })
            CardioMapLegend(row.status == "FINISHED")
        }
        Text("© OpenStreetMap · OpenFreeMap", fontSize = 12.sp, color = TextoSecundario)
    }
    if (expanded) Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val context = LocalContext.current
        val now by produceState(cardioClock(context), row.id, row.status) { while (row.status == "RUNNING") { value = cardioClock(context); delay(1000) } }
        val elapsed = if (row.status == "FINISHED") row.accumulatedMs else CardioEngine.elapsed(row, now)
        Column(Modifier.fillMaxSize().background(Fundo).safeDrawingPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { expanded = false }, modifier = Modifier.size(CardioMapTokens.touch)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar à atividade") }
                Column(Modifier.weight(1f)) {
                    Text(CardioActivity.valueOf(row.activity).title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (row.status == "FINISHED") "Percurso concluído" else if (row.status == "PAUSED") "Atividade pausada" else "Registro GPS em andamento", fontSize = 12.sp, color = TextoSecundario)
                }
            }
            renderer(Modifier.fillMaxWidth().weight(1f), true, null)
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(CardioRouteEngine.meters((row.distanceKm ?: 0.0) * 1000), "DISTÂNCIA")
                Stat(CardioEngine.time(elapsed), "TEMPO ATIVO")
            }
            CardioMapLegend(row.status == "FINISHED", Modifier.padding(horizontal = 22.dp))
            Text("© OpenStreetMap · OpenFreeMap · ruas via internet", Modifier.padding(horizontal = 22.dp).padding(bottom = 12.dp), fontSize = 12.sp, color = TextoSecundario)
        }
    }
}

@Composable private fun CardioMapLegend(finished: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("○ Início", color = TextoPrimario, fontSize = 12.sp)
        Text(if (finished) "● Fim" else "● Você", color = TextoPrimario, fontSize = 12.sp)
        Text("Marcas de 1 km", color = TextoSecundario, fontSize = 12.sp)
    }
}

private enum class RouteCamera { OVERVIEW, FOLLOW, FREE }

@Composable private fun CardioNativeMap(points: List<CardioFix>, finished: Boolean, initialMeters: Double,
    modifier: Modifier, expand: (() -> Unit)?) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val mapView = remember { MapLibre.getInstance(context.applicationContext); MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf(RouteCamera.OVERVIEW) }
    var retry by remember { mutableIntStateOf(0) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var alive by remember { mutableStateOf(true) }
    val latestRetry by rememberUpdatedState(retry)
    val latestPoints by rememberUpdatedState(points)
    val latestFinished by rememberUpdatedState(finished)
    val latestInitial by rememberUpdatedState(initialMeters)
    fun frame() {
        val current = map ?: return
        if (latestPoints.isEmpty() || mapView.width <= 0 || mapView.height <= 0) return
        if (latestPoints.map { it.lat to it.lon }.distinct().size == 1) current.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latestPoints[0].lat, latestPoints[0].lon), 16.0), 650)
        else current.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(latestPoints.map { LatLng(it.lat, it.lon) }).build(), (52 * context.resources.displayMetrics.density).toInt()), 650)
    }
    fun locate() { latestPoints.lastOrNull()?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), 16.5), 650) } }
    DisposableEffect(mapView, owner) {
        var disposed = false; var started = false; var resumed = false
        fun sync() {
            val active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!foreground && resumed) { mapView.onPause(); resumed = false }
            if (!active && started) { mapView.onStop(); started = false }
            if (active && !started) { mapView.onStart(); started = true }
            if (foreground && !resumed) { mapView.onResume(); resumed = true }
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }; owner.lifecycle.addObserver(observer); sync()
        val memory = object : android.content.ComponentCallbacks {
            override fun onConfigurationChanged(configuration: android.content.res.Configuration) = Unit
            override fun onLowMemory() { mapView.onLowMemory() }
        }
        val failure = MapView.OnDidFailLoadingMapListener { if (!disposed) { failed = true; ready = false } }
        val gesture = MapLibreMap.OnCameraMoveStartedListener { reason -> if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) camera = RouteCamera.FREE }
        mapView.addOnDidFailLoadingMapListener(failure)
        mapView.getMapAsync { current -> if (!disposed) { map = current; current.addOnCameraMoveStartedListener(gesture) } }
        context.applicationContext.registerComponentCallbacks(memory)
        onDispose {
            disposed = true
            alive = false
            map?.removeOnCameraMoveStartedListener(gesture); mapView.removeOnDidFailLoadingMapListener(failure)
            owner.lifecycle.removeObserver(observer); context.applicationContext.unregisterComponentCallbacks(memory)
            if (resumed) mapView.onPause(); if (started) mapView.onStop(); mapView.onDestroy()
        }
    }
    LaunchedEffect(map, retry) {
        val current = map ?: return@LaunchedEffect
        val generation = retry
        failed = false; ready = false
        current.setStyle(Style.Builder().fromUri(CardioMapTokens.STYLE)) { style ->
            if (!alive || generation != latestRetry) return@setStyle
            style.addSource(GeoJsonSource("remnant-route", cardioRouteGeoJson(latestPoints, latestFinished, latestInitial)))
            style.addLayer(LineLayer("route-halo", "remnant-route").withProperties(lineColor("#080808"), lineWidth(10f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
            style.addLayer(LineLayer("route", "remnant-route").withProperties(lineColor("#ffffff"), lineWidth(5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
            fun kind(name: String) = Expression.eq(Expression.get("kind"), Expression.literal(name))
            style.addLayer(CircleLayer("route-km", "remnant-route").withFilter(kind("km")).withProperties(circleColor("#141414"), circleRadius(12f), circleStrokeColor("#ffffff"), circleStrokeWidth(2f)))
            style.addLayer(SymbolLayer("route-km-label", "remnant-route").withFilter(kind("km")).withProperties(textField(Expression.get("label")), textFont(arrayOf("Noto Sans Regular")), textSize(11f), textColor("#ffffff"), textAllowOverlap(true)))
            style.addLayer(CircleLayer("route-start", "remnant-route").withFilter(kind("start")).withProperties(circleColor("#141414"), circleRadius(7f), circleStrokeColor("#ffffff"), circleStrokeWidth(3f)))
            style.addLayer(CircleLayer("route-position-halo", "remnant-route").withFilter(kind("current")).withProperties(circleColor("#ffffff"), circleRadius(18f), circleOpacity(.18f)))
            style.addLayer(CircleLayer("route-position", "remnant-route").withFilter(Expression.any(kind("current"), kind("finish"))).withProperties(circleColor("#ffffff"), circleRadius(7f), circleStrokeColor("#080808"), circleStrokeWidth(3f)))
            style.addLayer(SymbolLayer("route-end-labels", "remnant-route").withFilter(Expression.any(kind("start"), kind("current"), kind("finish"))).withProperties(textField(Expression.get("label")), textFont(arrayOf("Noto Sans Regular")), textSize(12f),
                textColor("#ffffff"), textHaloColor("#080808"), textHaloWidth(2f), textOffset(arrayOf(0f, 1.8f))))
            ready = true; failed = false; frame()
        }
    }
    LaunchedEffect(map, ready, retry) { if (map != null && !ready) { delay(15_000); if (!ready) failed = true } }
    LaunchedEffect(points, ready, camera, initialMeters, viewport) {
        if (ready) {
            map?.style?.getSourceAs<GeoJsonSource>("remnant-route")?.setGeoJson(cardioRouteGeoJson(points, finished, initialMeters))
            when (camera) { RouteCamera.OVERVIEW -> frame(); RouteCamera.FOLLOW -> locate(); RouteCamera.FREE -> Unit }
        }
    }
    Box(modifier.background(SuperficieAlta)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize().onSizeChanged { viewport = it }.semantics { contentDescription = "Mapa do percurso registrado. Arraste para explorar e faça pinça para ampliar." })
        if (failed) {
            CardioOfflineTrace(points, Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.Center).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = Fundo.copy(alpha = .94f), shape = RoundedCornerShape(12.dp)) {
                    Text("Ruas indisponíveis · seu trajeto está salvo", Modifier.padding(12.dp), fontSize = 13.sp, color = TextoPrimario)
                }
                Button(onClick = { retry++ }) { Text("Recarregar ruas") }
            }
        } else if (!ready) Surface(Modifier.align(Alignment.Center), color = Superficie, shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = TextoPrimario, strokeWidth = 2.dp)
                Text("Carregando ruas…", fontSize = 14.sp, color = TextoPrimario)
            }
        }
        Column(Modifier.align(Alignment.TopEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (expand != null) CardioMapButton("Abrir mapa em tela inteira", Icons.Outlined.Fullscreen, true, false, expand)
            CardioMapButton("Ver percurso inteiro", Icons.Outlined.Route, ready, camera == RouteCamera.OVERVIEW) { camera = RouteCamera.OVERVIEW; frame() }
            if (!finished) CardioMapButton("Acompanhar minha posição", Icons.Outlined.MyLocation, ready, camera == RouteCamera.FOLLOW) { camera = RouteCamera.FOLLOW; locate() }
        }
    }
}

@Composable private fun CardioMapButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean, selected: Boolean, action: () -> Unit) {
    Surface(color = if (selected) TextoPrimario else Superficie, shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp) {
        IconButton(onClick = action, enabled = enabled, modifier = Modifier.size(CardioMapTokens.touch)) {
            Icon(icon, label, tint = if (selected) Fundo else if (enabled) TextoPrimario else TextoSecundario)
        }
    }
}

/** Honest fallback: recorded geometry, never an invented offline street map. */
@Composable private fun CardioOfflineTrace(points: List<CardioFix>, modifier: Modifier) {
    if (points.isEmpty()) return
    val minLat = points.minOf { it.lat }; val maxLat = points.maxOf { it.lat }; val minLon = points.minOf { it.lon }; val maxLon = points.maxOf { it.lon }
    val longitudeScale = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2)).coerceAtLeast(.01)
    val width = ((maxLon - minLon) * longitudeScale).coerceAtLeast(.00001); val height = (maxLat - minLat).coerceAtLeast(.00001)
    Canvas(modifier.background(SuperficieAlta)) {
        val inset = 48.dp.toPx(); val scale = minOf((size.width - 2 * inset) / width, (size.height - 2 * inset) / height).coerceAtLeast(0.0)
        fun position(p: CardioFix) = Offset((size.width / 2 + ((p.lon - (minLon + maxLon) / 2) * longitudeScale) * scale).toFloat(),
            (size.height / 2 - (p.lat - (minLat + maxLat) / 2) * scale).toFloat())
        points.groupBy { it.segment }.values.forEach { segment -> val path = Path(); segment.forEachIndexed { index, p -> val v = position(p); if (index == 0) path.moveTo(v.x, v.y) else path.lineTo(v.x, v.y) }; drawPath(path, TextoPrimario, style = Stroke(4.dp.toPx())) }
        points.firstOrNull()?.let { drawCircle(TextoPrimario, 7.dp.toPx(), position(it)); drawCircle(SuperficieAlta, 4.dp.toPx(), position(it)) }
        points.lastOrNull()?.let { drawCircle(TextoPrimario, 6.dp.toPx(), position(it)) }
    }
}
