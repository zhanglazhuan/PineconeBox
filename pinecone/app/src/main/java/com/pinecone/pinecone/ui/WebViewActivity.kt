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

class WebViewActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var errorView: View
    private lateinit var errorMsg: TextView
    private lateinit var btnBack: Button
    private lateinit var btnRetry: Button

    private var currentUrl = ""

    /** Only allow navigation to these domains (and subdomains). */
    private val allowedDomains = setOf(
        // 国字号
        "smartedu.cn", "basic.smartedu.cn", "reading.smartedu.cn", "higher.smartedu.cn",
        "language.smartedu.cn", "jpk.basic.smartedu.cn",
        "eduyun.cn", "vlab.eduyun.cn", "ai.eduyun.cn", "1s1k.eduyun.cn",
        "cdstm.cn", "kepu.gov.cn", "kepuchina.cn",
        "open.nlc.cn", "nlc.cn",
        "pep.com.cn",
        "cctv.com", "cctv.cn", "jishi.cctv.com", "shaoer.cctv.com",
        "xuexi.cn",
        "icourse163.org",
        "qspfw.moe.gov.cn", "centv.cn",
        "chnmuseum.cn",
        // 优质站
        "gushiwen.cn", "sou-yun.cn", "zdic.net", "shidianguji.com", "allhistory.com",
        "phet.colorado.edu", "yangcong345.com", "leleketang.com",
        "vocabulary.com", "quizlet.com", "yingyutu.com",
        "jyeoo.com", "shijuan1.com", "jiaoyanyun.com",
        "scratch.mit.edu", "coding.codemao.cn",
        "jlpcn.net", "kids.nationalgeographic.com",
        "processon.com", "canva.cn", "canva.com",
        "kiddoworksheets.com", "withoutad.com",
        "coolmathgames.com",
        "chinese-culture.net",
        "studynav.com", "zxls.com", "zxxk.com",
        "qhfx.aixuetang.com",
        // 可视化
        "geogebra.org", "desmos.com", "netpad.net.cn", "mathigon.org",
        "chemix.org", "molview.org", "solarsystemscope.com",
        // 欧美
        "khanacademy.org", "zh.khanacademy.org",
        "ck12.org", "allinonehomeschool.com", "ed.ted.com",
        "newsela.com", "illustrativemathematics.org",
        "bbc.co.uk", "school-education.ec.europa.eu", "youth.europa.eu",
        // 纪录片
        "yangshipin.cn", "docuchina.cn",
        "bilibili.com", "1905.com",
        "archive.org", "video.pbs.org", "arte.tv", "nfb.ca",
        "youtube.com",
        // AI
        "hourofcode.com", "aiquest.org"
    )

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
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                // Check whitelist unless free browsing is enabled
                if (!com.pinecone.pinecone.ui.guard.editors.BrowserPrefs.isAllowFreeBrowsing(this@WebViewActivity)
                    && !isAllowed(url)) {
                    Toast.makeText(this@WebViewActivity,
                        "此网站不在学习白名单中", Toast.LENGTH_SHORT).show()
                    return true
                }
                return false // Allow navigation
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
                if (newProgress == 100) {
                    progress.visibility = View.GONE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                titleView.text = title ?: currentUrl
            }
        }

        btnBack.setOnClickListener { goBackOrFinish() }
        btnRetry.setOnClickListener { loadUrl(currentUrl) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goBackOrFinish()
            }
        })

        loadUrl(currentUrl)
    }

    /** Check if a URL's host is in the allowed domains or is a subdomain of one. */
    private fun isAllowed(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (url == "about:blank") return true
        val host = Uri.parse(url).host ?: return false
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
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }
}
