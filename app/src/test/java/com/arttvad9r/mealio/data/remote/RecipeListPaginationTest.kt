package com.arttvad9r.mealio.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mealio asks for every recipe in one request (`perPage = -1`), so a list larger
 * than the old 60-item cap must come back whole. This checks the request we put
 * on the wire and that every item in the response is parsed.
 */
class RecipeListPaginationTest {

    @Test
    fun `requests perPage=-1 and returns more than 60 recipes`() {
        val server = MockWebServer()
        val items = (1..75).joinToString(",") {
            """{"id":"id-$it","slug":"recipe-$it","name":"Recipe $it"}"""
        }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"page":1,"per_page":75,"total":75,"total_pages":1,"items":[$items]}"""),
        )
        server.start()

        try {
            val api = MealieApiFactory(
                okHttpClient = MealieApiFactory.defaultOkHttp(),
                tokenProvider = { "test-token" },
            ).create(server.url("/").toString())

            val page = runBlocking {
                api.recipes(
                    page = 1,
                    perPage = -1,
                    search = null,
                    categories = null,
                    orderBy = "name",
                    orderDirection = "asc",
                )
            }

            assertEquals(75, page.items.size)
            assertEquals(75, page.total)

            val request = server.takeRequest()
            val path = request.path.orEmpty()
            assertTrue("perPage=-1 must be sent: $path", path.contains("perPage=-1"))
        } finally {
            server.shutdown()
        }
    }
}
