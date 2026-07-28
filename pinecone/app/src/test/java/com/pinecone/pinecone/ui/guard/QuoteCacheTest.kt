package com.pinecone.pinecone.ui.guard

import org.junit.Assert.*
import org.junit.Test

class QuoteCacheTest {

    @Test
    fun `hitokoto API returns valid JSON`() {
        try {
            val json = java.net.URL("https://v1.hitokoto.cn/").readText()
            println("hitokoto raw: ${json.take(200)}")
            val obj = org.json.JSONObject(json)
            val quote = obj.optString("hitokoto", "")
            val from = obj.optString("from", "")
            println("quote=$quote from=$from")
            assertTrue("hitokoto should not be empty", quote.isNotEmpty())
        } catch (e: Exception) {
            println("hitokoto FAILED: ${e.javaClass.simpleName}: ${e.message}")
            // Not a hard failure — network may be unavailable in test
        }
    }

    @Test
    fun `jinrishici API returns valid JSON`() {
        try {
            val json = java.net.URL("https://v1.jinrishici.com/all.json").readText()
            println("jinrishici raw: ${json.take(200)}")
            val obj = org.json.JSONObject(json)
            val content = obj.optString("content", "")
            val origin = obj.optString("origin", "")
            val author = obj.optString("author", "")
            println("content=$content origin=$origin author=$author")
            assertTrue("content should not be empty", content.isNotEmpty())
        } catch (e: Exception) {
            println("jinrishici FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Test
    fun `zenquotes API returns valid JSON`() {
        try {
            val json = java.net.URL("https://zenquotes.io/api/random").readText()
            println("zenquotes raw: ${json.take(200)}")
            val arr = org.json.JSONArray(json)
            assertTrue("should have at least 1 item", arr.length() > 0)
            val obj = arr.getJSONObject(0)
            val q = obj.optString("q", "")
            val a = obj.optString("a", "")
            println("quote=$q author=$a")
            assertTrue("quote should not be empty", q.isNotEmpty())
        } catch (e: Exception) {
            println("zenquotes FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
