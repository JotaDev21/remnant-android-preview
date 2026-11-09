package com.liferpg.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.liferpg.MainViewModel
import com.liferpg.notif.Notificacoes
import com.liferpg.data.sync.AmizadeArena
import com.liferpg.data.sync.DesafioArena
import com.liferpg.data.sync.RankingGlobal
import com.liferpg.data.sync.PerfilAmigo
import com.liferpg.domain.SocialSection
import com.liferpg.domain.SocialArea
import com.liferpg.domain.SocialInbox
import com.liferpg.ui.theme.*
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalFocusManager
import com.liferpg.domain.AlimentoEncontrado
import com.liferpg.domain.EstagioFirmamento
import com.liferpg.domain.Patente
import com.liferpg.domain.RankEngine
import com.liferpg.ui.theme.DouradoLoot
import com.liferpg.ui.theme.Neon
import com.liferpg.ui.theme.NeonRoxo
import com.liferpg.ui.theme.Superficie
import com.liferpg.ui.theme.SuperficieAlta
import com.liferpg.ui.theme.TextoSecundario
import kotlinx.coroutines.delay
import kotlin.math.floor
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable internal fun ArenaGlobal(vm: MainViewModel, open: (String) -> Unit) {
    var section by rememberSaveable { mutableStateOf(SocialSection.RANKING) }
    SocialHub(vm,open,section) { section=it }
}

