package com.liferpg.ui

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferpg.MainViewModel
import com.liferpg.data.Perfil
import com.liferpg.domain.ClassePersonagem
import com.liferpg.domain.EstagioFirmamento
import com.liferpg.domain.RankEngine
import com.liferpg.domain.IdentidadePerfil
import com.liferpg.ui.components.GraficoEvolucao
import com.liferpg.ui.theme.Borda
import com.liferpg.ui.theme.DouradoLoot
import com.liferpg.ui.theme.Fundo
import com.liferpg.ui.theme.Neon
import com.liferpg.ui.theme.NeonRoxo
import com.liferpg.ui.theme.Superficie
import com.liferpg.ui.theme.TextoPrimario
import com.liferpg.ui.theme.TextoSecundario
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ArchiveBlue = TextoPrimario
private val ArchiveViolet = TextoSecundario
private val ArchiveShape = RoundedCornerShape(14.dp)

@Composable
internal fun CharacterArchive(vm: MainViewModel, open: (String) -> Unit) {
    val profile by vm.perfil.collectAsStateWithLifecycle()
    val competitive by vm.resumoCompetitivo.collectAsStateWithLifecycle()
    val title by vm.tituloEquipado.collectAsStateWithLifecycle()
    val snapshots by vm.snapshots.collectAsStateWithLifecycle(emptyList())
    val sessions by vm.sessoesRecentes.collectAsStateWithLifecycle(emptyList())
    val trophies by vm.trofeusGanhos.collectAsStateWithLifecycle(emptySet())
    val titles by vm.titulosGanhos.collectAsStateWithLifecycle(emptyList())
    val count by vm.totalSeries.collectAsStateWithLifecycle(0L)
    val steps by vm.passosHoje.collectAsStateWithLifecycle(0L)
    val volumes by vm.volumePorMusculo.collectAsStateWithLifecycle(emptyMap())
    val bossProgress by vm.cameraBosses.collectAsStateWithLifecycle()
    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        if (it.isNotEmpty()) vm.sincronizarSaude()
    }
    val level = RankEngine.nivelDoXp(profile?.xpTotal ?: 0L)
    val stage = EstagioFirmamento.deNivel(level)
    val classDef = ClassePersonagem.entries.firstOrNull { it.name == profile?.classe }
    var editing by rememberSaveable { mutableStateOf(false) }

    if (editing && profile != null) EditIdentitySheet(profile!!, { editing = false }) { name, motto, characterClass, frame, biography, focus, seal ->
        vm.atualizarIdentidade(name, motto, characterClass, frame, biography, focus, seal)
        editing = false
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Fundo),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            IdentityVault(
                name = profile?.nome.orEmpty(),
                subtitle = title?.texto ?: "Sem título equipado",
                className = classDef?.titulo ?: "Aventureiro",
                level = level,
                xp = profile?.xpTotal ?: 0L,
                motto = profile?.lema.orEmpty(),
                frame = profile?.estiloMoldura ?: "CINZAS",
                seal = profile?.seloPerfil ?: "ESPADA",
                power = competitive.powerScore.toInt(),
                rank = competitive.rotulo,
                stage = stage,
                onSystem = { open(RemnantRoute.SYSTEM) },
                onEdit = { editing = true },
            )
        }
        item {
            val p = profile
            if (p != null) Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow("FOCO · ${IdentidadePerfil.focos[p.focoTreino] ?: "Equilíbrio"}".uppercase(), perfilCor(p.estiloMoldura))
                if (p.biografia.isNotBlank()) Text(p.biografia, color = TextoSecundario, fontSize = 13.sp, lineHeight = 20.sp)
                TextButton(onClick = { open(RemnantRoute.RECORDS) }) { Text("Seu arquivo de PRs →", color = perfilCor(p?.estiloMoldura ?: "CINZAS")) }
            }
        }
        item {
            ArchiveSection("CORPO // REGISTRO VIVO", "Medidas, imagem e sinais do mundo real")
            BodyConsole(
                weight = profile?.pesoCorporalKg ?: 0f,
                steps = steps,
                totalSets = count,
                healthAvailable = vm.healthDisponivel,
                onWeight = { open(RemnantRoute.WEIGHT) },
                onPhotos = { open(RemnantRoute.BODY_PHOTOS) },
                onHealth = { if (vm.healthDisponivel) healthLauncher.launch(vm.permissoesHealth) else open(RemnantRoute.HEALTH) },
            )
        }
        item {
            ArchiveSection("ARQUIVO // LEGADO", "O que seus treinos deixaram para trás")
            LegacyRail(
                trophies = trophies.size,
                titles = titles.size,
                bosses = bossProgress.values.sumOf { it.wins },
                onTrophies = { open(RemnantRoute.TROPHIES) },
                onTitles = { open(RemnantRoute.TITLES) },
                onBosses = { open(RemnantRoute.BOSS_HUNT) },
            )
        }
        item {
            ArchiveSection("NEXUS // CONEXÕES", "Serviços e ferramentas recuperados")
            NexusGrid(open)
        }
        item {
            ArchiveSection("TRAJETÓRIA // PODER", "O sinal acumulado da sua evolução")
            EvolutionStrip(snapshots.sortedBy { it.epochDay }.map { it.powerScore }, competitive.powerScore.toInt())
        }
        item {
            ArchiveSection("MAPA // DISTRIBUIÇÃO", "Onde seu volume está concentrado")
            MuscleSignal(volumes.mapKeys { it.key.nomeExibicao })
        }
        item {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(24.dp))
                Section("Treinos recentes", "Ver histórico") { open(RemnantRoute.HISTORY) }
                if (sessions.isEmpty()) Text("Finalize um treino para começar seu arquivo.",color=TextoSecundario,fontSize=14.sp)
                else sessions.take(2).forEach { session ->
                    HistorySessionRow(session) { open(RemnantRoute.sessionHistory(session.id)) }
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            ArchiveWideAction(Icons.Outlined.AutoAwesome, "GALERIA DE ASCENSÕES", "Reveja frequência, nível e patente", DouradoLoot) { open(RemnantRoute.CELEBRATIONS) }
            Spacer(Modifier.height(14.dp))
            ArchiveWideAction(Icons.Outlined.Route, "MEMÓRIA DAS JORNADAS", "Campanha pessoal, capítulos e revisão do REM", ArchiveBlue) { open(RemnantRoute.JOURNEY) }
        }
    }
}

