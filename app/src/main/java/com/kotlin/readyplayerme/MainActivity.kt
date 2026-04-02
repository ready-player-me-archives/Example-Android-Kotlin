package com.kotlin.readyplayerme

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.kotlin.readyplayerme.databinding.ActivityMainBinding

// --- IMPORTANT OKHTTP IMPORTS ---
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity(), WebViewActivity.WebViewCallback {
    private lateinit var binding: ActivityMainBinding
    private var urlConfig: UrlConfig = UrlConfig()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WebViewActivity.setWebViewCallback(this)



        // --- THE UPDATED CREATE BUTTON ---
        binding.createButton.setOnClickListener {
            val myClientId = binding.clientIdInput.text.toString().trim()
            val myClientSecret = binding.clientSecretInput.text.toString().trim()
            val myUserName = binding.userNameInput.text.toString().trim()
            var myUserId = binding.userIdInput.text.toString().trim()

            if (myClientId.isEmpty() || myClientSecret.isEmpty() || myUserName.isEmpty() || myUserId.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields (including User ID)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // If you still want a fallback but allow override:
            // if (myUserId.isEmpty()) {
            //    myUserId = "user_${myUserName.lowercase().replace(" ", "_")}"
            // }

            // Switch to a loading message so the user knows it's working
            Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show()

            fetchStreamojiToken(myClientId, myClientSecret, myUserId, myUserName,
                onSuccess = { token ->
                    runOnUiThread {
                        // Put the token into our config and launch!
                        urlConfig.loginToken = token
                        urlConfig.clientId = myClientId
                        urlConfig.userName = myUserName
                        urlConfig.userId = myUserId

                        openWebViewPage(false)
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this, "Login Failed: $error", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }


    }

    // --- THE FUNCTION THAT EXCHANGES YOUR CREDENTIALS FOR A TOKEN ---
    private fun fetchStreamojiToken(
        clientId: String,
        clientSecret: String,
        userId: String,
        userName: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val client = OkHttpClient()
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val jsonBody = JSONObject().apply {
            put("userId", userId)
            put("userName", userName)
            put("expiresIn", 1800)
        }.toString()

        val request = Request.Builder()
            .url("https://us-central1-streamoji-265f4.cloudfunctions.net/getAuthToken")
            .addHeader("Client-Id", clientId)
            .addHeader("Client-Secret", clientSecret)
            .post(jsonBody.toRequestBody(mediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Network Error")
            }

            override fun onResponse(call: Call, response: Response) {
                // Using .body() instead of .body to solve the access error
                val responseBody = response.body?.string()
                val json = JSONObject(responseBody ?: "{}")

                if (response.isSuccessful && json.getBoolean("success")) {
                    val token = json.getString("authToken")
                    onSuccess(token)
                } else {
                    onError(json.optString("error", "Invalid Credentials"))
                }
            }
        })
    }

    private fun openAvatarView(avatarUrl: String){
        val intent = Intent(this, AvatarLoaded::class.java);
        intent.putExtra("user_avatar_url", avatarUrl);
        startActivity(intent);
    }

    private fun openWebViewPage(clearBrowserCache: Boolean) {
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra(WebViewActivity.CLEAR_BROWSER_CACHE, clearBrowserCache)
        intent.putExtra(WebViewActivity.URL_KEY, UrlBuilder(urlConfig).buildUrl())
        webViewActivityResultLauncher.launch(intent)
    }

    private val webViewActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("Streamoji", "WebView Session Finished")
        }
    }

    override fun onAvatarExported(avatarUrl: String) {
        Log.d("Streamoji", "Avatar Exported: $avatarUrl")
        // If it's a GLB, show a preview PNG
        val avatarImg = avatarUrl.replace(".glb", ".png")
        openAvatarView(avatarImg);
    }

    private fun showAlert(url: String){
        val context = this@MainActivity
        val clipboardData = ClipData.newPlainText("Streamoji avatars", url)
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(clipboardData)
        Toast.makeText(context, "Url copied into clipboard.", Toast.LENGTH_SHORT).show()

        val builder = AlertDialog.Builder(context).apply {
            setTitle("Result")
            setMessage(url)
            setPositiveButton("Ok"){ dialog, _ ->
                dialog.dismiss()
            }
        }.create()
        builder.show()
    }
}
