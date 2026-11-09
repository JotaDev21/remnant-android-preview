package com.liferpg.data.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class NutritionApiTest {
    @Test fun nameSearchUsesFullTextEndpointAndDoesNotCallDemoUsda() = runTest {
        val server = MockWebServer();server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"products":[{"code":"12345678","product_name":"Aveia","nutriments":{"energy-kcal_100g":380,"proteins_100g":14,"carbohydrates_100g":60,"fat_100g":8}}]}"""))
            val repo = NutricaoRepo(api=NutricaoRepo.criarApi(server.url("/").toString()))
            val result = repo.buscar("aveia",online=true).getOrThrow()
            val request = server.takeRequest()
            assertEquals("/cgi/search.pl",request.requestUrl!!.encodedPath)
            assertEquals("aveia",request.requestUrl!!.queryParameter("search_terms"))
            assertEquals(1,server.requestCount);assertEquals(1,result.alimentos.size);assertEquals(0,result.alimentos.single().dadosAusentes)
        } finally { server.shutdown() }
    }
    @Test fun incompleteLabelDoesNotPretendMissingMacrosAreMeasuredZero() = runTest {
        val server = MockWebServer();server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"status":"success","product":{"code":"12345678","product_name":"Produto incompleto","nutriments":{"energy-kcal_100g":100,"proteins_100g":5}}}"""))
            val food = NutricaoRepo(api=NutricaoRepo.criarApi(server.url("/").toString())).porBarcode("12345678").getOrThrow()!!
            assertEquals(12,food.dadosAusentes)
            assertEquals("/api/v3.6/product/12345678.json",server.takeRequest().requestUrl!!.encodedPath)
        } finally { server.shutdown() }
    }
    @Test fun noNutritionAndInvalidValuesAreNeverUsedAsACompleteLabel() {
        assertNull(OffProduct("1","Sem tabela",null,null).paraDominio())
        assertNull(OffProduct("1","Sem tabela",null,OffNutriments(null,null,null,null),noNutritionData="on").paraDominio())
        val invalid = OffProduct("1","Inválido",null,OffNutriments(-1.0,100.0,Double.NaN,500.0)).paraDominio()!!
        assertEquals(13,invalid.dadosAusentes)
    }
}
