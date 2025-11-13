# Edge Detection Web Viewer

A TypeScript-based web interface for viewing processed frames from the Edge Detection Android app.

## Features

- 📹 Display processed edge detection frames
- 📊 Real-time statistics (FPS, processing time, resolution)
- 🎨 Modern, responsive UI with gradient background
- 🔄 Live updates simulation
- 📱 Mobile-friendly design

## Setup Instructions

### Prerequisites

- Node.js (v16 or higher)
- npm or yarn

### Installation

1. Navigate to the web directory:
```bash
cd web
```

2. Install dependencies:
```bash
npm install
```

### Build

Compile TypeScript to JavaScript:
```bash
npm run build
```

This will compile `src/index.ts` to `dist/index.js`.

### Running

Open `dist/index.html` in your web browser, or serve it locally:

```bash
npm run serve
```

Then open http://localhost:8080 in your browser.

## Project Structure

```
web/
├── src/
│   └── index.ts          # Main TypeScript application
├── dist/
│   ├── index.html        # Main HTML page
│   └── index.js          # Compiled JavaScript (generated)
├── package.json          # NPM dependencies
├── tsconfig.json         # TypeScript configuration
└── README.md            # This file
```

## API

The viewer exposes a global API for integration with Android WebView:

### Update Frame from Base64
```javascript
window.edgeDetectionViewer.updateFrameFromBase64(base64String);
```

### Update Statistics
```javascript
window.edgeDetectionViewer.updateStatsFromApp({
    fps: 30.2,
    renderFps: 60.3,
    processingTime: 1,
    resolution: '1280x720',
    frameCount: 960,
    mode: 'Edge Detection'
});
```

## Integration with Android App

To integrate with the Android app:

1. Export processed frame as base64 from Android
2. Load this HTML page in a WebView
3. Call the JavaScript API to update frames/stats

Example Android code:
```kotlin
val bitmap: Bitmap = processedFrame
val base64 = bitmapToBase64(bitmap)
webView.evaluateJavascript(
    "window.edgeDetectionViewer.updateFrameFromBase64('$base64')",
    null
)
```

## Technologies Used

- **TypeScript** - Type-safe JavaScript
- **HTML5 Canvas** - Frame rendering
- **CSS3** - Modern styling with gradients and animations
- **ES2020** - Modern JavaScript features

## Statistics Displayed

- 📷 Camera FPS - Frame capture rate from Camera2 API
- 🎮 Render FPS - OpenGL ES rendering frame rate
- ⚡ Processing Time - OpenCV edge detection time (ms)
- 📐 Resolution - Frame dimensions
- 🎯 Mode - Current processing mode (Edge Detection / Raw)
- 📦 Frames Processed - Total frame count
- ⏱️ Last Update - Timestamp of latest update

## Author

Edge Detection Viewer Assessment Project

