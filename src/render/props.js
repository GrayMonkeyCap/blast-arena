// Reusable low-poly prop builders for level scenery. Pure builder functions:
// each takes a theme (and per-prop data) and returns a THREE.Object3D/Group
// (or an InstancedMesh) already positioned in world space, ready for the
// caller to `group.add(...)`. Geometries and materials are cached/shared at
// module scope so repeated calls (many solids/decor entries reusing the same
// dimensions or colors) don't allocate duplicates. Any "randomness" is seeded
// deterministically from a prop's own x/z so the level looks identical every
// time it's built — nothing here uses Math.random().

import * as THREE from 'three';
import { toonMat } from './characters.js';

// --- deterministic pseudo-random + color helpers -------------------------

function hash(n) {
  const s = Math.sin(n * 12.9898) * 43758.5453123;
  return s - Math.floor(s);
}
// stable 0..1 pseudo-random from two numbers (typically a prop's x,z)
function seed2(a, b) {
  return hash(a * 12.9898 + b * 78.233 + 37.719);
}

function shade(hex, amt) {
  const c = new THREE.Color(hex);
  if (amt >= 0) c.lerp(new THREE.Color(0xffffff), amt);
  else c.lerp(new THREE.Color(0x000000), -amt);
  return `#${c.getHexString()}`;
}

// --- shared geometry / material caches ------------------------------------

const geoCache = new Map();
function memoGeo(key, factory) {
  if (!geoCache.has(key)) geoCache.set(key, factory());
  return geoCache.get(key);
}
function boxGeo(w, h, d) {
  return memoGeo(`box:${w.toFixed(3)}:${h.toFixed(3)}:${d.toFixed(3)}`, () => new THREE.BoxGeometry(w, h, d));
}
function cylGeo(rt, rb, h, seg = 12, openEnded = false) {
  return memoGeo(
    `cyl:${rt.toFixed(3)}:${rb.toFixed(3)}:${h.toFixed(3)}:${seg}:${openEnded}`,
    () => new THREE.CylinderGeometry(rt, rb, h, seg, 1, openEnded),
  );
}
function icoGeo(r, detail = 0) {
  return memoGeo(`ico:${r.toFixed(3)}:${detail}`, () => new THREE.IcosahedronGeometry(r, detail));
}
function torusGeo(r, tube, rs, ts, arc = Math.PI * 2) {
  return memoGeo(
    `torus:${r.toFixed(3)}:${tube.toFixed(3)}:${rs}:${ts}:${arc.toFixed(3)}`,
    () => new THREE.TorusGeometry(r, tube, rs, ts, arc),
  );
}
function coneGeo(r, h, seg = 8) {
  return memoGeo(`cone:${r.toFixed(3)}:${h.toFixed(3)}:${seg}`, () => new THREE.ConeGeometry(r, h, seg));
}
function ringGeo(ri, ro, seg = 32) {
  return memoGeo(`ring:${ri.toFixed(3)}:${ro.toFixed(3)}:${seg}`, () => new THREE.RingGeometry(ri, ro, seg));
}
function circleGeo(r, seg = 40) {
  return memoGeo(`circle:${r.toFixed(3)}:${seg}`, () => new THREE.CircleGeometry(r, seg));
}
function planeGeo(w, h) {
  return memoGeo(`plane:${w.toFixed(3)}:${h.toFixed(3)}`, () => new THREE.PlaneGeometry(w, h));
}

const matCache = new Map();
function stdMat(key, opts) {
  const k = `std:${key}`;
  if (!matCache.has(k)) matCache.set(k, new THREE.MeshStandardMaterial(opts));
  return matCache.get(k);
}
function basicMat(key, opts) {
  const k = `basic:${key}`;
  if (!matCache.has(k)) matCache.set(k, new THREE.MeshBasicMaterial(opts));
  return matCache.get(k);
}
function rockMat(theme) {
  return stdMat(`rock:${theme.rock}`, { color: theme.rock, flatShading: true, roughness: 0.92, metalness: 0.02 });
}

function shadowOn(obj) {
  obj.traverse((o) => { if (o.isMesh) { o.castShadow = true; o.receiveShadow = true; } });
  return obj;
}

