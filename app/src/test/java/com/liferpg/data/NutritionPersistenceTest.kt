package com.liferpg.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.liferpg.data.net.BrazilianFoods
import com.liferpg.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class NutritionPersistenceTest {
    @Test fun upgradeKeepsSaveAndLegacyMealsAndCreatesNewTables() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "nutrition-migration.db"; context.deleteDatabase(name)
        val schema = JSONObject(listOf(File("schemas/com.liferpg.data.AppDatabase/11.json"),File("app/schemas/com.liferpg.data.AppDatabase/11.json")).first { it.isFile }.readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object:SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for(i in 0 until entities.length()) {
                        val e = entities.getJSONObject(i); val table = e.getString("tableName")
                        db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                        val indexes = e.optJSONArray("indices") ?: org.json.JSONArray()
                        for(j in 0 until indexes.length()) db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
                    }
                    db.execSQL("INSERT INTO perfil(id,nome,classe,pesoCorporalKg,xpTotal,streak,melhorStreak,ultimoDiaTreino,criadoEm) VALUES(1,'jota','TITAN',80,4200,12,15,20000,1)")
                    db.execSQL("INSERT INTO refeicoes(epochDay,nome,proteinaG,calorias,carboG,gorduraG,porcaoG,barcode,timestamp) VALUES(20000,'Almoço antigo',30,600,50,20,400,NULL,1)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion:Int,newVersion:Int) = Unit
            }).build())
        helper.writableDatabase; helper.close()
        val db = Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(*Migrations.TODAS).allowMainThreadQueries().build()
        try {
            assertEquals(4200L,db.dao().perfil()?.xpTotal);assertEquals(12,db.dao().perfil()?.streak)
            val meal = db.dao().todasRefeicoes().single()
            assertEquals(600f,meal.calorias,.01f);assertEquals(50f,meal.carboG,.01f);assertFalse(meal.macrosCompletos);assertNull(meal.fibraG)
            assertTrue(db.dao().todosAlimentosSalvos().isEmpty());assertTrue(db.dao().todaAgua().isEmpty())
        } finally { db.close();context.deleteDatabase(name) }
    }

    @Test fun referenceTableHasDistinctCookedEntriesAndValidMacros() {
        val catalog = BrazilianFoods(ApplicationProvider.getApplicationContext())
        assertEquals(578,catalog.alimentos.size)
        assertEquals(128.26f,catalog.obter("taco:3")!!.calorias,.01f)
        assertEquals(28.06f,catalog.obter("taco:3")!!.carboG,.01f)
        assertTrue(catalog.buscar("arroz cozido").all { "cozido" in it.nome })
        assertTrue(catalog.buscar("feijao carioca").any { "cozido" in it.nome })
        assertEquals(578,catalog.alimentos.map { it.id }.distinct().size)
        assertTrue(com.liferpg.domain.NutritionInsights.temVegetais(catalog.buscar("pepino cru")))
        assertTrue(com.liferpg.domain.NutritionInsights.temVegetais(catalog.buscar("abobora cabotian cozida")))
        assertFalse(com.liferpg.domain.NutritionInsights.temVegetais(catalog.buscar("banana nanica crua")))
        val vegetable = catalog.buscar("pepino cru").first()
        assertEquals(vegetable.grupo, com.liferpg.domain.NutritionFoodCodec.ler(com.liferpg.domain.NutritionFoodCodec.escrever(vegetable)).grupo)
    }

    @Test fun portableBackupPreservesNewNutritionDataAndRejectsBadImportBeforeClearing() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = AppRepository(db,CatalogoRepo(context)); val dao = db.dao()
            dao.salvarPerfil(Perfil(nome="jota",classe="TITAN",pesoCorporalKg=80f,xpTotal=4200,criadoEm=1))
            dao.salvarPreferencias(Preferencias(objetivo="GERAL",experiencia="INICIANTE",localPreferido="ACADEMIA",diasPorSemana=3,descansoPadraoSeg=90,metaProteinaG=150,rotinaAtiva=null,lembretesAtivos=false,metaCalorias=2200,metaCarboG=250,metaGorduraG=65,metasNutricaoManuais=true))
            val meal = Refeicao(epochDay=5,nome="Arroz",proteinaG=4f,calorias=200f,carboG=45f,gorduraG=1f,porcaoG=150f,barcode=null,timestamp=56,fonte="TACO",categoria="ALMOCO",estimado=true,fibraG=2f,macrosCompletos=true)
            repo.adicionarPrato(listOf(meal,meal.copy(nome="Feijão")))
            repo.registrarAgua(5,250);repo.registrarAgua(5,500);repo.desfazerAgua(5)
            val food = BrazilianFoods(context).obter("taco:3")!!
            repo.salvarAlimento(AlimentoSalvo(food.id,food.nome,NutritionFoodCodec.escrever(food),150f))
            val backup = repo.exportarBackupJson();repo.restaurarBackupJson(backup)
            assertEquals(4200L,dao.perfil()?.xpTotal)
            assertTrue(dao.preferencias()!!.metasNutricaoManuais);assertEquals(250,dao.preferencias()?.metaCarboG)
            assertEquals(2,dao.todasRefeicoes().size);assertTrue(dao.todasRefeicoes().all { it.estimado && it.macrosCompletos && it.categoria == "ALMOCO" })
            assertEquals(250,dao.todaAgua().single().ml);assertEquals(food,NutritionFoodCodec.ler(dao.todosAlimentosSalvos().single().alimentoJson))
            val invalid = JSONObject(backup).apply { getJSONArray("agua").getJSONObject(0).put("ml",-1) }.toString()
            assertTrue(runCatching { repo.restaurarBackupJson(invalid) }.isFailure);assertEquals(2,dao.todasRefeicoes().size)
            val bad = meal.copy(nome="Não salvar",proteinaG=Float.NaN)
            assertTrue(runCatching { repo.adicionarPrato(listOf(meal,bad)) }.isFailure);assertEquals(2,dao.todasRefeicoes().size)
            val legacy = JSONObject(backup).apply { put("schema",11);remove("agua");remove("alimentosSalvos") }.toString()
            repo.restaurarBackupJson(legacy);assertEquals(2,dao.todasRefeicoes().size);assertTrue(dao.todaAgua().isEmpty())
        } finally { db.close() }
    }

    @Test fun photoReviewSurvivesRecreationAndInvalidUserInput() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = NutritionDraftStore(context)
        val draft = PratoRevisao(listOf(ItemPratoRevisao("Arroz","taco:3","","media")),"Confira o preparo.","content://local/plate")
        store.salvar(draft);assertEquals(draft,NutritionDraftStore(context).ler());assertNull(store.ler()!!.itens.single().gramas)
        store.salvar(null);assertNull(store.ler())
    }

    @Test fun retryAfterProcessDeathCannotDuplicateCommittedPlate() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = AppRepository(db,CatalogoRepo(context))
            val meal = Refeicao(epochDay=5,nome="Arroz",proteinaG=4f,calorias=200f,carboG=45f,gorduraG=1f,porcaoG=150f,barcode=null,timestamp=56,
                registroId="plate:fixture:0",fonte="TACO",categoria="ALMOCO",estimado=true,macrosCompletos=true)
            val rows = listOf(meal,meal.copy(nome="Feijão",registroId="plate:fixture:1"))
            repo.adicionarPrato(rows);repo.adicionarPrato(rows)
            assertEquals(2,db.dao().todasRefeicoes().size)
            val backup = repo.exportarBackupJson();repo.restaurarBackupJson(backup);repo.adicionarPrato(rows)
            assertEquals(2,db.dao().todasRefeicoes().size)
            repo.adicionarRefeicao(meal.copy(registroId=null));assertEquals(3,db.dao().todasRefeicoes().size)
        } finally { db.close() }
    }

    @Test fun nutritionalProfileChangesPreserveXpAndIdentity() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = AppRepository(db,CatalogoRepo(context))
            val profile = Perfil(nome="jota",classe="TITAN",pesoCorporalKg=80f,xpTotal=4200,streak=12,criadoEm=1,lema="Minha jornada")
            db.dao().salvarPerfil(profile)
            val prefs = Preferencias(objetivo="GERAL",experiencia="INICIANTE",localPreferido="ACADEMIA",diasPorSemana=3,descansoPadraoSeg=90,metaProteinaG=150,rotinaAtiva=null,lembretesAtivos=false,metaCalorias=2200,metasNutricaoManuais=true)
            repo.salvarMetasNutri(prefs,30,180,"MASCULINO")
            assertEquals(profile.copy(idade=30,alturaCm=180,sexo="MASCULINO"),db.dao().perfil())
            assertEquals(prefs,db.dao().preferencias())
        } finally { db.close() }
    }
}
