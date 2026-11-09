package com.liferpg.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liferpg.ui.theme.*
import java.time.ZoneId

internal fun circleNameLength(name: String): Int = name.trim().let { it.codePointCount(0, it.length) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun CircleCreateDialog(busy: Boolean, error: String?, initialGoal: Int,
    dismiss: () -> Unit, canSubmit: Boolean = !busy, create: (String, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableIntStateOf(initialGoal.coerceIn(1, 7)) }
    val latestBusy by rememberUpdatedState(busy)
    ModalBottomSheet(onDismissRequest={ if(!busy) dismiss() },
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true,
            confirmValueChange={ it!=SheetValue.Hidden || !latestBusy }),
        containerColor=Fundo,contentColor=TextoPrimario,
        dragHandle={ BottomSheetDefaults.DragHandle(color=Borda) }) {
        CircleCreateContent(name,{ name=it },goal,{ goal=it },busy,error,dismiss,
            { create(name.trim(),goal) },Modifier.fillMaxHeight(.94f),canSubmit)
    }
}

@Composable internal fun CircleCreateContent(name: String, changeName: (String)->Unit,
    goal: Int, changeGoal: (Int)->Unit, busy: Boolean, error: String?,
    dismiss: ()->Unit, create: ()->Unit, modifier: Modifier=Modifier,canSubmit:Boolean=true) {
    val count=circleNameLength(name)
    val keyboard=LocalSoftwareKeyboardController.current
    Column(modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("NOVO CÍRCULO")
                Text("Treinar juntos.",fontSize=28.sp,fontWeight=FontWeight.SemiBold)
            }
            IconButton(onClick=dismiss,enabled=!busy) { Icon(Icons.Outlined.Close,"Fechar criação de círculo") }
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp),
            verticalArrangement=Arrangement.spacedBy(24.dp)) {
            item(key="name") {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Um grupo privado e contínuo para seus aliados.",fontSize=14.sp,color=TextoSecundario)
                    OutlinedTextField(name,changeName,Modifier.fillMaxWidth(),enabled=!busy,
                        label={ Text("Nome do círculo") },singleLine=true,
                        shape=RoundedCornerShape(14.dp),isError=count>60 || (name.isNotBlank() && count<2),
                        supportingText={ Text(if(count>60) "Use até 60 caracteres." else if(name.isNotBlank() && count<2)
                            "Use ao menos 2 caracteres." else "$count/60 caracteres") },
                        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),
                        keyboardActions=KeyboardActions(onDone={ keyboard?.hide() }))
                }
            }
            item(key="goal") {
                Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Eyebrow("SEU COMPROMISSO")
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp),
                        modifier=Modifier.semantics(mergeDescendants=true) { contentDescription="Compromisso de $goal dias por semana" }) {
                        AnimatedContent(goal,transitionSpec={
                            (slideInVertically(tween(190)) { if(targetState>initialState) it/3 else -it/3 } + fadeIn(tween(160))) togetherWith
                                (slideOutVertically(tween(190)) { if(targetState>initialState) -it/3 else it/3 } + fadeOut(tween(140)))
                        },label="Meta semanal") { value -> Text("$value",fontSize=56.sp,fontWeight=FontWeight.SemiBold) }
                        Text(if(goal==1) "dia por\nsemana" else "dias por\nsemana",fontSize=18.sp,color=TextoSecundario)
                    }
                    Column(Modifier.selectableGroup(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf((1..4).toList(),(5..7).toList()).forEach { row ->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                row.forEach { value ->
                                    val selected=goal==value
                                    Box(Modifier.weight(1f).heightIn(min=52.dp).clip(RoundedCornerShape(14.dp))
                                        .background(if(selected) TextoPrimario else SuperficieAlta)
                                        .selectable(selected=selected,enabled=!busy,role=Role.RadioButton,onClick={ changeGoal(value) })
                                        .semantics { contentDescription="Meta de $value dia${if(value==1) "" else "s"} por semana" },
                                        contentAlignment=Alignment.Center) {
                                        Text("$value",fontSize=18.sp,fontWeight=FontWeight.SemiBold,color=if(selected) Fundo else TextoPrimario)
                                    }
                                }
                                if(row.size==3) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    Text("Escolha os dias durante a semana. Sua meta fica fixa neste círculo; cada data compartilhada conta uma vez.",
                        fontSize=14.sp,color=TextoSecundario)
                }
            }
            item(key="rules") {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color=Borda)
                    Text("SEGUNDA A DOMINGO · ${ZoneId.systemDefault().id.substringAfterLast('/').replace('_',' ')}",
                        fontSize=12.sp,color=TextoSecundario)
                    Text("Compartilhar é sempre sua escolha. Só membros aceitos veem os resumos; descanso não gera punição.",
                        fontSize=14.sp,color=TextoSecundario)
                }
            }
            error?.let { item(key="error") { Text(it,color=VermelhoPerigo,fontSize=14.sp) } }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Convide seus aliados depois de criar.",fontSize=12.sp,color=TextoSecundario)
            Button(onClick=create,enabled=!busy && canSubmit && count in 2..60,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),
                shape=RoundedCornerShape(16.dp)) {
                if(busy) { CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if(busy) "Criando círculo…" else "Criar círculo",fontWeight=FontWeight.SemiBold,fontSize=16.sp)
            }
        }
    }
}
