package com.arttvad9r.mealio.data.remote

import com.arttvad9r.mealio.data.mapper.toDomain
import com.arttvad9r.mealio.data.remote.dto.CreateRecipeRequest
import com.arttvad9r.mealio.data.remote.dto.ImportRecipeUrlRequest
import com.arttvad9r.mealio.data.remote.dto.ParseIngredientsRequest
import com.arttvad9r.mealio.data.remote.dto.RecipeUpdateRequest
import com.arttvad9r.mealio.data.repository.recipeImageParts
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-contract checks for the V1.5 recipe write operations against Mealie v3.28.0:
 * method + path, the PATCH body carrying only changed fields, the multipart image
 * contract (`image` file part + `extension` form field), and the bare-string
 * responses of POST /api/recipes and /api/recipes/create/url.
 */
class RecipeWriteContractTest {

    private val json = MealieApiFactory(MealieApiFactory.defaultOkHttp(), { null }).json

    private fun apiFor(server: MockWebServer): MealieApi =
        MealieApiFactory(MealieApiFactory.defaultOkHttp(), { "test-token" })
            .create(server.url("/").toString())

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `create recipe posts name and decodes the slug string`() {
        val server = MockWebServer()
        server.enqueue(jsonResponse("\"my-borsch\""))
        server.start()
        try {
            val slug = runBlocking {
                apiFor(server).createRecipe(CreateRecipeRequest("Борщ"))
            }
            assertEquals("my-borsch", slug)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/recipes", request.path)
            val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals(setOf("name"), body.keys)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `patch sends only the changed field so Mealie cannot wipe others`() {
        val server = MockWebServer()
        server.enqueue(jsonResponse("""{"slug":"r1","name":"Новый"}"""))
        server.start()
        try {
            val detail = runBlocking {
                apiFor(server).updateRecipe("r1", RecipeUpdateRequest(name = "Новый"))
            }
            assertEquals("r1", detail.slug)

            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/recipes/r1", request.path)
            val raw = request.body.readUtf8()
            val body = json.parseToJsonElement(raw).jsonObject
            assertEquals(setOf("name"), body.keys)
            assertTrue("patch must not carry nutrition", !raw.contains("nutrition"))
            assertTrue("patch must not carry recipeCategory", !raw.contains("recipeCategory"))
            assertTrue("patch must not carry tags", !raw.contains("tags"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `image upload uses the multipart image + extension contract`() {
        val server = MockWebServer()
        server.enqueue(jsonResponse("""{"image":"new-key"}"""))
        server.start()
        try {
            val (imagePart, extensionPart) = recipeImageParts(byteArrayOf(1, 2, 3), "png")
            val key = runBlocking {
                apiFor(server).uploadRecipeImage("r1", imagePart, extensionPart).image
            }
            assertEquals("new-key", key)

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/recipes/r1/image", request.path)
            assertTrue(
                "multipart/form-data expected: ${request.getHeader("Content-Type")}",
                request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"),
            )
            val raw = request.body.readUtf8()
            assertTrue(raw, raw.contains("name=\"image\""))
            assertTrue(raw, raw.contains("filename=\"image.png\""))
            assertTrue(raw, raw.contains("name=\"extension\""))
            assertTrue(raw, raw.contains("\r\n\r\npng"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `image part carries the right filename, mime and extension`() {
        val (imagePart, extensionPart) = recipeImageParts(byteArrayOf(9), ".JPG")
        val disposition = imagePart.headers!!["Content-Disposition"]!!
        assertTrue(disposition, disposition.contains("name=\"image\""))
        assertTrue(disposition, disposition.contains("filename=\"image.JPG\""))
        assertEquals("image/jpeg", imagePart.body.contentType().toString())

        val buffer = Buffer()
        extensionPart.writeTo(buffer)
        assertEquals("JPG", buffer.readUtf8())
    }

    @Test
    fun `import from url posts url and decodes the slug string`() {
        val server = MockWebServer()
        server.enqueue(jsonResponse("\"imported-slug\""))
        server.start()
        try {
            val slug = runBlocking {
                apiFor(server).importRecipeFromUrl(ImportRecipeUrlRequest("https://example.com/r"))
            }
            assertEquals("imported-slug", slug)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/recipes/create/url", request.path)
            val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals(setOf("url"), body.keys)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `parser posts lines and maps the parsed ingredients`() {
        val server = MockWebServer()
        server.enqueue(
            jsonResponse(
                """[{"input":"2 eggs","confidence":{"average":0.9},"ingredient":{""" +
                    """"quantity":2.0,"note":"","display":"2 eggs",""" +
                    """"food":{"id":"f1","name":"eggs"},"unit":null,"substitutions":[],"referenceId":"ref"}}]""",
            ),
        )
        server.start()
        try {
            val parsed = runBlocking {
                apiFor(server).parseIngredients(ParseIngredientsRequest(listOf("2 eggs")))
            }
            assertEquals(1, parsed.size)
            val line = parsed[0].toDomain()
            assertEquals("2 eggs", line.input)
            assertEquals(2.0, line.quantity)
            assertEquals("eggs", line.foodName)
            assertNull(line.unitName)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/parser/ingredients", request.path)
            val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals(setOf("ingredients"), body.keys)
            assertEquals(listOf("2 eggs"), body["ingredients"]!!.jsonArray.map { it.jsonPrimitive.content })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `unit search asks for all matches and decodes ids and names`() {
        val server = MockWebServer()
        server.enqueue(
            jsonResponse(
                """{"page":1,"perPage":-1,"total":2,"items":[""" +
                    """{"id":"u1","name":"грамм","pluralName":"граммы"},{"id":"u2","name":"литр"}]}""",
            ),
        )
        server.start()
        try {
            val page = runBlocking { apiFor(server).units("грамм") }
            assertEquals(2, page.total)
            assertEquals("u1", page.items[0].id)
            assertEquals("грамм", page.items[0].name)
            assertEquals("граммы", page.items[0].pluralName)

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/units?search=%D0%B3%D1%80%D0%B0%D0%BC%D0%BC&perPage=-1", request.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `food search decodes the loose answer the exact match filters`() {
        val server = MockWebServer()
        server.enqueue(
            jsonResponse(
                """{"page":1,"perPage":-1,"total":1,"items":[""" +
                    """{"id":"f1","name":"свиное филе"}]}""",
            ),
        )
        server.start()
        try {
            val page = runBlocking { apiFor(server).foods("куриного филе") }
            assertEquals("f1", page.items.single().id)

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertTrue(
                "the query must be the parser name: ${request.path}",
                request.path.orEmpty().startsWith(
                    "/api/foods?search=%D0%BA%D1%83%D1%80%D0%B8%D0%BD%D0%BE%D0%B3%D0%BE",
                ),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `delete image ignores Mealie's success body`() {
        val server = MockWebServer()
        server.enqueue(jsonResponse("""{"message":"Image deleted","error":false}"""))
        server.start()
        try {
            runBlocking { apiFor(server).deleteRecipeImage("r1") }
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/recipes/r1/image", request.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `delete recipe decodes the returned recipe`() {
        val server = MockWebServer()
        server.enqueue(jsonResponse("""{"slug":"r1","name":"X"}"""))
        server.start()
        try {
            val deleted = runBlocking { apiFor(server).deleteRecipe("r1") }
            assertEquals("r1", deleted.slug)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/recipes/r1", request.path)
        } finally {
            server.shutdown()
        }
    }
}
