package com.liferpg.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class ArenaAccountsTest {
    private lateinit var server: MockWebServer
    private lateinit var auth: SupabaseAuth
    private lateinit var sync: SupabaseSyncService
    private val uid = "11111111-1111-4111-8111-111111111111"
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf("supabase_auth", "auth_pkce", "save_account_owner").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        server = MockWebServer(); server.start()
        val base = server.url("/").toString().trimEnd('/')
        auth = SupabaseAuth(context, base, "public-test-key")
        sync = SupabaseSyncService(auth, base, "public-test-key")
    }
    @After fun tearDown() { server.shutdown() }
    private fun respond(body: String) = server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))
    private suspend fun login() {
        respond("""{"access_token":"test-access","refresh_token":"test-refresh","expires_in":3600,"user":{"id":"$uid","email":"private@example.test","is_anonymous":false}}""")
        assertTrue(auth.entrarEmail("private@example.test", "password8").isSuccess)
        server.takeRequest()
    }
    @Test fun directoryIncludesNoPowerAccountsWithoutAssigningAPlace() = runTest {
        login()
        respond("""[{"user_id":"$uid","apelido":"Caçador 111111","power_score":0,"patente":0,"perfil_publicado":false,"posicao":0},{"user_id":"other","apelido":"Jota","power_score":120,"patente":3,"perfil_publicado":true,"posicao":1}]""")
        val players = sync.leaderboard(2, 500).getOrThrow()
        assertEquals(2, players.size)
        assertFalse(players[0].temPower)
        assertEquals(0, players[0].posicao)
        assertFalse(players[0].protegePublicacaoLocal("My chosen nickname", 0f))
        assertTrue(players[1].protegePublicacaoLocal("Jota", 0f))
        val request = server.takeRequest()
        assertEquals("/rest/v1/rpc/listar_arena", request.path)
        assertEquals("Bearer test-access", request.getHeader("Authorization"))
        assertEquals(200, JSONObject(request.body.readUtf8()).getInt("p_limite"))
    }
    @Test fun enrollmentMetadataAllowsFirstNicknameButProtectsLegacySave() = runTest {
        login()
        respond("""[{"user_id":"$uid","apelido":"Caçador 111111","power_score":0,"patente":0,"perfil_publicado":false}]""")
        assertFalse(sync.meuRanking().getOrThrow()!!.protegePublicacaoLocal("Jota", 0f))
        assertEquals("/rest/v1/leaderboard?user_id=eq.$uid&limit=1", server.takeRequest().path)
        respond("""[{"user_id":"$uid","apelido":"Original save","power_score":320,"patente":4}]""")
        val legacy = sync.meuRanking().getOrThrow()!!
        assertTrue(legacy.perfilPublicado)
        assertTrue(legacy.protegePublicacaoLocal("Other nickname", 0f))
        assertFalse(legacy.protegePublicacaoLocal("Original save", 350f))
    }
    @Test fun signedOutDirectoryNeverFallsBackToPublicKey() = runTest {
        assertTrue(sync.leaderboard(2).isFailure)
        assertEquals(0, server.requestCount)
    }
}
