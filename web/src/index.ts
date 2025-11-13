/**
 * PHASE 7: Web Viewer (TypeScript Component) - EXACT specification implementation
 * This creates a web interface to display edge detection results
 */

interface EdgeDetectionStats {
    resolution: string;
    fps: string;
    mode: string;
    frameCount?: number;
}

class EdgeDetectionViewer {
    private canvas!: HTMLCanvasElement;
    private context!: CanvasRenderingContext2D;
    private statsDisplay!: HTMLDivElement;
    private fileInput!: HTMLInputElement;
    private stats!: EdgeDetectionStats;

    constructor() {
        this.initializeElements();
        this.setupEventListeners();
        this.loadDefaultSample();
        this.updateStatsDisplay();
    }

    // STEP 7.3: Get canvas element and 2D context
    private initializeElements(): void {
        this.canvas = document.getElementById('imageCanvas') as HTMLCanvasElement;
        if (!this.canvas) {
            throw new Error('Canvas element with ID "imageCanvas" not found');
        }

        this.context = this.canvas.getContext('2d')!;
        if (!this.context) {
            throw new Error('Could not get 2D rendering context');
        }

        this.statsDisplay = document.getElementById('statsDisplay') as HTMLDivElement;
        this.fileInput = document.getElementById('fileInput') as HTMLInputElement;

        // Initialize default stats
        this.stats = {
            resolution: '640x480',
            fps: '30 FPS',
            mode: 'Edge Detection Sample'
        };

        console.log('EdgeDetectionViewer initialized');
    }

    // STEP 7.3: Handle file upload
    private setupEventListeners(): void {
        this.fileInput.addEventListener('change', (event) => {
            const file = (event.target as HTMLInputElement).files?.[0];
            if (file && file.type.startsWith('image/')) {
                this.loadImageFromFile(file);
            }
        });
    }

    // STEP 7.3: Load default sample image
    private loadDefaultSample(): void {
        // STEP 7.4: Include base64-encoded sample edge detection result
        const sampleBase64 = this.getDefaultSampleImage();
        this.loadImageFromBase64(sampleBase64, 'Default Edge Detection Sample');
    }

    // STEP 7.4: Generate sample image (placeholder for now, should come from Android app)
    private getDefaultSampleImage(): string {
        // This is a placeholder - in production, this would be a base64-encoded
        // edge detection result exported from the Android app
        return 'data:image/svg+xml;base64,' + btoa(`
            <svg width="640" height="480" xmlns="http://www.w3.org/2000/svg">
                <rect width="640" height="480" fill="black"/>
                <g stroke="white" stroke-width="2" fill="none">
                    <rect x="50" y="50" width="540" height="380"/>
                    <circle cx="320" cy="240" r="100"/>
                    <line x1="150" y1="150" x2="490" y2="330"/>
                    <polygon points="320,100 370,200 270,200"/>
                </g>
                <text x="320" y="450" fill="white" text-anchor="middle" font-family="Arial" font-size="16">
                    Sample Edge Detection Output (640x480)
                </text>
            </svg>
        `);
    }

    // STEP 7.3: Load image from file upload
    private loadImageFromFile(file: File): void {
        const reader = new FileReader();
        reader.onload = (e) => {
            const dataUrl = e.target?.result as string;
            this.loadImageFromBase64(dataUrl, `Uploaded: ${file.name}`);
        };
        reader.readAsDataURL(file);
    }

    // STEP 7.3: Create Image from data URL and draw to canvas
    private loadImageFromBase64(dataUrl: string, description: string): void {
        const image = new Image();
        image.onload = () => {
            // Update canvas size to match image
            this.canvas.width = image.width;
            this.canvas.height = image.height;

            // Draw image to canvas
            this.context.clearRect(0, 0, this.canvas.width, this.canvas.height);
            this.context.drawImage(image, 0, 0);

            // Update stats with actual image dimensions
            this.stats.resolution = `${image.width}x${image.height}`;
            this.stats.mode = description.includes('Edge') ? 'Edge Detection Sample' : 'Uploaded Image';
            this.updateStatsDisplay();

            console.log(`Loaded image: ${description} (${image.width}x${image.height})`);
        };
        image.src = dataUrl;
    }

    // STEP 7.3: Display stats
    private updateStatsDisplay(): void {
        if (this.statsDisplay) {
            this.statsDisplay.innerHTML = `
                <div class="stat-item">
                    <span class="stat-label">📊 Resolution:</span>
                    <span class="stat-value">${this.stats.resolution}</span>
                </div>
                <div class="stat-item">
                    <span class="stat-label">🎯 FPS:</span>
                    <span class="stat-value">${this.stats.fps}</span>
                </div>
                <div class="stat-item">
                    <span class="stat-label">🔧 Mode:</span>
                    <span class="stat-value">${this.stats.mode}</span>
                </div>
                ${this.stats.frameCount ? `
                <div class="stat-item">
                    <span class="stat-label">📈 Frames:</span>
                    <span class="stat-value">${this.stats.frameCount}</span>
                </div>
                ` : ''}
            `;
        }
    }

    // Public method for updating frame from Android app (STEP 7.4)
    public updateFrameFromBase64(base64Data: string): void {
        this.loadImageFromBase64(base64Data, 'Live Edge Detection');
        this.stats.frameCount = (this.stats.frameCount || 0) + 1;
        this.updateStatsDisplay();
    }

    // Public method for updating stats from Android app
    public updateStats(newStats: Partial<EdgeDetectionStats>): void {
        this.stats = { ...this.stats, ...newStats };
        this.updateStatsDisplay();
    }
}

// STEP 7.3: Initialize when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    const viewer = new EdgeDetectionViewer();

    // Make globally accessible for Android WebView integration
    (window as any).edgeDetectionViewer = viewer;

    console.log('EdgeDetectionViewer ready for use');
});

