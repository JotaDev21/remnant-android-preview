package com.liferpg.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferpg.MainViewModel
import com.liferpg.data.sync.*
import com.liferpg.domain.Patente
import com.liferpg.ui.theme.*
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable internal fun TrainingPacts(vm: MainViewModel) {
    Page("AMIGOS / COOP / PACTOS", "Juntos,\nalém da névoa.") { TrainingPactsContent(vm) }
}

@Composable internal fun TrainingPactsContent(vm: MainViewModel) {
    val rows by vm.pactos.collectAsStateWithLifecycle()
    val busy by vm.pactosLoading.collectAsStateWithLifecycle()
    val error by vm.pactosError.collectAsStateWithLifecycle()
    val account by vm.sessaoGlobal.collectAsStateWithLifecycle()
    val friends by vm.amizadesArena.collectAsStateWithLifecycle()
    val preferences by vm.prefsEfetivas.collectAsStateWithLifecycle()
    var create by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(account?.userId) { vm.carregarPactos(); vm.carregarSocial(silencioso = true) }
    Column(verticalArrangement=Arrangement.spacedBy(SocialTokens.gap)) {
        Text("De dois a cinco aliados contra um chefe. Cada um escolhe sua meta e contribui cumprindo o próprio compromisso.", color = TextoSecundario, fontSize = 14.sp, lineHeight = 21.sp)
        Action("Convidar aliados", !busy && friends.any { it.status == "accepted" }) { create = true }
        if (friends.none { it.status == "accepted" }) Text("Adicione e aceite um amigo para formar seu primeiro pacto.", color = TextoSecundario, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Seus pactos", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::carregarPactos, enabled = !busy) { Text(if (busy) "Atualizando…" else "Atualizar") }
        }
        if (error != null) Panel { Text(error!!, color = TextoSecundario, fontSize = 13.sp, lineHeight = 19.sp) }
        if (rows.isEmpty() && error == null && !busy) Text("Sua primeira expedição começa com um convite.", color = TextoSecundario, fontSize = 13.sp)
        rows.forEach { pact -> key(pact.id) { PactCard(pact, account?.userId.orEmpty(), preferences.diasPorSemana, busy,
            accept = { goal -> vm.responderPacto(pact.id, true, goal) }, decline = { vm.responderPacto(pact.id, false, preferences.diasPorSemana.coerceIn(1,7)) },
            start = { vm.iniciarPacto(pact.id) }, cancel = { vm.cancelarPacto(pact.id) }) } }
        Text("Contribuições usam dias com séries válidas registradas após o início do pacto, uma vez por dia. Cargas e séries manuais são declaradas; a câmera valida movimentos localmente. Fotos corporais não entram no pacto.", color = TextoSecundario, fontSize = 11.sp, lineHeight = 18.sp)
    }
    if (create) PactSetup(friends.filter { it.status == "accepted" }, preferences.diasPorSemana, busy, { create = false }) { name, boss, targets, goal ->
        vm.criarPacto(name, boss, targets, goal) { create = false }
    }
}

