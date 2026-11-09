package com.liferpg.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liferpg.ui.theme.*

internal data class PrimaryDestination(val id: Int,val title: String)
internal val primaryDestinations=listOf(PrimaryDestination(0,"QG"),PrimaryDestination(1,"Patentes"),PrimaryDestination(2,"Arsenal"),
    PrimaryDestination(3,"Treino"),PrimaryDestination(5,"Amigos"),PrimaryDestination(4,"Perfil"))

private object NavigationTokens {
    val padding=8.dp; val vertical=9.dp; val radius=12.dp; val indicatorHeight=48.dp
    val inset=3.dp; val icon=21.dp; val badge=17.dp; val label=10.sp; val badgeText=9.sp
}

@Composable internal fun MainNavigation(tab: Int, pendingInvites: Int, selectTab: (Int) -> Unit) {
    val visualIndex=primaryDestinations.indexOfFirst { it.id==tab }.coerceAtLeast(0)
    val marker=animateFloatAsState(visualIndex.toFloat(),spring(.86f,600f),label="navigationMarker")
    Column(Modifier.background(Fundo).navigationBarsPadding()) {
        HorizontalDivider(color=Borda)
        Box(Modifier.fillMaxWidth().padding(horizontal=NavigationTokens.padding,vertical=NavigationTokens.vertical)) {
            Canvas(Modifier.matchParentSize()) {
                val slot=size.width/primaryDestinations.size
                drawRoundRect(SuperficieAlta,Offset(slot*marker.value+NavigationTokens.inset.toPx(),NavigationTokens.inset.toPx()),
                    Size(slot-NavigationTokens.inset.toPx()*2,NavigationTokens.indicatorHeight.toPx()),CornerRadius(NavigationTokens.radius.toPx()))
            }
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                primaryDestinations.forEach { destination ->
                    val tint by animateColorAsState(if(tab==destination.id) Neon else TextoSecundario,tween(220),label="tabTint")
                    val interaction=remember { MutableInteractionSource() }
                    val lift=animateDpAsState(if(tab==destination.id) (-2).dp else 0.dp,spring(.82f,600f),label="tabLift")
                    Column(Modifier.weight(1f).heightIn(min=NavigationTokens.indicatorHeight).pressMotion(interaction,.94f)
                        .clip(RoundedCornerShape(NavigationTokens.radius))
                        .selectable(tab==destination.id,interactionSource=interaction,indication=LocalIndication.current,role=Role.Tab,
                            onClick={ selectTab(destination.id) })
                        .semantics { contentDescription=destination.title+if(destination.id==5 && pendingInvites>0) ", $pendingInvites novidades" else "" }
                        .padding(vertical=NavigationTokens.inset),horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(NavigationTokens.inset)) {
                        Box(Modifier.graphicsLayer { translationY=lift.value.toPx() }.padding(NavigationTokens.inset)) {
                            Icon(when(destination.id) { 0 -> Icons.Outlined.Dashboard; 1 -> Icons.Outlined.Shield; 2 -> Icons.Outlined.Explore;
                                3 -> Icons.Outlined.FitnessCenter; 5 -> Icons.Outlined.PeopleOutline; else -> Icons.Outlined.PersonOutline },null,
                                Modifier.size(NavigationTokens.icon),tint=tint)
                            if(destination.id==5 && pendingInvites>0) Box(Modifier.align(Alignment.TopEnd).offset(x=NavigationTokens.padding,y=-NavigationTokens.inset)
                                .size(NavigationTokens.badge).clip(RoundedCornerShape(NavigationTokens.radius)).background(Neon),contentAlignment=Alignment.Center) {
                                Text(if(pendingInvites>9) "9+" else pendingInvites.toString(),color=Fundo,fontSize=NavigationTokens.badgeText,fontWeight=FontWeight.Bold)
                            }
                        }
                        Text(destination.title,fontSize=NavigationTokens.label,color=tint,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