// =========================================================================
// Solid `kind` builders — each takes (theme, s) where s = {x,z,w,d,h,kind}
// and returns an Object3D positioned at world (x,0,z) (children own the y).
// =========================================================================

// engraved low stone kerb/wall block
export function makeStone(theme, s) {
  const g = new THREE.Group();
  const mat = toonMat(theme.stone);
  const body = new THREE.Mesh(boxGeo(s.w, s.h, s.d), mat);
  body.position.y = s.h / 2;
  const cap = new THREE.Mesh(boxGeo(s.w + 0.06, 0.08, s.d + 0.06), toonMat(shade(theme.stone, 0.16)));
  cap.position.y = s.h + 0.04;
  g.add(body, cap);
  g.position.set(s.x, 0, s.z);
  return shadowOn(g);
}

// waist-high stone cover cube, smaller top box gives a chamfered read
export function makeBlock(theme, s) {
  const g = new THREE.Group();
  const mat = toonMat(theme.block);
  const bodyH = s.h * 0.86;
  const body = new THREE.Mesh(boxGeo(s.w, bodyH, s.d), mat);
  body.position.y = bodyH / 2;
  const topH = Math.max(0.05, s.h - bodyH);
  const top = new THREE.Mesh(boxGeo(Math.max(0.1, s.w - 0.14), topH, Math.max(0.1, s.d - 0.14)), mat);
  top.position.y = bodyH + topH / 2;
  g.add(body, top);
  g.position.set(s.x, 0, s.z);
  return shadowOn(g);
}

// broken round stone column — also doubles as tree trunks (garden zone)
export function makeColumn(theme, s) {
  const g = new THREE.Group();
  const mat = toonMat(theme.column);
  const r = s.w / 2;
  const breakT = seed2(s.x, s.z);
  const shaftH = s.h * (0.78 + breakT * 0.1);
  const shaft = new THREE.Mesh(cylGeo(r, r * 1.05, shaftH, 12), mat);
  shaft.position.y = shaftH / 2;
  // subtle vertical flutes read as an engraved column, not a plain drum
  const fluteMat = toonMat(shade(theme.column, -0.06));
  const flutes = 6;
  for (let i = 0; i < flutes; i++) {
    const a = (i / flutes) * Math.PI * 2;
    const flute = new THREE.Mesh(boxGeo(r * 0.16, shaftH * 0.92, 0.03), fluteMat);
    flute.position.set(Math.cos(a) * r * 0.98, shaftH / 2, Math.sin(a) * r * 0.98);
    flute.rotation.y = -a;
    g.add(flute);
  }
  g.add(shaft);
  // jagged broken-top chunks so the silhouette reads "broken", not clean
  const chunkMat = toonMat(shade(theme.column, -0.1));
  const chunks = 3;
  for (let i = 0; i < chunks; i++) {
    const rnd = seed2(s.x + i * 3.17, s.z - i * 2.31);
    const cw = r * (0.55 + rnd * 0.4);
    const ch = s.h * (0.05 + rnd * 0.12);
    const chunk = new THREE.Mesh(boxGeo(cw, ch, cw * 0.85), chunkMat);
    const ang = (i / chunks) * Math.PI * 2 + rnd * 1.7;
    chunk.position.set(Math.cos(ang) * r * 0.3, shaftH + ch / 2 - 0.03, Math.sin(ang) * r * 0.3);
    chunk.rotation.y = rnd * Math.PI * 2;
    chunk.rotation.z = (rnd - 0.5) * 0.6;
    chunk.rotation.x = (seed2(s.z + i, s.x - i) - 0.5) * 0.5;
    g.add(chunk);
  }
  g.position.set(s.x, 0, s.z);
  return shadowOn(g);
}

