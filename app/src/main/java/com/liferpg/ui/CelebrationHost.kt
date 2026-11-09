package com.liferpg.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.invisibleToUser
import kotlinx.coroutines.launch

internal class CelebrationHostState {
    val pending = mutableStateListOf<CelebrationPresentation>()
}
internal data class CelebrationPresentation(val token: Any, val stages: List<Celebration>, val finished: () -> Unit)
internal val LocalCelebrationHost = staticCompositionLocalOf<CelebrationHostState> { error("CelebrationHost required") }

/** Activity overlay: measured in the same window as the rest of the app.
 * Floating Compose dialogs on the S25 reported a taller area than was visible. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable internal fun CelebrationHost(content: @Composable () -> Unit) {
    val host = remember { CelebrationHostState() }
    CompositionLocalProvider(LocalCelebrationHost provides host) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(if (host.pending.isNotEmpty()) Modifier.clearAndSetSemantics { invisibleToUser() } else Modifier)) {
                content()
            }
            host.pending.firstOrNull()?.let { presentation -> key(presentation.token) {
                var index by remember { mutableIntStateOf(0) }
                var advancing by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val veil = remember { Animatable(1f) }
                LaunchedEffect(index) {
                    veil.snapTo(1f)
                    veil.animateTo(0f, tween(180))
                }
                val advance: () -> Unit = {
                    if (!advancing) {
                        advancing = true
                        scope.launch {
                            veil.animateTo(1f, tween(160))
                            if (index < presentation.stages.lastIndex) index++
                            else {
                                host.pending.remove(presentation)
                                presentation.finished()
                            }
                            advancing = false
                        }
                    }
                }
                BackHandler(onBack = advance)
                MotionScene(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
                    key(index) { CelebrationScene(presentation.stages[index], advance) }
                    Box(Modifier.matchParentSize().graphicsLayer { alpha = veil.value }.background(Color.Black))
                }
            } }
        }
    }
}
