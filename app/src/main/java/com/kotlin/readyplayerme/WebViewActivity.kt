package com.kotlin.readyplayerme

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.kotlin.readyplayerme.databinding.ActivityWebViewBinding
import com.kotlin.readyplayerme.WebViewInterface.WebMessage

class WebViewActivity : AppCompatActivity() {
    interface WebViewCallback {
        fun onAvatarExported(avatarUrl: String, thumbnailUrl: String?)

    }

    companion object {
        private const val ID_KEY = "id"
        private const val ASSET_ID_KEY = "assetId"
        const val CLEAR_BROWSER_CACHE = "clear_browser_cache"
        const val URL_KEY = "url_key"
        var callback: WebViewCallback? = null

        fun setWebViewCallback(callback: WebViewCallback) {
            this.callback = callback
        }
    }

    private lateinit var binding: ActivityWebViewBinding
    private var isCreateNew = false

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var webViewUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCreateNew = intent.getBooleanExtra(CLEAR_BROWSER_CACHE, false)
        // Default pointed to Streamoji
        webViewUrl = intent.getStringExtra(URL_KEY) ?: "https://avatars.streamoji.com?iframe=true"
        binding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        setUpWebView(intent.getBooleanExtra(CLEAR_BROWSER_CACHE, false))
        setUpWebViewClient()
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setUpWebView(clearBrowserCache: Boolean) {
        Log.d("Streamoji", "onCreate: clearBrowserCache $clearBrowserCache")
        with(binding.webview.settings){
            javaScriptEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            databaseEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }

        with(binding.webview){
            // Bridge named "WebView" to match the injected JS call
            addJavascriptInterface(WebViewInterface(this@WebViewActivity){ webMessage ->
                handleWebMessage(webMessage)
            }, "WebView")

            if (clearBrowserCache){
                clearWebViewData()
            }
            Log.d("Streamoji","setUpWebView url = $webViewUrl")
            loadUrl(webViewUrl)
        }
    }

    private fun setUpWebViewClient() {
        with(binding.webview){
            webViewClient = object: WebViewClient(){
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.progressBar.visibility = View.GONE
                    visibility = View.VISIBLE
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    executeJavascript()
                }
            }

            webChromeClient = object: WebChromeClient(){
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@WebViewActivity.filePathCallback = filePathCallback

                    fileChooserParams?.let {
                        if (it.isCaptureEnabled){
                            if (hasPermissionAccess()) {
                                openCameraResultContract.launch(null)
                            } else {
                                requestPermission.launch(arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ))
                            }
                        } else {
                            openDocumentContract.launch("image/*")
                        }
                    }
                    return true
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    Log.d("PERMISSION", "onPermissionRequest: ${request?.resources} ")
                    request?.grant(arrayOf(Manifest.permission.CAMERA))
                }
            }
        }
    }

    private val openCameraResultContract  = registerForActivityResult(ActivityResultContracts.TakePicturePreview()){
        it?.let {
            val path = MediaStore.Images.Media.insertImage(contentResolver, it, "fromCamera.jpeg", "")
            filePathCallback?.onReceiveValue(arrayOf(Uri.parse(path)))
        } ?: Toast.makeText(this, "No Image captured !!", Toast.LENGTH_SHORT).show()
    }

    private val openDocumentContract = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ){
        it?.let {
            filePathCallback?.onReceiveValue(arrayOf(it))
        } ?: Toast.makeText(this, "No Image Selected !!", Toast.LENGTH_SHORT).show()
    }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){ permissionMap ->
        if (!permissionMap.values.all { it }){
            Toast.makeText(this, "Camera permission not granted.", Toast.LENGTH_SHORT).show()
        } else {
            openCameraResultContract.launch(null)
        }
    }

    private fun hasPermissionAccess(): Boolean{
        return arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ).all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun executeJavascript() {
        with(binding.webview){
            evaluateJavascript("""
                var hasSentPostMessage = false;
                function subscribe(event) {
                    const json = parse(event);
                    if (!json) return;
                    
                    const source = json.source;
                    if (source !== 'streamojiavatars') return;
                    
                    if (json.eventName === 'v1.frame.ready' && !hasSentPostMessage) {
                        window.postMessage(
                            JSON.stringify({
                                target: 'streamojiavatars',
                                type: 'subscribe',
                                eventName: 'v1.**'
                            }),
                            '*'
                        );
                        hasSentPostMessage = true;
                    }

                    WebView.receiveData(event.data)
                }

                function parse(event) {
                    try {
                        return JSON.parse(event.data);
                    } catch (error) {
                        return null;
                    }
                }

                window.removeEventListener('message', subscribe);
                window.addEventListener('message', subscribe);
            """.trimIndent(), null)
        }
    }

    private fun handleWebMessage(webMessage: WebMessage) {
        Log.d("Streamoji", "Event received: ${webMessage.eventName}")

        when (webMessage.eventName) {
            // Streamoji Events
            WebViewInterface.WebViewEvents.FRAME_READY -> {
                Log.d("Streamoji", "Creator Frame is ready.")
            }

            WebViewInterface.WebViewEvents.AVATAR_EXPORT -> {
                val avatarUrl = webMessage.data?.get("url")
                    ?: webMessage.data?.get("avatarUrl") // Handle both naming possibilities
                val thumbnailUrl = webMessage.data?.get("thumbnailUrl")
                val responseUserId = webMessage.data?.get("userid") ?: webMessage.data?.get("userId")

                if (avatarUrl != null) {
                    Log.d("Streamoji", "Avatar Exported: $avatarUrl for User ID: $responseUserId, Thumbnail: $thumbnailUrl")
                    callback?.onAvatarExported(avatarUrl, thumbnailUrl)
                    finishActivityWithResult()
                } else {
                    Log.e("Streamoji", "Exported event received but URL is missing.")
                }
            }

        }
    }

    private fun finishActivityWithResult() {
        val resultString = "Avatar Created Successfully"
        val data = Intent()
        data.putExtra("result_key", resultString)
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private fun finishActivityWithFailure(errorMessage: String) {
        val data = Intent()
        data.putExtra("error_key", errorMessage)
        setResult(Activity.RESULT_CANCELED, data)
        finish()
    }

    fun WebView.clearWebViewData() {
        clearHistory()
        clearFormData()
        clearCache(true)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().removeSessionCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }
}
