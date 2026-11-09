package com.liferpg.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class CircleNetworkTest {
    private lateinit var server: MockWebServer
    private lateinit var sync: SupabaseSyncService
    @Before fun before() {
        server=MockWebServer(); server.start(); val base=server.url("/").toString().trimEnd('/')
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("supabase_auth",Context.MODE_PRIVATE).edit().clear().putString("backend",base)
            .putString("access","test-token").putString("refresh","test-refresh").putString("uid","user-a").putBoolean("anon",false).putLong("expira",Long.MAX_VALUE).commit()
        sync=SupabaseSyncService(SupabaseAuth(ctx,base,"test-public") { false },base,"test-public")
    }
    @After fun after() = server.shutdown()
    @Test fun weeklyReadPinsTheCircleAndPeriodWithoutSendingProfileData() = runTest {
        server.enqueue(MockResponse().setBody("""{"circle_id":"circle","week_start":"2026-10-05","first_week":"2026-10-05","current_week":"2026-10-12","today":"2026-10-12","timezone":"America/Sao_Paulo","members":[{"uid":"a","nickname":"jota","goal":3,"dates":[],"workouts":0,"sets":0,"prs":0,"seconds":0}]}"""))
        assertTrue(sync.placarCirculo("circle","2026-10-05").isSuccess)
        val request=server.takeRequest(); assertEquals("/rest/v1/rpc/ver_placar_circulo",request.path)
        val body=JSONObject(request.body.readUtf8())
        assertEquals(setOf("p_circle","p_week"),body.keys().asSequence().toSet())
        assertEquals("2026-10-05",body.getString("p_week"))
        server.enqueue(MockResponse().setResponseCode(403).setBody("{}"))
        assertTrue((sync.placarCirculo("circle").exceptionOrNull() as CircleRequestFailure).accessDenied)
    }
    @Test fun publishCarriesExplicitActorAndNoCredentialsInPayload() = runTest {
        server.enqueue(MockResponse().setBody("null"))
        assertTrue(sync.enviarReciboCirculo("circle","session","{\"title\":\"Peito\"}","user-a").isSuccess)
        val req=server.takeRequest(); assertEquals("/rest/v1/rpc/publicar_recibo_circulo",req.path)
        val body=JSONObject(req.body.readUtf8()); assertEquals("user-a",body.getString("p_actor")); assertFalse(body.toString().contains("test-token"))
        assertEquals("Bearer test-token",req.getHeader("Authorization"))
    }
    @Test fun retryableServerFailureDiffersFromPermanentAccessFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
        assertTrue((sync.retirarReciboCirculo("c","s","user-a").exceptionOrNull() as CircleRequestFailure).retryable)
        server.enqueue(MockResponse().setResponseCode(403).setBody("membership required"))
        assertFalse((sync.retirarReciboCirculo("c","s","user-a").exceptionOrNull() as CircleRequestFailure).retryable)
        server.takeRequest(); server.takeRequest()
    }
}
