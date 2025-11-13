#include <opencv2/opencv.hpp>
#include <opencv2/imgproc.hpp>
#include <android/log.h>

#define LOG_TAG "OpenCVProcessing"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

void processFrameWithCanny(cv::Mat& inputMat, cv::Mat& outputMat) {
    try {
        // Convert RGBA to BGR (OpenCV standard format)
        cv::Mat bgrMat;
        cv::cvtColor(inputMat, bgrMat, cv::COLOR_RGBA2BGR);

        // Convert to grayscale
        cv::Mat grayMat;
        cv::cvtColor(bgrMat, grayMat, cv::COLOR_BGR2GRAY);

        // Apply Gaussian blur to reduce noise
        cv::Mat blurredMat;
        cv::GaussianBlur(grayMat, blurredMat, cv::Size(5, 5), 1.5);

        // Apply Canny edge detection
        cv::Mat edgesMat;
        double lowThreshold = 50.0;
        double highThreshold = 150.0;
        cv::Canny(blurredMat, edgesMat, lowThreshold, highThreshold);

        // Convert edges back to BGR
        cv::Mat edgesBgr;
        cv::cvtColor(edgesMat, edgesBgr, cv::COLOR_GRAY2BGR);

        // Convert BGR back to RGBA for output
        cv::cvtColor(edgesBgr, outputMat, cv::COLOR_BGR2RGBA);

        LOGI("Canny edge detection applied successfully");

    } catch (const cv::Exception& e) {
        LOGI("OpenCV exception: %s", e.what());
        // On error, just copy input to output
        inputMat.copyTo(outputMat);
    }
}