// wooden barrel — three stacked segments give a bulge, plus two hoop rings
export function makeBarrel(theme, s) {
  const g = new THREE.Group();
  const mat = toonMat(theme.barrel);
  const rMid = s.w / 2;
  const rEnd = rMid * 0.76;
  const segH = s.h / 3;
  const bottom = new THREE.Mesh(cylGeo(rMid, rEnd, segH, 12), mat);
  bottom.position.y = segH / 2;
  const mid = new THREE.Mesh(cylGeo(rMid, rMid, segH, 12), mat);
  mid.position.y = segH * 1.5;
  const top = new THREE.Mesh(cylGeo(rEnd, rMid, segH, 12), mat);
  top.position.y = segH * 2.5;
  g.add(bottom, mid, top);
  const hoopMat = toonMat(shade(theme.barrel, -0.32));
  for (const hy of [segH * 0.58, segH * 2.42]) {
    const hoop = new THREE.Mesh(torusGeo(rMid * 0.985, Math.max(0.02, rMid * 0.08), 6, 16), hoopMat);
    hoop.rotation.x = Math.PI / 2;
    hoop.position.y = hy;
    g.add(hoop);
  }
  g.position.set(s.x, 0, s.z);
  return shadowOn(g);
}

// natural boulder — flat-shaded icosahedron, non-uniform scale + rotation
// seeded from x,z so every boulder looks distinct but stable across reloads
export function makeRock(theme, s) {
  const mesh = new THREE.Mesh(icoGeo(1, 0), rockMat(theme));
  const r1 = seed2(s.x, s.z);
  const r2 = seed2(s.z * 1.7, s.x + 9.13);
  const r3 = seed2(s.x - s.z, s.z + 4.21);
  const rXZ = (s.w + s.d) / 4;
  const rY = s.h / 2;
  mesh.scale.set(rXZ * (0.82 + r1 * 0.42), rY * (0.85 + r2 * 0.35), rXZ * (0.82 + r3 * 0.42));
  mesh.rotation.set(r2 * Math.PI * 2, r1 * Math.PI * 2, r3 * Math.PI * 2);
  mesh.position.set(s.x, rY, s.z);
  return shadowOn(mesh);
}

// fallen log / wood beam — cylinder laid on its side along the long footprint axis
export function makeLog(theme, s) {
  const g = new THREE.Group();
  const mat = toonMat(theme.wood);
  const radius = s.h / 2;
  const horizontal = s.w >= s.d;
  const length = horizontal ? s.w : s.d;
  const body = new THREE.Mesh(cylGeo(radius, radius, length, 10), mat);
  body.rotation.z = Math.PI / 2; // cylinder now runs along local X
  g.add(body);
  const ringMat = toonMat(shade(theme.wood, -0.2));
  for (const t of [-0.34, 0.02, 0.36]) {
    const ring = new THREE.Mesh(torusGeo(radius * 0.99, Math.max(0.02, radius * 0.12), 6, 14), ringMat);
    ring.rotation.y = Math.PI / 2; // ring wraps around local X (the log's axis)
    ring.position.x = length * t;
    g.add(ring);
  }
  g.rotation.y = horizontal ? 0 : Math.PI / 2;
  g.position.set(s.x, radius, s.z);
  return shadowOn(g);
}

// =========================================================================
// Decor builders — each positions itself at world (opts.x, *, opts.z).
// =========================================================================

// stone archway: two legs + a curved top (torus arc)
export function makeArch(theme, opts) {
  const g = new THREE.Group();
  const mat = toonMat(theme.stone);
  const legW = 0.55, legD = 0.55, legH = 2.3, gap = 2.0;
  const legGeo = boxGeo(legW, legH, legD);
  const left = new THREE.Mesh(legGeo, mat);
  left.position.set(-(gap / 2 + legW / 2), legH / 2, 0);
  const right = new THREE.Mesh(legGeo, mat);
  right.position.set(gap / 2 + legW / 2, legH / 2, 0);
  g.add(left, right);
  const archR = gap / 2 + legW / 2 + 0.18;
  const arc = new THREE.Mesh(torusGeo(archR, 0.32, 8, 20, Math.PI), mat);
  arc.position.y = legH;
  g.add(arc);
  g.rotation.y = opts.rot ?? 0;
  g.position.set(opts.x, 0, opts.z);
  return shadowOn(g);
}

