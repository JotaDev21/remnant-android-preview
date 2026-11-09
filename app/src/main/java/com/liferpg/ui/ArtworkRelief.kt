package com.liferpg.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import com.liferpg.R
import com.liferpg.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

private const val RELIEF_GRID = 24
private data class ReliefArtwork(val bitmap: Bitmap, val surface: List<Vertex3>)
private val reliefCache = object : LruCache<Int, ReliefArtwork>(24 * 1024 * 1024) {
    override fun sizeOf(key: Int, value: ReliefArtwork) = value.bitmap.allocationByteCount
}

/** A textured bas-relief of the approved artwork, not a reconstruction of its unseen back. */
@Composable internal fun ArtworkRelief(rank: Patente?, badgeSize: Dp, division: Divisao?, stage: EstagioFirmamento, reveal: Float) {
    val context = LocalContext.current
    val id = remember(rank, stage) { rankArtwork(rank, stage) }
    val artwork by produceState<ReliefArtwork?>(reliefCache.get(id), id) {
        value = withContext(Dispatchers.IO) {
            reliefCache.get(id) ?: run {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(context.resources,id,bounds)
                var sample = 1
                while (max(bounds.outWidth,bounds.outHeight)/sample > 900) sample *= 2
                val bitmap = BitmapFactory.decodeResource(context.resources,id,BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false })
                    ?: return@withContext null
                val surface = buildList {
                    for (row in 0..RELIEF_GRID) for (col in 0..RELIEF_GRID) {
                        val x = col*2f/RELIEF_GRID-1f; val y = row*2f/RELIEF_GRID-1f
                        // Smooth convex body, with small image-derived detail; the texture is unchanged.
                        var luminance = 0f
                        for (dy in -1..1) for (dx in -1..1) {
                            val px = ((col.toFloat()/RELIEF_GRID)*bitmap.width + dx*bitmap.width/80).toInt().coerceIn(0,bitmap.width-1)
                            val py = ((row.toFloat()/RELIEF_GRID)*bitmap.height + dy*bitmap.height/80).toInt().coerceIn(0,bitmap.height-1)
                            val pixel = bitmap.getPixel(px,py)
                            luminance += ((pixel shr 16 and 255)*.21f+(pixel shr 8 and 255)*.72f+(pixel and 255)*.07f)/255f/9f
                        }
                        add(Vertex3(x,y,.04f + .23f*exp(-(x*x*2f+y*y*1.5f)) + luminance*.045f))
                    }
                }
                ReliefArtwork(bitmap,surface).also { reliefCache.put(id,it) }
            }
        }
    }
    val clock = LocalMotion.current
    var drag by remember(rank, stage) { mutableFloatStateOf(0f) }
    var dragging by remember(rank, stage) { mutableStateOf(false) }
    val settle by animateFloatAsState(if (dragging) 1f else 0f, spring(.42f,180f), label = "emblemRecoil")
    val coords = remember { FloatArray((RELIEF_GRID+1)*(RELIEF_GRID+1)*2) }
    val front = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val rim = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val framed = rank != null && badgeSize.value >= 58f
    front.alpha = when { rank == null -> 255; rank.ehEpica -> 245; else -> 224 }
    front.colorFilter = remember(rank) {
        ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(when { rank == null -> 1f; rank.ehEpica -> .9f; else -> .74f }) })
    }
    val tint = rank?.cor ?: stage.cor
    val rimColor = remember(tint) { PorterDuffColorFilter(lerp(Color(0xFF121B22), tint,.35f).toArgb(),PorterDuff.Mode.SRC_IN) }
    Box(Modifier.size(badgeSize)) {
    if (framed) Image(
        painter = painterResource(R.drawable.rank_relic_halo),
        contentDescription = null,
        modifier = Modifier.matchParentSize().graphicsLayer { alpha = (.84f + clock.pulse.value*.11f)*reveal },
    )
    Canvas(Modifier.matchParentSize().semantics { contentDescription = "${rank?.titulo ?: stage.titulo} ${division?.romano.orEmpty()}, relíquia em relevo. Arraste para inclinar." }
        .pointerInput(rank, stage) { detectHorizontalDragGestures(
            onDragStart = { drag = 0f; dragging = true },
            onDragEnd = { dragging = false },
            onDragCancel = { dragging = false }
        ) { change, amount -> change.consume(); drag = (drag + amount/size.width*2.4f).coerceIn(-.65f,.65f) } }) {
        val art = artwork ?: return@Canvas
        val phase = clock.orbit.value*PI.toFloat()/180f
        val yaw = drag * settle + sin(phase)*.10f - (1f-reveal)*.8f
        val pitch = sin(phase*.8f)*.055f
        val radius = size.minDimension*(if (framed) .37f else .47f)
        val scale = radius*(.7f+reveal*.3f)
        val origin = center + Offset(0f,-size.height*.025f+sin(phase*2f)*size.height*.01f)
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha=if(rank?.ehEpica==true) .16f else .07f),Color.Transparent)),radius)
        val honorable = rank?.ehEpica == true
        // Slow embers catch the metal; the original insignia remains the subject.
        repeat(if (honorable) 4 else 0) { i ->
            val cycle = (phase*.45f+i*.73f) % (2f*PI.toFloat())
            val side = if(i%2==0) -1f else 1f
            val base = origin + Offset(side*radius*(.58f+i%3*.07f),radius*.32f)
            val height = radius*(.75f + sin(cycle)*.15f)
            val sway = sin(cycle)*radius*.16f
            val flame = Path().apply {
                moveTo(base.x,base.y)
                cubicTo(base.x+side*radius*.32f,base.y-height*.28f,base.x+sway,base.y-height*.7f,base.x+side*radius*.10f+sway,base.y-height)
                cubicTo(base.x-side*radius*.1f,base.y-height*.55f,base.x-side*radius*.14f,base.y-height*.28f,base.x,base.y)
                close()
            }
            drawPath(flame,Brush.verticalGradient(listOf(Color.Transparent,Color(0xFFBD9256).copy(alpha=.22f),Color.Transparent),base.y-height,base.y))
        }
        repeat(if(honorable) 6 else 2) { i ->
            val t = ((clock.orbit.value/65f + i*.173f)%1f)
            val x = origin.x + sin(i*2.39f+t)*radius*.9f
            val y = origin.y + radius*(.7f-1.7f*t)
            val alpha = sin(t*PI.toFloat())*.7f
            drawCircle(Color(0xFFD8BA82).copy(alpha=alpha*.62f),size.minDimension*.005f,Offset(x,y))
        }
        val aspect = art.bitmap.width.toFloat()/art.bitmap.height
        fun project(depth: Float) {
            art.surface.forEachIndexed { i,v ->
                val point = Vertex3(v.x*min(1f,aspect),v.y*min(1f,1f/aspect),v.z-depth).rotate(yaw,pitch)
                val perspective = 4f/(4f-point.z)
                coords[i*2] = origin.x + point.x*scale*perspective
                coords[i*2+1] = origin.y + point.y*scale*perspective
            }
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            // Extruded silhouette slices follow the PNG alpha, including holes and ornaments.
            rim.colorFilter = rimColor
            for (layer in 4 downTo 1) {
                project(layer*.028f)
                native.drawBitmapMesh(art.bitmap,RELIEF_GRID,RELIEF_GRID,coords,0,null,0,rim)
            }
            project(0f)
            native.drawBitmapMesh(art.bitmap,RELIEF_GRID,RELIEF_GRID,coords,0,null,0,front)
        }
    }
    if (division != null) Text(
        division.romano,
        modifier = Modifier.align(Alignment.BottomCenter)
            .background(Color(0xEE100D09), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = Color(0xFFE6C78B),
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = (badgeSize.value*.15f).coerceAtLeast(9f).sp,
    )
    }
}