@Composable
private fun IdentityVault(
    name: String,
    subtitle: String,
    className: String,
    level: Int,
    xp: Long,
    motto: String,
    frame: String,
    seal: String,
    power: Int,
    rank: String,
    stage: EstagioFirmamento,
    onSystem: () -> Unit,
    onEdit: () -> Unit,
) {
    val frameColor = perfilCor(frame)
    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Perfil", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text("Editar perfil", color = TextoPrimario, fontSize = 13.sp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(86.dp).border(1.dp, frameColor.copy(alpha = .5f), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                Insignia(size = 72.dp, stage = stage)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name.ifBlank { "Caçador" }, fontSize = 25.sp, lineHeight = 29.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = TextoSecundario, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ProfileSeal(seal, frameColor, Modifier.size(18.dp))
                    Text(className, color = TextoPrimario, fontSize = 12.sp)
                }
            }
        }
        if (motto.isNotBlank()) Text(motto, color = TextoSecundario, fontSize = 13.sp, lineHeight = 19.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("$level", fontSize = 26.sp, fontWeight = FontWeight.SemiBold); Text("nível", fontSize = 11.sp, color = TextoSecundario) }
            Column { Text("$power", fontSize = 26.sp, fontWeight = FontWeight.SemiBold); Text("poder", fontSize = 11.sp, color = TextoSecundario) }
            Column(horizontalAlignment = Alignment.End) {
                Text(rank, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("$xp XP", fontSize = 11.sp, color = TextoSecundario)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Meter(RankEngine.progressoNoNivel(xp), TextoPrimario)
            val next = EstagioFirmamento.entries.getOrNull(stage.ordinal + 1)
            Text(if (next != null) "${stage.titulo} · ${next.titulo} no nível ${next.nivelMinimo}" else stage.titulo,
                color = TextoSecundario, fontSize = 11.sp)
        }
        TextButton(onClick = onSystem) { Text("Missões e desafios →", color = TextoPrimario, fontSize = 12.sp) }
        HorizontalDivider(color = Borda)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditIdentitySheet(
    profile: Perfil,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String) -> Unit,
) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.nome) }
    var motto by rememberSaveable(profile.id) { mutableStateOf(profile.lema) }
    var characterClass by rememberSaveable(profile.id) { mutableStateOf(profile.classe) }
    var frame by rememberSaveable(profile.id) { mutableStateOf(profile.estiloMoldura) }
    var biography by rememberSaveable(profile.id) { mutableStateOf(profile.biografia) }
    var focus by rememberSaveable(profile.id) { mutableStateOf(profile.focoTreino) }
    var seal by rememberSaveable(profile.id) { mutableStateOf(profile.seloPerfil) }
    val stage = EstagioFirmamento.deNivel(RankEngine.nivelDoXp(profile.xpTotal))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Superficie,
        scrimColor = Color.Black.copy(alpha = .78f),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 38.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Eyebrow("FORJE SUA IDENTIDADE", ArchiveBlue)
            Text("Sua crônica", color = TextoPrimario, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("A relíquia é conquistada com nível. A moldura e as palavras são suas.", color = TextoSecundario, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Insignia(size = 74.dp, stage = stage)
                ProfileSeal(seal, perfilCor(frame))
                Column {
                    Text(stage.titulo.uppercase(), color = TextoPrimario, fontWeight = FontWeight.Black)
                    Text("NÍVEL ${RankEngine.nivelDoXp(profile.xpTotal)}", color = TextoSecundario, fontSize = 11.sp)
                }
            }
            OutlinedTextField(
                name, { name = it.take(40) },
                label = { Text("Nome de caçador") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                motto, { motto = it.take(90) },
                label = { Text("Lema / apresentação") },
                supportingText = { Text("Até 90 caracteres · ${motto.length}/90") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(biography, { biography = it.take(240) }, label = { Text("Sua história / objetivos") },
                supportingText = { Text("${biography.length}/240 · visível para amigos aceitos") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
            Eyebrow("FOCO DO CAÇADOR", perfilCor(frame))
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IdentidadePerfil.focos.forEach { (id, label) -> Choice(label, focus == id) { focus = id } }
            }
            Text("Seu foco aparece na crônica. Séries, repetições e recomendações seguem o objetivo configurado em Treino.", color = TextoSecundario, fontSize = 11.sp)
            Eyebrow("SELO PESSOAL", perfilCor(frame))
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IdentidadePerfil.selos.forEach { (id, label) ->
                    Column(Modifier.clickable { seal = id }.border(1.dp, if (seal == id) perfilCor(frame) else Color.Transparent, RoundedCornerShape(14.dp)).padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        ProfileSeal(id, perfilCor(frame)); Text(label, color = TextoSecundario, fontSize = 10.sp)
                    }
                }
            }
            Eyebrow("CLASSE DE TREINO", ArchiveBlue)
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClassePersonagem.entries.forEach { option ->
                    Text(
                        option.titulo,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (characterClass == option.name) ArchiveBlue.copy(alpha = .2f) else Superficie)
                            .border(1.dp, if (characterClass == option.name) ArchiveBlue else Borda, RoundedCornerShape(12.dp))
                            .clickable { characterClass = option.name }.padding(horizontal = 14.dp, vertical = 11.dp),
                        color = if (characterClass == option.name) ArchiveBlue else TextoSecundario,
                        fontSize = 12.sp,
                    )
                }
            }
            Eyebrow("MOLDURA DA CRÔNICA", ArchiveBlue)
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IdentidadePerfil.molduras.forEach { (option, label) ->
                    val color = perfilCor(option)
                    Text(
                        label.uppercase(),
                        modifier = Modifier.width(90.dp).clip(RoundedCornerShape(12.dp))
                            .background(color.copy(alpha = if (frame == option) .19f else .06f))
                            .border(1.dp, color.copy(alpha = if (frame == option) 1f else .35f), RoundedCornerShape(12.dp))
                            .clickable { frame = option }.padding(vertical = 12.dp),
                        color = color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Button(
                onClick = { onSave(name.trim(), motto.trim(), characterClass, frame, biography.trim(), focus, seal) },
                enabled = name.isNotBlank() && name.length <= 40,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ArchiveBlue, contentColor = Fundo),
            ) { Text("SELAR PERFIL", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable private fun CharacterField() {
    val clock = LocalMotion.current
    Canvas(Modifier.fillMaxSize()) {
        val phase = clock.orbit.value * PI.toFloat() / 180f
        for (i in 0..8) {
            val x = size.width * ((i * .173f + .08f) % 1f)
            val y = size.height * ((i * .239f + .12f) % 1f)
            val pulse = .25f + .20f * sin(phase + i)
            drawCircle(ArchiveBlue.copy(alpha = pulse), 1.4.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
        }
        val path = Path().apply {
            moveTo(size.width * .57f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height * .56f)
            lineTo(size.width * .83f, size.height * .72f); lineTo(size.width * .55f, size.height * .72f); close()
        }
        drawPath(path, Brush.linearGradient(listOf(ArchiveViolet.copy(alpha = .13f), Color.Transparent)))
        drawLine(ArchiveBlue.copy(alpha = .18f), androidx.compose.ui.geometry.Offset(0f, size.height * .76f), androidx.compose.ui.geometry.Offset(size.width, size.height * .76f), 1.dp.toPx())
    }
}

@Composable private fun ArchiveSection(title: String, subtitle: String) {
    Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 27.dp, bottom = 12.dp)) {
        Text(title, color = ArchiveBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.7.sp)
        Text(subtitle, color = TextoSecundario, fontSize = 12.sp)
    }
}

@Composable private fun BodyConsole(weight: Float, steps: Long, totalSets: Long, healthAvailable: Boolean, onWeight: () -> Unit, onPhotos: () -> Unit, onHealth: () -> Unit) {
    Column(Modifier.padding(horizontal = 22.dp).clip(ArchiveShape).background(Brush.horizontalGradient(listOf(Superficie, Superficie))).border(1.dp, ArchiveBlue.copy(alpha = .28f), ArchiveShape)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            ArchiveMetric(Format.peso(weight), "KG ATUAL")
            ArchiveMetric(if (steps > 0) steps.toString() else "—", "PASSOS HOJE")
            ArchiveMetric(totalSets.toString(), "SÉRIES")
        }
        HorizontalDivider(color = ArchiveBlue.copy(alpha = .14f))
        Row(Modifier.fillMaxWidth()) {
            ConsoleCommand(Icons.Outlined.Balance, "PESO", Modifier.weight(1f), onWeight)
            ConsoleCommand(Icons.Outlined.CameraAlt, "FOTOS", Modifier.weight(1f), onPhotos)
            ConsoleCommand(Icons.Outlined.FavoriteBorder, if (healthAvailable) "SINCRONIZAR" else "HEALTH", Modifier.weight(1f), onHealth)
        }
    }
}

@Composable private fun ArchiveMetric(value: String, label: String) {
    Column { Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black); Text(label, color = TextoSecundario, fontSize = 8.sp, letterSpacing = 1.2.sp) }
}

@Composable private fun ConsoleCommand(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(18.dp), tint = ArchiveBlue); Spacer(Modifier.height(6.dp)); Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable private fun LegacyRail(trophies: Int, titles: Int, bosses: Int, onTrophies: () -> Unit, onTitles: () -> Unit, onBosses: () -> Unit) {
    Row(Modifier.padding(horizontal = 22.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LegacyArtifact(Icons.Outlined.EmojiEvents, trophies.toString(), "TROFÉUS", DouradoLoot, onTrophies)
        LegacyArtifact(Icons.Outlined.MilitaryTech, titles.toString(), "TÍTULOS", ArchiveViolet, onTitles)
        LegacyArtifact(Icons.Outlined.Whatshot, bosses.toString(), "CHEFES ABATIDOS", TextoPrimario, onBosses)
    }
}

@Composable private fun LegacyArtifact(icon: ImageVector, value: String, label: String, color: Color, onClick: () -> Unit) {
    Column(Modifier.width(142.dp).height(132.dp).clip(ArchiveShape).background(color.copy(alpha = .07f)).border(1.dp, color.copy(alpha = .32f), ArchiveShape).clickable(onClick = onClick).padding(16.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(25.dp)); Spacer(Modifier.weight(1f))
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(label, color = TextoSecundario, fontSize = 9.sp, letterSpacing = 1.5.sp)
    }
}

@Composable private fun NexusGrid(open: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NexusNode(Icons.Outlined.Restaurant, "NUTRIÇÃO", "Câmera do prato, macros e recuperação", DouradoLoot, Modifier.weight(1.35f)) { open(RemnantRoute.NUTRITION) }
            NexusNode(Icons.Outlined.TravelExplore, "WGER", "Referência externa", ArchiveViolet, Modifier.weight(1f)) { open(RemnantRoute.WGER) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NexusNode(Icons.Outlined.Tune, "FERRAMENTAS", "1RM + anilhas", ArchiveBlue, Modifier.weight(1f)) { open(RemnantRoute.TOOLS) }
            NexusNode(Icons.Outlined.Settings, "PREFERÊNCIAS", "Metas, rotina e lembretes", ArchiveBlue, Modifier.weight(1f)) { open(RemnantRoute.SETTINGS) }
        }
    }
}

@Composable private fun NexusNode(icon: ImageVector, title: String, subtitle: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(118.dp).clip(RoundedCornerShape(4.dp, 22.dp, 4.dp, 22.dp)).background(Superficie).border(1.dp, color.copy(alpha = .22f), RoundedCornerShape(4.dp, 22.dp, 4.dp, 22.dp)).clickable(onClick = onClick).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = color); Spacer(Modifier.weight(1f)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(14.dp), tint = color)
        }
        Spacer(Modifier.weight(1f)); Text(title, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
        Text(subtitle, color = TextoSecundario, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun EvolutionStrip(values: List<Float>, power: Int) {
    Column(Modifier.padding(horizontal = 22.dp).clip(ArchiveShape).background(Superficie).border(1.dp, ArchiveViolet.copy(alpha = .28f), ArchiveShape).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column { Text("SINAL ATUAL", color = TextoSecundario, fontSize = 9.sp, letterSpacing = 1.4.sp); Text(power.toString(), color = ArchiveViolet, fontSize = 30.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.weight(1f)); Text("${values.size} LEITURAS", color = TextoSecundario, fontSize = 9.sp, letterSpacing = 1.2.sp)
        }
        Spacer(Modifier.height(14.dp))
        if (values.size >= 2) GraficoEvolucao(values, ArchiveViolet, ArchiveBlue)
        else Text("O gráfico desperta após novos registros.", color = TextoSecundario, fontSize = 12.sp)
    }
}

@Composable private fun MuscleSignal(volumes: Map<String, Int>) {
    val max = volumes.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        if (volumes.isEmpty()) ArchiveEmpty("Registre séries para formar seu mapa corporal.")
        volumes.entries.sortedByDescending { it.value }.take(6).forEachIndexed { index, entry ->
            val target = entry.value.toFloat() / max
            val animated by animateFloatAsState(target, tween(650, index * 60, FastOutSlowInEasing), label = "muscleSignal")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((index + 1).toString().padStart(2, '0'), color = ArchiveBlue, fontSize = 9.sp, modifier = Modifier.width(25.dp))
                Text(entry.key.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(88.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(5.dp).background(Borda)) { Box(Modifier.fillMaxWidth(animated).height(5.dp).background(Brush.horizontalGradient(listOf(ArchiveBlue, ArchiveViolet)))) }
                Text(entry.value.toString(), color = TextoSecundario, fontSize = 10.sp, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable private fun ArchiveEmpty(text: String) { Text(text, color = TextoSecundario, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) }

@Composable private fun ArchiveWideAction(icon: ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.padding(horizontal = 22.dp).fillMaxWidth().pressMotion(interaction).clip(ArchiveShape).background(color.copy(alpha = .055f)).border(1.dp, color.copy(alpha = .25f), ArchiveShape).clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = color); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp); Text(subtitle, color = TextoSecundario, fontSize = 10.sp) }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(17.dp), tint = color)
    }
}
