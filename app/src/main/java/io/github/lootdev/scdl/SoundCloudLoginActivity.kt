package io.github.lootdev.scdl

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.net.URLDecoder

class SoundCloudLoginActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    @Volatile
    private var capturedClientId: String = ""

    @Volatile
    private var capturedAuthToken: String = ""

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soundcloud_login)

        webView = findViewById(R.id.soundCloudWebView)
        statusText = findViewById(R.id.loginStatus)
        progressBar = findViewById(R.id.loginProgress)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = "$userAgentString SCDL-Android"
        }
        webView.addJavascriptInterface(CredentialBridge(), "AndroidCredentials")
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                captureFromRequest(request)
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                captureFromCookies(url)
                scanBrowserStorage(view)
                updateStatus()
            }
        }

        findViewById<Button>(R.id.useCredentialsButton).setOnClickListener { finishWithCredentials() }
        findViewById<Button>(R.id.closeLoginButton).setOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        webView.loadUrl("https://soundcloud.com/signin")
    }

    private fun captureFromRequest(request: WebResourceRequest) {
        val uri = request.url
        uri.getQueryParameter("client_id")?.let { captureClientId(it) }
        uri.getQueryParameter("oauth_token")?.let { captureAuthToken(it) }
        uri.getQueryParameter("access_token")?.let { captureAuthToken(it) }

        request.requestHeaders.entries
            .firstOrNull { it.key.equals("Authorization", ignoreCase = true) }
            ?.value
            ?.let { captureAuthToken(it) }
        updateStatus()
    }

    private fun captureFromCookies(url: String) {
        val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
        cookies.split(';').forEach { part ->
            val key = part.substringBefore('=', "").trim()
            val value = part.substringAfter('=', "").trim()
            when {
                key.contains("client", ignoreCase = true) && key.contains("id", ignoreCase = true) -> captureClientId(value)
                key.contains("oauth", ignoreCase = true) || key.contains("token", ignoreCase = true) -> captureAuthToken(value)
            }
        }
    }

    private fun scanBrowserStorage(view: WebView) {
        val script = """
            (function() {
                var dump = document.cookie || '';
                function addStorage(storage) {
                    try {
                        for (var i = 0; i < storage.length; i++) {
                            var key = storage.key(i);
                            dump += '\n' + key + '=' + storage.getItem(key);
                        }
                    } catch (e) {}
                }
                addStorage(window.localStorage);
                addStorage(window.sessionStorage);
                var client = '';
                var token = '';
                var cm = dump.match(/client[_-]?id[^A-Za-z0-9]+([A-Za-z0-9]{16,64})/i);
                var tm = dump.match(/(?:oauth[_-]?token|access[_-]?token)[^A-Za-z0-9._-]+([A-Za-z0-9._-]{20,})/i);
                if (cm) client = cm[1];
                if (tm) token = tm[1];
                AndroidCredentials.capture(client, token);
            })();
        """.trimIndent()
        view.evaluateJavascript(script, null)
    }

    private fun captureClientId(candidate: String) {
        val cleaned = clean(candidate)
        if (cleaned.matches(Regex("[A-Za-z0-9_-]{16,128}"))) capturedClientId = cleaned
    }

    private fun captureAuthToken(candidate: String) {
        var cleaned = clean(candidate)
        if (cleaned.startsWith("OAuth ", ignoreCase = true)) cleaned = cleaned.substringAfter(' ').trim()
        if (cleaned.length >= 20 && cleaned.matches(Regex("[A-Za-z0-9._-]+"))) capturedAuthToken = cleaned
    }

    private fun clean(value: String): String =
        runCatching { URLDecoder.decode(value.trim().trim('"', '\''), "UTF-8") }
            .getOrDefault(value.trim().trim('"', '\''))

    private fun updateStatus() {
        runOnUiThread {
            val client = if (capturedClientId.isBlank()) "not captured" else "captured"
            val token = if (capturedAuthToken.isBlank()) "not captured" else "captured"
            statusText.text =
                "Sign in to SoundCloud, then press Use captured credentials.\nclient_id: $client | auth_token: $token\nManual entry remains available in Settings."
        }
    }

    private fun finishWithCredentials() {
        captureFromCookies(webView.url ?: "https://soundcloud.com")
        scanBrowserStorage(webView)
        if (capturedClientId.isBlank() && capturedAuthToken.isBlank()) {
            updateStatus()
            return
        }
        setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(EXTRA_CLIENT_ID, capturedClientId)
                putExtra(EXTRA_AUTH_TOKEN, capturedAuthToken)
            }
        )
        finish()
    }

    private inner class CredentialBridge {
        @JavascriptInterface
        fun capture(clientId: String?, authToken: String?) {
            clientId?.takeIf { it.isNotBlank() }?.let { captureClientId(it) }
            authToken?.takeIf { it.isNotBlank() }?.let { captureAuthToken(it) }
            updateStatus()
        }
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("AndroidCredentials")
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CLIENT_ID = "client_id"
        const val EXTRA_AUTH_TOKEN = "auth_token"
    }
}