private fun rankArtwork(rank: Patente?, stage: EstagioFirmamento): Int = when (rank) {
    null -> when (stage) {
        EstagioFirmamento.BRASA -> R.drawable.player_brasa
        EstagioFirmamento.FAISCA -> R.drawable.player_faisca
        EstagioFirmamento.CHAMA -> R.drawable.player_chama
        EstagioFirmamento.ESTELAR -> R.drawable.player_estelar
        EstagioFirmamento.NEBULOSA -> R.drawable.player_nebulosa
        EstagioFirmamento.CONSTELACAO -> R.drawable.player_constelacao
        EstagioFirmamento.PULSAR -> R.drawable.player_pulsar
        EstagioFirmamento.NOVA -> R.drawable.player_nova
        EstagioFirmamento.QUASAR -> R.drawable.astro_estelar_v2
        EstagioFirmamento.SUPERNOVA -> R.drawable.rank_olimpiano
        EstagioFirmamento.GALAXIA -> R.drawable.rank_imortal
        EstagioFirmamento.FIRMAMENTO -> R.drawable.player_firmamento
    }
    Patente.SEM_PATENTE -> R.drawable.rank_recruta
    Patente.BRONZE -> R.drawable.rank_bronze_v2
    Patente.PRATA -> R.drawable.rank_prata_v2
    Patente.OURO -> R.drawable.rank_ouro_v2
    Patente.PLATINA -> R.drawable.rank_platina_v2
    Patente.DIAMANTE -> R.drawable.rank_diamante_v2
    Patente.MESTRE -> R.drawable.rank_mestre_v2
    Patente.LENDARIO -> R.drawable.rank_lendario_v2
    Patente.BRUTO -> R.drawable.rank_bruto
    Patente.GLADIADOR -> R.drawable.rank_gladiador
    Patente.BERSERKER -> R.drawable.rank_berserker
    Patente.COLOSSO -> R.drawable.rank_colosso
    Patente.TITA -> R.drawable.rank_tita
    Patente.OLIMPIANO -> R.drawable.rank_olimpiano
    Patente.IMORTAL -> R.drawable.rank_imortal
}
