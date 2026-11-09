package com.liferpg.data.sync

import android.content.Context
import android.net.Uri
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
class AccountEntryTest {
    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var auth: SupabaseAuth
    private var hasProfile = false
    private val base get() = server.url("/").toString().trimEnd('/')
    @Before fun before() {
        context = ApplicationProvider.getApplicationContext()
        listOf("supabase_auth", "auth_pkce", "save_account_owner").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        server = MockWebServer(); server.start()
        auth = SupabaseAuth(context, base, "public-test-key") { hasProfile }
    }
    @After fun after() { server.shutdown() }
    private fun response(body: String, code: Int = 200) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body)
    private fun token(uid: String = "original-user", anon: Boolean = false) = JSONObject()
        .put("access_token", "test-access").put("refresh_token", "test-refresh").put("expires_in", 3600)
        .put("user", JSONObject().put("id", uid).put("email", if (anon) JSONObject.NULL else "test@example.com")
            .put("is_anonymous", anon)).toString()
    private fun seed(anon: Boolean = true, expiry: Long = Long.MAX_VALUE) {
        context.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE).edit().putString("backend", base)
            .putString("access", "old-access").putString("refresh", "old-refresh").putString("uid", "original-user")
            .putBoolean("anon", anon).putLong("expira", expiry).commit()
        auth = SupabaseAuth(context, base, "public-test-key") { hasProfile }
    }

    @Test fun emailLoginEncodesSpecialCharactersAndAcceptsOnlyCompleteSessions() = runTest {
        server.enqueue(response(token()))
        val login=auth.entrarEmail("test@example.com", "word\"\\\n8")
        assertTrue(login.exceptionOrNull()?.stackTraceToString(),login.isSuccess)
        val req = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=password", req.path)
        assertEquals("word\"\\\n8", JSONObject(req.body.readUtf8()).getString("password"))
        assertFalse(auth.sessao.value!!.anonimo)
    }
    @Test fun unconfirmedSignupCannotEnterAndUsesAnAppRedirect() = runTest {
        server.enqueue(response("{\"user\":{\"id\":\"original-user\"}}"))
        assertTrue(auth.cadastrar("test@example.com", "password8").exceptionOrNull() is SupabaseAuth.ConfirmacaoNecessaria)
        assertNull(auth.sessao.value)
        val req = server.takeRequest()
        assertTrue(req.requestUrl!!.queryParameter("redirect_to")!!.startsWith("remnant://auth/callback?request="))
        assertEquals("s256", JSONObject(req.body.readUtf8()).getString("code_challenge_method"))
    }
    @Test fun anonymousAndEmailLessTokensCannotUnlockTheApp() = runTest {
        server.enqueue(response(token(anon = true)))
        assertTrue(auth.entrarEmail("test@example.com", "password8").isFailure)
        assertNull(auth.sessao.value)
        server.enqueue(response(JSONObject(token()).apply {
            getJSONObject("user").put("email", JSONObject.NULL)
        }.toString()))
        assertTrue(auth.entrarEmail("test@example.com", "password8").isFailure)
        assertNull(auth.sessao.value)
    }
    @Test fun expiredLinkedAccountKeepsItsSaveAvailableOffline() = runTest {
        context.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE).edit()
            .putString("backend", base).putString("access", "old-access").putString("refresh", "old-refresh")
            .putString("uid", "original-user").putString("email", "test@example.com")
            .putBoolean("anon", false).putLong("expira", 1).commit()
        auth = SupabaseAuth(context, base, "public-test-key") { true }
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        assertNull(auth.accessTokenValido())
        assertTrue(auth.sessao.value!!.vinculada)
        assertEquals("original-user", auth.sessao.value!!.userId)
    }
    @Test fun conversionPreservesUuidAndNeverCallsSignup() = runTest {
        hasProfile = true; seed()
        val user = "{\"id\":\"original-user\",\"email\":\"test@example.com\",\"email_confirmed_at\":\"2026-10-06\"}"
        server.enqueue(response(user)); server.enqueue(response(user)); server.enqueue(response(token()))
        assertTrue(auth.converterConta("test@example.com", "password8").isSuccess)
        assertEquals("original-user", auth.sessao.value!!.userId)
        assertFalse(auth.sessao.value!!.anonimo)
        assertEquals("GET", server.takeRequest().method)
        val password = server.takeRequest()
        assertEquals("PUT", password.method)
        assertEquals("password8", JSONObject(password.body.readUtf8()).getString("password"))
        assertEquals("/auth/v1/token?grant_type=password", server.takeRequest().path)
        assertEquals("original-user", context.getSharedPreferences("save_account_owner", Context.MODE_PRIVATE).getString("uid", null))
    }
    @Test fun unverifiedConversionKeepsOldSessionAndNeverSetsPassword() = runTest {
        hasProfile = true; seed()
        server.enqueue(response("{\"id\":\"original-user\",\"email\":null,\"email_confirmed_at\":null}"))
        server.enqueue(response("{\"id\":\"original-user\",\"email\":\"test@example.com\",\"email_confirmed_at\":null}"))
        assertTrue(auth.converterConta("test@example.com", "password8").exceptionOrNull() is SupabaseAuth.ConfirmacaoNecessaria)
        assertTrue(auth.sessao.value!!.anonimo)
        assertTrue(auth.conversaoPendente.value)
        assertEquals(2, server.requestCount)
        assertEquals("old-access", auth.sessao.value!!.accessToken)
    }
    @Test fun anotherAccountCannotClaimAnExistingSaveEvenAfterLogout() = runTest {
        hasProfile = true; seed(); auth.protegerSaveLocal()
        server.enqueue(response(token("different-user")))
        assertTrue(auth.entrarEmail("other@example.com", "password8").isFailure)
        assertEquals("original-user", auth.sessao.value!!.userId)
        server.enqueue(response("{}")); auth.sair()
        server.enqueue(response(token("different-user")))
        assertTrue(auth.entrarEmail("other@example.com", "password8").isFailure)
        assertNull(auth.sessao.value)
    }
    @Test fun disabledGoogleDoesNotLaunchAFakeOAuthFlow() = runTest {
        server.enqueue(response("{\"external\":{\"google\":false}}"))
        assertTrue(auth.iniciarGoogle().isFailure)
        assertFalse(auth.googleDisponivel.value)
        assertEquals(1, server.requestCount)
    }
    @Test fun googleUsesPkceAndRejectsUnrelatedOrReplayedCallbacks() = runTest {
        server.enqueue(response("{\"external\":{\"google\":true}}"))
        val url = Uri.parse(auth.iniciarGoogle().getOrThrow())
        val callback = url.getQueryParameter("redirect_to")!!
        assertEquals("s256", url.getQueryParameter("code_challenge_method"))
        assertEquals(43, url.getQueryParameter("code_challenge")!!.length)
        assertTrue(auth.concluirLink(Uri.parse("remnant://auth/callback?request=forged&code=fake")).isFailure)
        assertNull(auth.sessao.value)
        server.enqueue(response(token()))
        assertTrue(auth.concluirLink(Uri.parse("$callback&code=verified-code")).isSuccess)
        server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("verified-code", body.getString("auth_code"))
        assertEquals(43, body.getString("code_verifier").length)
        assertTrue(auth.concluirLink(Uri.parse("$callback&code=verified-code")).isFailure)
    }
    @Test fun recoveryCannotEnterUntilANewPasswordIsSaved() = runTest {
        server.enqueue(response("{}")); auth.recuperarSenha("test@example.com").getOrThrow()
        val callback = server.takeRequest().requestUrl!!.queryParameter("redirect_to")!!
        server.enqueue(response(token()))
        auth.concluirLink(Uri.parse("$callback&code=recovery-code")).getOrThrow()
        assertNull(auth.sessao.value)
        assertTrue(auth.recuperandoSenha.value)
        server.enqueue(response("{}"))
        assertTrue(auth.salvarNovaSenha("newpassword8").isSuccess)
        assertNotNull(auth.sessao.value)
        assertFalse(auth.recuperandoSenha.value)
    }
    @Test fun logoutInvalidatesPendingLinksAndRecoveryWithoutDeletingTheSaveOwner() = runTest {
        hasProfile = true; seed(anon = false)
        auth.protegerSaveLocal()
        server.enqueue(response("{}")); auth.recuperarSenha("test@example.com").getOrThrow()
        val callback = server.takeRequest().requestUrl!!.queryParameter("redirect_to")!!
        server.enqueue(response(token()))
        auth.concluirLink(Uri.parse("$callback&code=recovery-code")).getOrThrow()
        assertTrue(auth.recuperandoSenha.value)
        server.enqueue(response("{}")); auth.sair()
        assertNull(auth.sessao.value)
        assertFalse(auth.recuperandoSenha.value)
        assertTrue(auth.salvarNovaSenha("newpassword8").isFailure)
        assertTrue(auth.concluirLink(Uri.parse("$callback&code=recovery-code")).isFailure)
        assertEquals("original-user", context.getSharedPreferences("save_account_owner", Context.MODE_PRIVATE).getString("uid", null))
    }
    @Test fun temporaryServerFailureKeepsSessionButRejectedRefreshRequiresLogin() = runTest {
        hasProfile = true; seed(expiry = 0)
        server.enqueue(response("{}", 503))
        assertTrue(auth.atualizarToken().isFailure)
        assertNotNull(auth.sessao.value)
        server.enqueue(response("{}", 401))
        assertTrue(auth.atualizarToken().isFailure)
        assertNull(auth.sessao.value)
    }
}
