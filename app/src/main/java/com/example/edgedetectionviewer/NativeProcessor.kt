package com.example.edgedetectionviewer

import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer

/**
 * JNI Bridge for native OpenCV processing
 * This class provides the interface between Kotlin/Java and C++ OpenCV code
 */
class NativeProcessor {

    companion object {
        private const val TAG = "NativeProcessor"

        init {
            try {
                System.loadLibrary("native-lib")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}")
                throw e
            }
        }
    }

    /**
     * Process camera frame with OpenCV
     * @param imageData RGBA byte array from camera
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @param applyEdgeDetection true = apply Canny edge detection, false = passthrough
     * @return Processed RGBA byte array
     */
    external fun processFrame(
        imageData: ByteArray,
        width: Int,
        height: Int,
        applyEdgeDetection: Boolean
    ): ByteArray

    /**
     * Get processing time of last frame in milliseconds
     * @return Processing time in ms
     */
    external fun getProcessingTime(): Long

    /**
     * Check if OpenCV is properly loaded and available
     * @return true if OpenCV is ready to use
     */
    external fun isOpenCVAvailable(): Boolean

    /**
     * Get OpenCV version string for debugging
     * @return OpenCV version (e.g., "4.8.0")
     */
    external fun getOpenCVVersion(): String

    /**
     * NEW METHOD: Process frame using ByteArray (follows specification)
     * @param imageData RGBA byte array
     * @param width image width in pixels
     * @param height image height in pixels
     * @param applyEdgeDetection true = edge detection, false = passthrough
     * @return processed RGBA byte array
     */
    external fun processFrameBytes(
        imageData: ByteArray,
        width: Int,
        height: Int,
        applyEdgeDetection: Boolean
    ): ByteArray?

    /**
     * Process frame from Bitmap and return as Bitmap (convenience method)
     */
    fun processFrameToBitmap(inputBitmap: Bitmap, applyEdgeDetection: Boolean): Bitmap? {
        try {
            val width = inputBitmap.width
            val height = inputBitmap.height

            // Convert Bitmap to ByteArray (RGBA)
            val inputBytes = ByteArray(width * height * 4)
            val buffer = ByteBuffer.wrap(inputBytes)
            inputBitmap.copyPixelsToBuffer(buffer)

            // Process via native code
            val outputBytes = processFrameBytes(inputBytes, width, height, applyEdgeDetection)
                ?: return null

            // Convert ByteArray back to Bitmap
            val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val outputBuffer = ByteBuffer.wrap(outputBytes)
            outputBitmap.copyPixelsFromBuffer(outputBuffer)

            return outputBitmap

        } catch (e: Exception) {
            Log.e(TAG, "Error in processFrameToBitmap: ${e.message}", e)
            return null
        }
    }

    // OLD METHODS - Kept for compatibility but deprecated
    @Deprecated("Use processFrameBytes or processFrameToBitmap instead")
    external fun processFrame(bitmapIn: Bitmap, bitmapOut: Bitmap)

    @Deprecated("Use processFrameToBitmap instead")
    fun processFrameToNewBitmap(inputBitmap: Bitmap): Bitmap {
        val outputBitmap = Bitmap.createBitmap(
            inputBitmap.width,
            inputBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        processFrame(inputBitmap, outputBitmap)
        return outputBitmap
    }
}