// stylized tree: tapered trunk (shares the "column" theme key) + 1-2 canopy blobs
export function makeTree(theme, opts) {
  const g = new THREE.Group();
  const trunk = new THREE.Mesh(cylGeo(0.12, 0.2, 2.2, 8), toonMat(theme.column));
  trunk.position.y = 1.1;
  g.add(trunk);
  const canopyMat = toonMat(theme.foliage);
  const r1 = seed2(opts.x, opts.z);
  const r2 = seed2(opts.z, opts.x + 5.7);
  const blobs = r1 > 0.4 ? 2 : 1;
  for (let i = 0; i < blobs; i++) {
    const rr = seed2(opts.x + i * 3.3, opts.z - i * 2.1);
    const blob = new THREE.Mesh(icoGeo(0.85, 1), canopyMat);
    const ang = rr * Math.PI * 2;
    const off = blobs > 1 ? 0.4 : 0;
    blob.position.set(Math.cos(ang) * off, 2.2 + rr * 0.3, Math.sin(ang) * off);
    blob.scale.setScalar(0.75 + rr * 0.5);
    blob.rotation.y = rr * Math.PI * 2;
    g.add(blob);
  }
  g.scale.setScalar(opts.scale ?? 1);
  g.position.set(opts.x, 0, opts.z);
  return shadowOn(g);
}

// stone garden lantern: short pedestal + a glowing head
export function makeLantern(theme, opts, touch) {
  const g = new THREE.Group();
  const stoneMat = toonMat(theme.stone);
  const base = new THREE.Mesh(cylGeo(0.16, 0.22, 0.9, 8), stoneMat);
  base.position.y = 0.45;
  const cap = new THREE.Mesh(cylGeo(0.26, 0.2, 0.12, 8), stoneMat);
  cap.position.y = 0.96;
  g.add(base, cap);
  shadowOn(g);
  const head = new THREE.Mesh(icoGeo(0.22, 0), basicMat(`lantern:${theme.lantern}`, { color: theme.lantern }));
  head.position.y = 1.2;
  g.add(head);
  if (!touch) {
    const glow = new THREE.PointLight(theme.lantern, 6, 6, 2);
    glow.position.y = 1.2;
    g.add(glow);
  }
  g.position.set(opts.x, 0, opts.z);
  return g;
}

// wooden scaffold frame: 4 corner posts + cross beams + a diagonal brace
export function makeScaffold(theme, opts) {
  const g = new THREE.Group();
  const mat = toonMat(theme.wood);
  const w = opts.w ?? 2.5;
  const h = opts.h ?? 2.4;
  const d = w * 0.8;
  const postGeo = boxGeo(0.1, 1, 0.1);
  for (const [px, pz] of [[-w / 2, -d / 2], [w / 2, -d / 2], [w / 2, d / 2], [-w / 2, d / 2]]) {
    const post = new THREE.Mesh(postGeo, mat);
    post.scale.y = h;
    post.position.set(px, h / 2, pz);
    g.add(post);
  }
  const beamGeoX = boxGeo(w, 0.09, 0.09);
  for (const by of [h * 0.45, h * 0.92]) {
    const front = new THREE.Mesh(beamGeoX, mat);
    front.position.set(0, by, -d / 2);
    const back = new THREE.Mesh(beamGeoX, mat);
    back.position.set(0, by, d / 2);
    g.add(front, back);
  }
  const diagLen = Math.hypot(w, h * 0.5);
  const diag = new THREE.Mesh(boxGeo(0.08, diagLen, 0.08), mat);
  diag.position.set(0, h * 0.35, -d / 2);
  diag.rotation.z = Math.atan2(w, h * 0.5);
  g.add(diag);
  g.rotation.y = opts.rot ?? 0;
  g.position.set(opts.x, 0, opts.z);
  return shadowOn(g);
}

