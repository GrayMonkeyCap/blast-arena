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

// per-kind shells (BombSquad: dark sphere / blue ice / green sticky /
// blinking orange impact / flat land mine)
const SHELLS = {
  normal: { color: '#23262e', emissive: '#ff2a1a', fuse: true },
  ice: { color: '#6fb4d9', emissive: '#bfe9ff', fuse: true },
  sticky: { color: '#3f8a3a', emissive: '#5aff6a', fuse: true },
  impact: { color: '#4a3220', emissive: '#ff8a1a', fuse: false },
  mine: { color: '#9aa4ad', emissive: '#ff3a2a', fuse: false, flat: true },
};

export class BombView {
  constructor(scene, b) {
    this.scene = scene;
    this.kind = b?.kind ?? 'normal';
    const style = SHELLS[this.kind] ?? SHELLS.normal;
    this.group = new THREE.Group();

    this.mat = new THREE.MeshToonMaterial({
      color: style.color,
      gradientMap: toonGradient(),
      emissive: style.emissive,
      emissiveIntensity: 0,
    });
    const r = CONFIG.physics.materials.bomb.radius;
    this.shell = new THREE.Mesh(new THREE.SphereGeometry(r, 18, 14), this.mat);
    this.shell.castShadow = true;
    if (style.flat) {
      // land mine: a squat disc that hugs the ground
      this.shell.scale.set(1.15, 0.42, 1.15);
      this.shell.position.y = -r * 0.5;
    }

    // top group stays upright while the shell rolls
    this.top = new THREE.Group();
    if (style.fuse) {
      const collar = new THREE.Mesh(
        new THREE.CylinderGeometry(0.09, 0.11, 0.09, 10),
        new THREE.MeshToonMaterial({ color: '#4a4e5c', gradientMap: toonGradient() }),
      );
      collar.position.y = r + 0.02;
      const fuse = new THREE.Mesh(
        new THREE.CylinderGeometry(0.028, 0.028, 0.2, 6),
        new THREE.MeshToonMaterial({ color: '#c9b38a', gradientMap: toonGradient() }),
      );
      fuse.position.set(0.045, r + 0.14, 0);
      fuse.rotation.z = -0.4;

      this.spark = new THREE.Sprite(new THREE.SpriteMaterial({
        map: getSparkTex(),
        blending: THREE.AdditiveBlending,
        transparent: true,
        depthWrite: false,
      }));
      this.spark.position.set(0.1, r + 0.26, 0);
      this.top.add(collar, fuse, this.spark);
    }
    this.group.add(this.shell, this.top);
    scene.add(this.group);
  }

  update(b, dt, time) {
    this.group.position.set(b.x, b.y, b.z);
    if (!b.holder && !b.stuckTo && !SHELLS[this.kind]?.flat) {
      this.shell.rotation.x += (b.vz || 0) * dt * 2.4;
      this.shell.rotation.z -= (b.vx || 0) * dt * 2.4;
    }
    const armed = !(b.arm > 0);
    if (b.fuse == null) {
      // land mine: dark until armed, then a steady menacing blink
      this.mat.emissiveIntensity = armed ? (Math.sin(time * 6) > 0 ? 0.8 : 0.15) : 0;
    } else if (this.kind === 'impact') {
      // impact bomb: rapid strobe once armed — touch means boom
      this.mat.emissiveIntensity = armed ? (Math.sin(time * 16) + 1) / 2 * 0.8 : 0;
    } else {
      // danger pulse: slow amber blink -> frantic red strobe
      const f = Math.max(b.fuse, 0.001);
      const rate = 3 + 10 / Math.max(f, 0.35);
      const pulse = (Math.sin(time * rate * Math.PI) + 1) / 2;
      this.mat.emissiveIntensity = pulse * (0.25 + 0.75 * (1 - Math.min(f / CONFIG.bomb.fuse, 1)));
    }
    if (this.spark) {
      const s = 0.32 + Math.random() * 0.22;
      this.spark.scale.set(s, s, 1);
    }
  }

  dispose() {
    this.mat.dispose();
    this.scene.remove(this.group);
  }
}