@Composable internal fun SocialHub(vm: MainViewModel, open: (String) -> Unit, section: SocialSection, changeSection: (SocialSection) -> Unit) {
    val context = LocalContext.current
    var notificationGranted by remember { mutableStateOf(Notificacoes.temPermissao(context)) }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationGranted = it
        if (it) vm.carregarSocial(silencioso = true)
    }
    val account by vm.sessaoGlobal.collectAsStateWithLifecycle()
    val leaderboard by vm.leaderboard.collectAsStateWithLifecycle()
    val leaderboardLoading by vm.leaderboardCarregando.collectAsStateWithLifecycle()
    val leaderboardError by vm.leaderboardErro.collectAsStateWithLifecycle()
    val arenaSyncStatus by vm.arenaSyncStatus.collectAsStateWithLifecycle()
    val friendships by vm.amizadesArena.collectAsStateWithLifecycle()
    val challenges by vm.desafiosArena.collectAsStateWithLifecycle()
    val me by vm.resumoCompetitivo.collectAsStateWithLifecycle()
    val profile by vm.perfil.collectAsStateWithLifecycle()
    val myStreak by vm.streakEfetivo.collectAsStateWithLifecycle()
    val loading by vm.authCarregando.collectAsStateWithLifecycle()
    val socialLoading by vm.socialCarregando.collectAsStateWithLifecycle()
    val foundPlayers by vm.cacadoresEncontrados.collectAsStateWithLifecycle()
    val searchLoading by vm.buscaCacadoresCarregando.collectAsStateWithLifecycle()
    val searchError by vm.buscaCacadoresErro.collectAsStateWithLifecycle()
    val friendProfile by vm.perfilAmigo.collectAsStateWithLifecycle()
    val friendProfileLoading by vm.perfilAmigoCarregando.collectAsStateWithLifecycle()
    val myRecords by vm.meusRecordes.collectAsStateWithLifecycle()
    val myWeek by vm.revisaoSemanal.collectAsStateWithLifecycle()
    val catalog by vm.exercicios.collectAsStateWithLifecycle()
    val incomingFriends = friendships.count { it.status == "pending" && !it.enviadaPorMim }
    val incomingDuels = challenges.count { it.status == "pending" && !it.euDesafiei }
    val circleState by vm.circles.state.collectAsStateWithLifecycle()
    val circleInvites = if(circleState.accountId==account?.userId) circleState.circles.count { it.status=="pending" } else 0
    val pacts by vm.pactos.collectAsStateWithLifecycle()
    val incomingPacts=pacts.count { it.status=="forming" && it.members.any { m -> m.uid==account?.userId && m.status=="pending" } }
    val inbox=SocialInbox(incomingFriends,circleInvites,incomingPacts,incomingDuels)
    val focus=LocalFocusManager.current
    LaunchedEffect(section) { focus.clearFocus() }
    var selectedPlayerId by rememberSaveable(account?.userId) { mutableStateOf<String?>(null) }
    var friendQuery by rememberSaveable(account?.userId) { mutableStateOf("") }
    val selected = leaderboard.firstOrNull { it.userId == selectedPlayerId }
        ?: foundPlayers.firstOrNull { it.userId == selectedPlayerId }
        ?: friendships.firstOrNull { it.jogadorId == selectedPlayerId }?.let {
            RankingGlobal(0, it.apelido, it.powerScore, it.patente, it.jogadorId)
        }
    LaunchedEffect(selectedPlayerId, friendships) {
        selectedPlayerId?.let(vm::carregarPerfilAmigo)
    }
    LaunchedEffect(account?.userId) {
        if (account != null) {
            Notificacoes.limparConvitesArena(context)
            vm.carregarSocial(silencioso=true)
            vm.circles.load()
        }
    }
    LaunchedEffect(section,account?.userId) {
        if(account!=null && section==SocialSection.RANKING) vm.carregarLeaderboard()
    }
    key(section) {
    Page("REMNANT / COMUNIDADE", when(section.area) { SocialArea.FRIENDS -> "Amigos."; SocialArea.COOP -> "Treine junto."; SocialArea.COMPETITIVE -> "Supere-se." }) {
        if (!vm.arenaDisponivel) {
            Panel { Icon(Icons.Outlined.CloudOff, null, tint = TextoSecundario); Text("Arena indisponível nesta instalação.", fontWeight = FontWeight.Bold); Text("O restante do seu progresso continua local e preservado.", color = TextoSecundario, fontSize = 12.sp) }
            return@Page
        }
        if (account == null) {
            Panel { Text("Sua sessão encerrou. Entre novamente para acessar a Arena.", color = TextoSecundario) }
        } else {
            SocialHubHeader(section,inbox,friendships.count { it.status=="accepted" }) { selectedPlayerId=null; changeSection(it) }
            CircleConversationNotices(if(circleState.accountId==account?.userId) circleState.notices else emptyList()) { notice ->
                changeSection(SocialSection.CIRCLES)
                vm.circles.conversation.open(notice.circle,notice.post)
            }
            if(socialLoading) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth(),color=Neon)
            if (!notificationGranted && Build.VERSION.SDK_INT >= 33) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Receba avisos de convites e conversas", Modifier.weight(1f), color = TextoSecundario, fontSize = 12.sp)
                    TextButton(onClick = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Ativar", color = Neon, fontWeight = FontWeight.Bold)
                    }
                }
            }
            when (section) {
                SocialSection.CIRCLES -> CircleArenaContent(vm,inspect={ selectedPlayerId=it },train={ open(RemnantRoute.tab(3)) })
                SocialSection.PACTS -> TrainingPactsContent(vm)
                SocialSection.RANKING -> {
                    RankingBoard(
                        rows = leaderboard,
                        myId = account?.userId.orEmpty(),
                        myName = profile?.nome?.ifBlank { "Caçador" } ?: "Caçador",
                        myPower = me.powerScore,
                        myRank = me.rotulo,
                        myLevel = RankEngine.nivelDoXp(profile?.xpTotal ?: 0L),
                        status = arenaSyncStatus,
                        loading = leaderboardLoading,
                        error = leaderboardError,
                        refresh = vm::carregarLeaderboard,
                        publish = { vm.publicarRankingAgora(avisar = true) },
                        inspect = { selectedPlayerId = it },
                    )
                }
                SocialSection.FRIENDS -> FriendsHub(
                    friendships = friendships,
                    myPower = me.powerScore,
                    foundPlayers = foundPlayers,
                    searchLoading = searchLoading,
                    searchError = searchError,
                    query = friendQuery,
                    onQueryChange = { friendQuery = it; vm.buscarCacadores(it) },
                    inviteCode = account?.userId?.let(::arenaInviteCode).orEmpty(),
                    busy = socialLoading,
                    inspect = { selectedPlayerId = it.jogadorId },
                    inspectPlayer = { selectedPlayerId = it.userId },
                    addFriend = vm::solicitarAmizade,
                    accept = { vm.responderAmizade(it.id, true) },
                    decline = { vm.responderAmizade(it.id, false) },
                )
                SocialSection.DUELS -> ChallengesHub(
                    challenges = challenges,
                    myUserId = account?.userId.orEmpty(),
                    busy = socialLoading,
                    inspect = { selectedPlayerId = it.oponenteId },
                    accept = { vm.responderDesafio(it.id, true) },
                    decline = { vm.responderDesafio(it.id, false) },
                    cancel = { vm.cancelarDesafio(it.id) },
                )
            }
        }
    }
    }
    selected?.let { player ->
        PlayerProfileSheet(
            player = player,
            myPower = me.powerScore,
            myLevel = RankEngine.nivelDoXp(profile?.xpTotal ?: 0L),
            myStreak = myStreak,
            myMuscleRanks = me.ratings.mapKeys { it.key.nomeExibicao }.mapValues { it.value.rotulo },
            myRecords = myRecords,
            myWeek = myWeek,
            catalog = catalog,
            copyRoutine = { routine -> vm.copiarTreinoAmigo(routine, player.apelido) { selectedPlayerId = null; open(RemnantRoute.ROUTINES) } },
            relationship = friendships.firstOrNull { it.jogadorId == player.userId },
            friendProfile = friendProfile,
            friendProfileLoading = friendProfileLoading,
            busy = socialLoading,
            close = { selectedPlayerId = null },
            addFriend = { vm.solicitarAmizade(player.userId) },
            acceptFriend = { id -> vm.responderAmizade(id, true) },
            declineFriend = { id -> vm.responderAmizade(id, false) },
            removeFriend = { vm.removerAmizade(player.userId) },
            duel24 = { vm.criarDesafio(player.userId, 24) },
            duel7d = { vm.criarDesafio(player.userId, 168) },
        )
    }
}

