package com.liferpg.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferpg.MainViewModel
import com.liferpg.ui.theme.*

@Composable internal fun CloudSave(vm: MainViewModel) {
    val backup by vm.cloudBackup.collectAsStateWithLifecycle()
    val busy by vm.cloudBusy.collectAsStateWithLifecycle()
    val message by vm.cloudMessage.collectAsStateWithLifecycle()
    val checked by vm.cloudChecked.collectAsStateWithLifecycle()
    val account by vm.sessaoGlobal.collectAsStateWithLifecycle()
    val profile by vm.perfil.collectAsStateWithLifecycle()
    val recovery by vm.localRecovery.collectAsStateWithLifecycle()
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(account?.userId) { vm.conferirCopiaAnterior(); if (account != null) vm.conferirBackupNuvem() }
    Page("PROGRESSO / CÓPIA NA CONTA", "Sua jornada\nvai com você.") {
        Text("Guarde uma cópia para recuperar seu progresso em outro aparelho. Restaurar é uma escolha sua; entrar na conta não substitui um save local automaticamente.", color = TextoSecundario, fontSize = 14.sp, lineHeight = 22.sp)
        Panel {
            Eyebrow("O QUE ENTRA NA CÓPIA")
            Text("Perfil e medidas, séries, cardio concluído, rotinas, alimentação, chefes e campanhas.", fontSize = 14.sp, lineHeight = 21.sp)
            Text("Fotos corporais, senha e conversas do REM não entram. Pelo app, só sua conta pode abrir a cópia. A administração do serviço tem acesso técnico aos dados armazenados; esta cópia não tem criptografia de ponta a ponta.", color = TextoSecundario, fontSize = 12.sp, lineHeight = 19.sp)
        }
        if (backup != null && checked) Panel {
            Eyebrow("CÓPIA DISPONÍVEL")
            Text(backup!!.nickname, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("${backup!!.setCount} séries · ${backup!!.xp} XP · versão da cópia ${backup!!.revision}", color = TextoSecundario, fontSize = 12.sp)
            Text("Salva ${backup!!.updatedAt.take(19).replace('T', ' ')}", color = TextoSecundario, fontSize = 11.sp)
            Action("Restaurar neste aparelho", !busy) { confirmation = "restore" }
            TextButton(onClick = { confirmation = "delete" }, enabled = !busy) { Text("Apagar cópia da nuvem", color = TextoSecundario) }
        } else if (checked) Text("Ainda não há uma cópia nesta conta.", color = TextoSecundario)
        message?.let { Text(it, color = TextoSecundario, fontSize = 13.sp, lineHeight = 20.sp) }
        Action(if (busy) "Aguarde…" else if (backup == null) "Salvar meu progresso na conta" else "Atualizar cópia com este aparelho", !busy && checked && profile != null) { confirmation = "save" }
        OutlinedButton(onClick = vm::conferirBackupNuvem, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Conferir cópia da conta") }
        Text("Se outro aparelho atualizar a cópia primeiro, o app interrompe a substituição e pede que você confira. Seus registros locais permanecem disponíveis offline.", color = TextoSecundario, fontSize = 11.sp, lineHeight = 18.sp)
        recovery?.let { point -> Panel {
            Eyebrow("ANTES DA ÚLTIMA RESTAURAÇÃO")
            Text(point.nickname, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("${point.sets} séries · ${point.xp} XP · " + java.time.Instant.ofEpochMilli(point.createdAt).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM · HH:mm")), color = TextoSecundario, fontSize = 12.sp)
            Text("A cópia anterior fica só neste aparelho e nesta conta. Você pode voltar a ela mesmo sem conexão. Fotos locais permanecem.", color = TextoSecundario, fontSize = 12.sp, lineHeight = 18.sp)
            OutlinedButton(onClick = { confirmation = "previous" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Voltar à cópia anterior") }
        } }
    }
    confirmation?.let { action -> AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(when(action) {
        "previous" -> "Voltar à cópia anterior?"; "restore" -> "Restaurar esta jornada?"; "delete" -> "Apagar a cópia da nuvem?"; else -> "Salvar este progresso na conta?"
    }) }, text = { Text(when(action) {
        "previous" -> "A cópia de ${recovery?.nickname}, com ${recovery?.sets} séries, substituirá o save aberto agora. O save atual ficará guardado como cópia anterior. As fotos e a cópia na nuvem permanecem."
        "restore" -> "A cópia de ${backup?.nickname} substituirá o perfil, treinos e alimentação deste aparelho. As fotos locais permanecem. Você poderá voltar ao save atual pela opção Cópia anterior."
        "delete" -> "Só a cópia da nuvem será apagada. Os dados e as fotos deste aparelho permanecem."
        else -> "Serão enviados os registros deste aparelho, incluindo medidas e alimentação. Fotos corporais não serão enviadas. A cópia anterior da conta será substituída."
    }) }, confirmButton = { TextButton(onClick = {
        confirmation = null
        when(action) { "previous" -> vm.restaurarCopiaAnterior(); "restore" -> vm.restaurarBackupNuvem(); "delete" -> vm.apagarBackupNuvem(); else -> vm.salvarBackupNuvem() }
    }) { Text(when(action) { "previous" -> "Recuperar anterior"; "restore" -> "Restaurar"; "delete" -> "Apagar cópia"; else -> "Salvar cópia" }) } },
        dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } }) }
}

@Composable internal fun EntryCloudRecovery(vm: MainViewModel) {
    val account by vm.sessaoGlobal.collectAsStateWithLifecycle()
    val backup by vm.cloudBackup.collectAsStateWithLifecycle()
    val busy by vm.cloudBusy.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(account?.userId) { if (account?.anonimo == false) vm.conferirBackupNuvem() }
    if (backup != null && account?.anonimo == false) {
        Text("Encontramos ${backup!!.nickname} na sua conta, com ${backup!!.setCount} séries. Você pode recuperar essa jornada em vez de criar outro perfil.", color = TextoSecundario, fontSize = 13.sp, lineHeight = 20.sp)
        OutlinedButton(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Restaurando…" else "Recuperar minha jornada") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Recuperar ${backup?.nickname}?") },
        text = { Text("O perfil e o planejamento salvos na conta serão restaurados. As respostas deste novo cadastro não substituirão essa jornada.") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.restaurarBackupNuvem() }) { Text("Recuperar") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
}