// glowing spawn-pad marker for a powerup location (system isn't built yet —
// this just marks the spot). premium = larger/warm/gold, standard = cool/silver
export function makePowerupPad(theme, opts, touch) {
  const g = new THREE.Group();
  const premium = opts.tier === 'premium';
  const baseR = premium ? 1.5 : 0.9;
  const color = premium ? '#ffcf4d' : '#cfe3f2';
  const ring = new THREE.Mesh(
    ringGeo(baseR * 0.72, baseR, 40),
    basicMat(`pad-ring:${color}`, {
      color, transparent: true, opacity: premium ? 0.85 : 0.55, side: THREE.DoubleSide, depthWrite: false,
    }),
  );
  ring.rotation.x = -Math.PI / 2;
  ring.position.y = 0.03;
  g.add(ring);
  const disc = new THREE.Mesh(
    circleGeo(baseR * 0.68, 32),
    basicMat(`pad-disc:${color}`, {
      color, transparent: true, opacity: premium ? 0.32 : 0.16, depthWrite: false, blending: THREE.AdditiveBlending,
    }),
  );
  disc.rotation.x = -Math.PI / 2;
  disc.position.y = 0.025;
  g.add(disc);
  if (premium) {
    const beam = new THREE.Mesh(
      cylGeo(baseR * 0.85, baseR * 0.18, 2.6, 16, true),
      basicMat('pad-beam', {
        color, transparent: true, opacity: 0.2, side: THREE.DoubleSide, blending: THREE.AdditiveBlending, depthWrite: false,
      }),
    );
    beam.position.y = 1.3;
    g.add(beam);
    const halo = new THREE.Mesh(torusGeo(baseR * 0.8, 0.05, 6, 24), basicMat('pad-halo', { color }));
    halo.rotation.x = Math.PI / 2;
    halo.position.y = 1.7;
    g.add(halo);
    if (!touch) {
      const light = new THREE.PointLight(color, 8, 8, 2);
      light.position.y = 1.0;
      g.add(light);
    }
  }
  g.position.set(opts.x, 0, opts.z);
  return g;
}

// decorative water disc — visual only, walk-through
export function makePond(theme, opts) {
  const mat = stdMat(`water:${theme.water}`, {
    color: theme.water, transparent: true, opacity: 0.7, roughness: 0.25, metalness: 0.35, depthWrite: false,
  });
  const mesh = new THREE.Mesh(circleGeo(opts.r, 40), mat);
  mesh.rotation.x = -Math.PI / 2;
  mesh.position.set(opts.x, 0.04, opts.z);
  return mesh;
}

// large, subtly tinted floor patch marking a themed area (drawn above the
// base floor but below props/solids)
export function makeZone(theme, opts) {
  const mat = basicMat(`zone:${opts.tint}`, { color: opts.tint, transparent: true, opacity: 0.2, depthWrite: false });
  const geo = opts.shape === 'rect' ? planeGeo(opts.w, opts.d) : circleGeo(opts.r, 48);
  const mesh = new THREE.Mesh(geo, mat);
  mesh.rotation.x = -Math.PI / 2;
  mesh.position.set(opts.x, 0.015, opts.z);
  return mesh;
}

// broken cliff-rim marker along the lethal boundary — kept LOW (a warning
// edge, not a wall). style: 'fence' | 'stone' | 'ruin'
export function makeEdge(theme, opts) {
  const g = new THREE.Group();
  const len = opts.len ?? 6;
  if (opts.style === 'fence') {
    const mat = toonMat(theme.wood);
    const postH = 1.1;
    const n = Math.max(3, Math.round(len / 1.8));
    for (let i = 0; i <= n; i++) {
      const rr = seed2(opts.x + i * 2.3, opts.z - i * 1.7);
      const rr2 = seed2(opts.z + i, opts.x - i);
      const post = new THREE.Mesh(boxGeo(0.12, postH, 0.12), mat);
      const off = -len / 2 + (i * len) / n;
      post.position.set(off, postH / 2, 0);
      post.rotation.z = (rr - 0.5) * 0.5; // leaning, weathered
      post.rotation.x = (rr2 - 0.5) * 0.3;
      g.add(post);
    }
    const rail = new THREE.Mesh(boxGeo(len, 0.08, 0.08), mat);
    rail.position.y = postH * 0.82;
    g.add(rail);
  } else if (opts.style === 'stone') {
    const mat = toonMat(theme.stone);
    const n = Math.max(3, Math.round(len / 1.5));
    for (let i = 0; i < n; i++) {
      const rr = seed2(opts.x + i * 4.1, opts.z + i * 2.9);
      const bh = 0.5 + rr * 0.55;
      const chunk = new THREE.Mesh(boxGeo((len / n) * 0.82, bh, 0.5), mat);
      chunk.position.set(-len / 2 + (i + 0.5) * (len / n), bh / 2, 0);
      g.add(chunk);
    }
  } else { // 'ruin'
    const mat = toonMat(theme.stone);
    const n = Math.max(3, Math.round(len / 1.3));
    for (let i = 0; i < n; i++) {
      const rr = seed2(opts.x - i * 3.3, opts.z + i * 5.1);
      if (rr < 0.22) continue; // crumbled away entirely — a gap
      const bh = 0.3 + rr * 0.5;
      const bw = (len / n) * (0.6 + rr * 0.4);
      const chunk = new THREE.Mesh(boxGeo(bw, bh, 0.55), mat);
      chunk.position.set(-len / 2 + (i + 0.5) * (len / n), bh / 2, 0);
      chunk.rotation.y = (rr - 0.5) * 0.6;
      g.add(chunk);
    }
  }
  g.rotation.y = opts.rot ?? 0;
  g.position.set(opts.x, 0, opts.z);
  return shadowOn(g);
}

