package com.pinecone.pinecone.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.pinecone.pinecone.R
import com.pinecone.pinecone.data.ResourceData
import com.pinecone.pinecone.log.*

class WebViewActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var errorView: View
    private lateinit var errorMsg: TextView
    private lateinit var btnBack: Button
    private lateinit var btnRetry: Button

    private var currentUrl = ""
    private var enterTimeMs: Long = 0L

    /** Auto-generated from ResourceData — no hardcoded domains. */
    private val allowedDomains: Set<String>
        get() = ResourceData.getDomainWhitelist()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        webView = findViewById(R.id.webview)
        progress = findViewById(R.id.progress)
        titleView = findViewById(R.id.title)
        errorView = findViewById(R.id.error_view)
        errorMsg = findViewById(R.id.error_msg)
        btnBack = findViewById(R.id.btn_back)
        btnRetry = findViewById(R.id.btn_retry)

        currentUrl = intent?.getStringExtra("url") ?: "about:blank"

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/125.0.0.0 Safari/537.36"
        }

        // Mouse wheel / D-Pad scroll
        webView.setOnGenericMotionListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_SCROLL) {
                val v = event.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL) * 150
                val h = event.getAxisValue(android.view.MotionEvent.AXIS_HSCROLL) * 150
                if (v != 0f || h != 0f) {
                    webView.scrollBy((-h).toInt(), (-v).toInt())
                    return@setOnGenericMotionListener true
                }
            }
            false
        }

        webView.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                val scrollAmount = 200
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_PAGE_UP ->
                        webView.scrollBy(0, -scrollAmount)
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                    android.view.KeyEvent.KEYCODE_PAGE_DOWN ->
                        webView.scrollBy(0, scrollAmount)
                    else -> return@setOnKeyListener false
                }
                true
            } else false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (!com.pinecone.pinecone.ui.guard.editors.BrowserPrefs.isAllowFreeBrowsing(this@WebViewActivity)
                    && !isAllowed(url)) {
                    Toast.makeText(this@WebViewActivity,
                        "此网站不在学习白名单中", Toast.LENGTH_SHORT).show()
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (!com.pinecone.pinecone.ui.guard.editors.BrowserPrefs.isAllowFreeBrowsing(this@WebViewActivity)
                    && !isAllowed(url)) {
                    showError("此网站不在学习白名单中")
                    return
                }
                progress.visibility = View.VISIBLE
                progress.progress = 0
                errorView.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                titleView.text = view?.title ?: url ?: ""
                webView.requestFocus()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    showError("加载失败: ${error?.description ?: "网络错误"}")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                if (newProgress == 100) progress.visibility = View.GONE
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                titleView.text = title ?: currentUrl
            }
        }

        btnBack.setOnClickListener { goBackOrFinish() }
        btnRetry.setOnClickListener { loadUrl(currentUrl) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBackOrFinish() }
        })

        loadUrl(currentUrl)
    }

    override fun onResume() {
        super.onResume()
        enterTimeMs = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        logBrowsingDuration()
    }

    private fun logBrowsingDuration() {
        if (enterTimeMs > 0L && currentUrl.isNotEmpty()) {
            val domain = try {
                java.net.URI(currentUrl).host ?: currentUrl
            } catch (_: Exception) { currentUrl }
            try {
                PineconeLogger.log(WebBrowsingEvent(
                    System.currentTimeMillis(),
                    PineconeLogger.getSession()?.sessionId ?: "",
                    System.currentTimeMillis() - enterTimeMs,
                    domain
                ))
                PineconeLogger.getSession()?.recordDomain(domain)
            } catch (_: Exception) {}
        }
    }

    /** Check if a URL's host is in the auto-generated allowed domains. */
    private fun isAllowed(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (url == "about:blank") return true
        val host = Uri.parse(url).host?.removePrefix("www.")?.lowercase() ?: return false
        return allowedDomains.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    private fun loadUrl(url: String) {
        errorView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        titleView.text = "加载中…"
        webView.loadUrl(url)
        webView.requestFocus()
    }

    private fun showError(msg: String) {
        webView.visibility = View.GONE
        progress.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        errorMsg.text = msg
    }

    private fun goBackOrFinish() {
        if (webView.canGoBack()) webView.goBack() else finish()
    }
}