@Composable private fun RankingBoard(
    rows: List<RankingGlobal>, myId: String, myName: String, myPower: Float,
    myRank: String, myLevel: Int, status: String?, loading: Boolean, error: String?,
    refresh: () -> Unit, publish: () -> Unit, inspect: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var visibleCount by rememberSaveable { mutableIntStateOf(20) }
    val me = rows.firstOrNull { it.userId == myId }
    val competitors = rows.filter { it.temPower }
    val leader = competitors.firstOrNull()
    val distance = leader?.let { (it.powerScore - myPower).coerceAtLeast(0f).toInt() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF132630), Color(0xFF101820), Color(0xFF1D1830))))
            .border(1.dp, Neon.copy(alpha = .36f), RoundedCornerShape(25.dp)).padding(19.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Eyebrow("CLASSIFICAÇÃO GLOBAL", Neon)
                Text("O topo é\nconquistado.", fontSize = 29.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(rows.size.toString().padStart(2, '0'), color = DouradoLoot, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Eyebrow("CAÇADORES", TextoSecundario)
            }
        }
        Text(
            if (distance == null) "Você já faz parte da Arena. Seu primeiro Power começa nos treinos."
            else if (distance == 0) "Você está na linha de frente desta temporada."
            else "Faltam $distance Power para alcançar o primeiro lugar.",
            color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp,
        )
        HorizontalDivider(color = Neon.copy(alpha = .20f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Insignia(size = 54.dp, stage = EstagioFirmamento.deNivel(myLevel))
            Column(Modifier.weight(1f)) {
                Eyebrow(if (me?.temPower == true) "SUA POSIÇÃO · #${me.posicao}" else if (me != null) "NA ARENA · INICIANDO" else "CONECTANDO À ARENA", Neon)
                Text(myName, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(myRank, color = TextoSecundario, fontSize = 11.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(myPower.toInt().toString(), fontSize = 21.sp, fontWeight = FontWeight.Black, color = Neon)
                Eyebrow("POWER", TextoSecundario)
            }
        }
        status?.let { Text(it, color = if (it.startsWith("Perfil publicado")) Neon else DouradoLoot, fontSize = 11.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = publish) { Text("Sincronizar Power", color = Neon, fontWeight = FontWeight.Bold) }
            TextButton(onClick = refresh, enabled = !loading) { Text("Atualizar lista", color = NeonRoxo, fontWeight = FontWeight.Bold) }
        }
    }

    if (loading && rows.isEmpty()) CircularProgressIndicator()
    error?.let { Panel { Text(it, color = DouradoLoot); TextButton(onClick = refresh) { Text("Tentar novamente") } } }
    if (rows.isEmpty() && !loading && error == null) Panel {
        Eyebrow("A ARENA ESTÁ ABERTA", DouradoLoot)
        Text("Você pode ser o primeiro caçador desta temporada.", fontWeight = FontWeight.Bold)
        Text("Contas confirmadas entram automaticamente. Atualize a lista para conferir a conexão.", color = TextoSecundario, fontSize = 12.sp)
    }
    if (rows.isEmpty()) return

    if (competitors.isNotEmpty()) {
        Section("Pódio da temporada")
        RankingChampion(competitors[0], competitors[0].userId == myId) { inspect(competitors[0].userId) }
        if (competitors.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            competitors.drop(1).take(2).forEach { row ->
                RankingPodiumSide(row, row.userId == myId, Modifier.weight(1f)) { inspect(row.userId) }
            }
            if (competitors.size == 2) Spacer(Modifier.weight(1f))
        }
    }

    Section("Todos os caçadores · ${rows.size}", "Atualizar", refresh)
    OutlinedTextField(
        query, { query = it; visibleCount = 20 }, Modifier.fillMaxWidth(),
        placeholder = { Text("Buscar caçador pelo apelido") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
    )
    val filtered = remember(rows, query) {
        val term = com.liferpg.domain.BuscaExercicios.normalizar(query)
        if (term.isBlank()) rows else rows.filter {
            com.liferpg.domain.BuscaExercicios.normalizar(it.apelido).contains(term)
        }
    }
    if (filtered.isEmpty()) Text("Nenhum caçador com esse apelido nesta temporada.", color = TextoSecundario, fontSize = 12.sp)
    filtered.take(visibleCount).forEach { row ->
        RankingPlayerRow(row, row.userId == myId) { inspect(row.userId) }
    }
    if (filtered.size > visibleCount) Action("Ver mais ${minOf(20, filtered.size - visibleCount)} caçadores") {
        visibleCount += 20
    }
}

@Composable private fun RankingChampion(row: RankingGlobal, isMe: Boolean, inspect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF403015), Color(0xFF212735))))
            .border(1.dp, DouradoLoot.copy(alpha = .57f), RoundedCornerShape(22.dp))
            .clickable(enabled = !isMe, onClick = inspect).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Insignia(size = 69.dp, stage = EstagioFirmamento.de(row.powerScore))
        Column(Modifier.weight(1f)) {
            Eyebrow("#01 · LÍDER", DouradoLoot)
            Text(row.apelido, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Patente.entries.getOrNull(row.patente)?.titulo ?: "Recruta", color = TextoSecundario, fontSize = 12.sp)
            if (!isMe) Text("VER PERFIL ›", color = DouradoLoot, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(row.powerScore.toInt().toString(), color = DouradoLoot, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Eyebrow("POWER", TextoSecundario)
        }
    }
}

