package com.liferpg.ui

import android.content.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.liferpg.data.sync.*
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import kotlinx.coroutines.delay

@Composable internal fun FriendsHub(friendships: List<AmizadeArena>,myPower: Float,foundPlayers: List<RankingGlobal>,
    searchLoading: Boolean,searchError: String?,query: String,onQueryChange: (String) -> Unit,inviteCode: String,busy: Boolean,
    inspect: (AmizadeArena) -> Unit,inspectPlayer: (RankingGlobal) -> Unit,addFriend: (String) -> Unit,
    accept: (AmizadeArena) -> Unit,decline: (AmizadeArena) -> Unit) {
    val context=LocalContext.current
    var showCode by rememberSaveable(inviteCode) { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if(copied) { delay(1800); copied=false } }
    val received=friendships.filter { it.status=="pending" && !it.enviadaPorMim }
    val sent=friendships.filter { it.status=="pending" && it.enviadaPorMim }
    val friends=friendships.filter { it.status=="accepted" }

    Column(verticalArrangement=Arrangement.spacedBy(SocialTokens.gap)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Encontre seus amigos",Modifier.weight(1f),fontSize=SocialTokens.heading,fontWeight=FontWeight.SemiBold)
            if(inviteCode.isNotBlank()) TextButton(onClick={ showCode=true }) { Text("Meu código") }
        }
        OutlinedTextField(query,{ onQueryChange(it.take(32)) },Modifier.fillMaxWidth(),singleLine=true,
            label={ Text("Apelido ou código REM") },leadingIcon={ Icon(Icons.Outlined.Search,null) },
            trailingIcon=if(query.isNotEmpty()) { { IconButton(onClick={ onQueryChange("") }) { Icon(Icons.Outlined.Close,"Limpar busca") } } } else null)
        Text("A partir de 2 letras. Sem precisar procurar no ranking.",color=TextoSecundario,fontSize=SocialTokens.caption)
    }
    if(query.trim().length>=2) {
        Section("Resultados da busca")
        when {
            searchLoading -> LinearProgressIndicator(Modifier.fillMaxWidth(),color=TextoPrimario)
            searchError!=null -> Panel { Text(searchError,color=TextoSecundario); TextButton(onClick={ onQueryChange(query) }) { Text("Tentar busca novamente") } }
            foundPlayers.isEmpty() -> Panel { Text("Nenhum perfil encontrado.",fontWeight=FontWeight.Medium); Text("Tente outro apelido ou peça o código REM do amigo.",color=TextoSecundario,fontSize=SocialTokens.body) }
            else -> foundPlayers.forEach { player -> key(player.userId) {
                val link=friendships.firstOrNull { it.jogadorId==player.userId }
                Panel {
                    FriendIdentity(player.apelido,player.powerScore,player.patente) { inspectPlayer(player) }
                    when {
                        link==null -> Action("Adicionar amigo",!busy) { addFriend(player.userId) }
                        link.status=="accepted" -> Text("Vocês já são amigos.",color=TextoSecundario,fontSize=SocialTokens.caption)
                        link.enviadaPorMim -> Text("Pedido enviado. Aguardando resposta.",color=TextoSecundario,fontSize=SocialTokens.caption)
                        else -> TextButton(onClick={ inspectPlayer(player) }) { Text("Ver pedido recebido") }
                    }
                }
            } }
        }
    }
    if(received.isNotEmpty()) {
        Section("Pedidos recebidos · ${received.size}")
        received.forEach { friend -> key(friend.id) { Panel {
            FriendIdentity(friend.apelido,friend.powerScore,friend.patente) { inspect(friend) }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(SocialTokens.compact)) {
                Button(onClick={ accept(friend) },enabled=!busy,modifier=Modifier.weight(1f)) { Text("Aceitar") }
                OutlinedButton(onClick={ decline(friend) },enabled=!busy,modifier=Modifier.weight(1f)) { Text("Recusar") }
            }
        } } }
    }
    Section("Seus amigos · ${friends.size}")
    if(friends.isEmpty()) Panel {
        Icon(Icons.Outlined.PeopleOutline,null,tint=TextoSecundario)
        Text("O próximo treino pode ser junto.",fontWeight=FontWeight.SemiBold,fontSize=SocialTokens.heading)
        Text("Busque um amigo acima ou compartilhe seu código. Depois do aceite, vocês podem comparar PRs, conhecer os treinos e formar um grupo.",color=TextoSecundario,fontSize=SocialTokens.body)
    }
    friends.forEach { friend -> key(friend.jogadorId) {
        Panel {
            FriendIdentity(friend.apelido,friend.powerScore,friend.patente) { inspect(friend) }
            Text(if(friend.powerScore>=myPower) "${(friend.powerScore-myPower).toInt()} Power à frente de você" else "${(myPower-friend.powerScore).toInt()} Power atrás de você",
                color=TextoSecundario,fontSize=SocialTokens.caption)
        }
    } }
    if(sent.isNotEmpty()) {
        Section("Pedidos enviados · ${sent.size}")
        sent.forEach { friend -> MenuRow(friend.apelido,"Aguardando resposta") { inspect(friend) } }
    }
    if(showCode) AlertDialog(onDismissRequest={ showCode=false },title={ Text("Convide para treinar junto") },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(SocialTokens.gap)) {
            Text("Seu código REM",color=TextoSecundario,fontSize=SocialTokens.caption)
            Text(inviteCode,fontWeight=FontWeight.SemiBold,fontSize=SocialTokens.heading)
            Text("Seu amigo abre Amigos na barra principal e cola este código na busca. Não precisa compartilhar e-mail nem credenciais.",color=TextoSecundario,fontSize=SocialTokens.body)
            Row {
                TextButton(onClick={ (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("Código REMNANT",inviteCode)); copied=true }) { Text(if(copied) "Copiado" else "Copiar código") }
                TextButton(onClick={
                    val send=Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,"Vamos treinar juntos no REMNANT! Abra Amigos na barra principal e busque meu código: $inviteCode") }
                    runCatching { context.startActivity(Intent.createChooser(send,"Compartilhar código REMNANT")) }
                }) { Text("Compartilhar") }
            }
            AnimatedVisibility(copied) { Text("Código copiado.",fontSize=SocialTokens.caption,color=TextoSecundario) }
        }
    },confirmButton={ TextButton(onClick={ showCode=false }) { Text("Fechar") } })
}

@Composable private fun FriendIdentity(name: String,power: Float,rank: Int,inspect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=SocialTokens.touch).clickable(onClick=inspect),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(SocialTokens.gap)) {
        Insignia(size=SocialTokens.touch,stage=EstagioFirmamento.de(power))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(SocialTokens.compact)) {
            Text(name,fontSize=SocialTokens.body,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(if (power>0f) "${Patente.entries.getOrNull(rank)?.titulo ?: "Recruta"} · ${power.toInt()} Power" else "Iniciante · Ainda sem Power",color=TextoSecundario,fontSize=SocialTokens.caption)
        }
        Icon(Icons.Outlined.ChevronRight,"Ver perfil de $name",tint=TextoSecundario)
    }
}
