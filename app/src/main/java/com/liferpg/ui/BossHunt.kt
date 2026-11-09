package com.liferpg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferpg.MainViewModel
import com.liferpg.domain.CameraBoss

/** The same trial entry, artwork and rules as System. Combat/storage stay unchanged. */
@Composable internal fun BossHuntScreen(vm: MainViewModel, open: (String)->Unit, back: ()->Unit) {
    val rows by vm.cameraBosses.collectAsStateWithLifecycle()
    val exercises by vm.exercicios.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().background(SystemTokens.bg).statusBarsPadding().navigationBarsPadding(),
        contentPadding=PaddingValues(SystemTokens.inset),verticalArrangement=Arrangement.spacedBy(SystemTokens.inset)) {
        item {
            SystemHeader("PROVAÇÕES","Solo / validação por câmera",back)
            Spacer(Modifier.height(SystemTokens.inset))
            Text("Seu corpo.\nUma tentativa.",color=SystemTokens.ink,fontSize=SystemTokens.title,lineHeight=38.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(SystemTokens.small))
            Text("Complete as repetições exigidas para vencer e enfrentar a próxima patente. 15 segundos sem completar uma repetição encerram a tentativa.",color=SystemTokens.muted,fontSize=SystemTokens.body,lineHeight=22.sp)
        }
        CameraBoss.entries.forEach { boss -> item(key=boss.name) {
            SystemBossCard(boss,rows[boss]) { open(RemnantRoute.camera(boss.exerciseId)) }
            val pending=rows[boss]?.pendingReps ?: 0
            val exercise=exercises.firstOrNull { it.id==boss.exerciseId }
            if(pending>0 && exercise!=null) {
                TextButton(onClick={vm.salvarSerieCamera(exercise,boss,"salvar-pendente",0,onSaved={})},modifier=Modifier.fillMaxWidth()) {
                    Text("Registrar $pending repetições pendentes",color=SystemTokens.ink,fontSize=SystemTokens.caption)
                }
            }
        } }
        item { Text("A câmera analisa no aparelho, sem gravar ou enviar vídeo. Séries pendentes ficam guardadas quando você fecha o app.",color=SystemTokens.muted,fontSize=SystemTokens.caption,lineHeight=18.sp) }
    }
}
