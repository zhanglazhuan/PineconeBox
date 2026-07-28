package com.pinecone.pinecone.ui.guard

import android.content.Context
import android.net.ConnectivityManager
import java.net.HttpURLConnection
import java.net.URL

object QuoteCache {
    const val DEFAULT_QUOTE = "眼睛是心灵的窗户，要好好爱护它。"

    private val LOCAL_QUOTES = listOf(
        "眼睛是心灵的窗户，要好好爱护它。" to "",
        "目不能两视而明，耳不能两听而聪。" to "《荀子·劝学》",
        "眼睛如果还没有变得像太阳，它就看不见太阳。" to "普洛丁",
        "休息一下，世界会更清晰。" to "",
        "闭目养神，方能看得更远。" to "",
        "身体是革命的本钱，眼睛是学习的窗口。" to "",
        "保护视力，从每一次休息开始。" to "",
        "Take a break — your eyes deserve it." to "",
        "The eyes are the window to the soul." to "Thomas Phaer",
        "Rest is not idleness. It is the key to better vision." to "",
    )

    @Volatile
    var quote: String = DEFAULT_QUOTE
        private set

    @Volatile
    var quoteFrom: String = ""
        private set

    @Volatile
    private var loaded: Boolean = false

    /** Immediate local quote, no network. */
    fun preFetch() {
        if (loaded) return
        val (q, from) = LOCAL_QUOTES.random()
        quote = q
        quoteFrom = from
        loaded = true
    }

    /** Local + network: local first, then try all 3 sources via active network. */
    fun preFetch(context: Context) {
        if (loaded) return
        val (q, from) = LOCAL_QUOTES.random()
        quote = q
        quoteFrom = from
        loaded = true

        Thread {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return@Thread

            // Random order, first success wins
            val fetchers = listOf(
                { fetchHitokoto(net) },
                { fetchJinrishici(net) },
                { fetchZenQuotes(net) }
            ).shuffled()

            for (f in fetchers) {
                try {
                    if (f()) {
                        android.util.Log.d("QuoteCache", "network OK: ${quote.take(40)}")
                        return@Thread
                    }
                } catch (e: Exception) {
                    android.util.Log.d("QuoteCache", "source failed: ${e.message}")
                }
            }
            android.util.Log.d("QuoteCache", "all sources failed, keeping local quote")
        }.start()
    }

    private fun fetchHitokoto(net: android.net.Network): Boolean {
        val conn = net.openConnection(URL("https://v1.hitokoto.cn/")) as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 5000
        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val obj = org.json.JSONObject(json)
        val hitokoto = obj.optString("hitokoto", "")
        if (hitokoto.isEmpty()) return false
        quote = hitokoto
        val from = obj.optString("from", "")
        quoteFrom = if (from.isNotEmpty()) "《$from》" else ""
        return true
    }

    private fun fetchJinrishici(net: android.net.Network): Boolean {
        val conn = net.openConnection(URL("https://v1.jinrishici.com/all.json")) as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 5000
        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val obj = org.json.JSONObject(json)
        val content = obj.optString("content", "")
        if (content.isEmpty()) return false
        quote = content
        val origin = obj.optString("origin", "")
        val author = obj.optString("author", "")
        quoteFrom = when {
            origin.isNotEmpty() && author.isNotEmpty() -> "《$origin》$author"
            origin.isNotEmpty() -> "《$origin》"
            author.isNotEmpty() -> author
            else -> ""
        }
        return true
    }

    private fun fetchZenQuotes(net: android.net.Network): Boolean {
        val conn = net.openConnection(URL("https://zenquotes.io/api/random")) as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 5000
        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = org.json.JSONArray(json)
        if (arr.length() == 0) return false
        val obj = arr.getJSONObject(0)
        val q = obj.optString("q", "")
        if (q.isEmpty()) return false
        quote = q
        val author = obj.optString("a", "")
        quoteFrom = if (author.isNotEmpty()) "— $author" else ""
        return true
    }
}
