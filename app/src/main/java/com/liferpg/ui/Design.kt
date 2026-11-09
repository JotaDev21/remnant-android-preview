package com.liferpg.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.liferpg.domain.Patente
import com.liferpg.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

@Composable internal fun Page(kicker: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    val headerReveal = remember { Animatable(0f) }
    val bodyReveal = remember { Animatable(0f) }
    LaunchedEffect(kicker, title) {
        launch { headerReveal.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
        launch {
            delay(45)
            bodyReveal.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 24.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Box(Modifier.graphicsLayer {
            alpha = headerReveal.value
            translationY = (1f - headerReveal.value) * 10.dp.toPx()
        }) { Header(kicker, title) }
        Column(
            Modifier.fillMaxWidth().graphicsLayer {
                alpha = bodyReveal.value.coerceIn(0f, 1f)
                translationY = (1f - bodyReveal.value) * 14.dp.toPx()
            },
            verticalArrangement = Arrangement.spacedBy(22.dp),
            content = content,
        )
    }
}

@Composable internal fun Header(kicker: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow(kicker)
        Text(title, fontSize = 30.sp, lineHeight = 35.sp, letterSpacing = (-.8).sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable internal fun Eyebrow(text: String, color: Color = Neon) {
    Text(text.uppercase(), fontSize = 10.sp, letterSpacing = .9.sp, fontWeight = FontWeight.Medium, color = color)
}

@Composable internal fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Superficie)
            .border(1.dp, Borda.copy(alpha = .88f), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable internal fun Section(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onClick) { Text(action, fontSize = 12.sp) }
    }
}

@Composable internal fun Action(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val arrowShift by animateDpAsState(if (pressed) 3.dp else 0.dp, tween(120), label = "buttonArrow")
    Button(onClick = onClick, enabled = enabled, interactionSource = interaction, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).pressMotion(interaction), contentPadding = PaddingValues(18.dp)) {
        Text(text, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp).graphicsLayer { translationX = arrowShift.toPx() })
    }
}

@Composable internal fun Meter(value: Float, color: Color = Neon) {
    val progress = remember { Animatable(0f) }
    val clock = LocalMotion.current
    LaunchedEffect(value) { progress.animateTo(if (value.isFinite()) value.coerceIn(0f, 1f) else 0f, tween(950, easing = FastOutSlowInEasing)) }
    Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp))) {
        drawRect(Borda)
        val width = size.width * progress.value
        clipRect(right = width) {
            drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = .65f), color)), size = androidx.compose.ui.geometry.Size(width, size.height))
            val shine = ((clock.orbit.value / 90f) % 1f) * (size.width + 100.dp.toPx()) - 50.dp.toPx()
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .55f), Color.Transparent), shine - 35.dp.toPx(), shine + 35.dp.toPx()))
        }
        if (width > 0) drawCircle(color.copy(alpha = .5f + clock.pulse.value * .5f), size.height * .48f, Offset(width - size.height / 2, size.height / 2))
    }
}

@Composable internal fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        AnimatedLabel(value)
        Text(label, fontSize = 11.sp, color = TextoSecundario)
    }
}

@Composable internal fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val fill by androidx.compose.animation.animateColorAsState(if (selected) Neon.copy(alpha = .15f) else Color.Transparent,
        tween(180), label = "filterSelection")
    FilterChip(selected, onClick, interactionSource = interaction,
        modifier = Modifier.pressMotion(interaction).animateContentSize(spring(.9f, 700f)),
        label = { Text(text, fontSize = 12.sp) },
        leadingIcon = if (selected) { { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) } } else null,
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(containerColor = fill, selectedContainerColor = fill, selectedLabelColor = Neon))
}

@Composable internal fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val arrowShift by animateDpAsState(if (pressed) 4.dp else 0.dp, tween(110), label = "menuArrow")
    val highlight by androidx.compose.animation.animateColorAsState(if (pressed) Neon.copy(alpha = .07f) else Color.Transparent,
        tween(120), label = "menuHighlight")
    Row(Modifier.fillMaxWidth().pressMotion(interaction, .985f).clip(RoundedCornerShape(16.dp))
        .background(highlight)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, color = TextoSecundario)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp).graphicsLayer { translationX = arrowShift.toPx() }, tint = Neon)
    }
}