// =========================================================================
// Instanced helpers — take the FULL list of entries and return one
// InstancedMesh (or null if the list is empty).
// =========================================================================

// small scattered rocks
export function makeRubble(theme, list) {
  if (!list?.length) return null;
  const mesh = new THREE.InstancedMesh(icoGeo(0.22, 0), rockMat(theme), list.length);
  const dummy = new THREE.Object3D();
  for (let i = 0; i < list.length; i++) {
    const r = list[i];
    const r1 = seed2(r.x, r.z);
    const r2 = seed2(r.z, r.x + 4.4);
    dummy.position.set(r.x + (r1 - 0.5) * 0.3, 0.12 + r2 * 0.08, r.z + (r2 - 0.5) * 0.3);
    dummy.rotation.set(r1 * Math.PI, r2 * Math.PI * 2, r1 * r2 * Math.PI);
    const sc = 0.7 + r1 * 0.6;
    dummy.scale.set(sc, sc * (0.7 + r2 * 0.4), sc);
    dummy.updateMatrix();
    mesh.setMatrixAt(i, dummy.matrix);
  }
  mesh.instanceMatrix.needsUpdate = true;
  mesh.castShadow = false; // small decor — skip shadow casting for perf
  mesh.receiveShadow = true;
  return mesh;
}

const FLOWER_HUES = ['#ff5d7a', '#ffd23f', '#7ee081', '#8ecbff', '#c893ff', '#ff9f4d'];

// vibrant flower cluster — a tiny cone bloom per instance, bright varied colors
export function makeFlowers(theme, list) {
  if (!list?.length) return null;
  const mesh = new THREE.InstancedMesh(coneGeo(0.1, 0.24, 6), basicMat('flowers', { color: '#ffffff' }), list.length);
  const dummy = new THREE.Object3D();
  const color = new THREE.Color();
  for (let i = 0; i < list.length; i++) {
    const f = list[i];
    const r1 = seed2(f.x, f.z);
    const r2 = seed2(f.z, f.x + 2.2);
    dummy.position.set(f.x + (r1 - 0.5) * 0.4, 0.12, f.z + (r2 - 0.5) * 0.4);
    dummy.rotation.y = r1 * Math.PI * 2;
    dummy.scale.setScalar(0.8 + r2 * 0.5);
    dummy.updateMatrix();
    mesh.setMatrixAt(i, dummy.matrix);
    color.set(FLOWER_HUES[Math.floor(r1 * FLOWER_HUES.length) % FLOWER_HUES.length]);
    mesh.setColorAt(i, color);
  }
  mesh.instanceMatrix.needsUpdate = true;
  if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
  mesh.castShadow = false;
  return mesh;
}