@Composable private fun RankingPodiumSide(row: RankingGlobal, isMe: Boolean, modifier: Modifier = Modifier, inspect: () -> Unit) {
    val accent = if (row.posicao == 2) Color(0xFFC5D5E2) else Color(0xFFD69A72)
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(SuperficieAlta)
            .border(1.dp, accent.copy(alpha = .35f), RoundedCornerShape(18.dp))
            .clickable(enabled = !isMe, onClick = inspect).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Eyebrow("#0${row.posicao}", accent)
        Text(row.apelido, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${row.powerScore.toInt()} POWER", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text(Patente.entries.getOrNull(row.patente)?.titulo ?: "Recruta", color = TextoSecundario, fontSize = 10.sp)
    }
}

@Composable private fun RankingPlayerRow(row: RankingGlobal, isMe: Boolean, inspect: () -> Unit) {
    val accent = if (isMe) Neon else if (row.posicao in 1..3) DouradoLoot else TextoSecundario
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (isMe) Color(0xFF18313A) else SuperficieAlta.copy(alpha = .63f))
            .border(1.dp, accent.copy(alpha = if (isMe) .53f else .14f), RoundedCornerShape(16.dp))
            .clickable(enabled = !isMe, onClick = inspect).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text(if (row.temPower) "#${row.posicao}" else "—", color = accent, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.width(39.dp))
        Column(Modifier.weight(1f)) {
            Text(if (isMe) "${row.apelido} · você" else row.apelido, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (row.temPower) Patente.entries.getOrNull(row.patente)?.titulo ?: "Recruta" else "Ainda sem Power", color = TextoSecundario, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (row.temPower) row.powerScore.toInt().toString() else "INICIANTE", color = accent, fontWeight = FontWeight.Black, fontSize = if (row.temPower) 17.sp else 10.sp)
            Text(if (isMe) "POWER" else "VER PERFIL ›", color = TextoSecundario, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable private fun PlayerProfileSheet(
    player: RankingGlobal,
    myPower: Float,
    myLevel: Int,
    myStreak: Int,
    myMuscleRanks: Map<String, String>,
    myRecords: List<com.liferpg.data.sync.RecordeAmigo>,
    myWeek: com.liferpg.domain.WeeklyReview,
    catalog: List<com.liferpg.domain.Exercicio>,
    copyRoutine: (com.liferpg.data.sync.RotinaAmigo) -> Unit,
    relationship: AmizadeArena?,
    friendProfile: PerfilAmigo?,
    friendProfileLoading: Boolean,
    busy: Boolean,
    close: () -> Unit,
    addFriend: () -> Unit,
    acceptFriend: (String) -> Unit,
    declineFriend: (String) -> Unit,
    removeFriend: () -> Unit,
    duel24: () -> Unit,
    duel7d: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = sheet,
        containerColor = Superficie,
        scrimColor = Color.Black.copy(alpha = .76f),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(48.dp, 5.dp).clip(RoundedCornerShape(8.dp)).background(Neon.copy(alpha = .55f)))
        },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 42.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Insignia(size = 82.dp, stage = friendProfile?.let { EstagioFirmamento.deNivel(it.level) } ?: EstagioFirmamento.de(player.powerScore))
                    Column(Modifier.weight(1f)) {
                        Eyebrow(if (relationship?.status == "accepted") "CRÔNICA DE ALIADO" else "DOSSIÊ PÚBLICO", NeonRoxo)
                        Text(player.apelido, fontSize = 25.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Caçador ativo nesta temporada", color = TextoSecundario, fontSize = 11.sp)
                    }
                }
                TextButton(onClick = close) { Text("✕", fontSize = 18.sp) }
            }
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat(if (player.temPower) player.powerScore.toInt().toString() else "—", "POWER")
                    Stat(if (player.temPower) Patente.entries.getOrNull(player.patente)?.titulo ?: "Recruta" else "Iniciante", "PATENTE")
                    Stat(if (player.posicao > 0) "#${player.posicao}" else "—", "ARENA")
                }
                val gap = player.powerScore - myPower
                Text(
                    if (!player.temPower) "Ainda sem Power. Vocês já podem se adicionar como aliados."
                    else if (gap >= 0) "ALVO // ${gap.toInt()} de poder à sua frente" else "VANTAGEM // você lidera por ${(-gap).toInt()}",
                    color = if (gap > 0) DouradoLoot else Neon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
            when {
                relationship == null -> SocialButton("＋  ADICIONAR COMO ALIADO", NeonRoxo, !busy, addFriend)
                relationship.status == "pending" && relationship.enviadaPorMim -> {
                    SocialStatus("PEDIDO ENVIADO", "Aguardando ${player.apelido} responder.", NeonRoxo)
                    TextButton(onClick = removeFriend, enabled = !busy) { Text("Cancelar pedido") }
                }
                relationship.status == "pending" -> {
                    SocialButton("✓  ACEITAR ALIANÇA", Neon, !busy) { acceptFriend(relationship.id) }
                    SocialButton("RECUSAR", Color(0xFF475569), !busy) { declineFriend(relationship.id) }
                }
                else -> {
                    SocialStatus("ALIADO", "Vocês podem acompanhar a evolução e duelar.", Neon)
                    if (friendProfileLoading) CircularProgressIndicator(color = Neon, modifier = Modifier.size(24.dp))
                    else if (friendProfile != null) FriendArchive(friendProfile, myLevel, myStreak, myMuscleRanks, myRecords, catalog, copyRoutine, myWeek)
                    else Text("Esse aliado ainda não publicou os detalhes nesta versão.", color = TextoSecundario, fontSize = 12.sp)
                    TextButton(onClick = removeFriend, enabled = !busy) { Text("Remover amizade") }
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = .08f))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Eyebrow("DUELO DE ASCENSÃO", DouradoLoot)
                Text("Vence quem conquistar mais Power depois do aceite. A patente atual não dá vantagem.", color = TextoSecundario, fontSize = 12.sp)
            }
            SocialButton("⚡  DUELO RELÂMPAGO  ·  24H", DouradoLoot, !busy, duel24)
            SocialButton("✦  ASCENSÃO TOTAL  ·  7 DIAS", NeonRoxo, !busy, duel7d)
        }
    }
}

