package com.liferpg.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.liferpg.ui.theme.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SystemMotionLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun frame(name: String): Bitmap {
        compose.waitForIdle()
        val bitmap=compose.runOnIdle { val view=compose.activity.window.decorView
            Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val file=File("../../dist/qa-alpha39/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        return bitmap
    }
    @Test fun orbitHasNoVisibleJumpWhenTheSharedClockWraps() {
        val orbit=mutableFloatStateOf(359.99f)
        val pulse=mutableStateOf(.5f)
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalMotion provides MotionClock(orbit,pulse)) {
            Box(Modifier.fillMaxSize().background(Fundo),contentAlignment=Alignment.Center) { SystemSeal(.5f,false,Modifier.size(108.dp)) }
        } } }
        val before=frame("system-seal-loop-before")
        compose.runOnIdle { orbit.floatValue=0f }
        val after=frame("system-seal-loop-after")
        var changed=0
        for(y in 0 until before.height) for(x in 0 until before.width) {
            val first=before.getPixel(x,y);val second=after.getPixel(x,y)
            if(kotlin.math.abs((first and 255)-(second and 255))>8) changed++
        }
        assertTrue("The cycle seam moved $changed pixels; orbit must close smoothly",changed<30)
    }
}
