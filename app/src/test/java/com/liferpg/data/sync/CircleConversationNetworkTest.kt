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
class CircleConversationNetworkTest {
    private lateinit var server:MockWebServer
    private lateinit var sync:SupabaseSyncService
    @Before fun before() {
        server=MockWebServer(); server.start(); val base=server.url("/").toString().trimEnd('/')
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("supabase_auth",Context.MODE_PRIVATE).edit().clear().putString("backend",base)
            .putString("access","test-token").putString("refresh","test-refresh").putString("uid","user-a").putBoolean("anon",false).putLong("expira",Long.MAX_VALUE).commit()
        sync=SupabaseSyncService(SupabaseAuth(ctx,base,"test-public") { false },base,"test-public")
    }
    @After fun after() { server.shutdown() }
    @Test fun explicitUndoIsJsonNullAndCommentCarriesStableIdButNoPrivateFields()=runTest {
        server.enqueue(MockResponse().setBody("{\"respect\":0,\"fire\":0,\"record\":0,\"mine\":null,\"comments\":1}"))
        assertNull(sync.reagirCirculo("post",null).getOrThrow().mine)
        var request=server.takeRequest(); assertEquals("/rest/v1/rpc/reagir_recibo_circulo",request.path)
        assertTrue(JSONObject(request.body.readUtf8()).isNull("p_kind"))
        server.enqueue(MockResponse().setBody("null"))
        sync.comentarCirculo("post","stable-id","Marca incrível! 💪").getOrThrow()
        request=server.takeRequest(); val body=JSONObject(request.body.readUtf8())
        assertEquals(setOf("p_post","p_id","p_body"),body.keys().asSequence().toSet())
        assertEquals("stable-id",body.getString("p_id")); assertEquals("Marca incrível! 💪",body.getString("p_body"))
        assertEquals("Bearer test-token",request.getHeader("Authorization"))
    }
    @Test fun revokedAccessIsDistinctFromOfflineAndReadReceiptIsRestrictedToDeliveredIds()=runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("membership required"))
        val error=sync.conversaCirculo("circle","post").exceptionOrNull() as CircleRequestFailure
        assertTrue(error.accessDenied); assertFalse(error.retryable); server.takeRequest()
        server.enqueue(MockResponse().setBody("null")); sync.lerComentariosCirculo("post",listOf("a","b")).getOrThrow()
        val request=server.takeRequest(); val body=JSONObject(request.body.readUtf8())
        assertEquals("/rest/v1/rpc/ler_comentarios_circulo",request.path)
        assertEquals(listOf("a","b"),(0 until body.getJSONArray("p_ids").length()).map { body.getJSONArray("p_ids").getString(it) })
    }
    @Test fun previousFeedWithoutSocialSummaryStillParsesAndMalformedCommentIsRejected() {
        val post="""{"id":"p","session_id":"s","uid":"a","nickname":"Jota","created_at":"2026-10-08T17:00:00Z","receipt":{}}"""
        assertEquals(CircleSocial(),CircleCodec.posts("[$post]").single().social)
        val conversation=JSONObject().put("post",JSONObject(post)).put("comments",org.json.JSONArray().put(JSONObject()
            .put("id","c").put("uid","a").put("nickname","Jota").put("body","x".repeat(801)).put("created_at","2026-10-08T17:00:00Z")))
        assertThrows(IllegalArgumentException::class.java) { CircleConversationCodec.conversation(conversation.toString()) }
    }
}
