package com.liferpg.data.sync

import android.content.Context
import android.net.Uri
import com.liferpg.data.net.await
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class Sessao(val accessToken: String, val refreshToken: String, val expiraEmMs: Long,
    val userId: String, val email: String?, val anonimo: Boolean) {
    // Expiry is deliberately excluded: a linked account can keep its local save offline.
    val vinculada: Boolean get() = !anonimo && !email.isNullOrBlank() && userId.isNotBlank() &&
        accessToken.isNotBlank() && refreshToken.isNotBlank()
}

/** Password authentication, legacy anonymous-account conversion, and OAuth PKCE. */
class SupabaseAuth(private val appContext: Context, private val url: String = SupabaseConfig.URL,
    private val anonKey: String = SupabaseConfig.ANON_KEY,
    private val temPerfilLocal: suspend () -> Boolean = { false }) {
    private val prefs = appContext.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE)
    private val owner = appContext.getSharedPreferences("save_account_owner", Context.MODE_PRIVATE)
    private val pending = appContext.getSharedPreferences("auth_pkce", Context.MODE_PRIVATE)
    private val json = "application/json".toMediaType()
    private val adapter = Moshi.Builder().build().adapter(GoTrueToken::class.java)
    private val refreshLock = Mutex()
    private val callbackLock = Mutex()
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private fun base() = url.trimEnd('/')
    private val _sessao = MutableStateFlow(carregar())
    val sessao: StateFlow<Sessao?> = _sessao.asStateFlow()
    val configurado get() = SupabaseConfig.configurado
    private val _googleDisponivel = MutableStateFlow(false)
    val googleDisponivel = _googleDisponivel.asStateFlow()
    private val _recuperandoSenha = MutableStateFlow(false)
    val recuperandoSenha = _recuperandoSenha.asStateFlow()
    private val _conversaoPendente = MutableStateFlow(pending.getString("purpose", "") == "UPGRADE")
    val conversaoPendente = _conversaoPendente.asStateFlow()
    private var recoverySession: Sessao? = null

    @JsonClass(generateAdapter = true)
    data class GoTrueToken(@Json(name = "access_token") val accessToken: String?,
        @Json(name = "refresh_token") val refreshToken: String?, @Json(name = "expires_in") val expiresIn: Long?, val user: GoTrueUser?)
    @JsonClass(generateAdapter = true)
    data class GoTrueUser(val id: String?, val email: String?, @Json(name = "is_anonymous") val isAnonymous: Boolean?)
    class SessaoInvalida(message: String) : IllegalStateException(message)
    class ConfirmacaoNecessaria(message: String) : IllegalStateException(message)

    suspend fun carregarProvedores() = resultado {
        _googleDisponivel.value = JSONObject(request("settings", method = "GET"))
            .optJSONObject("external")?.optBoolean("google") == true
    }
    /** An existing local save cannot accidentally be published under another account. */
    suspend fun protegerSaveLocal() {
        if (temPerfilLocal()) _sessao.value?.let { s ->
            if (owner.getString("uid", null) == null) owner.edit().putString("uid", s.userId).commit()
        }
    }
    private suspend fun aceitar(s: Sessao) {
        require(s.userId.isNotBlank() && s.accessToken.isNotBlank() && s.refreshToken.isNotBlank()) { "Sessão inválida." }
        protegerSaveLocal()
        if (temPerfilLocal()) {
            val uid = owner.getString("uid", null)
            if (uid != null && uid != s.userId) throw SessaoInvalida(
                "Este progresso pertence a outra conta. Entre na conta original ou proteja a identidade atual; o save não foi alterado.")
            owner.edit().putString("uid", s.userId).commit()
        }
        salvar(s); _sessao.value = s
    }
    suspend fun accessTokenValido(): String? {
        val s = _sessao.value ?: return null
        return if (System.currentTimeMillis() < s.expiraEmMs - 60_000) s.accessToken else atualizarToken().getOrNull()?.accessToken
    }
    suspend fun entrarEmail(email: String, senha: String): Result<Sessao> = resultado {
        postToken("token?grant_type=password", JSONObject().put("email", email).put("password", senha).toString())
    }
    suspend fun cadastrar(email: String, senha: String): Result<Sessao> = resultado {
        require(senha.length >= 8) { "Use pelo menos 8 caracteres na senha." }
        val redirect = prepararPkce("SIGNUP")
        val s = postToken("signup?redirect_to=${Uri.encode(redirect)}", JSONObject().put("email", email)
            .put("password", senha).put("code_challenge", challenge()).put("code_challenge_method", "s256").toString())
        pending.edit().clear().commit(); s
    }
    /** Same UUID throughout: friends and ranking remain attached to the original identity. */
    suspend fun converterConta(email: String, senha: String): Result<Sessao> = resultado {
        val s = _sessao.value ?: throw SessaoInvalida("Entre na identidade original antes de protegê-la.")
        require(s.anonimo || _conversaoPendente.value) { "Esta conta já está protegida." }
        require(senha.length >= 8) { "Use pelo menos 8 caracteres na senha." }
        val token = accessTokenValido() ?: throw SessaoInvalida("Sua sessão expirou. Entre novamente.")
        var user = JSONObject(request("user", method = "GET", bearer = token))
        if (!user.optString("email").equals(email, true) || user.isNull("email_confirmed_at")) {
            val redirect = prepararPkce("UPGRADE")
            user = JSONObject(request("user?redirect_to=${Uri.encode(redirect)}", JSONObject().put("email", email).toString(), "PUT", token))
            if (!user.optString("email").equals(email, true) || user.isNull("email_confirmed_at")) throw ConfirmacaoNecessaria(
                "Enviamos a confirmação ao seu e-mail. Confirme e volte para finalizar com a mesma senha. Seu progresso e seus amigos continuam preservados.")
        }
        require(user.optString("id") == s.userId) { "Identidade diferente; conversão interrompida." }
        request("user", JSONObject().put("password", senha).toString(), "PUT", token)
        val permanent = postToken("token?grant_type=password", JSONObject().put("email", email).put("password", senha).toString(), accept = false)
        require(!permanent.anonimo) { "Finalize a confirmação do e-mail antes de continuar." }
        aceitar(permanent)
        pending.edit().clear().commit(); _conversaoPendente.value = false; permanent
    }
    suspend fun atualizarToken(): Result<Sessao> = refreshLock.withLock {
        resultado {
            val s = _sessao.value ?: throw SessaoInvalida("Sem sessão")
            if (System.currentTimeMillis() < s.expiraEmMs - 60_000) s else postToken("token?grant_type=refresh_token",
                JSONObject().put("refresh_token", s.refreshToken).toString(), refresh = true)
        }
    }
    suspend fun iniciarGoogle(): Result<String> = resultado {
        carregarProvedores().getOrThrow()
        require(_googleDisponivel.value) { "O login Google ainda precisa ser ativado no servidor. Use e-mail e senha por enquanto." }
        val redirect = prepararPkce("GOOGLE")
        val query = "provider=google&redirect_to=${Uri.encode(redirect)}&code_challenge=${challenge()}&code_challenge_method=s256"
        if (_sessao.value?.anonimo == true) {
            val token = accessTokenValido() ?: throw SessaoInvalida("Sua sessão expirou.")
            JSONObject(request("user/identities/authorize?$query", method = "GET", bearer = token)).getString("url")
        } else "${base()}/auth/v1/authorize?$query"
    }
    suspend fun recuperarSenha(email: String): Result<Unit> = resultado {
        val redirect = prepararPkce("RECOVERY")
        request("recover?redirect_to=${Uri.encode(redirect)}", JSONObject().put("email", email)
            .put("code_challenge", challenge()).put("code_challenge_method", "s256").toString()); Unit
    }
    /** Only this device's recent PKCE request can consume an incoming app link. */
    suspend fun concluirLink(uri: Uri): Result<Unit> = callbackLock.withLock { resultado {
        require(uri.scheme == "remnant" && uri.host == "auth" && uri.path == "/callback") { "Link inválido." }
        val nonce = pending.getString("nonce", null)
        require(nonce != null && uri.getQueryParameter("request") == nonce &&
            System.currentTimeMillis() - pending.getLong("at", 0) in 0..900_000) { "Este link expirou. Solicite novamente neste aparelho." }
        if (uri.getQueryParameter("error") != null) throw SessaoInvalida("Autenticação cancelada ou recusada. Tente novamente.")
        val purpose = pending.getString("purpose", "")
        if (purpose == "UPGRADE") return@resultado Unit
        val code = uri.getQueryParameter("code") ?: throw SessaoInvalida("Link incompleto. Solicite outro.")
        val s = postToken("token?grant_type=pkce", JSONObject().put("auth_code", code)
            .put("code_verifier", pending.getString("verifier", "")).toString(), accept = purpose != "RECOVERY")
        pending.edit().clear().commit(); _conversaoPendente.value = false
        if (purpose == "RECOVERY") { recoverySession = s; _recuperandoSenha.value = true }
        Unit
    } }
    suspend fun salvarNovaSenha(senha: String): Result<Sessao> = resultado {
        require(senha.length >= 8) { "Use pelo menos 8 caracteres na senha." }
        val s = recoverySession ?: throw SessaoInvalida("Solicite outro link para recuperar a senha.")
        request("user", JSONObject().put("password", senha).toString(), "PUT", s.accessToken)
        aceitar(s); recoverySession = null; _recuperandoSenha.value = false; s
    }
    fun cancelarRecuperacao() { recoverySession = null; _recuperandoSenha.value = false }
    private fun prepararPkce(purpose: String): String {
        fun random() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val nonce = random()
        pending.edit().putString("purpose", purpose).putString("nonce", nonce).putString("verifier", random())
            .putLong("at", System.currentTimeMillis()).commit()
        _conversaoPendente.value = purpose == "UPGRADE"
        return "remnant://auth/callback?request=$nonce"
    }
    private fun challenge() = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
        .digest(pending.getString("verifier", "").orEmpty().toByteArray(Charsets.US_ASCII)))
    suspend fun sair() {
        val token = _sessao.value?.accessToken
        limparSessao()
        pending.edit().clear().commit()
        _conversaoPendente.value = false
        cancelarRecuperacao()
        if (token != null) resultado { request("logout?scope=local", "{}", bearer = token) }
    }
    private fun limparSessao() { prefs.edit().clear().commit(); _sessao.value = null }
    private suspend fun postToken(path: String, body: String, refresh: Boolean = false, accept: Boolean = true): Sessao {
        val t = adapter.fromJson(request(path, body, refresh = refresh)) ?: throw SessaoInvalida("Resposta inválida.")
        val access = t.accessToken ?: throw ConfirmacaoNecessaria("Confirme seu e-mail e entre com sua senha para continuar.")
        val s = Sessao(access, t.refreshToken.orEmpty(), System.currentTimeMillis() + (t.expiresIn ?: 3600) * 1000,
            t.user?.id.orEmpty(), t.user?.email, t.user?.isAnonymous ?: (t.user?.email == null))
        if (!refresh && !s.vinculada) throw SessaoInvalida("Vincule uma conta Google ou confirme seu e-mail para continuar.")
        if (accept) aceitar(s)
        return s
    }
    private suspend fun request(path: String, body: String? = null, method: String = "POST",
        bearer: String = anonKey, refresh: Boolean = false): String {
        val req = Request.Builder().url("${base()}/auth/v1/$path").header("apikey", anonKey)
            .header("Authorization", "Bearer $bearer")
            .method(method, if (method == "GET") null else (body ?: "{}").toRequestBody(json)).build()
        return client.newCall(req).await().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (refresh && response.code in listOf(400, 401, 403)) limparSessao()
                throw SessaoInvalida(mensagemErro(text, response.code))
            }
            text
        }
    }
    private suspend fun <T> resultado(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try { Result.success(block()) } catch (e: CancellationException) { throw e }
        catch (e: IOException) { Result.failure(IOException("Sem conexão. Tente novamente com internet.", e)) }
        catch (e: Exception) { Result.failure(e) }
    }
    private fun mensagemErro(body: String, code: Int): String {
        val msg = runCatching { JSONObject(body).optString("msg").ifBlank {
            JSONObject(body).optString("error_description").ifBlank { JSONObject(body).optString("message") }
        } }.getOrDefault("")
        return when {
            "Invalid login credentials" in msg -> "E-mail ou senha incorretos."
            "already registered" in msg || "already been registered" in msg -> "Este e-mail já possui uma conta."
            "Email not confirmed" in msg -> "Confirme seu e-mail antes de entrar."
            "Password" in msg || "password" in msg -> "Use uma senha com pelo menos 8 caracteres."
            "manual linking" in msg.lowercase() -> "A conversão de contas precisa ser ativada no servidor. Seu save continua preservado."
            code == 429 -> "Muitas tentativas. Aguarde um pouco."
            code in listOf(401, 403) -> "Sessão expirada. Entre novamente."
            else -> "Não foi possível concluir a autenticação. Tente novamente."
        }
    }
    private fun salvar(s: Sessao) { prefs.edit().putString("backend", base()).putString("access", s.accessToken)
        .putString("refresh", s.refreshToken).putLong("expira", s.expiraEmMs).putString("uid", s.userId)
        .putString("email", s.email).putBoolean("anon", s.anonimo).commit() }
    private fun carregar(): Sessao? {
        if (prefs.getString("backend", null) != base()) { prefs.edit().clear().commit(); return null }
        val access = prefs.getString("access", null) ?: return null
        return Sessao(access, prefs.getString("refresh", "").orEmpty(), prefs.getLong("expira", 0),
            prefs.getString("uid", "").orEmpty(), prefs.getString("email", null), prefs.getBoolean("anon", true))
    }
}
