import {
  Component,
  ElementRef,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  ViewChild,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import * as THREE from 'three';

interface TelemetryPoint {
  mesh: THREE.Mesh;
  ring: THREE.Mesh;
  ringScale: number;
  initialPos: THREE.Vector3;
  name: string;
  status: 'HEALTHY' | 'ALERT';
}

const CONTINENT_DATA: number[][][] = [
  // North America
  [
    [-168, 65], [-160, 71], [-140, 70], [-125, 70], [-115, 68], [-95, 70], [-85, 70],
    [-80, 62], [-65, 58], [-60, 52], [-65, 45], [-70, 42], [-75, 35], [-80, 25],
    [-82, 25], [-80, 30], [-90, 30], [-97, 26], [-97, 20], [-90, 16], [-83, 9],
    [-77, 8], [-80, 15], [-92, 16], [-105, 20], [-110, 24], [-115, 30], [-124, 38],
    [-125, 49], [-130, 55], [-140, 60], [-160, 58], [-168, 65]
  ],
  // South America
  [
    [-77, 8], [-72, 12], [-60, 10], [-50, 0], [-35, -5], [-35, -10], [-38, -13],
    [-42, -23], [-50, -30], [-58, -34], [-65, -42], [-68, -54], [-75, -50], [-73, -40],
    [-72, -30], [-76, -15], [-81, -5], [-77, 8]
  ],
  // Europe
  [
    [-9, 36], [-9, 43], [0, 43], [-1, 46], [-4, 48], [2, 51], [8, 54], [10, 57],
    [5, 62], [15, 65], [25, 71], [30, 70], [35, 65], [30, 60], [25, 55], [20, 45],
    [25, 40], [20, 38], [15, 38], [12, 44], [4, 43], [-3, 36], [-9, 36]
  ],
  // British Isles
  [
    [-5, 50], [-5, 58], [2, 53], [0, 50], [-5, 50]
  ],
  // Africa
  [
    [-6, 36], [10, 37], [25, 32], [32, 31], [34, 28], [43, 12], [51, 12], [42, 0],
    [40, -10], [35, -20], [32, -28], [28, -32], [18, -34], [18, -28], [12, -18],
    [10, -5], [10, 5], [2, 6], [-5, 5], [-15, 12], [-17, 15], [-17, 21], [-13, 28], [-6, 36]
  ],
  // Asia
  [
    [35, 65], [60, 70], [80, 75], [105, 78], [140, 75], [170, 66], [180, 65],
    [160, 55], [140, 50], [130, 42], [120, 38], [122, 30], [118, 22], [108, 20],
    [105, 10], [100, 5], [104, 1], [98, 8], [92, 16], [90, 22], [80, 16], [78, 8],
    [72, 18], [68, 24], [60, 25], [55, 25], [50, 15], [44, 12], [36, 28], [35, 36],
    [40, 40], [50, 42], [55, 50], [40, 55], [35, 65]
  ],
  // Japan
  [
    [130, 32], [135, 34], [140, 36], [142, 40], [145, 44], [141, 44], [138, 38], [130, 32]
  ],
  // Australia
  [
    [114, -22], [116, -34], [120, -35], [135, -34], [140, -38], [148, -38], [153, -28],
    [150, -22], [145, -15], [142, -11], [136, -12], [130, -14], [122, -17], [114, -22]
  ],
  // New Zealand
  [
    [166, -46], [174, -41], [178, -38], [173, -35], [168, -44], [166, -46]
  ],
  // Antarctica
  [
    [-180, -78], [180, -78], [180, -89], [-180, -89], [-180, -78]
  ]
];

@Component({
  selector: 'app-telemetry-canvas-3d',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './telemetry-canvas-3d.component.html',
  styleUrl: './telemetry-canvas-3d.component.css'
})
export class TelemetryCanvas3dComponent implements OnInit, OnDestroy, OnChanges {
  private readonly ngZone = inject(NgZone);

  @ViewChild('canvasContainer', { static: true })
  containerRef!: ElementRef<HTMLDivElement>;

  @ViewChild('webglCanvas', { static: true })
  canvasRef!: ElementRef<HTMLCanvasElement>;

  @Input() theme: 'dark' | 'light' = 'dark';
  @Input() uptime: number = 99.98;
  @Input() latency: number = 65;

  readonly isAutoRotating = signal<boolean>(true);
  readonly isPulseActive = signal<boolean>(false);

  // Three.js Core Objects
  private renderer: THREE.WebGLRenderer | null = null;
  private scene: THREE.Scene | null = null;
  private camera: THREE.PerspectiveCamera | null = null;
  private animationFrameId: number | null = null;
  private resizeObserver: ResizeObserver | null = null;

  // 3D Visual Objects
  private globeGroup: THREE.Group | null = null;
  private earthMesh: THREE.Mesh | null = null;
  private innerCoreMesh: THREE.Mesh | null = null;
  private atmosphereHalo: THREE.Mesh | null = null;
  private particlesMesh: THREE.Points | null = null;
  private shockwaveRing: THREE.Mesh | null = null;
  private telemetryNodes: TelemetryPoint[] = [];

  // Interaction & Inertia Model
  private rotationX = 0.2;
  private rotationY = -1.2; // Initially face Americas/Atlantic
  private velocityX = 0;
  private velocityY = 0;
  private isDragging = false;
  private previousMousePosition = { x: 0, y: 0 };
  private shockwaveScale = 0;
  private shockwaveOpacity = 0;

  ngOnInit(): void {
    this.initThreeScene();
    this.setupResizeObserver();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['theme'] && !changes['theme'].firstChange) {
      this.updateColorsForTheme();
    }
  }

  ngOnDestroy(): void {
    this.cleanup();
  }

  private createEarthTexture(isDark: boolean): THREE.CanvasTexture {
    const canvas = document.createElement('canvas');
    canvas.width = 2048;
    canvas.height = 1024;
    const ctx = canvas.getContext('2d');
    if (!ctx) return new THREE.CanvasTexture(canvas);

    const w = canvas.width;
    const h = canvas.height;

    // 1. Ocean Background
    ctx.fillStyle = isDark ? '#0b1322' : '#ebf3fb';
    ctx.fillRect(0, 0, w, h);

    // 2. Graticule (Parallels & Meridians every 30 deg)
    ctx.lineWidth = 1;
    ctx.strokeStyle = isDark ? 'rgba(56, 189, 248, 0.12)' : 'rgba(9, 105, 218, 0.1)';

    for (let lat = -60; lat <= 60; lat += 30) {
      const y = ((90 - lat) / 180) * h;
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(w, y);
      ctx.stroke();
    }

    for (let lon = -180; lon <= 180; lon += 30) {
      const x = ((lon + 180) / 360) * w;
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, h);
      ctx.stroke();
    }

    // Highlight Equator & Prime Meridian
    ctx.lineWidth = 1.5;
    ctx.strokeStyle = isDark ? 'rgba(56, 189, 248, 0.25)' : 'rgba(9, 105, 218, 0.22)';
    ctx.beginPath();
    ctx.moveTo(0, h * 0.5);
    ctx.lineTo(w, h * 0.5);
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(w * 0.5, 0);
    ctx.lineTo(w * 0.5, h);
    ctx.stroke();

    // 3. Draw Continents
    const toX = (lon: number) => ((lon + 180) / 360) * w;
    const toY = (lat: number) => ((90 - lat) / 180) * h;

    CONTINENT_DATA.forEach((poly) => {
      ctx.beginPath();
      poly.forEach(([lon, lat], idx) => {
        const px = toX(lon);
        const py = toY(lat);
        if (idx === 0) ctx.moveTo(px, py);
        else ctx.lineTo(px, py);
      });
      ctx.closePath();

      // Landmass fill
      ctx.fillStyle = isDark ? 'rgba(37, 99, 235, 0.18)' : 'rgba(14, 116, 144, 0.14)';
      ctx.fill();

      // Coastline stroke
      ctx.lineWidth = 2.2;
      ctx.strokeStyle = isDark ? '#38bdf8' : '#0284c7';
      ctx.stroke();
    });

    const texture = new THREE.CanvasTexture(canvas);
    texture.wrapS = THREE.ClampToEdgeWrapping;
    texture.wrapT = THREE.ClampToEdgeWrapping;
    return texture;
  }

  private initThreeScene(): void {
    const container = this.containerRef.nativeElement;
    const canvas = this.canvasRef.nativeElement;
    const width = container.clientWidth || 460;
    const height = container.clientHeight || 420;

    // 1. Scene & Camera
    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 100);
    this.camera.position.set(0, 0, 7.2);

    // 2. WebGL Renderer
    this.renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: true,
      alpha: true,
      powerPreference: 'high-performance'
    });
    this.renderer.setSize(width, height);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));

    // 3. Globe Master Group
    this.globeGroup = new THREE.Group();
    this.globeGroup.rotation.x = this.rotationX;
    this.globeGroup.rotation.y = this.rotationY;
    this.scene.add(this.globeGroup);

    const isDark = this.theme === 'dark';

    // 4. Smooth Round Earth Sphere Geometry with Continents
    const globeGeo = new THREE.SphereGeometry(2.35, 64, 64);
    const earthTexture = this.createEarthTexture(isDark);
    const earthMat = new THREE.MeshBasicMaterial({
      map: earthTexture,
      transparent: true,
      opacity: 0.95
    });
    this.earthMesh = new THREE.Mesh(globeGeo, earthMat);
    this.globeGroup.add(this.earthMesh);

    // 5. Solid Inner Core
    const innerGeo = new THREE.SphereGeometry(2.31, 32, 32);
    const innerMat = new THREE.MeshBasicMaterial({
      color: isDark ? 0x090f1a : 0xffffff
    });
    this.innerCoreMesh = new THREE.Mesh(innerGeo, innerMat);
    this.globeGroup.add(this.innerCoreMesh);

    // 6. Atmospheric Rim Halo
    const haloGeo = new THREE.SphereGeometry(2.42, 48, 48);
    const haloMat = new THREE.MeshBasicMaterial({
      color: isDark ? 0x38bdf8 : 0x0284c7,
      transparent: true,
      opacity: 0.1,
      side: THREE.BackSide
    });
    this.atmosphereHalo = new THREE.Mesh(haloGeo, haloMat);
    this.globeGroup.add(this.atmosphereHalo);

    // 7. Monitored Service Nodes on Exact Geographical Coordinates
    const nodeConfigs = [
      { lat: 38.0, lon: -78.0, name: 'Render Clúster (US-East)', status: 'HEALTHY' as const },
      { lat: -34.6, lon: -58.4, name: 'Buenos Aires (Ingress)', status: 'HEALTHY' as const },
      { lat: -33.4, lon: -70.7, name: 'Santiago (SSE Relayer)', status: 'HEALTHY' as const },
      { lat: 48.8, lon: 2.3, name: 'París / Frankfurt (Engine)', status: 'HEALTHY' as const },
      { lat: 35.7, lon: 139.7, name: 'Tokio (Edge Gateway)', status: 'HEALTHY' as const },
      { lat: -33.9, lon: 151.2, name: 'Sídney (Oceanía)', status: 'ALERT' as const }
    ];

    nodeConfigs.forEach((cfg) => {
      const pos = this.latLonToSphereVector(cfg.lat, cfg.lon, 2.37);
      const isHealthy = cfg.status === 'HEALTHY';
      const nodeColor = isHealthy ? 0x3fb950 : 0xd29922;

      // Small round node marker
      const nodeGeo = new THREE.SphereGeometry(0.08, 16, 16);
      const nodeMat = new THREE.MeshBasicMaterial({ color: nodeColor });
      const nodeMesh = new THREE.Mesh(nodeGeo, nodeMat);
      nodeMesh.position.copy(pos);
      this.globeGroup!.add(nodeMesh);

      // Gentle, calm breathing ping ring (no harsh flashing)
      const ringGeo = new THREE.RingGeometry(0.1, 0.16, 32);
      const ringMat = new THREE.MeshBasicMaterial({
        color: nodeColor,
        transparent: true,
        opacity: 0.5,
        side: THREE.DoubleSide
      });
      const ringMesh = new THREE.Mesh(ringGeo, ringMat);
      ringMesh.position.copy(pos);
      ringMesh.lookAt(pos.clone().multiplyScalar(2));
      this.globeGroup!.add(ringMesh);

      this.telemetryNodes.push({
        mesh: nodeMesh,
        ring: ringMesh,
        ringScale: Math.random() * 0.6,
        initialPos: pos,
        name: cfg.name,
        status: cfg.status
      });
    });

    // 8. Inter-continental Telemetry Data Arcs
    this.createDataArc(nodeConfigs[0], nodeConfigs[1], isDark ? 0x38bdf8 : 0x0284c7); // US-East <-> Buenos Aires
    this.createDataArc(nodeConfigs[0], nodeConfigs[3], isDark ? 0x3fb950 : 0x16a34a); // US-East <-> Paris
    this.createDataArc(nodeConfigs[3], nodeConfigs[4], isDark ? 0x38bdf8 : 0x0284c7); // Paris <-> Tokyo
    this.createDataArc(nodeConfigs[4], nodeConfigs[5], isDark ? 0xd29922 : 0xd97706); // Tokyo <-> Sydney

    // 9. Floating Ambient Space Particles
    const particleCount = 100;
    const particleGeo = new THREE.BufferGeometry();
    const particlePositions = new Float32Array(particleCount * 3);

    for (let i = 0; i < particleCount * 3; i += 3) {
      const radius = 3.6 + Math.random() * 3.0;
      const theta = Math.random() * Math.PI * 2;
      const phi = Math.acos(Math.random() * 2 - 1);
      particlePositions[i] = radius * Math.sin(phi) * Math.cos(theta);
      particlePositions[i + 1] = radius * Math.sin(phi) * Math.sin(theta);
      particlePositions[i + 2] = radius * Math.cos(phi);
    }
    particleGeo.setAttribute('position', new THREE.BufferAttribute(particlePositions, 3));
    const particleMat = new THREE.PointsMaterial({
      color: isDark ? 0x38bdf8 : 0x0284c7,
      size: 0.04,
      transparent: true,
      opacity: isDark ? 0.45 : 0.35
    });
    this.particlesMesh = new THREE.Points(particleGeo, particleMat);
    this.scene.add(this.particlesMesh);

    // 10. Calm Shockwave Ring
    const shockwaveGeo = new THREE.RingGeometry(0.1, 0.2, 64);
    const shockwaveMat = new THREE.MeshBasicMaterial({
      color: 0x38bdf8,
      transparent: true,
      opacity: 0,
      side: THREE.DoubleSide
    });
    this.shockwaveRing = new THREE.Mesh(shockwaveGeo, shockwaveMat);
    this.globeGroup.add(this.shockwaveRing);

    // 11. Run Render Loop Outside Angular Zone
    this.ngZone.runOutsideAngular(() => {
      this.animate();
    });
  }

  private createDataArc(
    from: { lat: number; lon: number },
    to: { lat: number; lon: number },
    color: number
  ): void {
    if (!this.globeGroup) return;
    const vFrom = this.latLonToSphereVector(from.lat, from.lon, 2.37);
    const vTo = this.latLonToSphereVector(to.lat, to.lon, 2.37);

    // Quadratic Bezier midpoint elevated above globe
    const mid = vFrom.clone().add(vTo).multiplyScalar(0.5);
    const distance = vFrom.distanceTo(vTo);
    mid.normalize().multiplyScalar(2.37 + distance * 0.24);

    const curve = new THREE.QuadraticBezierCurve3(vFrom, mid, vTo);
    const curvePoints = curve.getPoints(36);
    const curveGeo = new THREE.BufferGeometry().setFromPoints(curvePoints);
    const curveMat = new THREE.LineBasicMaterial({
      color,
      transparent: true,
      opacity: 0.4
    });
    const arcLine = new THREE.Line(curveGeo, curveMat);
    this.globeGroup.add(arcLine);
  }

  private latLonToSphereVector(lat: number, lon: number, radius: number): THREE.Vector3 {
    const u = (lon + 180) / 360;
    const v = (lat + 90) / 180;
    const phi = u * 2 * Math.PI;
    const theta = (1 - v) * Math.PI;
    const x = -radius * Math.cos(phi) * Math.sin(theta);
    const y = radius * Math.cos(theta);
    const z = radius * Math.sin(phi) * Math.sin(theta);
    return new THREE.Vector3(x, y, z);
  }

  private updateColorsForTheme(): void {
    const isDark = this.theme === 'dark';
    if (this.earthMesh) {
      const newTex = this.createEarthTexture(isDark);
      (this.earthMesh.material as THREE.MeshBasicMaterial).map = newTex;
      (this.earthMesh.material as THREE.MeshBasicMaterial).needsUpdate = true;
    }
    if (this.innerCoreMesh) {
      (this.innerCoreMesh.material as THREE.MeshBasicMaterial).color.setHex(isDark ? 0x090f1a : 0xffffff);
    }
    if (this.atmosphereHalo) {
      (this.atmosphereHalo.material as THREE.MeshBasicMaterial).color.setHex(isDark ? 0x38bdf8 : 0x0284c7);
    }
  }

  private animate = (): void => {
    this.animationFrameId = requestAnimationFrame(this.animate);

    if (this.globeGroup) {
      if (!this.isDragging) {
        // Inertia friction
        this.rotationX += this.velocityX;
        this.rotationY += this.velocityY;
        this.velocityX *= 0.92;
        this.velocityY *= 0.92;

        // Auto-rotation advances smoothly along the current tilted orientation
        if (this.isAutoRotating()) {
          this.rotationY += 0.0025;
        }
      }

      this.globeGroup.rotation.x = this.rotationX;
      this.globeGroup.rotation.y = this.rotationY;
    }

    // Drifting ambient particles
    if (this.particlesMesh) {
      this.particlesMesh.rotation.y -= 0.0006;
      this.particlesMesh.rotation.x += 0.0003;
    }

    // Gentle, calm breathing pulse for each service node
    this.telemetryNodes.forEach((node) => {
      node.ringScale += 0.007; // 3x slower, gentle breathing
      if (node.ringScale > 1.5) {
        node.ringScale = 0.2;
      }
      node.ring.scale.set(node.ringScale, node.ringScale, 1);
      const ringMat = node.ring.material as THREE.MeshBasicMaterial;
      ringMat.opacity = Math.max(0, 0.55 - node.ringScale * 0.35);
    });

    // Shockwave expansion
    if (this.shockwaveRing && this.shockwaveOpacity > 0.01) {
      this.shockwaveScale += 0.05;
      this.shockwaveOpacity *= 0.95;
      this.shockwaveRing.scale.set(this.shockwaveScale, this.shockwaveScale, 1);
      (this.shockwaveRing.material as THREE.MeshBasicMaterial).opacity = this.shockwaveOpacity;
    }

    if (this.renderer && this.scene && this.camera) {
      this.renderer.render(this.scene, this.camera);
    }
  };

  triggerPulse(): void {
    this.isPulseActive.set(true);
    this.shockwaveScale = 0.4;
    this.shockwaveOpacity = 0.8;
    if (this.shockwaveRing) {
      this.shockwaveRing.scale.set(0.4, 0.4, 1);
      (this.shockwaveRing.material as THREE.MeshBasicMaterial).opacity = 0.8;
    }

    this.telemetryNodes.forEach((node) => {
      node.ringScale = 0.1;
    });

    setTimeout(() => {
      this.isPulseActive.set(false);
    }, 1500);
  }

  toggleAutoRotation(): void {
    this.isAutoRotating.set(!this.isAutoRotating());
  }

  // Pointer Interaction Handlers
  onPointerMove(event: MouseEvent): void {
    if (this.isDragging) {
      const deltaX = event.clientX - this.previousMousePosition.x;
      const deltaY = event.clientY - this.previousMousePosition.y;
      this.velocityY = deltaX * 0.005;
      this.velocityX = deltaY * 0.005;
      this.rotationY += this.velocityY;
      this.rotationX += this.velocityX;
      // Clamp vertical angle to avoid upside down
      this.rotationX = Math.max(-1.3, Math.min(1.3, this.rotationX));
      this.previousMousePosition = { x: event.clientX, y: event.clientY };
    }
  }

  onPointerDown(event: MouseEvent): void {
    this.isDragging = true;
    this.velocityX = 0;
    this.velocityY = 0;
    this.previousMousePosition = { x: event.clientX, y: event.clientY };
  }

  onPointerUp(): void {
    this.isDragging = false;
  }

  private setupResizeObserver(): void {
    const container = this.containerRef.nativeElement;
    this.resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        const { width, height } = entry.contentRect;
        if (width > 0 && height > 0 && this.camera && this.renderer) {
          this.camera.aspect = width / height;
          this.camera.updateProjectionMatrix();
          this.renderer.setSize(width, height);
        }
      }
    });
    this.resizeObserver.observe(container);
  }

  private cleanup(): void {
    if (this.animationFrameId !== null) {
      cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
      this.resizeObserver = null;
    }
    if (this.renderer) {
      this.renderer.dispose();
      this.renderer.forceContextLoss();
      this.renderer = null;
    }
    this.scene = null;
    this.camera = null;
  }
}
