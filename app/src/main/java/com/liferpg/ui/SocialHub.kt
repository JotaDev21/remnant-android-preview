package com.liferpg.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liferpg.domain.*
import com.liferpg.ui.theme.*

internal object SocialTokens {
    val gap=12.dp; val compact=8.dp; val touch=48.dp; val radius=14.dp
    val body=14.sp; val caption=12.sp; val heading=20.sp
}

/** Shared shell: changing modes keeps one social page, rather than stacking copies. */
@Composable internal fun SocialHubHeader(section: SocialSection, inbox: SocialInbox, friendCount: Int,
    change: (SocialSection) -> Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(SocialTokens.gap),modifier=Modifier.animateContentSize()) {
        Text("${friendCount} amigo${if(friendCount==1) "" else "s"} · treine junto, evolua junto",color=TextoSecundario,fontSize=SocialTokens.body)
        if(inbox.total>0) Panel {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(SocialTokens.compact)) {
                Icon(Icons.Outlined.MarkEmailUnread,null,tint=TextoPrimario)
                Text("${inbox.total} convite${if(inbox.total==1) "" else "s"} para você",fontWeight=FontWeight.SemiBold)
            }
            Column {
                inbox.notices.forEach { notice -> TextButton(onClick={ change(notice.section) },modifier=Modifier.fillMaxWidth()) {
                    Text(when(notice.section) { SocialSection.FRIENDS -> "Pedidos de amizade"; SocialSection.CIRCLES -> "Convites para Círculos";
                        SocialSection.PACTS -> "Convites para Pactos"; else -> "Convites para duelos" },Modifier.weight(1f))
                    Text(notice.count.toString())
                } }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(SocialTokens.compact)) {
            SocialArea.entries.forEach { area ->
                val selected=section.area==area
                Column(Modifier.weight(1f).heightIn(min=SocialTokens.touch).clip(RoundedCornerShape(SocialTokens.radius))
                    .background(if(selected) TextoPrimario else Superficie)
                    .selectable(selected,role=Role.Tab,onClick={ change(section.selectArea(area)) })
                    .padding(horizontal=SocialTokens.compact,vertical=SocialTokens.compact),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(SocialTokens.compact)) {
                    Icon(when(area) { SocialArea.FRIENDS -> Icons.Outlined.PeopleOutline; SocialArea.COOP -> Icons.Outlined.Groups;
                        SocialArea.COMPETITIVE -> Icons.Outlined.EmojiEvents },null,tint=if(selected) Fundo else TextoSecundario)
                    Text(area.title,color=if(selected) Fundo else TextoPrimario,fontSize=SocialTokens.caption,fontWeight=FontWeight.Medium)
                }
            }
        }
        if(section.area!=SocialArea.FRIENDS) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(SocialTokens.compact)) {
            SocialSection.entries.filter { it.area==section.area }.forEach { target ->
                Choice(target.title,section==target) { change(target) }
            }
        }
    }
}
