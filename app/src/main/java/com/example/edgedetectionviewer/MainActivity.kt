package com.example.edgedetectionviewer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.edgedetectionviewer.camera.CameraHandler
import com.example.edgedetectionviewer.gl.GLRenderer

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_REQUEST = 100
    }

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button

    private val nativeProcessor = NativeProcessor()
    private var cameraHandler: CameraHandler? = null
    private var glRenderer: GLRenderer? = null

    private var isEdgeDetectionEnabled = true
    private var frameCount = 0
    private var totalProcessingTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        glSurfaceView = findViewById(R.id.glSurfaceView)
        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)

        // Setup OpenGL ES 2.0
        glSurfaceView.setEGLContextClientVersion(2)
        glRenderer = GLRenderer()
        glSurfaceView.setRenderer(glRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        Log.i(TAG, "OpenGL ES 2.0 renderer initialized")

        // Verify native library is loaded
        try {
            if (!nativeProcessor.isOpenCVAvailable()) {
                statusText.text = "❌ Native OpenCV not available"
                Toast.makeText(this, "Native OpenCV library failed to load", Toast.LENGTH_LONG).show()
                return
            }

            val version = nativeProcessor.getOpenCVVersion()
            Log.i(TAG, "✅ OpenCV version: $version")
            statusText.text = "✅ OpenCV $version\n🎮 OpenGL ES 2.0 Ready\nRequesting camera permission..."

        } catch (e: Exception) {
            Log.e(TAG, "Error checking OpenCV: ${e.message}", e)
            statusText.text = "❌ Error: ${e.message}"
            Toast.makeText(this, "Failed to initialize: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        // Setup toggle button
        toggleButton.setOnClickListener {
            isEdgeDetectionEnabled = !isEdgeDetectionEnabled
            toggleButton.text = if (isEdgeDetectionEnabled) {
                "Disable Edge Detection"
            } else {
                "Enable Edge Detection"
            }
            updateStatus()
        }

        // Check and request camera permission
        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        if (hasCameraPermission() && cameraHandler == null) {
            startCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
        stopCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
    }

    // CRITICAL: Complete camera pipeline implementation
    private fun startCamera() {
        Log.i(TAG, "Starting camera...")

        // Create camera handler with proper callback that connects the entire pipeline
        cameraHandler = CameraHandler(this) { rgbaBytes, width, height, timestamp ->
            // PHASE 3: JNI Bridge - Process frame through native OpenCV
            try {
                val startTime = System.currentTimeMillis()

                // Call native processing with ByteArray (exact specification)
                val processedBytes = nativeProcessor.processFrameBytes(
                    rgbaBytes, width, height, isEdgeDetectionEnabled
                )

                val processingTime = System.currentTimeMillis() - startTime
                totalProcessingTime += processingTime
                frameCount++

                if (processedBytes != null) {
                    // PHASE 5: OpenGL Rendering - Update renderer with processed frame
                    glRenderer?.updateFrame(processedBytes, width, height)

                    // Update UI stats on main thread
                    runOnUiThread {
                        updateStatus()
                    }
                } else {
                    Log.w(TAG, "Native processing returned null")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame: ${e.message}", e)
            }
        }

        cameraHandler?.startCamera()

        statusText.text = "📷 Camera starting...\n🔄 Initializing pipeline"
        Log.i(TAG, "Camera handler created and started")
    }

    private fun stopCamera() {
        cameraHandler?.stopCamera()
        cameraHandler = null
        Log.i(TAG, "Camera stopped")
    }

    private fun updateStatus() {
        val mode = if (isEdgeDetectionEnabled) "Edge Detection" else "Raw Camera"
        val avgProcessingTime = if (frameCount > 0) totalProcessingTime / frameCount else 0
        val cameraFps = cameraHandler?.getCurrentFps() ?: 0f
        val renderFps = glRenderer?.getRenderFps() ?: 0f
        val stabilizationStatus = cameraHandler?.getStabilizationStatus() ?: "❌ Not Available"

        statusText.text = """
            📷 Camera: ${if (cameraHandler != null) "Active" else "Inactive"}
            🎯 Mode: $mode
            🛡️ Stabilization: $stabilizationStatus
            📊 Camera FPS: ${"%.1f".format(cameraFps)}
            🖥️ Render FPS: ${"%.1f".format(renderFps)}
            ⚡ Processing: ${avgProcessingTime}ms avg
            📈 Frames: $frameCount
        """.trimIndent()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Camera permission granted")
                    startCamera()
                } else {
                    Log.w(TAG, "Camera permission denied")
                    statusText.text = "❌ Camera permission required\nPlease grant camera permission in settings"
                    Toast.makeText(
                        this,
                        "Camera permission is required for this app to function",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}