@Composable private fun SocialStatus(title: String, subtitle: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = .10f))
            .border(1.dp, color.copy(alpha = .32f), RoundedCornerShape(18.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(12.dp)).background(color))
        Column { Text(title, color = color, fontWeight = FontWeight.Black); Text(subtitle, color = TextoSecundario, fontSize = 11.sp) }
    }
}

@Composable private fun SocialButton(text: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val faceOffset by animateDpAsState(if (pressed) 6.dp else 0.dp, spring(dampingRatio = .62f, stiffness = 720f), label = "socialButtonDepth")
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth().height(64.dp)) {
        Box(Modifier.fillMaxWidth().height(58.dp).offset(y = 6.dp).clip(shape).background(color.copy(alpha = if (enabled) .34f else .12f)))
        Box(
            Modifier.fillMaxWidth().height(58.dp).offset(y = faceOffset).clip(shape)
                .background(color.copy(alpha = if (enabled) .96f else .24f))
                .border(1.dp, Color.White.copy(alpha = if (enabled) .18f else .05f), shape)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }.padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = if (enabled) Color(0xFF07110F) else TextoSecundario, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = .5.sp)
        }
    }
}

private fun arenaInviteCode(userId: String): String =
    userId.replace("-", "").takeIf { it.length >= 12 }
        ?.take(12)?.uppercase()?.let { "REM-$it" }.orEmpty()

@Composable private fun CompactSocialAction(
    label: String,
    color: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.height(44.dp)) {
        Box(Modifier.fillMaxWidth().height(39.dp).offset(y = 5.dp).clip(shape).background(color.copy(alpha = .28f)))
        Box(
            Modifier.fillMaxWidth().height(39.dp).clip(shape)
                .background(color.copy(alpha = if (enabled) .94f else .24f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = if (enabled) Color(0xFF07110F) else TextoSecundario, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable private fun ChallengesHub(
    challenges: List<DesafioArena>,
    myUserId: String,
    busy: Boolean,
    inspect: (DesafioArena) -> Unit,
    accept: (DesafioArena) -> Unit,
    decline: (DesafioArena) -> Unit,
    cancel: (DesafioArena) -> Unit,
) {
    val invitations = challenges.filter { it.status == "pending" && !it.euDesafiei }
    val active = challenges.filter { it.status == "active" }
    val sent = challenges.filter { it.status == "pending" && it.euDesafiei }
    val history = challenges.filter { it.status == "completed" || it.status == "declined" || it.status == "cancelled" }
    var showAllHistory by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF28203A), Color(0xFF123137))))
            .border(1.dp, NeonRoxo.copy(alpha = .32f), RoundedCornerShape(21.dp)).padding(17.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column { Eyebrow("DISPUTAS AO VIVO", NeonRoxo); Text(active.size.toString().padStart(2, '0'), fontSize = 33.sp, fontWeight = FontWeight.Black) }
        Column(horizontalAlignment = Alignment.End) {
            Eyebrow("VITÓRIAS", DouradoLoot)
            Text(history.count { it.status == "completed" && it.vencedorId == myUserId }.toString().padStart(2, '0'),
                fontSize = 33.sp, fontWeight = FontWeight.Black, color = DouradoLoot)
        }
    }
    Text("A disputa mede o Power conquistado após o aceite. Quem mais evoluir vence; alcançar a meta encerra o duelo antes do prazo.",
        color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp)

    if (invitations.isNotEmpty()) {
        Section("CONVOCAÇÕES PARA VOCÊ · ${invitations.size}")
        invitations.forEach { duel ->
            DuelCard(duel, myUserId, now, inspect, busy, accept, decline, cancel)
        }
    }
    if (active.isNotEmpty()) {
        Section("EM CURSO · ${active.size}")
        active.forEach { duel -> DuelCard(duel, myUserId, now, inspect, busy, accept, decline, cancel) }
    }
    if (sent.isNotEmpty()) {
        Section("AGUARDANDO RESPOSTA · ${sent.size}")
        sent.forEach { duel -> DuelCard(duel, myUserId, now, inspect, busy, accept, decline, cancel) }
    }
    if (challenges.isEmpty()) Panel {
        Eyebrow("SEU PRIMEIRO DUELO", Neon)
        Text("Abra o perfil de um caçador no ranking ou de um aliado e convide para 24 horas ou 7 dias.", color = TextoSecundario)
    }
    if (history.isNotEmpty()) {
        Section("HISTÓRICO · ${history.size}")
        history.let { if (showAllHistory) it else it.take(3) }.forEach { duel ->
            DuelCard(duel, myUserId, now, inspect, busy, accept, decline, cancel)
        }
        if (history.size > 3) TextButton(onClick = { showAllHistory = !showAllHistory }) {
            Text(if (showAllHistory) "Mostrar menos" else "Ver todos os confrontos", color = NeonRoxo)
        }
    }
}

