package com.kotlin.readyplayerme

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.SceneView
import com.squareup.picasso.Picasso
import com.google.ar.sceneform.rendering.ModelRenderable
import com.kotlin.readyplayerme.databinding.ActivityAvatarLoadedBinding

class AvatarLoaded : AppCompatActivity() {

    private lateinit var sceneView: SceneView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityAvatarLoadedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sceneView = binding.sceneView
        val modelUrl = intent.getStringExtra("user_avatar_url")
        val thumbnailUrl = intent.getStringExtra("user_thumbnail_url")

        binding.responseText.text = "Avatar URL:\n$modelUrl\n\nThumbnail URL:\n$thumbnailUrl"

        if (modelUrl != null) {
            downloadAndLoadModel(modelUrl)
        } else {
            Toast.makeText(this, "Model URL is missing", Toast.LENGTH_SHORT).show()
        }


    }

    private fun downloadAndLoadModel(url: String) {
        val file = java.io.File(cacheDir, "avatar_temp.glb")
        
        // Optional: If you want to skip download if it already exists (useful for debugging)
        // if (file.exists()) { load3DModel(Uri.fromFile(file)); return }

        Toast.makeText(this, "Downloading 3D Model...", Toast.LENGTH_SHORT).show()
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread {
                    Toast.makeText(this@AvatarLoaded, "Failed to download model", Toast.LENGTH_SHORT).show()
                    Log.e("AvatarLoaded", "Download failed", e)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@AvatarLoaded, "Server Error: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                try {
                    val body = response.body ?: throw java.io.IOException("Empty response body")
                    val inputStream = body.byteStream()
                    val outputStream = java.io.FileOutputStream(file)
                    
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.close()
                    inputStream.close()

                    runOnUiThread {
                        load3DModel(Uri.fromFile(file))
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@AvatarLoaded, "Error saving model file", Toast.LENGTH_SHORT).show()
                        Log.e("AvatarLoaded", "File error", e)
                    }
                }
            }
        })
    }

    private fun load3DModel(uri: Uri) {
        ModelRenderable.builder()
            .setSource(this, uri)
            .setIsFilamentGltf(true)
            .build()
            .thenAccept { renderable ->
                Toast.makeText(this, "Model Rendered!", Toast.LENGTH_SHORT).show()
                val node = Node()
                node.renderable = renderable
                
                // Adjust position: Streamoji/RPM models are usually at (0,0,0)
                // We move it forward (-z) and down (-y) to be seen by the default camera
                node.localPosition = com.google.ar.sceneform.math.Vector3(0f, -0.6f, -1.2f)
                node.localScale = com.google.ar.sceneform.math.Vector3(0.5f, 0.5f, 0.5f) // Slightly smaller for better fit
                sceneView.scene.addChild(node)

                // Add a light source
                val light = com.google.ar.sceneform.rendering.Light.builder(com.google.ar.sceneform.rendering.Light.Type.DIRECTIONAL)
                    .setColor(com.google.ar.sceneform.rendering.Color(android.graphics.Color.WHITE))
                    .setIntensity(100000f)
                    .build()
                val lightNode = Node()
                lightNode.light = light
                lightNode.localPosition = com.google.ar.sceneform.math.Vector3(0f, 2f, 2f)
                sceneView.scene.addChild(lightNode)
            }
            .exceptionally { throwable ->
                Log.e("AvatarLoaded", "Unable to load Renderable", throwable)
                runOnUiThread {
                    Toast.makeText(this, "Unable to load 3D model", Toast.LENGTH_SHORT).show()
                }
                null
            }
    }

    override fun onResume() {
        super.onResume()
        try {
            sceneView.resume()
        } catch (e: Exception) {
            Log.e("AvatarLoaded", "Error resuming scene", e)
        }
    }

    override fun onPause() {
        super.onPause()
        sceneView.pause()
    }

}