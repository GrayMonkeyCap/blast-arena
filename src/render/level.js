// Builds the arena meshes from a level definition. Purely data-driven: the
// same `solids` list the sim collides against is what gets rendered, styled
// by `kind`, so a new level file automatically renders correctly.

import * as THREE from 'three';
import { TEAMS } from '../core/config.js';
import { toonMat, toonGradient } from './characters.js';

function canvasTex(size, draw, repeat) {
  const c = document.createElement('canvas');
  c.width = c.height = size;
  draw(c.getContext('2d'), size);
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  if (repeat) {
    tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
    tex.repeat.set(repeat[0], repeat[1]);
  }
  return tex;
}

function floorTexture(theme) {
  return canvasTex(512, (g, s) => {
    g.fillStyle = theme.floor;
    g.fillRect(0, 0, s, s);
    // panel variation
    const tile = s / 4;
    for (let y = 0; y < 4; y++) {
      for (let x = 0; x < 4; x++) {
        if ((x + y) % 2 === 0) continue;
        g.fillStyle = 'rgba(0,0,0,0.05)';
        g.fillRect(x * tile, y * tile, tile, tile);
      }
    }
    // grime speckle
    for (let i = 0; i < 320; i++) {
      g.fillStyle = `rgba(${Math.random() > 0.5 ? '255,255,255' : '10,15,25'},${0.02 + Math.random() * 0.05})`;
      const r = 1 + Math.random() * 4;
      g.fillRect(Math.random() * s, Math.random() * s, r, r);
    }
    // grout lines
    g.strokeStyle = 'rgba(20,26,36,0.35)';
    g.lineWidth = 3;
    for (let i = 0; i <= 4; i++) {
      g.beginPath(); g.moveTo(i * tile, 0); g.lineTo(i * tile, s); g.stroke();
      g.beginPath(); g.moveTo(0, i * tile); g.lineTo(s, i * tile); g.stroke();
    }
  });
}

function crateTexture(theme) {
  return canvasTex(128, (g, s) => {
    g.fillStyle = theme.crate;
    g.fillRect(0, 0, s, s);
    g.strokeStyle = 'rgba(60,30,5,0.55)';
    g.lineWidth = 5;
    g.strokeRect(4, 4, s - 8, s - 8);
    for (let i = 1; i < 4; i++) { // planks
      g.beginPath(); g.moveTo(0, (i * s) / 4); g.lineTo(s, (i * s) / 4);
      g.lineWidth = 2; g.stroke();
    }
    g.lineWidth = 6; // cross brace
    g.beginPath(); g.moveTo(8, 8); g.lineTo(s - 8, s - 8); g.stroke();
    g.beginPath(); g.moveTo(s - 8, 8); g.lineTo(8, s - 8); g.stroke();
  });
}