// flat stone discs just above the floor
export function makeSteppingStones(theme, list) {
  if (!list?.length) return null;
  const mesh = new THREE.InstancedMesh(cylGeo(0.55, 0.6, 0.09, 14), toonMat(theme.stone), list.length);
  const dummy = new THREE.Object3D();
  for (let i = 0; i < list.length; i++) {
    const p = list[i];
    dummy.position.set(p.x, 0.05, p.z);
    dummy.rotation.y = seed2(p.x, p.z) * Math.PI * 2;
    dummy.updateMatrix();
    mesh.setMatrixAt(i, dummy.matrix);
  }
  mesh.instanceMatrix.needsUpdate = true;
  mesh.receiveShadow = true;
  return mesh;
}

// =========================================================================
// Distant low-detail background scenery (the cloud sea).
// =========================================================================

// a big soft "sea of clouds" disc, plus a scattered belt of puffy cloud blobs
export function makeCloudSea(theme, opts) {
  const g = new THREE.Group();
  const y = opts.cloudSeaY ?? -6;
  const seaMat = stdMat(`cloudsea:${theme.cloud}`, { color: theme.cloud, roughness: 1, metalness: 0 });
  const sea = new THREE.Mesh(circleGeo(130, 48), seaMat);
  sea.rotation.x = -Math.PI / 2;
  sea.position.y = y;
  g.add(sea);

  const count = 46;
  const blobMat = stdMat(`cloudblob:${theme.cloud}`, { color: theme.cloud, roughness: 1 });
  const blobs = new THREE.InstancedMesh(icoGeo(1, 1), blobMat, count);
  const dummy = new THREE.Object3D();
  for (let i = 0; i < count; i++) {
    const ang = (i / count) * Math.PI * 2 + hash(i * 3.71) * 0.5;
    const rad = 26 + hash(i * 5.13) * 88;
    dummy.position.set(Math.cos(ang) * rad, y + (hash(i * 2.11) - 0.3) * 5, Math.sin(ang) * rad);
    const sc = 3 + hash(i * 7.77) * 6;
    dummy.scale.set(sc, sc * 0.55, sc);
    dummy.rotation.y = hash(i * 9.33) * Math.PI * 2;
    dummy.updateMatrix();
    blobs.setMatrixAt(i, dummy.matrix);
  }
  blobs.instanceMatrix.needsUpdate = true;
  blobs.castShadow = false;
  blobs.receiveShadow = false;
  g.add(blobs);
  return g;
}

// small floating-island silhouette: a squashed rock top + a tapered underside
export function makeFloatingIsland(theme, opts) {
  const g = new THREE.Group();
  const top = new THREE.Mesh(icoGeo(2.2, 1), rockMat(theme));
  top.scale.y = 0.5;
  g.add(top);
  const under = new THREE.Mesh(
    coneGeo(1.9, 2.6, 7),
    stdMat(`islandunder:${theme.rock}`, { color: shade(theme.rock, -0.28), flatShading: true, roughness: 0.95 }),
  );
  under.rotation.x = Math.PI; // flip so the point tapers downward
  under.position.y = -1.6;
  g.add(under);
  g.scale.setScalar(opts.scale ?? 1);
  g.position.set(opts.x, opts.y, opts.z);
  return g;
}

// distant hazy mountain cone
export function makePeak(theme, opts) {
  const mesh = new THREE.Mesh(coneGeo(3, 7, 7), rockMat(theme));
  const s = opts.scale ?? 1;
  const tall = 0.85 + seed2(opts.x, opts.z) * 0.35;
  mesh.scale.set(s, s * tall, s);
  mesh.position.set(opts.x, opts.y + (7 * s * tall) / 2, opts.z);
  return mesh;
}

// thin vertical translucent plane falling from an island edge into the clouds
export function makeWaterfall(theme, opts) {
  const mat = basicMat(`waterfall:${theme.water}`, {
    color: theme.water, transparent: true, opacity: 0.45, side: THREE.DoubleSide,
    blending: THREE.AdditiveBlending, depthWrite: false,
  });
  const mesh = new THREE.Mesh(planeGeo(1.6, opts.h), mat);
  mesh.position.set(opts.x, opts.y + opts.h / 2, opts.z);
  return mesh;
}
