package com.liferpg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.liferpg.domain.Patente
import com.liferpg.ui.theme.DouradoLoot

@Composable internal fun HonorablePanel(
    rank: Patente,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(8.dp, 32.dp, 8.dp, 32.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF302A21), Color(0xFF101719), rank.cor.copy(alpha = .16f))))
            .border(1.dp, Brush.linearGradient(listOf(DouradoLoot.copy(alpha = .85f), rank.cor.copy(alpha = .18f), DouradoLoot.copy(alpha = .55f))), shape)
            .drawBehind {
                val inset = 9.dp.toPx()
                val length = 30.dp.toPx()
                drawLine(DouradoLoot, Offset(inset, inset), Offset(inset + length, inset), 2.dp.toPx())
                drawLine(DouradoLoot, Offset(inset, inset), Offset(inset, inset + length), 2.dp.toPx())
                drawLine(DouradoLoot, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - length, size.height - inset), 2.dp.toPx())
                drawLine(DouradoLoot, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - length), 2.dp.toPx())
            }.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}
