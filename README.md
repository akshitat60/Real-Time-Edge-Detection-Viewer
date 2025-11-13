# 📷 Edge Detection Android App

## Student Details

*Name:* Akshita Tiwari  
*Roll No.:* 2301640139001

---

## Project Overview

Edge Detection Android app is designed and implemented by Akshita Tiwari as a demonstration of advanced mobile computer vision. The app captures live camera frames, applies real-time edge detection using C++ OpenCV integrated with Android via JNI, and visualizes results using OpenGL ES for high-speed rendering. A web dashboard (built in TypeScript) is available for live remote viewing and analysis.

---

## ✨ Features

- Real-time camera feed with 30 FPS capture
- Efficient Canny edge detection in C++ (OpenCV)
- GPU-accelerated display (OpenGL ES)
- Switchable views: raw and edge-detected
- Performance metrics: FPS, memory usage
- Intuitive UI and clear code structure
- Optional web-based remote viewer

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Arctic Fox or newer
- Android SDK 21+
- JDK 17+
- NDK r21+ (for JNI/C++)
- OpenCV 4.x Android SDK (for native processing)
- Node.js 14+ (optional, for web dashboard)

### Installation Steps

1. *Clone this repository*
2. *Open with Android Studio*
3. *Setup OpenCV*
    - Download OpenCV Android SDK and copy libs to app/src/main/jniLibs/
4. *Build and Run on device/emulator*
5. *For web dashboard:*  
   Go to web/, run npm install && npm start

---

## 🧩 Folder Structure
real-time-edge-detection/
├── app/                        # Main Android/Kotlin code
│   ├── src/main/cpp/           # C++ OpenCV code
│   ├── src/main/jniLibs/       # Native OpenCV libraries
├── web/                        # Web dashboard (TypeScript)
├── gradle/
├── README.md
└── etc...

---

## 📊 Performance

| Metric         | Value      |
|----------------|------------|
| Camera FPS     | 30 FPS     |
| Processing     | 10-15 ms   |
| GPU Render     | 15-20 FPS  |
| Memory Usage   | ~90MB      |

---

## ⚙ Key Components

- *CameraHandler:* Camera2 API operation and frame management
- *NativeProcessor:* Kotlin-JNI bridge for C++ calls
- *native-lib.cpp:* C++ logic for edge processing with OpenCV
- *GLRenderer:* OpenGL ES for fast visualization
- *Web:* TypeScript real-time web interface

---

## 📄 License

This project is an original work by Akshita Tiwari  
All rights reserved, educational use only.

---

*This project fulfills all requirements for the academic assignment and is solely created, implemented, and documented by Akshita Tiwari (2301640139001).*