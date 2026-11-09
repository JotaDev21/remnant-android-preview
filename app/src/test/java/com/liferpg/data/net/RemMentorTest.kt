package com.liferpg.data.net

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RemMentorTest {
    @Test fun searchesCatalogThenPreparesActionWithoutExecuting() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"search_exercises","args":{"query":"peito"}}}]}}],"usageMetadata":{"totalTokenCount":15}}"""))
            server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"prepare_action","args":{"kind":"start_workout","ids":["pushup"],"name":"Peito"}}}]}}],"usageMetadata":{"totalTokenCount":28}}"""))
            var queries = 0
            val reply = RemMentor(server.url("/generateContent").toString()).ask(
                "AIza-test-key", listOf(MentorMessage("user", "Monte um treino")), "Sem sessão ativa",
            ) { query ->
                assertEquals("peito", query)
                queries++
                "id=pushup; Flexão"
            }

            assertEquals(1, queries)
            assertEquals(43, reply.tokens)
            assertEquals(listOf("pushup"), reply.actions.single().ids)
            val first = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("AIza-test-key", first.getHeader("x-goog-api-key"))
            val firstBody = JSONObject(first.body.readUtf8())
            assertFalse(firstBody.has("model"))
            assertEquals(700, firstBody.getJSONObject("generationConfig").getInt("maxOutputTokens"))
            val second = JSONObject(server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8())
            val functionResult = second.getJSONArray("contents").getJSONObject(2)
                .getJSONArray("parts").getJSONObject(0).getJSONObject("functionResponse")
            assertEquals("search_exercises", functionResult.getString("name"))
            assertTrue(functionResult.getJSONObject("response").getString("result").contains("pushup"))
        }
    }

    @Test fun rateLimitDoesNotRetryOrExposeResponseBody() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setBody("private response details"))
            try {
                RemMentor(server.url("/generateContent").toString()).ask(
                    "AIza-test-key", listOf(MentorMessage("user", "Oi")), "",
                ) { "" }
                fail("Expected rate limit")
            } catch (e: MentorFailure) {
                assertFalse(e.explanation.contains("private response"))
            }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun plainTextReplyUsesGeminiUsageMetadata() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"role":"model","parts":[{"text":"Próxima série em 90 segundos."}]}}],"usageMetadata":{"totalTokenCount":21}}"""))
            val reply = RemMentor(server.url("/generateContent").toString()).ask(
                "AIza-test-key", listOf(MentorMessage("user", "E agora?")), "",
            ) { "" }
            assertEquals("Próxima série em 90 segundos.", reply.text)
            assertEquals(21, reply.tokens)
        }
    }
}
