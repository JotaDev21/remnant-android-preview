package com.liferpg.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Shared press response. Hit targets keep their size; drawing reacts on the layer. */
@Composable internal fun Modifier.pressMotion(
    interaction: MutableInteractionSource,
    pressedScale: Float = .975f,
): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val response = animateFloatAsState(
        if (pressed) pressedScale else 1f,
        if (pressed) tween(85, easing = FastOutSlowInEasing)
        else spring(dampingRatio = .78f, stiffness = 650f),
        label = "touchResponse",
    )
    return graphicsLayer { scaleX = response.value; scaleY = response.value }
}
