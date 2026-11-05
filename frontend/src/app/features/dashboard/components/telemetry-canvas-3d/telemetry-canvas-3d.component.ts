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
  readonly hoveredNodeName = signal<string | null>(null);
  readonly liveFps = signal<number>(60);

  // Three.js Core Objects
  private renderer: THREE.WebGLRenderer | null = null;
  private scene: THREE.Scene | null = null;
  private camera: THREE.PerspectiveCamera | null = null;
  private animationFrameId: number | null = null;
  private resizeObserver: ResizeObserver | null = null;

  // 3D Visual Objects
  private globeGroup: THREE.Group | null = null;
  private globeWireframe: THREE.LineSegments | null = null;
  private innerCoreMesh: THREE.Mesh | null = null;
  private orbitGroup1: THREE.Group | null = null;
  private orbitGroup2: THREE.Group | null = null;
  private particlesMesh: THREE.Points | null = null;
  private shockwaveRing: THREE.Mesh | null = null;
  private telemetryNodes: TelemetryPoint[] = [];

  // Interaction State
  private targetRotationX = 0;
  private targetRotationY = 0;
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
    this.scene.add(this.globeGroup);

    // 4. Wireframe Outer Geodesic Sphere
    const isDark = this.theme === 'dark';
    const wireColor = isDark ? 0x2f81f7 : 0x0969da;
    const coreColor = isDark ? 0x161b22 : 0xf0f6fc;

    const globeGeo = new THREE.IcosahedronGeometry(2.35, 2);
    const wireGeo = new THREE.WireframeGeometry(globeGeo);
    const wireMat = new THREE.LineBasicMaterial({
      color: wireColor,
      transparent: true,
      opacity: isDark ? 0.38 : 0.45
    });
    this.globeWireframe = new THREE.LineSegments(wireGeo, wireMat);
    this.globeGroup.add(this.globeWireframe);

    // 5. Semi-Transparent Inner Solid Sphere
    const innerGeo = new THREE.SphereGeometry(2.2, 32, 32);
    const innerMat = new THREE.MeshBasicMaterial({
      color: coreColor,
      transparent: true,
      opacity: isDark ? 0.6 : 0.85
    });
    this.innerCoreMesh = new THREE.Mesh(innerGeo, innerMat);
    this.globeGroup.add(this.innerCoreMesh);

    // 6. Monitored Service Nodes on Globe Surface
    const nodeConfigs = [
      { lat: 37, lon: -122, name: 'Render US-East (Engine)', status: 'HEALTHY' as const },
      { lat: 51, lon: 0, name: 'Supabase DB (PostgreSQL)', status: 'HEALTHY' as const },
      { lat: 35, lon: 139, name: 'Tokyo Gateway (Edge)', status: 'HEALTHY' as const },
      { lat: -33, lon: -70, name: 'Santiago SSE Relayer', status: 'HEALTHY' as const },
      { lat: 48, lon: 2, name: 'Paris Auth Cluster', status: 'ALERT' as const },
      { lat: -34, lon: -58, name: 'Buenos Aires Ingress', status: 'HEALTHY' as const }
    ];

    nodeConfigs.forEach((cfg) => {
      const pos = this.latLonToVector3(cfg.lat, cfg.lon, 2.38);
      const isHealthy = cfg.status === 'HEALTHY';
      const nodeColor = isHealthy ? 0x3fb950 : 0xd29922;

      // Small glowing node sphere
      const nodeGeo = new THREE.SphereGeometry(0.09, 16, 16);
      const nodeMat = new THREE.MeshBasicMaterial({ color: nodeColor });
      const nodeMesh = new THREE.Mesh(nodeGeo, nodeMat);
      nodeMesh.position.copy(pos);
      this.globeGroup!.add(nodeMesh);

      // Expanding beacon ping ring
      const ringGeo = new THREE.RingGeometry(0.12, 0.18, 24);
      const ringMat = new THREE.MeshBasicMaterial({
        color: nodeColor,
        transparent: true,
        opacity: 0.7,
        side: THREE.DoubleSide
      });
      const ringMesh = new THREE.Mesh(ringGeo, ringMat);
      ringMesh.position.copy(pos);
      ringMesh.lookAt(pos.clone().multiplyScalar(2));
      this.globeGroup!.add(ringMesh);

      this.telemetryNodes.push({
        mesh: nodeMesh,
        ring: ringMesh,
        ringScale: Math.random() * 0.8,
        initialPos: pos,
        name: cfg.name,
        status: cfg.status
      });
    });

    // 7. Data Transmission Arcs between nodes
    this.createDataArc(nodeConfigs[0], nodeConfigs[1], isDark ? 0x58a6ff : 0x0969da);
    this.createDataArc(nodeConfigs[1], nodeConfigs[4], isDark ? 0xd29922 : 0x9a6700);
    this.createDataArc(nodeConfigs[0], nodeConfigs[5], isDark ? 0x3fb950 : 0x1a7f37);
    this.createDataArc(nodeConfigs[5], nodeConfigs[3], isDark ? 0x58a6ff : 0x0969da);

    // 8. Dual Tilted Orbital Rings with Satellites
    this.orbitGroup1 = this.createOrbitRing(3.2, 0.35, isDark ? 0x58a6ff : 0x0969da, 0x3fb950);
    this.globeGroup.add(this.orbitGroup1);

    this.orbitGroup2 = this.createOrbitRing(3.5, -0.45, isDark ? 0x3fb950 : 0x1a7f37, 0x58a6ff);
    this.globeGroup.add(this.orbitGroup2);

    // 9. Floating Telemetry Particles (Ambient Starfield)
    const particleCount = 130;
    const particleGeo = new THREE.BufferGeometry();
    const particlePositions = new Float32Array(particleCount * 3);

    for (let i = 0; i < particleCount * 3; i += 3) {
      const radius = 3.8 + Math.random() * 3.5;
      const theta = Math.random() * Math.PI * 2;
      const phi = Math.acos(Math.random() * 2 - 1);
      particlePositions[i] = radius * Math.sin(phi) * Math.cos(theta);
      particlePositions[i + 1] = radius * Math.sin(phi) * Math.sin(theta);
      particlePositions[i + 2] = radius * Math.cos(phi);
    }
    particleGeo.setAttribute('position', new THREE.BufferAttribute(particlePositions, 3));
    const particleMat = new THREE.PointsMaterial({
      color: isDark ? 0x58a6ff : 0x0969da,
      size: 0.045,
      transparent: true,
      opacity: isDark ? 0.6 : 0.45
    });
    this.particlesMesh = new THREE.Points(particleGeo, particleMat);
    this.scene.add(this.particlesMesh);

    // 10. Shockwave Ring (triggered on user pulse click)
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

  private createOrbitRing(radius: number, tilt: number, ringColor: number, satColor: number): THREE.Group {
    const group = new THREE.Group();
    group.rotation.x = tilt;

    // Orbit Path
    const curve = new THREE.EllipseCurve(0, 0, radius, radius * 0.92, 0, 2 * Math.PI, false, 0);
    const points = curve.getPoints(64);
    const orbitGeo = new THREE.BufferGeometry().setFromPoints(
      points.map((p) => new THREE.Vector3(p.x, 0, p.y))
    );
    const orbitMat = new THREE.LineBasicMaterial({
      color: ringColor,
      transparent: true,
      opacity: 0.28
    });
    const orbitLine = new THREE.Line(orbitGeo, orbitMat);
    group.add(orbitLine);

    // Satellite Packet
    const satGeo = new THREE.BoxGeometry(0.12, 0.12, 0.12);
    const satMat = new THREE.MeshBasicMaterial({ color: satColor });
    const satellite = new THREE.Mesh(satGeo, satMat);
    satellite.position.set(radius, 0, 0);
    group.add(satellite);

    return group;
  }

  private createDataArc(
    from: { lat: number; lon: number },
    to: { lat: number; lon: number },
    color: number
  ): void {
    if (!this.globeGroup) return;
    const vFrom = this.latLonToVector3(from.lat, from.lon, 2.38);
    const vTo = this.latLonToVector3(to.lat, to.lon, 2.38);

    // Quadratic Bezier midpoint elevated above globe
    const mid = vFrom.clone().add(vTo).multiplyScalar(0.5);
    const distance = vFrom.distanceTo(vTo);
    mid.normalize().multiplyScalar(2.38 + distance * 0.25);

    const curve = new THREE.QuadraticBezierCurve3(vFrom, mid, vTo);
    const curvePoints = curve.getPoints(36);
    const curveGeo = new THREE.BufferGeometry().setFromPoints(curvePoints);
    const curveMat = new THREE.LineBasicMaterial({
      color,
      transparent: true,
      opacity: 0.45
    });
    const arcLine = new THREE.Line(curveGeo, curveMat);
    this.globeGroup.add(arcLine);
  }

  private latLonToVector3(lat: number, lon: number, radius: number): THREE.Vector3 {
    const phi = (90 - lat) * (Math.PI / 180);
    const theta = (lon + 180) * (Math.PI / 180);
    return new THREE.Vector3(
      -(radius * Math.sin(phi) * Math.cos(theta)),
      radius * Math.cos(phi),
      radius * Math.sin(phi) * Math.sin(theta)
    );
  }

  private updateColorsForTheme(): void {
    const isDark = this.theme === 'dark';
    const wireColor = isDark ? 0x2f81f7 : 0x0969da;
    const coreColor = isDark ? 0x161b22 : 0xf0f6fc;

    if (this.globeWireframe) {
      (this.globeWireframe.material as THREE.LineBasicMaterial).color.setHex(wireColor);
      (this.globeWireframe.material as THREE.LineBasicMaterial).opacity = isDark ? 0.38 : 0.45;
    }
    if (this.innerCoreMesh) {
      (this.innerCoreMesh.material as THREE.MeshBasicMaterial).color.setHex(coreColor);
      (this.innerCoreMesh.material as THREE.MeshBasicMaterial).opacity = isDark ? 0.6 : 0.85;
    }
    if (this.particlesMesh) {
      (this.particlesMesh.material as THREE.PointsMaterial).color.setHex(isDark ? 0x58a6ff : 0x0969da);
    }
  }

  private animate = (): void => {
    this.animationFrameId = requestAnimationFrame(this.animate);

    if (this.globeGroup) {
      // Auto-rotation when active
      if (this.isAutoRotating() && !this.isDragging) {
        this.globeGroup.rotation.y += 0.0035;
      }

      // Smooth damping interpolation toward target mouse rotation
      this.globeGroup.rotation.x += (this.targetRotationX - this.globeGroup.rotation.x) * 0.06;
      if (!this.isAutoRotating() || this.isDragging) {
        this.globeGroup.rotation.y += (this.targetRotationY - this.globeGroup.rotation.y) * 0.06;
      }
    }

    // Rotate Orbital Rings & Satellites
    if (this.orbitGroup1) {
      this.orbitGroup1.rotation.y += 0.012;
      this.orbitGroup1.rotation.z += 0.002;
    }
    if (this.orbitGroup2) {
      this.orbitGroup2.rotation.y -= 0.009;
      this.orbitGroup2.rotation.z -= 0.002;
    }

    // Drifting ambient particles
    if (this.particlesMesh) {
      this.particlesMesh.rotation.y -= 0.001;
      this.particlesMesh.rotation.x += 0.0005;
    }

    // Pulsate beacon rings on each service node
    this.telemetryNodes.forEach((node) => {
      node.ringScale += 0.022;
      if (node.ringScale > 1.6) {
        node.ringScale = 0.2;
      }
      node.ring.scale.set(node.ringScale, node.ringScale, 1);
      const ringMat = node.ring.material as THREE.MeshBasicMaterial;
      ringMat.opacity = Math.max(0, 0.85 - node.ringScale * 0.5);
    });

    // Expand Shockwave when triggered
    if (this.shockwaveRing && this.shockwaveOpacity > 0.01) {
      this.shockwaveScale += 0.07;
      this.shockwaveOpacity *= 0.94;
      this.shockwaveRing.scale.set(this.shockwaveScale, this.shockwaveScale, 1);
      (this.shockwaveRing.material as THREE.MeshBasicMaterial).opacity = this.shockwaveOpacity;
    }

    if (this.renderer && this.scene && this.camera) {
      this.renderer.render(this.scene, this.camera);
    }
  };

  triggerPulse(): void {
    this.isPulseActive.set(true);
    this.shockwaveScale = 0.5;
    this.shockwaveOpacity = 0.9;
    if (this.shockwaveRing) {
      this.shockwaveRing.scale.set(0.5, 0.5, 1);
      (this.shockwaveRing.material as THREE.MeshBasicMaterial).opacity = 0.9;
    }

    // Node flash
    this.telemetryNodes.forEach((node) => {
      node.ringScale = 0.1;
    });

    setTimeout(() => {
      this.isPulseActive.set(false);
    }, 1200);
  }

  toggleAutoRotation(): void {
    this.isAutoRotating.set(!this.isAutoRotating());
  }

  // Pointer Interaction Handlers
  onPointerMove(event: MouseEvent): void {
    const rect = this.containerRef.nativeElement.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    const y = -(((event.clientY - rect.top) / rect.height) * 2 - 1);

    if (this.isDragging) {
      const deltaX = event.clientX - this.previousMousePosition.x;
      const deltaY = event.clientY - this.previousMousePosition.y;
      this.targetRotationY += deltaX * 0.012;
      this.targetRotationX += deltaY * 0.012;
      this.previousMousePosition = { x: event.clientX, y: event.clientY };
    } else {
      // Subtle tilt based on cursor coordinates
      this.targetRotationX = y * 0.35;
      if (!this.isAutoRotating()) {
        this.targetRotationY = x * 0.5;
      }
    }
  }

  onPointerDown(event: MouseEvent): void {
    this.isDragging = true;
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
