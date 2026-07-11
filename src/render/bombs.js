// Bomb visuals: the classic cartoon sphere-with-a-fuse. The shell pulses an
// ever-faster red glow as the fuse runs down (the sim's fuse value drives
// it, so what you see is exactly when it pops), and the fuse tip carries a
// flickering additive spark.

import * as THREE from 'three';
import { toonGradient } from './characters.js';
import { CONFIG } from '../core/config.js';

let sparkTex = null;
function getSparkTex() {
  if (!sparkTex) {
    const c = document.createElement('canvas');
    c.width = c.height = 64;
    const g = c.getContext('2d');
    const grad = g.createRadialGradient(32, 32, 2, 32, 32, 30);
    grad.addColorStop(0, 'rgba(255,255,235,1)');
    grad.addColorStop(0.35, 'rgba(255,210,110,0.9)');
    grad.addColorStop(1, 'rgba(255,140,40,0)');
    g.fillStyle = grad;
    g.fillRect(0, 0, 64, 64);
    sparkTex = new THREE.CanvasTexture(c);
    sparkTex.colorSpace = THREE.SRGBColorSpace;
  }
  return sparkTex;
}

export class BombView {
  constructor(scene) {
    this.scene = scene;
    this.group = new THREE.Group();

    this.mat = new THREE.MeshToonMaterial({
      color: '#23262e',
      gradientMap: toonGradient(),
      emissive: '#ff2a1a',
      emissiveIntensity: 0,
    });
    this.shell = new THREE.Mesh(new THREE.SphereGeometry(CONFIG.physics.materials.bomb.radius, 18, 14), this.mat);
    this.shell.castShadow = true;

    const collar = new THREE.Mesh(
      new THREE.CylinderGeometry(0.09, 0.11, 0.09, 10),
      new THREE.MeshToonMaterial({ color: '#4a4e5c', gradientMap: toonGradient() }),
    );
    collar.position.y = CONFIG.physics.materials.bomb.radius + 0.02;
    const fuse = new THREE.Mesh(
      new THREE.CylinderGeometry(0.028, 0.028, 0.2, 6),
      new THREE.MeshToonMaterial({ color: '#c9b38a', gradientMap: toonGradient() }),
    );
    fuse.position.set(0.045, CONFIG.physics.materials.bomb.radius + 0.14, 0);
    fuse.rotation.z = -0.4;

    this.spark = new THREE.Sprite(new THREE.SpriteMaterial({
      map: getSparkTex(),
      blending: THREE.AdditiveBlending,
      transparent: true,
      depthWrite: false,
    }));
    this.spark.position.set(0.1, CONFIG.physics.materials.bomb.radius + 0.26, 0);

    // top group stays upright while the shell rolls
    this.top = new THREE.Group();
    this.top.add(collar, fuse, this.spark);
    this.group.add(this.shell, this.top);
    scene.add(this.group);
  }

  update(b, dt, time) {
    this.group.position.set(b.x, b.y, b.z);
    if (!b.holder) {
      this.shell.rotation.x += (b.vz || 0) * dt * 2.4;
      this.shell.rotation.z -= (b.vx || 0) * dt * 2.4;
    }
    // danger pulse: slow amber blink -> frantic red strobe
    const f = Math.max(b.fuse, 0.001);
    const rate = 3 + 10 / Math.max(f, 0.35);
    const pulse = (Math.sin(time * rate * Math.PI) + 1) / 2;
    this.mat.emissiveIntensity = pulse * (0.25 + 0.75 * (1 - Math.min(f / CONFIG.bomb.fuse, 1)));
    const s = 0.32 + Math.random() * 0.22;
    this.spark.scale.set(s, s, 1);
  }

  dispose() {
    this.mat.dispose();
    this.scene.remove(this.group);
  }
}
