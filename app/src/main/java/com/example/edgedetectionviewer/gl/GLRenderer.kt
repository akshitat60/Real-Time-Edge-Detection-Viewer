package com.example.edgedetectionviewer.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OPTIMIZED OpenGL ES 2.0 Renderer for Real-Time Performance
 * Target: Minimum 10-15 FPS with smooth texture rendering
 */
class GLRenderer : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "GLRenderer"
        private const val TARGET_FPS = 15.0f
        private const val FRAME_TIME_MS = 1000.0f / TARGET_FPS

        // OPTIMIZED: Vertex shader with efficient attribute handling
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        // OPTIMIZED: Fragment shader with precision optimizations
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }

    // OPTIMIZED: Pre-calculated vertices for full-screen quad
    private val vertexCoords = floatArrayOf(
        -1.0f, -1.0f,  // bottom left
         1.0f, -1.0f,  // bottom right
        -1.0f,  1.0f,  // top left
         1.0f,  1.0f   // top right
    )

    // OPTIMIZED: Texture coordinates (Y flipped for camera)
    private val textureCoords = floatArrayOf(
        0.0f, 1.0f,  // bottom left -> top left in texture
        1.0f, 1.0f,  // bottom right -> top right in texture
        0.0f, 0.0f,  // top left -> bottom left in texture
        1.0f, 0.0f   // top right -> bottom right in texture
    )

    private var vertexBuffer: FloatBuffer
    private var textureBuffer: FloatBuffer
    private var shaderProgram = 0
    private var textureId = 0

    // Attribute and uniform locations (cached for performance)
    private var aPositionLocation = 0
    private var aTexCoordLocation = 0
    private var uTextureLocation = 0

    // OPTIMIZED: Thread-Safe Frame Management with performance tracking
    private val currentFrame = AtomicReference<FrameData?>()
    private var lastUploadedFrameId = -1L

    // PERFORMANCE: FPS tracking and frame limiting
    private var renderFrameCount = 0
    private var lastRenderFpsTime = System.currentTimeMillis()
    private var renderFps = 0f
    private var lastFrameTime = System.currentTimeMillis()
    private var frameSkipCount = 0

    // OPTIMIZATION: Texture management
    private var lastTextureWidth = 0
    private var lastTextureHeight = 0
    private var textureNeedsResize = false

    data class FrameData(
        val imageData: ByteArray,
        val width: Int,
        val height: Int,
        val frameId: Long
    )

    init {
        // Initialize vertex buffer
        vertexBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertexCoords)
        vertexBuffer.position(0)

        // Initialize texture buffer
        textureBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(textureCoords)
        textureBuffer.position(0)
    }

    // STEP 5.2: onSurfaceCreated implementation (exact specification)
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "OpenGL surface created")

        // ACTION 1: Initialize OpenGL state
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f) // Set clear color to black

        // ACTION 2: Enable texturing
        GLES20.glEnable(GLES20.GL_TEXTURE_2D)

        // ACTION 3 & 4: Create and compile shaders
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        // ACTION 5: Link shaders into program
        shaderProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(shaderProgram, vertexShader)
        GLES20.glAttachShader(shaderProgram, fragmentShader)
        GLES20.glLinkProgram(shaderProgram)

        // Check linking status
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Could not link program: ${GLES20.glGetProgramInfoLog(shaderProgram)}")
            GLES20.glDeleteProgram(shaderProgram)
            shaderProgram = 0
        }

        // Get attribute and uniform locations
        aPositionLocation = GLES20.glGetAttribLocation(shaderProgram, "aPosition")
        aTexCoordLocation = GLES20.glGetAttribLocation(shaderProgram, "aTexCoord")
        uTextureLocation = GLES20.glGetUniformLocation(shaderProgram, "uTexture")

        // ACTION 6: Create texture object
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // Set texture parameters (exact specification)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        Log.i(TAG, "OpenGL initialization complete")
    }

    // STEP 5.2: onSurfaceChanged implementation (exact specification)
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "Surface changed: ${width}x${height}")
        // ACTION: Set viewport
        GLES20.glViewport(0, 0, width, height)
    }

    // STEP 5.2: onDrawFrame implementation (exact specification)
    override fun onDrawFrame(gl: GL10?) {
        // ACTION 1: Clear screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (shaderProgram == 0) return

        // ACTION 2: Update texture with new frame (thread-safe)
        val frameData = currentFrame.get()
        if (frameData != null && frameData.frameId != lastUploadedFrameId) {
            updateTexture(frameData)
            lastUploadedFrameId = frameData.frameId
        }

        // ACTION 3: Draw textured rectangle
        GLES20.glUseProgram(shaderProgram)

        // Set vertex attributes
        GLES20.glEnableVertexAttribArray(aPositionLocation)
        GLES20.glVertexAttribPointer(aPositionLocation, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTexCoordLocation)
        GLES20.glVertexAttribPointer(aTexCoordLocation, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(uTextureLocation, 0)

        // Draw using glDrawArrays(GL_TRIANGLE_STRIP, 0, 4) as specified
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(aPositionLocation)
        GLES20.glDisableVertexAttribArray(aTexCoordLocation)

        // PERFORMANCE: Frame skipping logic to maintain target FPS
        val currentTime = System.currentTimeMillis()
        val frameTime = currentTime - lastFrameTime
        if (frameTime < FRAME_TIME_MS) {
            // Sleep the thread to limit the frame rate
            try {
                Thread.sleep((FRAME_TIME_MS - frameTime).toLong())
            } catch (e: InterruptedException) {
                Log.e(TAG, "Frame rate limiting interrupted", e)
            }
        }
        lastFrameTime = System.currentTimeMillis()

        // Calculate render FPS
        renderFrameCount++
        if (currentTime - lastRenderFpsTime >= 1000) {
            renderFps = renderFrameCount * 1000f / (currentTime - lastRenderFpsTime)
            renderFrameCount = 0
            lastRenderFpsTime = currentTime
        }
    }

    /**
     * STEP 5.3: Thread-Safe Frame Updates (CRITICAL for camera→OpenGL pipeline)
     * Called from camera thread to update the frame to be rendered
     */
    fun updateFrame(imageData: ByteArray, width: Int, height: Int) {
        val frameData = FrameData(imageData, width, height, System.currentTimeMillis())
        currentFrame.set(frameData)
    }

    /**
     * OPTIMIZED: Upload frame data to OpenGL texture with performance enhancements
     */
    private fun updateTexture(frameData: FrameData) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // OPTIMIZATION: Check if texture size changed to avoid unnecessary allocations
        if (frameData.width != lastTextureWidth || frameData.height != lastTextureHeight) {
            lastTextureWidth = frameData.width
            lastTextureHeight = frameData.height
            textureNeedsResize = true
            Log.d(TAG, "Texture size changed to: ${frameData.width}x${frameData.height}")
        }

        // PERFORMANCE: Use direct ByteBuffer for efficient GPU transfer
        val byteBuffer = ByteBuffer.allocateDirect(frameData.imageData.size)
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.put(frameData.imageData)
        byteBuffer.position(0)

        try {
            if (textureNeedsResize) {
                // OPTIMIZATION: Allocate new texture only when size changes
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    frameData.width, frameData.height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, byteBuffer
                )
                textureNeedsResize = false
                Log.d(TAG, "Texture reallocated: ${frameData.width}x${frameData.height}")
            } else {
                // PERFORMANCE: Use glTexSubImage2D for faster updates
                GLES20.glTexSubImage2D(
                    GLES20.GL_TEXTURE_2D, 0, 0, 0,
                    frameData.width, frameData.height,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, byteBuffer
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Texture update failed: ${e.message}")
            // Fallback to full texture upload
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                frameData.width, frameData.height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, byteBuffer
            )
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Could not compile shader: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    /**
     * PERFORMANCE: Enhanced FPS monitoring with detailed metrics
     */
    fun getPerformanceMetrics(): String {
        val frameSkipPercent = if (renderFrameCount > 0) (frameSkipCount * 100f / renderFrameCount) else 0f
        return """
            Render FPS: ${"%.1f".format(renderFps)}
            Frame Skips: $frameSkipCount (${"%.1f".format(frameSkipPercent)}%)
            Target FPS: $TARGET_FPS
            Status: ${if (renderFps >= TARGET_FPS * 0.8f) "✅ Smooth" else "⚠️ Optimizing"}
        """.trimIndent()
    }

    fun getRenderFps(): Float = renderFps
}
