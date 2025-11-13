# How to View the Web Viewer

## Quick Start

1. **Open the web viewer:**
   - Navigate to: `A:\EdgeDetectionViewer\web\dist\`
   - Double-click `index.html` to open in your browser
   - Or right-click → Open with → Chrome/Firefox/Edge

2. **The page will display:**
   - A simulated edge detection output (placeholder)
   - Real-time statistics matching your Android app
   - Modern, responsive UI with live updates

## To Add Real Frame from Android App

### Option 1: Manual Export (For Testing)
1. Take a screenshot of the edge-detected output from your Android app
2. Save it as `sample_frame.jpg` in the `web/dist/` folder
3. Refresh the web page - it will display your actual frame!

### Option 2: WebView Integration (Production)
Add a WebView to your Android app and load this HTML page:
```kotlin
webView.loadUrl("file:///android_asset/web/index.html")
webView.evaluateJavascript(
    "window.edgeDetectionViewer.updateFrameFromBase64('${base64Frame}')", null
)
```

## Serving Locally (Optional)

If you want to serve via HTTP:

```bash
cd A:\EdgeDetectionViewer\web
npm run serve
```

Then open: http://localhost:8080

## Features Demonstrated

✅ TypeScript project with proper structure  
✅ Compiled JavaScript (ES2020)  
✅ Clean, modular code  
✅ Real-time stats display  
✅ Responsive design  
✅ Frame rendering with Canvas API  
✅ API for Android integration  

## API Usage Examples

### JavaScript Console (Browser DevTools)

```javascript
// Update stats
window.edgeDetectionViewer.updateStatsFromApp({
    fps: 32.5,
    renderFps: 61.2,
    processingTime: 2,
    frameCount: 1200
});

// Update frame from base64
window.edgeDetectionViewer.updateFrameFromBase64('YOUR_BASE64_STRING_HERE');
```