@Composable private fun DuelCard(
    duel: DesafioArena,
    myUserId: String,
    now: Long,
    inspect: (DesafioArena) -> Unit,
    busy: Boolean,
    accept: (DesafioArena) -> Unit,
    decline: (DesafioArena) -> Unit,
    cancel: (DesafioArena) -> Unit,
) {
    val active = duel.status == "active"
    val finished = duel.status == "completed"
    val accent = when { active -> Neon; finished -> DouradoLoot; else -> NeonRoxo }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SuperficieAlta)
            .border(1.dp, accent.copy(alpha = .38f), RoundedCornerShape(20.dp)).padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable { inspect(duel) }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow(when (duel.status) {
                    "active" -> "ASCENSÃO EM CURSO"
                    "completed" -> "CONFRONTO ENCERRADO"
                    "pending" -> if (duel.euDesafiei) "CONVITE ENVIADO" else "VOCÊ FOI DESAFIADO"
                    else -> "CONVITE ENCERRADO"
                }, accent)
                Text("VOCÊ  ×  ${duel.oponente.uppercase()}", fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (duel.duracaoHoras == 24) "24H" else "7 DIAS", color = accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        when (duel.status) {
            "pending" -> if (!duel.euDesafiei) {
                Text("${duel.oponente} propôs uma meta de +${duel.metaPower.toInt()} Power. O tempo começa quando você aceitar.", color = TextoSecundario, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CompactSocialAction("ACEITAR", Neon, !busy, Modifier.weight(1f)) { accept(duel) }
                    CompactSocialAction("RECUSAR", Color(0xFF64748B), !busy, Modifier.weight(1f)) { decline(duel) }
                }
            } else {
                Text("Aguardando ${duel.oponente} aceitar. O relógio ainda não começou.", color = TextoSecundario, fontSize = 12.sp)
                TextButton(onClick = { cancel(duel) }, enabled = !busy) { Text("Cancelar convite", color = TextoSecundario) }
            }
            "active", "completed" -> {
                val myGain = duel.meuGanho.toInt()
                val theirGain = duel.ganhoOponente.toInt()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column { Eyebrow("VOCÊ", Neon); Text("+$myGain", color = Neon, fontSize = 29.sp, fontWeight = FontWeight.Black) }
                    Text("META +${duel.metaPower.toInt()}", color = TextoSecundario, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Column(horizontalAlignment = Alignment.End) { Eyebrow(duel.oponente.uppercase(), NeonRoxo); Text("+$theirGain", color = NeonRoxo, fontSize = 29.sp, fontWeight = FontWeight.Black) }
                }
                Meter((duel.meuGanho / duel.metaPower).coerceIn(0f, 1f), Neon)
                Meter((duel.ganhoOponente / duel.metaPower).coerceIn(0f, 1f), NeonRoxo)
                val deadline = duel.terminaEm?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
                Text(
                    if (active) {
                        val lead = myGain - theirGain
                        when {
                            deadline != null && deadline <= now -> "Prazo encerrado · atualizando resultado"
                            lead > 0 -> "Você está $lead Power à frente${deadline?.let { " · ${arenaTimeLeft(it - now)} restantes" }.orEmpty()}"
                            lead < 0 -> "Faltam ${-lead + 1} Power para virar${deadline?.let { " · ${arenaTimeLeft(it - now)} restantes" }.orEmpty()}"
                            else -> "Empate agora${deadline?.let { " · ${arenaTimeLeft(it - now)} restantes" }.orEmpty()}"
                        }
                    } else when {
                        duel.vencedorId == null -> "EMPATE · evolução equivalente"
                        duel.vencedorId == myUserId -> "VITÓRIA · você evoluiu mais neste duelo"
                        else -> "${duel.oponente} venceu este duelo"
                    },
                    color = if (finished && duel.vencedorId == myUserId) DouradoLoot else TextoSecundario,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                )
            }
            else -> Text("Convite encerrado sem disputa.", color = TextoSecundario, fontSize = 12.sp)
        }
    }
}