export function buildLevel(scene, level, { touch }) {
  const theme = level.theme;
  const group = new THREE.Group();
  const { w, d } = level.bounds;

  // floor slab + dark underside skirt (we're floating over a void)
  const floorMat = new THREE.MeshStandardMaterial({
    map: floorTexture(theme),
    roughness: 0.9,
    metalness: 0.05,
  });
  floorMat.map.wrapS = floorMat.map.wrapT = THREE.RepeatWrapping;
  floorMat.map.repeat.set(w / 8, d / 8);
  const floor = new THREE.Mesh(new THREE.BoxGeometry(w, 1.1, d), floorMat);
  floor.position.y = -0.55;
  floor.receiveShadow = true;
  group.add(floor);
  const skirt = new THREE.Mesh(
    new THREE.BoxGeometry(w - 1.6, 6, d - 1.6),
    toonMat('#1b202b'),
  );
  skirt.position.y = -4.1;
  group.add(skirt);

  // field markings
  const lineMat = new THREE.MeshBasicMaterial({ color: theme.line, transparent: true, opacity: 0.4, depthWrite: false });
  const midline = new THREE.Mesh(new THREE.PlaneGeometry(0.18, d - 1), lineMat);
  midline.rotation.x = -Math.PI / 2;
  midline.position.y = 0.02;
  const circle = new THREE.Mesh(new THREE.RingGeometry(2.7, 2.92, 48), lineMat);
  circle.rotation.x = -Math.PI / 2;
  circle.position.set(0, 0.02, 0); // center-court marking
  group.add(midline, circle);

  // team base pads (score zones — unmistakable)
  for (const [teamId, base] of Object.entries(level.bases)) {
    const team = TEAMS[teamId];
    const pad = new THREE.Mesh(
      new THREE.CircleGeometry(base.r, 40),
      new THREE.MeshBasicMaterial({ color: team.color, transparent: true, opacity: 0.16, depthWrite: false }),
    );
    pad.rotation.x = -Math.PI / 2;
    pad.position.set(base.x, 0.025, base.z);
    const rim = new THREE.Mesh(
      new THREE.RingGeometry(base.r - 0.18, base.r, 48),
      new THREE.MeshBasicMaterial({ color: team.glow, transparent: true, opacity: 0.85, depthWrite: false }),
    );
    rim.rotation.x = -Math.PI / 2;
    rim.position.set(base.x, 0.03, base.z);
    const chevrons = new THREE.Mesh(
      new THREE.RingGeometry(base.r * 0.35, base.r * 0.5, 3),
      new THREE.MeshBasicMaterial({ color: team.glow, transparent: true, opacity: 0.5, depthWrite: false }),
    );
    chevrons.rotation.x = -Math.PI / 2;
    chevrons.rotation.z = teamId === 'red' ? -Math.PI / 2 : Math.PI / 2; // arrow points inward
    chevrons.position.set(base.x, 0.03, base.z);
    group.add(pad, rim, chevrons);
  }

  // solids — same boxes the sim collides with
  const wallMat = toonMat(theme.wall);
  const wallTopMat = toonMat(theme.wallTop);
  const pillarMat = toonMat(theme.pillar);
  const railMat = toonMat(theme.rail);
  const crateMat = new THREE.MeshStandardMaterial({ map: crateTexture(theme), roughness: 0.85 });
  for (const s of level.solids) {
    let mesh;
    if (s.kind === 'rail') {
      // post-and-bar guard rail
      const rail = new THREE.Group();
      const horizontal = s.w > s.d;
      const len = horizontal ? s.w : s.d;
      const bar = new THREE.Mesh(new THREE.BoxGeometry(horizontal ? s.w : 0.14, 0.12, horizontal ? 0.14 : s.d), railMat);
      bar.position.y = s.h - 0.06;
      rail.add(bar);
      const n = Math.max(2, Math.round(len / 2.4));
      for (let i = 0; i <= n; i++) {
        const post = new THREE.Mesh(new THREE.BoxGeometry(0.14, s.h, 0.14), railMat);
        const off = -len / 2 + (i * len) / n;
        post.position.set(horizontal ? off : 0, s.h / 2, horizontal ? 0 : off);
        rail.add(post);
      }
      rail.position.set(s.x, 0, s.z);
      rail.traverse((o) => { if (o.isMesh) o.castShadow = true; });
      group.add(rail);
      continue;
    }
    if (s.kind === 'pillar') {
      mesh = new THREE.Mesh(new THREE.CylinderGeometry(s.w / 2, s.w / 2 + 0.1, s.h, 14), pillarMat);
      mesh.position.set(s.x, s.h / 2, s.z);
      const cap = new THREE.Mesh(
        new THREE.CylinderGeometry(s.w / 2 + 0.12, s.w / 2 + 0.12, 0.12, 14),
        new THREE.MeshBasicMaterial({ color: '#ffcf4d' }),
      );
      cap.position.set(s.x, s.h + 0.06, s.z);
      group.add(cap);
    } else if (s.kind === 'crate') {
      mesh = new THREE.Mesh(new THREE.BoxGeometry(s.w, s.h, s.d), crateMat);
      mesh.position.set(s.x, s.h / 2, s.z);
      mesh.rotation.y = ((s.x * 7 + s.z * 13) % 10) * 0.012; // subtle scatter
    } else {
      mesh = new THREE.Mesh(new THREE.BoxGeometry(s.w, s.h, s.d), wallMat);
      mesh.position.set(s.x, s.h / 2, s.z);
      const cap = new THREE.Mesh(new THREE.BoxGeometry(s.w + 0.08, 0.1, s.d + 0.08), wallTopMat);
      cap.position.set(s.x, s.h + 0.05, s.z);
      cap.castShadow = true;
      group.add(cap);
    }
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
  }

  // decor: corner lamps + team banners
  for (const lamp of level.decor?.lamps ?? []) {
    const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.09, 0.12, 3.4, 8), railMat);
    pole.position.set(lamp.x, 1.7, lamp.z);
    pole.castShadow = true;
    const bulb = new THREE.Mesh(
      new THREE.SphereGeometry(0.26, 12, 10),
      new THREE.MeshBasicMaterial({ color: theme.lamp }),
    );
    bulb.position.set(lamp.x, 3.5, lamp.z);
    group.add(pole, bulb);
    if (!touch) {
      const glow = new THREE.PointLight(theme.lamp, 14, 13, 2);
      glow.position.set(lamp.x, 3.4, lamp.z);
      group.add(glow);
    }
  }
  // measurement rings (physics-lab levels): read distances off the floor
  for (const ring of level.decor?.rings ?? []) {
    const r = new THREE.Mesh(
      new THREE.RingGeometry(ring.r - 0.06, ring.r + 0.06, 56),
      new THREE.MeshBasicMaterial({ color: theme.line, transparent: true, opacity: 0.35, depthWrite: false }),
    );
    r.rotation.x = -Math.PI / 2;
    r.position.set(ring.x, 0.022, ring.z);
    group.add(r);
  }

  for (const b of level.decor?.banners ?? []) {
    const team = TEAMS[b.team];
    const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.08, 3.0, 8), railMat);
    pole.position.set(b.x, 1.5, b.z);
    const cloth = new THREE.Mesh(
      new THREE.PlaneGeometry(0.95, 1.7),
      new THREE.MeshToonMaterial({ color: team.color, side: THREE.DoubleSide, gradientMap: toonGradient() }),
    );
    const inward = b.x > 0 ? -1 : 1;
    cloth.position.set(b.x + inward * 0.52, 2.1, b.z);
    cloth.rotation.y = inward * 0.35; // angled to read from the fixed camera
    cloth.castShadow = true;
    group.add(pole, cloth);
  }

  // starfield void below/around the platform
  const starGeo = new THREE.BufferGeometry();
  const starPos = new Float32Array(260 * 3);
  for (let i = 0; i < 260; i++) {
    const a = Math.random() * Math.PI * 2;
    const r = 45 + Math.random() * 70;
    starPos[i * 3] = Math.cos(a) * r;
    starPos[i * 3 + 1] = -30 + Math.random() * 55;
    starPos[i * 3 + 2] = Math.sin(a) * r;
  }
  starGeo.setAttribute('position', new THREE.BufferAttribute(starPos, 3));
  const stars = new THREE.Points(starGeo, new THREE.PointsMaterial({
    color: '#8fb3ff', size: 0.4, transparent: true, opacity: 0.7, fog: false,
  }));
  group.add(stars);

  scene.add(group);
  return group;
}
