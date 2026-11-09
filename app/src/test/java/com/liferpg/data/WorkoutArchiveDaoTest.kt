package com.liferpg.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class WorkoutArchiveDaoTest {
    @Test fun archiveQueriesKeepMoreThanFiftySessionsAndAllSetValidityStates() = runTest {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao=db.dao()
            for(i in 1..75) dao.salvarSessao(TreinoSessao(epochDay=20000-i.toLong(),nome="Treino $i",xpGanho=10,series=1,exercicios=1,recordes=0,duracaoSeg=60,encerradaEm=i.toLong()))
            assertEquals(75,dao.arquivoSessoes().first().size)
            assertEquals(75L,dao.arquivoSessoes().first().first().encerradaEm)
            assertEquals(50,dao.sessoesRecentes(50).first().size)
            val row=SerieRegistro(exercicioId="bench",musculo="PEITO",pesoKg=80f,reps=10,segundos=null,score=1f,xpGanho=20,epochDay=20000,timestamp=1)
            dao.inserirSerie(row);dao.inserirSerie(row.copy(valido=false,xpGanho=0,timestamp=2))
            assertEquals(2,dao.arquivoSeries().first().size)
            assertEquals(1,dao.validHistory().first().size)
        } finally { db.close() }
    }
}