private fun arenaTimeLeft(ms: Long): String {
    val minutes = (ms.coerceAtLeast(0L) / 60_000L).coerceAtLeast(1L)
    return if (minutes >= 1_440) "${minutes / 1_440}d ${minutes % 1_440 / 60}h"
    else if (minutes >= 60) "${minutes / 60}h ${minutes % 60}min"
    else "${minutes}min"
}

@Composable internal fun WgerLibrary(vm: MainViewModel) {
    val refs by vm.wger.collectAsStateWithLifecycle()
    val loading by vm.buscandoWger.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(vm.categoriasWger.firstOrNull()?.id ?: 8) }
    LaunchedEffect(selected) { vm.carregarCategoriaWger(selected) }
    Page("WGER // REFERÊNCIA EXTERNA", "Outra lente\nsobre o movimento.") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.categoriasWger.forEach { (id, name) -> Choice(name, selected == id) { selected = id } }
        }
        if (loading) CircularProgressIndicator()
        if (!loading && refs.isEmpty()) Panel { Text("Nenhum exercício recebido nesta categoria.", color = TextoSecundario) }
        refs.forEach { ref ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SuperficieAlta).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(13.dp)).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .25f))) {
                    ref.imagem?.let { AsyncImage(it, null, Modifier.size(72.dp), contentScale = ContentScale.Crop) }
                }
                Spacer(Modifier.width(14.dp)); Column { Text(ref.nome, fontWeight = FontWeight.SemiBold); Text(ref.categoria, color = TextoSecundario, fontSize = 11.sp) }
            }
        }
    }
}

@Composable internal fun FoodSearch(vm: MainViewModel) {
    val results by vm.resultadosBusca.collectAsStateWithLifecycle()
    val loading by vm.buscandoAlimento.collectAsStateWithLifecycle()
    val notice by vm.avisoBuscaAlimento.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var online by rememberSaveable { mutableStateOf(false) }
    var selectedFoodJson by rememberSaveable { mutableStateOf<String?>(null) }
    Page("NUTRIÇÃO / BIBLIOTECA DE ALIMENTOS", "Seu arsenal\nde alimentos.") {
        NutritionSurface {
            OutlinedTextField(query, { query = it }, label = { Text("Alimento ou marca") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Choice("Brasil · offline", !online) { online = false }
                Choice("Incluir marcas online", online) { online = true }
            }
            Text(if (online) "A base brasileira aparece junto com produtos do Open Food Facts." else "578 alimentos da TACO/Unicamp. Escolha o preparo correspondente ao que consumiu.", color = TextoSecundario, fontSize = 12.sp)
            Action("Buscar alimento", query.trim().length >= 2 && !loading) { vm.buscarAlimento(query, online) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Arroz", "Frango", "Feijão", "Aveia").forEach { name -> Choice(name, query == name) { query = name; vm.buscarAlimento(name, online) } }
            }
        }
        if (loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Text("Consultando alimentos…", color = TextoSecundario, fontSize = 12.sp) }
        notice?.let { Text(it, color = DouradoLoot, fontSize = 12.sp) }
        if (results.isNotEmpty()) Text("${results.size} resultados · toque para conferir a porção", color = TextoSecundario, fontSize = 12.sp)
        results.forEach { food -> FoodResult(food) { selectedFoodJson = com.liferpg.domain.NutritionFoodCodec.escrever(food) } }
        Text("TACO: NEPA/UNICAMP, 4ª ed., 2011. Open Food Facts: ODbL, dados colaborativos; confira o rótulo.", color = TextoSecundario, fontSize = 11.sp)
    }
    selectedFoodJson?.let { raw -> runCatching { com.liferpg.domain.NutritionFoodCodec.ler(raw) }.getOrNull()?.let { FoodPortionDialog(vm, it, 100f, { selectedFoodJson = null }) } }
}

@Composable private fun FoodResult(food: AlimentoEncontrado, click: () -> Unit) {
    NutritionSurface(Modifier.clickable(onClick = click)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            food.imagemUrl?.let { AsyncImage(it, null, Modifier.size(55.dp)); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(food.nome, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                food.marca?.let { Text(it, color = TextoSecundario, fontSize = 11.sp) }
                Text(food.fonte, color = DouradoLoot, fontSize = 10.sp)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Revisar porção", tint = DouradoLoot, modifier = Modifier.size(19.dp))
        }
        fun value(number: Float, bit: Int) = if (food.dadosAusentes and bit == 0) number.toInt().toString() else "—"
        Text("${value(food.calorias, 2)} kcal  ·  P ${value(food.proteinaG, 1)}  C ${value(food.carboG, 4)}  G ${value(food.gorduraG, 8)} g / 100 g", color = TextoSecundario, fontSize = 11.sp)
        if (food.dadosAusentes != 0) Text("Rótulo incompleto · toque para preencher", color = DouradoLoot, fontSize = 11.sp)
    }
}

@Composable internal fun BarcodeFoodScanner(vm: MainViewModel) {
    val context = LocalContext.current
    val found by vm.alimentoEscaneado.collectAsStateWithLifecycle()
    val loading by vm.buscandoBarcode.collectAsStateWithLifecycle()
    var reviewedFoodJson by rememberSaveable { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    var entering by rememberSaveable { mutableStateOf(false) }
    val scanner = remember {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom().build()
        GmsBarcodeScanning.getClient(context, options)
    }
    Page("SCANNER // RASTRO NUTRICIONAL", "Aponte para o\ncódigo de barras.") {
        Panel {
            Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(22.dp)).background(SuperficieAlta), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CenterFocusStrong, null, Modifier.size(72.dp), tint = Neon)
            }
            Text("A leitura acontece no scanner seguro do Google Play Services. A imagem não entra no diário; somente o código do produto é consultado.", color = TextoSecundario, fontSize = 12.sp)
            Action("Abrir scanner", !loading) {
                vm.limparEscaneado()
                scanner.startScan()
                    .addOnSuccessListener { code -> code.rawValue?.let(vm::buscarPorBarcode) }
                    .addOnFailureListener { vm.avisar("Não foi possível abrir o scanner.") }
            }
            TextButton(onClick = { entering = !entering }) { Text("Digitar código de barras") }
            if (entering) {
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(14) }, label = { Text("Código EAN / UPC") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Action("Consultar produto", code.length in listOf(8, 12, 13, 14) && !loading) { vm.buscarPorBarcode(code) }
            }
        }
        if (loading) CircularProgressIndicator(Modifier.size(25.dp))
        found?.let { food ->
            FoodResult(food) { reviewedFoodJson = com.liferpg.domain.NutritionFoodCodec.escrever(food) }
            TextButton(onClick = vm::limparEscaneado) { Text("Descartar leitura") }
        }
    }
    reviewedFoodJson?.let { raw -> runCatching { com.liferpg.domain.NutritionFoodCodec.ler(raw) }.getOrNull()?.let { FoodPortionDialog(vm, it, 100f, { reviewedFoodJson = null }) } }
}

