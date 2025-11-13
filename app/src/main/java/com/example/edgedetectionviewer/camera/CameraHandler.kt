package com.example.edgedetectionviewer.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import java.nio.ByteBuffer
import java.util.Arrays

/**
 * ENHANCED: Camera Handler with IMAGE STABILIZATION + ORIENTATION FIX
 * This handles camera → stabilization → YUV/RGB conversion → edge detection pipeline
 */
class CameraHandler(
    private val context: Context,
    private val onFrameProcessed: (ByteArray, Int, Int, Long) -> Unit  // CRITICAL: ByteArray format as specified
) {
    companion object {
        private const val TAG = "CameraHandler"
        // STEP 2.2: Specific format specification - start with 640x480 for testing
        private const val CAMERA_WIDTH = 640
        private const val CAMERA_HEIGHT = 480
        private const val CAMERA_FPS = 30

        // STABILIZATION PARAMETERS
        private const val STABILIZATION_ENABLED = true
        private const val STABILIZATION_BUFFER_SIZE = 5
        private const val MOTION_THRESHOLD = 0.15f
        private const val SMOOTHING_FACTOR = 0.3f
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    private var previewSize = Size(CAMERA_WIDTH, CAMERA_HEIGHT)
    private var isProcessing = false
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()
    private var currentFps = 0f

    // NEW: Orientation handling variables
    private var sensorOrientation = 0
    private var deviceRotation = 0

    // NEW: Image Stabilization Components
    private var isStabilizationEnabled = STABILIZATION_ENABLED
    private val frameBuffer = mutableListOf<StabilizedFrame>()
    private var referenceFrame: ByteArray? = null
    private var lastStableTransform = FloatArray(6) // [tx, ty, rotation, scale_x, scale_y, shear]
    private var motionDetector: MotionDetector? = null

    data class StabilizedFrame(
        val imageData: ByteArray,
        val width: Int,
        val height: Int,
        val timestamp: Long,
        val transform: FloatArray
    )

    inner class MotionDetector {
        private var previousGrayFrame: ByteArray? = null

        fun detectMotion(currentFrame: ByteArray, width: Int, height: Int): FloatArray {
            val grayFrame = convertToGrayscale(currentFrame, width, height)

            val transform = FloatArray(6)

            previousGrayFrame?.let { prevFrame ->
                // Simple motion estimation using feature correlation
                val motionVector = calculateMotionVector(prevFrame, grayFrame, width, height)
                transform[0] = motionVector[0] // tx
                transform[1] = motionVector[1] // ty
                transform[2] = motionVector[2] // rotation
                transform[3] = 1.0f // scale_x
                transform[4] = 1.0f // scale_y
                transform[5] = 0.0f // shear
            }

            previousGrayFrame = grayFrame
            return transform
        }

        private fun convertToGrayscale(rgbaFrame: ByteArray, width: Int, height: Int): ByteArray {
            val grayFrame = ByteArray(width * height)
            for (i in 0 until width * height) {
                val rgbaIndex = i * 4
                val r = rgbaFrame[rgbaIndex].toInt() and 0xFF
                val g = rgbaFrame[rgbaIndex + 1].toInt() and 0xFF
                val b = rgbaFrame[rgbaIndex + 2].toInt() and 0xFF
                grayFrame[i] = ((r * 0.299f + g * 0.587f + b * 0.114f).toInt().toByte())
            }
            return grayFrame
        }

        private fun calculateMotionVector(
            prevFrame: ByteArray,
            currentFrame: ByteArray,
            width: Int,
            height: Int
        ): FloatArray {
            var totalDx = 0.0f
            var totalDy = 0.0f
            var totalRotation = 0.0f
            var validPoints = 0

            // Sample points for motion estimation
            val stepSize = 32
            for (y in stepSize until height - stepSize step stepSize) {
                for (x in stepSize until width - stepSize step stepSize) {
                    val currentIndex = y * width + x
                    val currentPixel = currentFrame[currentIndex].toInt() and 0xFF

                    // Find best match in previous frame within search window
                    val searchWindow = 16
                    var bestMatch = Float.MAX_VALUE
                    var bestDx = 0.0f
                    var bestDy = 0.0f

                    for (dy in -searchWindow..searchWindow step 4) {
                        for (dx in -searchWindow..searchWindow step 4) {
                            val newY = y + dy
                            val newX = x + dx

                            if (newY >= 0 && newY < height && newX >= 0 && newX < width) {
                                val prevIndex = newY * width + newX
                                val prevPixel = prevFrame[prevIndex].toInt() and 0xFF
                                val diff = kotlin.math.abs(currentPixel - prevPixel).toFloat()

                                if (diff < bestMatch) {
                                    bestMatch = diff
                                    bestDx = dx.toFloat()
                                    bestDy = dy.toFloat()
                                }
                            }
                        }
                    }

                    if (bestMatch < 50) { // Valid correlation threshold
                        totalDx += bestDx
                        totalDy += bestDy
                        validPoints++
                    }
                }
            }

            return if (validPoints > 0) {
                floatArrayOf(
                    totalDx / validPoints,
                    totalDy / validPoints,
                    totalRotation / validPoints
                )
            } else {
                floatArrayOf(0.0f, 0.0f, 0.0f)
            }
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.i(TAG, "Camera opened successfully")
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.w(TAG, "Camera disconnected")
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error: $error")
            camera.close()
            cameraDevice = null
        }
    }

    // STEP 2.3: Image Conversion (MOST CRITICAL STEP) - OnImageAvailable callback
    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        if (isProcessing) {
            reader.acquireLatestImage()?.close()
            return@OnImageAvailableListener
        }

        val image = reader.acquireLatestImage()
        if (image != null) {
            backgroundHandler?.post {
                processImage(image)
                image.close()
            }
        }
    }

    /**
     * CRITICAL: Image Conversion WITH ORIENTATION FIX
     * Convert YUV_420_888 to RGBA format and correct rotation
     */
    private fun processImage(image: Image) {
        isProcessing = true
        val startTime = System.currentTimeMillis()

        try {
            // Initialize motion detector on first frame
            if (motionDetector == null) {
                motionDetector = MotionDetector()
                Log.i(TAG, "Image stabilization initialized")
            }

            val width = image.width
            val height = image.height

            Log.d(TAG, "Processing image: ${width}x${height}, format: ${image.format}")

            // Extract image data planes and convert YUV to RGBA
            val planes = image.planes
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val rgbaBytes = ByteArray(width * height * 4)

            convertYUVtoRGBA(
                yBuffer, uBuffer, vBuffer,
                rgbaBytes,
                width, height,
                yPlane.rowStride, uPlane.rowStride, vPlane.rowStride,
                uPlane.pixelStride, vPlane.pixelStride
            )

            // NEW: Apply image stabilization BEFORE rotation
            val stabilizedBytes = if (isStabilizationEnabled) {
                applyImageStabilization(rgbaBytes, width, height, startTime)
            } else {
                rgbaBytes
            }

            // Apply rotation correction based on device orientation
            val rotationAngle = getRotationAngle()
            val (rotatedBytes, finalWidth, finalHeight) = if (rotationAngle != 0) {
                Log.d(TAG, "Applying rotation: $rotationAngle degrees")
                rotateRGBAImage(stabilizedBytes, width, height, rotationAngle)
            } else {
                Triple(stabilizedBytes, width, height)
            }

            // Calculate FPS
            frameCount++
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastFpsTime >= 1000) {
                currentFps = frameCount * 1000f / (currentTime - lastFpsTime)
                frameCount = 0
                lastFpsTime = currentTime
                Log.d(TAG, "Camera FPS: $currentFps ${if (isStabilizationEnabled) "(Stabilized)" else ""}")
            }

            // Result: Stabilized and properly oriented ByteArray
            onFrameProcessed(rotatedBytes, finalWidth, finalHeight, currentTime)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}", e)
        } finally {
            isProcessing = false
        }
    }

    /**
     * CRITICAL: YUV to RGB conversion formula as specified
     * For each pixel at position (x, y):
     * R = Y + 1.370705 × (V - 128)
     * G = Y - 0.337633 × (U - 128) - 0.698001 × (V - 128)
     * B = Y + 1.732446 × (U - 128)
     * Clamp values to 0-255 range
     * Store as [R, G, B, 255] in output array
     */
    private fun convertYUVtoRGBA(
        yBuffer: ByteBuffer, uBuffer: ByteBuffer, vBuffer: ByteBuffer,
        rgbaArray: ByteArray,
        width: Int, height: Int,
        yRowStride: Int, uvRowStride: Int, vRowStride: Int,
        uvPixelStride: Int, vPixelStride: Int
    ) {
        var rgbaIndex = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                // Get Y value
                val yIndex = y * yRowStride + x
                val yValue = (yBuffer.get(yIndex).toInt() and 0xFF)

                // Get U and V values (with proper subsampling)
                val uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride
                val uValue = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                val vValue = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                // Apply YUV to RGB conversion formula
                val r = (yValue + 1.370705 * vValue).toInt()
                val g = (yValue - 0.337633 * uValue - 0.698001 * vValue).toInt()
                val b = (yValue + 1.732446 * uValue).toInt()

                // Clamp values to 0-255 range and store as [R, G, B, 255]
                rgbaArray[rgbaIndex++] = r.coerceIn(0, 255).toByte()
                rgbaArray[rgbaIndex++] = g.coerceIn(0, 255).toByte()
                rgbaArray[rgbaIndex++] = b.coerceIn(0, 255).toByte()
                rgbaArray[rgbaIndex++] = 255.toByte() // Alpha channel
            }
        }
    }

    fun startCamera() {
        startBackgroundThread()
        openCamera()
    }

    fun stopCamera() {
        closeCaptureSession()
        closeCameraDevice()
        stopBackgroundThread()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun openCamera() {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0] // Use back camera
            manager.openCamera(cameraId, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera", e)
        }
    }

    private fun createCameraPreviewSession() {
        try {
            // STEP 2.2: Configure ImageReader with specific format
            imageReader = ImageReader.newInstance(
                previewSize.width,
                previewSize.height,
                ImageFormat.YUV_420_888,  // CRITICAL: Camera native format as specified
                2
            )
            imageReader!!.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)

            val surface = imageReader!!.surface
            val previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)

            // STEP 2.2: Frame rate configuration - 30 FPS
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                android.util.Range(CAMERA_FPS, CAMERA_FPS))

            cameraDevice!!.createCaptureSession(
                Arrays.asList(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return

                        captureSession = session
                        try {
                            val previewRequest = previewRequestBuilder.build()
                            session.setRepeatingRequest(previewRequest, null, backgroundHandler)
                            Log.i(TAG, "Camera preview started: ${previewSize.width}x${previewSize.height}")
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Failed to start camera preview", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Camera session configuration failed")
                    }
                },
                null
            )

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to create camera preview session", e)
        }
    }

    private fun closeCaptureSession() {
        captureSession?.close()
        captureSession = null
    }

    private fun closeCameraDevice() {
        cameraDevice?.close()
        cameraDevice = null
    }

    fun getCurrentFps(): Float = currentFps

    // NEW: Orientation handling methods
    private fun getRotationAngle(): Int {
        // Get the current rotation of the device
        val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        deviceRotation = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        // Get the sensor orientation of the camera
        val cameraId = (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager).cameraIdList[0]
        val characteristics = (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager).getCameraCharacteristics(cameraId)
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        // Calculate the rotation angle based on device rotation and sensor orientation
        // ADD 180 DEGREES: Additional correction for proper orientation
        val baseRotation = (sensorOrientation + deviceRotation + 360) % 360
        val correctedRotation = (baseRotation + 180) % 360

        Log.d(TAG, "Device rotation: $deviceRotation, Sensor: $sensorOrientation, Base: $baseRotation, Final: $correctedRotation")

        return correctedRotation
    }

    private fun rotateRGBAImage(rgbaBytes: ByteArray, width: Int, height: Int, angle: Int): Triple<ByteArray, Int, Int> {
        // Rotation logic for 90, 180, 270 degrees
        // NOTE: This is a simplified rotation logic, may not be optimal for all cases
        val rotatedWidth = if (angle == 90 || angle == 270) height else width
        val rotatedHeight = if (angle == 90 || angle == 270) width else height
        val rotatedBytes = ByteArray(rotatedWidth * rotatedHeight * 4)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val srcIndex = (y * width + x) * 4
                val dstIndex = when (angle) {
                    90 -> ((width - 1 - x) * rotatedWidth + y) * 4
                    180 -> ((height - 1 - y) * rotatedWidth + (width - 1 - x)) * 4
                    270 -> (x * rotatedWidth + (height - 1 - y)) * 4
                    else -> srcIndex
                }

                rotatedBytes[dstIndex] = rgbaBytes[srcIndex]
                rotatedBytes[dstIndex + 1] = rgbaBytes[srcIndex + 1]
                rotatedBytes[dstIndex + 2] = rgbaBytes[srcIndex + 2]
                rotatedBytes[dstIndex + 3] = rgbaBytes[srcIndex + 3]
            }
        }

        return Triple(rotatedBytes, rotatedWidth, rotatedHeight)
    }

    /**
     * NEW: Image Stabilization Pipeline
     * Applies motion detection and frame smoothing for improved quality
     */
    private fun applyImageStabilization(rgbaBytes: ByteArray, width: Int, height: Int, timestamp: Long): ByteArray {
        try {
            // Detect motion in current frame
            val currentTransform = motionDetector?.detectMotion(rgbaBytes, width, height) ?: FloatArray(6)

            // Apply smoothing to reduce jitter
            val smoothedTransform = applySmoothingFilter(currentTransform)

            // Check if motion is significant enough to warrant stabilization
            val motionMagnitude = kotlin.math.sqrt(
                smoothedTransform[0] * smoothedTransform[0] +
                smoothedTransform[1] * smoothedTransform[1]
            )

            return if (motionMagnitude > MOTION_THRESHOLD) {
                Log.d(TAG, "Stabilizing frame - Motion: ${"%.2f".format(motionMagnitude)}")

                // Add frame to buffer
                addFrameToBuffer(rgbaBytes, width, height, timestamp, smoothedTransform)

                // Apply stabilization transform
                applyStabilizationTransform(rgbaBytes, width, height, smoothedTransform)
            } else {
                // Motion below threshold - use frame as-is but still buffer it
                addFrameToBuffer(rgbaBytes, width, height, timestamp, smoothedTransform)
                rgbaBytes
            }

        } catch (e: Exception) {
            Log.w(TAG, "Stabilization failed, using original frame: ${e.message}")
            return rgbaBytes
        }
    }

    /**
     * Smoothing filter to reduce camera shake and jitter
     */
    private fun applySmoothingFilter(currentTransform: FloatArray): FloatArray {
        val smoothedTransform = FloatArray(6)

        for (i in currentTransform.indices) {
            smoothedTransform[i] = lastStableTransform[i] * (1 - SMOOTHING_FACTOR) +
                                  currentTransform[i] * SMOOTHING_FACTOR
        }

        // Update last stable transform
        lastStableTransform = smoothedTransform.copyOf()

        return smoothedTransform
    }

    /**
     * Add frame to circular buffer for temporal stabilization
     */
    private fun addFrameToBuffer(imageData: ByteArray, width: Int, height: Int, timestamp: Long, transform: FloatArray) {
        val frame = StabilizedFrame(imageData.copyOf(), width, height, timestamp, transform.copyOf())

        frameBuffer.add(frame)

        // Maintain buffer size
        if (frameBuffer.size > STABILIZATION_BUFFER_SIZE) {
            frameBuffer.removeAt(0)
        }
    }

    /**
     * Apply stabilization transform to correct camera shake
     */
    private fun applyStabilizationTransform(rgbaBytes: ByteArray, width: Int, height: Int, transform: FloatArray): ByteArray {
        val stabilizedBytes = ByteArray(width * height * 4)

        val tx = -transform[0] // Inverse translation
        val ty = -transform[1]
        val rotation = -transform[2] // Inverse rotation

        for (y in 0 until height) {
            for (x in 0 until width) {
                // Apply inverse transform to stabilize
                val newX = (x + tx).toInt()
                val newY = (y + ty).toInt()

                val srcIndex = (y * width + x) * 4

                if (newX >= 0 && newX < width && newY >= 0 && newY < height) {
                    val dstIndex = (newY * width + newX) * 4

                    // Copy RGBA values
                    stabilizedBytes[dstIndex] = rgbaBytes[srcIndex]
                    stabilizedBytes[dstIndex + 1] = rgbaBytes[srcIndex + 1]
                    stabilizedBytes[dstIndex + 2] = rgbaBytes[srcIndex + 2]
                    stabilizedBytes[dstIndex + 3] = rgbaBytes[srcIndex + 3]
                } else {
                    // Fill with black for out-of-bounds pixels
                    stabilizedBytes[srcIndex] = 0
                    stabilizedBytes[srcIndex + 1] = 0
                    stabilizedBytes[srcIndex + 2] = 0
                    stabilizedBytes[srcIndex + 3] = 255.toByte()
                }
            }
        }

        return stabilizedBytes
    }

    // NEW: Stabilization control methods
    fun enableStabilization(enabled: Boolean) {
        isStabilizationEnabled = enabled
        if (enabled) {
            Log.i(TAG, "Image stabilization enabled")
        } else {
            Log.i(TAG, "Image stabilization disabled")
            frameBuffer.clear()
            motionDetector = null
        }
    }

    fun isStabilizationEnabled(): Boolean = isStabilizationEnabled

    fun getStabilizationStatus(): String {
        return if (isStabilizationEnabled) {
            val bufferSize = frameBuffer.size
            val motionMagnitude = if (lastStableTransform[0] != 0f || lastStableTransform[1] != 0f) {
                kotlin.math.sqrt(lastStableTransform[0] * lastStableTransform[0] + lastStableTransform[1] * lastStableTransform[1])
            } else 0.0

            "✅ Active | Buffer: $bufferSize/$STABILIZATION_BUFFER_SIZE | Motion: ${"%.2f".format(motionMagnitude)}"
        } else {
            "❌ Disabled"
        }
    }
}
