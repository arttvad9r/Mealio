package com.arttvad9r.mealio.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlNormalizerTest {

    @Test
    fun `adds scheme when missing`() {
        val r = UrlNormalizer.normalize("192.168.1.10:9925")
        assertTrue(r is UrlNormalizer.Result.Ok)
        assertEquals("http://192.168.1.10:9925/", (r as UrlNormalizer.Result.Ok).baseUrl)
    }

    @Test
    fun `keeps https`() {
        val r = UrlNormalizer.normalize("https://mealie.example.com")
        assertEquals("https://mealie.example.com/", (r as UrlNormalizer.Result.Ok).baseUrl)
    }

    @Test
    fun `preserves sub path`() {
        val r = UrlNormalizer.normalize("http://host:9925/mealie")
        assertEquals("http://host:9925/mealie/", (r as UrlNormalizer.Result.Ok).baseUrl)
    }

    @Test
    fun `trims trailing slash`() {
        val r = UrlNormalizer.normalize("http://host:9925/")
        assertEquals("http://host:9925/", (r as UrlNormalizer.Result.Ok).baseUrl)
    }

    @Test
    fun `trims whitespace`() {
        val r = UrlNormalizer.normalize("  http://host:9925  ")
        assertTrue(r is UrlNormalizer.Result.Ok)
    }

    @Test
    fun `blank is invalid`() {
        assertTrue(UrlNormalizer.normalize("   ") is UrlNormalizer.Result.Invalid)
    }

    @Test
    fun `unsupported scheme is invalid`() {
        assertTrue(UrlNormalizer.normalize("ftp://host") is UrlNormalizer.Result.Invalid)
    }
}
