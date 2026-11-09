package com.liferpg.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.liferpg.domain.CameraBoss
import com.liferpg.domain.CameraTrialState
import com.liferpg.domain.Patente
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppRepositoryBackupTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: AppRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = AppRepository(db, CatalogoRepo(context))
    }

    @After fun tearDown() = db.close()

    @Test fun previousCheckpointCanUndoAndThenRedoARealDatabaseRestore() = runTest {
        val dao = db.dao()
        dao.salvarPerfil(Perfil(nome = "Original", classe = "TITAN", pesoCorporalKg = 80f, xpTotal = 2500L, streak = 9, criadoEm = 1L))
        dao.inserirSerie(SerieRegistro(exercicioId = "bench", musculo = "PEITO", pesoKg = 80f, reps = 8,
            segundos = null, score = 1f, xpGanho = 20, epochDay = 20000, timestamp = 20000000, equipamento = "Máquina A"))
        val original = repo.exportarBackupJson()
        dao.resetTotal()
        dao.salvarPerfil(Perfil(nome = "Nuvem", classe = "TITAN", pesoCorporalKg = 82f, xpTotal = 5000L, streak = 15, criadoEm = 2L))
        val cloud = repo.exportarBackupJson()
        repo.restaurarBackupJson(original)
        val directory = java.nio.file.Files.createTempDirectory("remnant-restore-test").toFile()
        try {
            val store = LocalRestoreStore(directory)
            suspend fun restore(payload: String, timestamp: Long) {
                store.prepare("owner", repo.exportarBackupJson(), timestamp)
                repo.restaurarBackupJson(payload) { true }
                assertTrue(store.committed())
            }
            restore(cloud, 100)
            assertEquals("Nuvem", dao.perfil()!!.nome)
            assertEquals(0, dao.todasSeries().size)
            restore(store.read("owner")!!.payload, 200)
            assertEquals("Original", dao.perfil()!!.nome)
            assertEquals(2500L, dao.perfil()!!.xpTotal)
            assertEquals(9, dao.perfil()!!.streak)
            assertEquals("Máquina A", dao.todasSeries().single().equipamento)
            restore(store.read("owner")!!.payload, 300)
            assertEquals("Nuvem", dao.perfil()!!.nome)
            assertEquals(5000L, dao.perfil()!!.xpTotal)
            assertEquals(15, dao.perfil()!!.streak)
        } finally { directory.deleteRecursively() }
    }

    @Test fun schema13RoundTripPreservesPortableDataAndCompletedTrials() = runTest {
        val dao = db.dao()
        dao.salvarPerfil(Perfil(nome = "Atleta", classe = "TITAN", pesoCorporalKg = 82f, criadoEm = 10L, lema = "Forjado em cada treino", estiloMoldura = "OURO"))
        dao.salvarPreferencias(Preferencias(objetivo = "FORCA", experiencia = "INTERMEDIARIO", localPreferido = "ACADEMIA", diasPorSemana = 4, descansoPadraoSeg = 120, metaProteinaG = 160, rotinaAtiva = null, lembretesAtivos = false))
        dao.inserirSerie(SerieRegistro(exercicioId = "bench", musculo = "PEITO", pesoKg = 90f, reps = 5, segundos = null, score = 101f, xpGanho = 20, epochDay = 5L, timestamp = 50L))
        dao.salvarSessao(TreinoSessao(epochDay = 5L, nome = "Peito", xpGanho = 20, series = 1, exercicios = 1, recordes = 1, duracaoSeg = 600, encerradaEm = 60L))
        dao.salvarPeso(PesoCorporal(5L, 82f, 55L))
        dao.inserirRefeicao(Refeicao(epochDay = 5L, nome = "Arroz", proteinaG = 4f, calorias = 200f, carboG = 45f, gorduraG = 1f, porcaoG = 150f, barcode = null, timestamp = 56L))
        dao.salvarRotina(RotinaCustom(nome = "Push", itensJson = "{\"dias\":[1],\"itens\":[]}", criadoEm = 40L))
        dao.salvarCameraBoss(CameraBossProgress("ASH_WARDEN", stage = 1, damage = 5, wins = 1, pendingReps = 3,
            trialRank = 5, bestTrialRank = 4, deaths = 2))

        val document = repo.exportarBackupJson()
        assertTrue(document.contains("\"schema\": 15"))
        dao.resetTotal()

        val result = repo.restaurarBackupJson(document)

        assertEquals(1, result.series)
        assertEquals(1, result.sessoes)
        assertEquals(1, result.refeicoes)
        assertEquals(1, result.rotinas)
        assertEquals("Atleta", dao.perfil()?.nome)
        assertEquals("Forjado em cada treino", dao.perfil()?.lema)
        assertEquals("OURO", dao.perfil()?.estiloMoldura)
        assertEquals("FORCA", dao.preferencias()?.objetivo)
        assertEquals("bench", dao.todasSeries().single().exercicioId)
        assertEquals("Arroz", dao.todasRefeicoes().single().nome)
        assertEquals("Push", dao.todasRotinas().single().nome)
        assertEquals(0, dao.cameraBoss("ASH_WARDEN")?.damage)
        assertEquals(5, dao.cameraBoss("ASH_WARDEN")?.trialRank)
        assertEquals(4, dao.cameraBoss("ASH_WARDEN")?.bestTrialRank)
        assertEquals(2, dao.cameraBoss("ASH_WARDEN")?.deaths)
        assertEquals(3, dao.cameraBoss("ASH_WARDEN")?.pendingReps)
    }

    @Test fun evolutionAndEffortSurviveRestoreWithoutAnyPhotoPayload() = runTest {
        val dao=db.dao()
        dao.salvarPerfil(Perfil(nome="Conquista",classe="TITAN",pesoCorporalKg=80f,xpTotal=4200L,streak=12,criadoEm=1L))
        val gear=com.liferpg.domain.EvolutionCodec.encodeEquipment("bench",com.liferpg.domain.EquipmentSetup("Máquina A",5f,200f))
        repo.saveEvolution(gear)
        val campaign=com.liferpg.domain.EvolutionEngine.start("Minha Forja",20000,20000001,4,3,emptyList())
        val entry=com.liferpg.domain.EvolutionCodec.encodeCampaign(campaign)
        repo.saveEvolution(entry)
        dao.inserirSerie(SerieRegistro(exercicioId="bench",musculo="PEITO",pesoKg=80f,reps=10,segundos=null,score=1f,xpGanho=20,epochDay=20000,timestamp=20000002,esforco="PESADO",equipamento="Máquina A",origem="CAMERA"))
        dao.desbloquearConquista(ConquistaDesbloqueada("first",30))
        val document=repo.exportarBackupJson()
        assertFalse(document.contains("progress_photos"));assertFalse(document.contains("access_token"))
        dao.resetTotal();repo.restaurarBackupJson(document)
        assertEquals(setOf(gear,entry),dao.evolutionEntries().toSet())
        assertEquals("PESADO",dao.todasSeries().single().esforco);assertEquals("CAMERA",dao.todasSeries().single().origem)
        assertEquals(4200L,dao.perfil()!!.xpTotal);assertEquals(12,dao.perfil()!!.streak)
        assertEquals("first",dao.allAchievements().single().id)
    }
    @Test fun changedAccountCannotCommitARestore() = runTest {
        val dao=db.dao()
        dao.salvarPerfil(Perfil(nome="Original",classe="TITAN",pesoCorporalKg=80f,criadoEm=1L))
        val backup=repo.exportarBackupJson().replace("Original","Other")
        var checks=0
        val failed=runCatching { repo.restaurarBackupJson(backup) { ++checks == 1 } }
        assertTrue(failed.isFailure);assertEquals("Original",dao.perfil()!!.nome)
    }

    @Test fun invalidDocumentCannotEraseExistingData() = runTest {
        val dao = db.dao()
        dao.salvarPerfil(Perfil(nome = "Fica", classe = "TITAN", pesoCorporalKg = 80f, criadoEm = 1L))

        val failed = runCatching { repo.restaurarBackupJson("{\"app\":\"OUTRO\",\"schema\":8}") }

        assertTrue(failed.isFailure)
        assertEquals("Fica", dao.perfil()?.nome)
    }

    @Test fun editingIdentityKeepsTrainingProgress() = runTest {
        val dao = db.dao()
        dao.salvarPerfil(Perfil(nome = "Antes", classe = "TITAN", pesoCorporalKg = 80f, xpTotal = 4_200L, streak = 12, criadoEm = 1L))

        repo.atualizarIdentidade("Depois", "Minha jornada", "AVENTUREIRO", "GEADA", "Treinando há 3 anos", "FORCA", "LOBO")

        val saved = dao.perfil()!!
        assertEquals("Depois", saved.nome)
        assertEquals("Minha jornada", saved.lema)
        assertEquals("GEADA", saved.estiloMoldura)
        assertEquals("Treinando há 3 anos", saved.biografia)
        assertEquals("FORCA", saved.focoTreino)
        assertEquals("LOBO", saved.seloPerfil)
        assertEquals(4_200L, saved.xpTotal)
        assertEquals(12, saved.streak)
        val backup = repo.exportarBackupJson()
        repo.restaurarBackupJson(backup)
        assertEquals(saved, dao.perfil())
    }

    @Test fun recordProjectionKeepsActualLoadAndPairedRepsSeparateFromScore() = runTest {
        val dao = db.dao()
        fun row(weight: Float?, reps: Int?, seconds: Int? = null, score: Float, day: Long = 2, valid: Boolean = true) =
            SerieRegistro(exercicioId="bench",musculo="PEITO",pesoKg=weight,reps=reps,segundos=seconds,score=score,xpGanho=10,epochDay=day,timestamp=day,valido=valid)
        dao.inserirSerie(row(80f,1,score=1f))
        dao.inserirSerie(row(70f,20,score=2f))
        dao.inserirSerie(row(80f,5,score=1.2f,day=3))
        dao.inserirSerie(row(200f,99,score=9f,valid=false))
        val record = dao.recordesExercicios().first().single()
        assertEquals(80f,record.pesoKg!!,.001f)
        assertEquals(5,record.reps)
        assertEquals(3L,record.epochDay)
        assertEquals(2f,record.melhorScore,.001f)
        assertEquals(3,record.series)
    }

    @Test fun recordProjectionHandlesRepetitionsTimeAndTies() = runTest {
        val dao = db.dao()
        fun row(id:String,reps:Int?,seconds:Int?,day:Long) = SerieRegistro(exercicioId=id,musculo="PEITO",pesoKg=null,reps=reps,segundos=seconds,score=1f,xpGanho=10,epochDay=day,timestamp=day)
        dao.inserirSerie(row("push",12,null,2));dao.inserirSerie(row("push",24,null,3));dao.inserirSerie(row("push",24,null,4))
        dao.inserirSerie(row("plank",null,60,2));dao.inserirSerie(row("plank",null,90,3))
        val records = dao.recordesExercicios().first().associateBy { it.exercicioId }
        assertEquals(24,records.getValue("push").reps);assertEquals(4L,records.getValue("push").epochDay)
        assertEquals(90,records.getValue("plank").segundos)
    }

    @Test fun completedSessionPrescriptionSurvivesBackupAndLaterRoutineChanges() = runTest {
        val prescription = listOf(com.liferpg.domain.ItemRotinaDef("bench",4,6,10,120))
        db.dao().salvarSessao(TreinoSessao(epochDay=2,nome="Push",xpGanho=10,series=4,exercicios=1,recordes=0,duracaoSeg=600,encerradaEm=30,itensJson=repo.codificarItensRotina(prescription)))
        val backup = repo.exportarBackupJson()
        db.dao().resetTotal();repo.restaurarBackupJson(backup)
        assertEquals(prescription,repo.decodificarItensRotina(db.dao().todasSessoes().single().itensJson))
    }

    @Test fun cameraCallbacksAreIdempotentAndProgressSurvivesReopening() = runTest {
        repo.registrarRepeticoesCamera(CameraBoss.ASH_WARDEN, "one", 3)
        repo.registrarRepeticoesCamera(CameraBoss.ASH_WARDEN, "one", 3)
        repo.registrarRepeticoesCamera(CameraBoss.ASH_WARDEN, "one", 5)
        repo.registrarRepeticoesCamera(CameraBoss.ASH_WARDEN, "two", 2)

        val progress = db.dao().cameraBoss(CameraBoss.ASH_WARDEN.name)!!
        assertEquals(7, progress.damage)
        assertEquals(7, progress.pendingReps)
        assertEquals(0, progress.wins)
    }

    @Test fun savingCameraSetClearsPendingRepsInSameWorkoutWrite() = runTest {
        db.dao().salvarPerfil(Perfil(nome = "Atleta", classe = "AVENTUREIRO", pesoCorporalKg = 80f, criadoEm = 1L))
        repo.registrarRepeticoesCamera(CameraBoss.ASH_WARDEN, "set-1", 5)
        val pushups = repo.catalogo.exercicios().first { it.id == CameraBoss.ASH_WARDEN.exerciseId }

        val saved = repo.registrarSerie(pushups, null, 5, null, cameraBoss = CameraBoss.ASH_WARDEN)

        assertTrue(saved is RegistroSerie.Ok)
        assertEquals(0, db.dao().cameraBoss(CameraBoss.ASH_WARDEN.name)?.pendingReps)
        assertEquals(5, db.dao().todasSeries().single().reps)
        assertEquals(5, db.dao().cameraBoss(CameraBoss.ASH_WARDEN.name)?.damage)
    }

    @Test fun failedDiamondAttemptKeepsRepsButCannotCarryDamageIntoRetry() = runTest {
        val boss = CameraBoss.ASH_WARDEN
        var run = CameraTrialState(rank = Patente.DIAMANTE, revision = 1)
        repo.registrarProvaCamera(boss, "trial", run)
        repeat(39) { run = run.accept(boss, it + 1, (it + 1) * 2000L) }
        repo.registrarProvaCamera(boss, "trial", run)
        run = run.interrupt()
        repo.registrarProvaCamera(boss, "trial", run)
        repo.registrarProvaCamera(boss, "trial", run.copy(revision = run.revision - 1, attemptReps = 39))
        val saved = db.dao().cameraBoss(boss.name)!!
        assertEquals(0, saved.damage)
        assertEquals(39, saved.pendingReps)
        assertEquals(1, saved.deaths)
        assertEquals(0, saved.wins)
        run = run.accept(boss, 40, 100_000)
        repo.registrarProvaCamera(boss, "trial", run)
        assertEquals(1, db.dao().cameraBoss(boss.name)?.damage)
        assertEquals(40, db.dao().cameraBoss(boss.name)?.pendingReps)
    }

    @Test fun trialVictoryIsDurableAndDuplicateCheckpointCannotAwardTwice() = runTest {
        val boss = CameraBoss.ASH_WARDEN
        var run = CameraTrialState(rank = Patente.DIAMANTE, revision = 1)
        repeat(50) { run = run.accept(boss, it + 1, (it + 1) * 2000L) }
        repo.registrarProvaCamera(boss, "trial", run)
        repo.registrarProvaCamera(boss, "trial", run)
        val saved = db.dao().cameraBoss(boss.name)!!
        assertEquals(1, saved.wins)
        assertEquals(Patente.MESTRE.ordinal, saved.trialRank)
        assertEquals(Patente.DIAMANTE.ordinal, saved.bestTrialRank)
        assertEquals(50, saved.pendingReps)
        repo.registrarProvaCamera(boss, "new-session", CameraTrialState(rank = Patente.MESTRE, revision = 1))
        assertEquals(1, db.dao().cameraBoss(boss.name)?.wins)
        assertEquals(50, db.dao().cameraBoss(boss.name)?.pendingReps)
    }

    @Test fun recordingTwoCameraAttemptsDoesNotCreateAnInflatedSingleSet() = runTest {
        val boss = CameraBoss.ASH_WARDEN
        db.dao().salvarPerfil(Perfil(nome = "Atleta", classe = "AVENTUREIRO", pesoCorporalKg = 80f, criadoEm = 1L))
        val exercise = repo.catalogo.exercicios().first { it.id == boss.exerciseId }
        var run = CameraTrialState(rank = Patente.DIAMANTE, revision = 1)
        repeat(39) { run = run.accept(boss, it + 1, (it + 1) * 2000L) }
        run = run.interrupt()
        repeat(50) { run = run.accept(boss, 40 + it, 100_000L + it * 2000L) }
        repo.registrarProvaCamera(boss, "two-attempts", run)
        val saved = repo.registrarSeriesCameraPendentes(exercise, boss)
        assertEquals(2, saved.size)
        assertTrue(saved.all { it.second is RegistroSerie.Ok })
        assertEquals(listOf(39, 50), db.dao().todasSeries().map { it.reps })
        assertEquals(50f, db.dao().melhorScoreDe(exercise.id)!!, 0f)
        assertEquals(0, db.dao().cameraBoss(boss.name)?.pendingReps)
        assertTrue(repo.registrarSeriesCameraPendentes(exercise, boss).isEmpty())
        repo.registrarProvaCamera(boss, "two-attempts", run)
        assertEquals(2, db.dao().todasSeries().size)
        assertEquals(0, db.dao().cameraBoss(boss.name)?.pendingReps)
    }
}