@Composable internal fun TrainingTools() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var weight by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableStateOf("") }
    var total by rememberSaveable { mutableStateOf("") }
    var bar by rememberSaveable { mutableStateOf("20") }
    Page("OFICINA // CÁLCULO DE CARGA", "Números que viram\nexecução.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Choice("Estimativa de 1RM", tab == 0) { tab = 0 }; Choice("Montar anilhas", tab == 1) { tab = 1 } }
        if (tab == 0) {
            val kg = weight.replace(',', '.').toFloatOrNull()
            val count = reps.toIntOrNull()
            val estimate = if (kg != null && count != null && count in 1..30) kg * (1f + count / 30f) else null
            Panel {
                NumberField("Carga usada / kg", weight) { weight = it }; NumberField("Repetições", reps) { reps = it }
                Eyebrow("ESTIMATIVA EPLEY", DouradoLoot)
                Text(estimate?.let { "${Format.peso(it)} kg" } ?: "—", fontSize = 42.sp, fontWeight = FontWeight.Black)
                Text("Estimativa para acompanhar progressão; não é uma ordem para testar carga máxima.", color = TextoSecundario, fontSize = 11.sp)
            }
        } else {
            val target = total.replace(',', '.').toFloatOrNull()
            val barWeight = bar.replace(',', '.').toFloatOrNull()
            val side = if (target != null && barWeight != null) ((target - barWeight).coerceAtLeast(0f) / 2f) else null
            val plates = side?.let(::plateStack).orEmpty()
            Panel {
                NumberField("Carga total / kg", total) { total = it }; NumberField("Peso da barra / kg", bar) { bar = it }
                Eyebrow("CADA LADO", Neon)
                Text(side?.let { "${Format.peso(it)} kg" } ?: "—", fontSize = 38.sp, fontWeight = FontWeight.Black)
                Text(if (plates.isEmpty()) "Informe uma carga válida." else plates.joinToString("  +  ") { Format.peso(it) }, color = TextoSecundario, fontSize = 13.sp)
            }
        }
    }
}

private fun plateStack(target: Float): List<Float> {
    var remaining = target
    val result = mutableListOf<Float>()
    for (plate in listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f, .5f)) {
        val count = floor((remaining + .001f) / plate).toInt()
        repeat(count) { result += plate; remaining -= plate }
    }
    return result
}

@Composable internal fun HealthBridge(vm: MainViewModel) {
    val steps by vm.passosHoje.collectAsStateWithLifecycle()
    Page("HEALTH CONNECT // PONTE LOCAL", "Seu corpo fala\ncom seus dados.") {
        Panel {
            Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(45.dp), tint = Neon)
            Text(if (vm.healthDisponivel) "Health Connect disponível" else "Health Connect indisponível", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("O Remnant lê somente peso e passos autorizados por você. Os dados ficam sob o controle de permissões do Android.", color = TextoSecundario, fontSize = 12.sp)
            Stat(if (steps > 0) steps.toString() else "—", "passos sincronizados hoje")
        }
    }
}
