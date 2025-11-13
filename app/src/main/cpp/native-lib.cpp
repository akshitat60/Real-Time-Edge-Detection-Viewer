#include <jni.h>
#include <string>
#include <android/log.h>
#include <opencv2/opencv.hpp>
#include <chrono>

#define LOG_TAG "NativeLib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global variable to track processing time
static long lastProcessingTime = 0;

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_edgedetectionviewer_NativeProcessor_getOpenCVVersion(
        JNIEnv* env,
        jobject /* this */) {
    std::string version = CV_VERSION;
    LOGI("OpenCV version: %s", version.c_str());
    return env->NewStringUTF(version.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_edgedetectionviewer_NativeProcessor_isOpenCVAvailable(
        JNIEnv* env,
        jobject /* this */) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_edgedetectionviewer_NativeProcessor_getProcessingTime(
        JNIEnv* env,
        jobject /* this */) {
    return lastProcessingTime;
}

// CRITICAL: Process frame with ByteArray input/output (EXACT specification implementation)
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_edgedetectionviewer_NativeProcessor_processFrameBytes(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray imageData,
        jint width,
        jint height,
        jboolean applyEdgeDetection) {

    auto startTime = std::chrono::high_resolution_clock::now();

    // STEP 1: Validate input parameters
    jsize inputLength = env->GetArrayLength(imageData);
    jsize expectedLength = width * height * 4; // RGBA format

    if (inputLength != expectedLength) {
        LOGE("Input array length mismatch: expected %d, got %d", expectedLength, inputLength);
        return nullptr;
    }

    // STEP 2: Extract Data from JNI Types (as per specification)
    jbyte* inputBytes = env->GetByteArrayElements(imageData, nullptr);
    if (inputBytes == nullptr) {
        LOGE("Failed to get byte array elements");
        return nullptr;
    }

    // STEP 3: Create OpenCV Mat from raw data (EXACT format specification)
    cv::Mat inputMat(height, width, CV_8UC4, reinterpret_cast<unsigned char*>(inputBytes));

    // STEP 4: Verify Mat properties (critical validation)
    if (inputMat.rows != height || inputMat.cols != width || inputMat.channels() != 4) {
        LOGE("Mat validation failed: %dx%d, %d channels", inputMat.cols, inputMat.rows, inputMat.channels());
        env->ReleaseByteArrayElements(imageData, inputBytes, JNI_ABORT);
        return nullptr;
    }

    cv::Mat outputMat;

    // STEP 5: Apply Edge Detection (The Actual Processing as specified)
    if (applyEdgeDetection) {
        LOGI("Applying edge detection to %dx%d image", width, height);

        // STEP 5.1: Convert RGBA to Grayscale
        cv::Mat grayMat;
        cv::cvtColor(inputMat, grayMat, cv::COLOR_RGBA2GRAY);

        // STEP 5.2: Apply Gaussian Blur (reduce noise)
        cv::Mat blurredMat;
        cv::GaussianBlur(grayMat, blurredMat, cv::Size(5, 5), 1.5);

        // STEP 5.3: Apply Canny Edge Detection
        cv::Mat edgesMat;
        cv::Canny(blurredMat, edgesMat, 50, 150);

        // STEP 5.4: Convert back to RGBA for display
        cv::cvtColor(edgesMat, outputMat, cv::COLOR_GRAY2RGBA);

        LOGI("Edge detection completed: %dx%d, %d channels",
             outputMat.cols, outputMat.rows, outputMat.channels());
    } else {
        // Raw camera mode: simply copy inputMat to outputMat
        LOGI("Passthrough mode: %dx%d image", width, height);
        inputMat.copyTo(outputMat);
    }

    // STEP 6: Return Data to Java (exact specification)
    jsize outputSize = height * width * 4; // RGBA format
    jbyteArray outputArray = env->NewByteArray(outputSize);
    if (outputArray == nullptr) {
        LOGE("Failed to create output byte array");
        env->ReleaseByteArrayElements(imageData, inputBytes, JNI_ABORT);
        return nullptr;
    }

    // Copy processed data to Java array
    env->SetByteArrayRegion(outputArray, 0, outputSize,
                           reinterpret_cast<jbyte*>(outputMat.data));

    // Release input array (JNI_ABORT = don't copy back, we didn't modify input)
    env->ReleaseByteArrayElements(imageData, inputBytes, JNI_ABORT);

    // Calculate and store processing time
    auto endTime = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(endTime - startTime);
    lastProcessingTime = duration.count();

    LOGI("Frame processed in %ld ms", lastProcessingTime);

    return outputArray;
}

// LEGACY METHODS: Bitmap-based processing (kept for compatibility)
extern "C" JNIEXPORT void JNICALL
Java_com_example_edgedetectionviewer_NativeProcessor_processFrame(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmapIn,
        jobject bitmapOut) {

    // This is the legacy method - implementation would go here
    // For now, just log that it was called
    LOGI("Legacy processFrame method called");
}