@Composable internal fun PactCard(pact: TrainingPact, uid: String, defaultGoal: Int, busy: Boolean,
    accept: (Int) -> Unit, decline: () -> Unit, start: () -> Unit, cancel: () -> Unit) {
    var goal by rememberSaveable(pact.id) { mutableIntStateOf(defaultGoal.coerceIn(1, 7)) }
    var confirmCancel by rememberSaveable(pact.id) { mutableStateOf(false) }
    val mine = pact.members.firstOrNull { it.uid == uid }
    val pending = mine?.status == "pending" && pact.status == "forming"
    val remaining by animateIntAsState(((1f - pact.progress) * pact.participants.size * 100).toInt(), tween(650), label = "cooperativeBossHealth")
    Panel {
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Eyebrow(when (pact.status) { "forming" -> if (pending) "VOCÊ FOI CONVIDADO" else "REUNINDO ALIADOS"; "active" -> "EXPEDIÇÃO EM ANDAMENTO"; "won" -> "CHEFE DERROTADO"; "expired" -> "EXPEDIÇÃO ENCERRADA"; else -> "PACTO CANCELADO" })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Insignia(when(pact.boss) { "CRYPT" -> Patente.DIAMANTE; "ASH" -> Patente.BRUTO; else -> Patente.OURO }, 62.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(pact.name, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text(pact.bossName, color = TextoSecundario, fontSize = 12.sp)
                }
            }
            if (pact.status != "forming") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (pact.status == "won") "Legado conquistado pelo grupo" else "Vida do chefe", color = TextoSecundario, fontSize = 12.sp)
                    if (pact.status != "won") Text("$remaining / ${pact.participants.size * 100}", fontSize = 12.sp)
                }
                HearthProgress(pact.progress)
                pact.endsAt?.let { Text("Prazo · ${pactDate(it)}", color = TextoSecundario, fontSize = 11.sp) }
            } else Text("Convites até ${pactDate(pact.inviteDeadline)}. O dono inicia quando houver pelo menos dois participantes; a batalha dura sete dias.", color = TextoSecundario, fontSize = 11.sp, lineHeight = 17.sp)
            pact.members.forEach { member ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(member.nickname + if (member.uid == uid) " · você" else "", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(when(member.status) { "pending" -> "Convite aguardando resposta"; "declined" -> "Não participa"; else -> "${member.days}/${member.goal} dias · ${(member.contribution * 100).toInt()}% do compromisso" }, color = TextoSecundario, fontSize = 11.sp)
                    }
                    if (member.status == "accepted") Text("${(member.contribution * 100).toInt()} / 100", fontSize = 12.sp)
                }
            }
            if (pending) {
                Text("Sua meta para esta expedição", fontSize = 12.sp)
                GoalChoices(goal) { goal = it }
                Action("Aceitar e assumir $goal dias", !busy) { accept(goal) }
                TextButton(onClick = decline, enabled = !busy) { Text("Recusar convite", color = TextoSecundario) }
            }
            if (pact.owner == uid && pact.status == "forming") Action("Iniciar expedição", !busy && pact.participants.size >= 2) { start() }
            if (pact.owner == uid && pact.status in listOf("forming", "active")) TextButton(onClick = { confirmCancel = true }, enabled = !busy) { Text("Cancelar pacto", color = TextoSecundario) }
        }
    }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false }, title = { Text("Cancelar o pacto?") },
        text = { Text("Os treinos de todos ficam preservados. Esta expedição será encerrada para o grupo.") },
        confirmButton = { TextButton(onClick = { confirmCancel = false; cancel() }) { Text("Cancelar pacto") } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Continuar") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PactSetup(friends: List<AmizadeArena>, defaultGoal: Int, busy: Boolean, close: () -> Unit,
    save: (String, String, List<String>, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("Pacto da Forja") }
    var boss by rememberSaveable { mutableStateOf("FORGE") }
    var goal by rememberSaveable { mutableIntStateOf(defaultGoal.coerceIn(1, 7)) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    ModalBottomSheet(onDismissRequest = { if (!busy) close() }, containerColor = Fundo) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Forme seu pacto.", fontSize = 27.sp, fontWeight = FontWeight.SemiBold) }
            item { OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nome da expedição") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth()) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("FORGE" to "Forja", "CRYPT" to "Cripta", "ASH" to "Cinzas").forEach { (id, label) -> Choice(label, boss == id) { if (!busy) boss = id } }
            } }
            item { Text("Sua meta · dias de treino em sete dias", color = TextoSecundario, fontSize = 12.sp) }
            item { GoalChoices(goal) { if (!busy) goal = it } }
            item { Text("Convide até quatro aliados. Cada um confirma a própria meta ao aceitar.", color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp) }
            friends.forEach { friend -> item(key = friend.jogadorId) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(friend.jogadorId in selected, { checked -> selected = if (checked) selected + friend.jogadorId else selected - friend.jogadorId }, enabled = !busy && (selected.size < 4 || friend.jogadorId in selected))
                    Text(friend.apelido, Modifier.weight(1f), fontSize = 15.sp)
                }
            } }
            item { Action(if (busy) "Enviando…" else "Enviar convite${if (selected.size > 1) "s" else ""}", !busy && selected.isNotEmpty() && name.isNotBlank()) { save(name, boss, selected, goal) } }
        }
    }
}
@Composable private fun GoalChoices(goal: Int, select: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..7).forEach { n -> Choice("$n", n == goal) { select(n) } } }
}
private fun pactDate(value: String): String = runCatching { OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM · HH:mm")) }.getOrDefault("atualize para conferir")
