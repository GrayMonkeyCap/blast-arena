// Character models + animation. Bombsquad-style bobbleheads: big round head,
// stubby body, expressive eyes. Everything is built from primitives at
// runtime — no asset downloads — and styled with a 3-step toon ramp.
//
// Cosmetics: buildHat() is the extension point — each hat id from
// content/cosmetics.js gets a small mesh group mounted on the head. Skins
// tint head + hands. Team is communicated by jersey, headband and feet.

import * as THREE from 'three';
import { TEAMS } from '../core/config.js';
import { clamp, lerp } from '../core/math.js';

let gradTex = null;
export function toonGradient() {
  if (!gradTex) {
    gradTex = new THREE.DataTexture(
      new Uint8Array([90, 90, 90, 255, 170, 170, 170, 255, 255, 255, 255, 255]),
      3, 1, THREE.RGBAFormat,
    );
    gradTex.needsUpdate = true;
    gradTex.magFilter = THREE.NearestFilter;
    gradTex.minFilter = THREE.NearestFilter;
  }
  return gradTex;
}

const matCache = new Map();
export function toonMat(color, extra = {}) {
  const key = color + JSON.stringify(extra);
  if (!matCache.has(key)) {
    matCache.set(key, new THREE.MeshToonMaterial({ color, gradientMap: toonGradient(), ...extra }));
  }
  return matCache.get(key);
}

function makeNameSprite(name, colorHex) {
  const c = document.createElement('canvas');
  c.width = 256; c.height = 64;
  const g = c.getContext('2d');
  g.font = '700 34px system-ui, sans-serif';
  g.textAlign = 'center';
  g.textBaseline = 'middle';
  g.lineWidth = 7;
  g.strokeStyle = 'rgba(10,12,18,0.9)';
  g.strokeText(name, 128, 34);
  g.fillStyle = colorHex;
  g.fillText(name, 128, 34);
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false }));
  sprite.scale.set(2.1, 0.52, 1);
  return sprite;
}

// --- cosmetic hats -----------------------------------------------------
export function buildHat(id) {
  const h = new THREE.Group();
  const gold = toonMat('#ffcf4d', { emissive: '#4d3200' });
  switch (id) {
    case 'cap': {
      const m = toonMat('#2e6b4f');
      const dome = new THREE.Mesh(new THREE.SphereGeometry(0.34, 16, 10, 0, Math.PI * 2, 0, Math.PI / 2), m);
      dome.scale.y = 0.62;
      const brim = new THREE.Mesh(new THREE.BoxGeometry(0.34, 0.05, 0.3), m);
      brim.position.set(0, 0.0, 0.36);
      h.add(dome, brim);
      break;
    }
    case 'tophat': {
      const m = toonMat('#191922');
      const tube = new THREE.Mesh(new THREE.CylinderGeometry(0.26, 0.26, 0.44, 14), m);
      tube.position.y = 0.24;
      const brim = new THREE.Mesh(new THREE.CylinderGeometry(0.44, 0.44, 0.05, 18), m);
      const band = new THREE.Mesh(new THREE.CylinderGeometry(0.27, 0.27, 0.09, 14), toonMat('#c0392b'));
      band.position.y = 0.08;
      h.add(tube, brim, band);
      break;
    }
    case 'crown': {
      const ring = new THREE.Mesh(new THREE.CylinderGeometry(0.3, 0.33, 0.18, 12), gold);
      ring.position.y = 0.06;
      h.add(ring);
      for (let i = 0; i < 5; i++) {
        const spike = new THREE.Mesh(new THREE.ConeGeometry(0.06, 0.16, 6), gold);
        const a = (i / 5) * Math.PI * 2;
        spike.position.set(Math.sin(a) * 0.28, 0.2, Math.cos(a) * 0.28);
        h.add(spike);
      }
      break;
    }
    case 'halo': {
      const halo = new THREE.Mesh(
        new THREE.TorusGeometry(0.3, 0.045, 10, 24),
        new THREE.MeshBasicMaterial({ color: '#ffe27a' }),
      );
      halo.rotation.x = Math.PI / 2;
      halo.position.y = 0.34;
      halo.userData.float = true; // bobbed in update()
      h.add(halo);
      break;
    }
    case 'horns': {
      const m = toonMat('#d63b2f');
      for (const s of [-1, 1]) {
        const horn = new THREE.Mesh(new THREE.ConeGeometry(0.09, 0.3, 8), m);
        horn.position.set(0.26 * s, 0.1, 0);
        horn.rotation.z = -0.5 * s;
        h.add(horn);
      }
      break;
    }
    case 'chef': {
      const m = toonMat('#f4f2ec');
      const base = new THREE.Mesh(new THREE.CylinderGeometry(0.28, 0.3, 0.24, 14), m);
      base.position.y = 0.08;
      const puff = new THREE.Mesh(new THREE.SphereGeometry(0.32, 14, 10), m);
      puff.scale.set(1, 0.7, 1);
      puff.position.y = 0.28;
      h.add(base, puff);
      break;
    }
    default:
      return null;
  }
  h.traverse((o) => { if (o.isMesh) o.castShadow = true; });
  return h;
}

// --- the character ------------------------------------------------------
export class CharacterView {
  constructor(scene, p, isMe) {
    const team = TEAMS[p.team];
    this.team = p.team;
    this.scene = scene;
    this.phase = Math.random() * 10;
    this.blinkAt = 2 + Math.random() * 3;
    this.blinkT = 0;
    this.lie = 0; // 0 upright .. 1 flat on back (KO)
    this.time = 0;

    const jersey = toonMat(team.color);
    const jerseyDark = toonMat(team.dark);
    const skin = toonMat(p.cos?.skin || '#ffd29c');
    const white = toonMat('#ffffff');
    const black = new THREE.MeshBasicMaterial({ color: '#14161c' });

    this.group = new THREE.Group();
    this.rig = new THREE.Group(); // yaw (facing)
    this.pose = new THREE.Group(); // pitch/tumble (running lean, KO flop)
    this.group.add(this.rig);
    this.rig.add(this.pose);

    // torso
    const torso = new THREE.Mesh(new THREE.CapsuleGeometry(0.34, 0.34, 6, 14), jersey);
    torso.position.y = 0.62;
    // head
    const head = new THREE.Mesh(new THREE.SphereGeometry(0.42, 20, 16), skin);
    head.position.y = 1.3;
    head.scale.y = 0.95;
    // team headband
    const band = new THREE.Mesh(new THREE.TorusGeometry(0.4, 0.075, 8, 22), jersey);
    band.rotation.x = Math.PI / 2 - 0.18;
    band.position.y = 1.42;
    this.pose.add(torso, head, band);

    // face
    this.eyes = [];
    this.pupils = [];
    this.xeyes = [];
    for (const s of [-1, 1]) {
      const eye = new THREE.Mesh(new THREE.SphereGeometry(0.105, 12, 10), white);
      eye.position.set(0.15 * s, 1.36, 0.34);
      const pupil = new THREE.Mesh(new THREE.SphereGeometry(0.048, 8, 8), black);
      pupil.position.set(0.15 * s, 1.36, 0.43);
      // knocked-out X eyes
      const xg = new THREE.Group();
      for (const r of [0.8, -0.8]) {
        const bar = new THREE.Mesh(new THREE.BoxGeometry(0.16, 0.035, 0.02), black);
        bar.rotation.z = r;
        xg.add(bar);
      }
      xg.position.set(0.15 * s, 1.36, 0.42);
      xg.visible = false;
      this.pose.add(eye, pupil, xg);
      this.eyes.push(eye);
      this.pupils.push(pupil);
      this.xeyes.push(xg);
    }
    const brow = new THREE.Mesh(new THREE.BoxGeometry(0.34, 0.045, 0.03), black);
    brow.position.set(0, 1.52, 0.36);
    const mouth = new THREE.Mesh(new THREE.SphereGeometry(0.06, 8, 6), black);
    mouth.scale.set(1.4, 0.55, 0.5);
    mouth.position.set(0, 1.16, 0.4);
    this.pose.add(brow, mouth);

    // limbs (group pivots at joint, mesh hangs below)
    const limb = (r, len, mat, handMat, handR) => {
      const g = new THREE.Group();
      const seg = new THREE.Mesh(new THREE.CapsuleGeometry(r, len, 4, 10), mat);
      seg.position.y = -(len / 2 + r);
      const tip = new THREE.Mesh(new THREE.SphereGeometry(handR, 10, 8), handMat);
      tip.position.y = -(len + r * 2);
      g.add(seg, tip);
      return g;
    };
    this.armL = limb(0.105, 0.3, jersey, skin, 0.13);
    this.armR = limb(0.105, 0.3, jersey, skin, 0.13);
    this.armL.position.set(-0.42, 0.95, 0);
    this.armR.position.set(0.42, 0.95, 0);
    this.legL = limb(0.12, 0.22, jerseyDark, jerseyDark, 0.15);
    this.legR = limb(0.12, 0.22, jerseyDark, jerseyDark, 0.15);
    this.legL.position.set(-0.17, 0.42, 0);
    this.legR.position.set(0.17, 0.42, 0);
    this.pose.add(this.armL, this.armR, this.legL, this.legR);

    // cosmetic hat
    const hat = buildHat(p.cos?.hat);
    if (hat) {
      hat.position.y = 1.62;
      this.hat = hat;
      this.pose.add(hat);
    }

    // name tag + local-player ground marker
    this.name = makeNameSprite(p.name, team.color);
    this.name.position.y = 2.2;
    this.group.add(this.name);
    if (isMe) {
      const ring = new THREE.Mesh(
        new THREE.RingGeometry(0.62, 0.78, 28),
        new THREE.MeshBasicMaterial({ color: '#ffffff', transparent: true, opacity: 0.5, depthWrite: false }),
      );
      ring.rotation.x = -Math.PI / 2;
      ring.position.y = 0.03;
      this.group.add(ring);
    }

    this.pose.traverse((o) => { if (o.isMesh) o.castShadow = true; });
    scene.add(this.group);
  }

  update(p, dt) {
    this.time += dt;
    const g = this.group;
    g.position.set(p.x, p.y, p.z);
    this.rig.rotation.y = p.face;

    const ko = p.state === 'ko';
    // knocked out cold (alive, unconscious): tumbles mid-air, lies flat on
    // the ground until it wears off — BombSquad's knockout ragdoll
    const knocked = !ko && (p.knockT ?? 0) > 0;
    this.lie = clamp(this.lie + (ko || (knocked && p.y <= 0.05) ? 5 : -8) * dt, 0, 1);
    for (const x of this.xeyes) x.visible = ko;
    for (const e of this.eyes) e.visible = !ko;
    for (const pu of this.pupils) pu.visible = !ko;

    if ((ko || knocked) && p.y > 0.05) {
      this.pose.rotation.x -= 8 * dt; // tumbling through the air
    } else {
      const run = knocked ? 0 : clamp(p.spd / 6.8, 0, 1.2);
      this.phase += dt * (4 + p.spd * 2.1);
      const airborne = p.y > 0.05;
      const sw = Math.sin(this.phase) * 0.95 * run;
      // legs tuck mid-jump instead of cycling
      this.legL.rotation.x = airborne ? 0.55 : sw;
      this.legR.rotation.x = airborne ? -0.25 : -sw;
      const armSw = -sw * 0.8;
      this.armL.rotation.x = armSw;
      this.armR.rotation.x = -armSw;
      // anything grabbed — flag, bomb, or a whole player — is hoisted
      // overhead with BOTH hands (BombSquad carry)
      const holding = p.carryFlag || p.heldBomb || p.heldPlayer;
      if (holding) {
        this.armL.rotation.x = -2.75;
        this.armR.rotation.x = -2.75;
      }
      if (p.throwT > 0) {
        // big hurl: windup -> release over 0.35s (both arms if carrying)
        const t = 1 - p.throwT / 0.35;
        const swing = t < 0.4 ? lerp(0, -2.6, t / 0.4) : lerp(-2.6, 0.9, (t - 0.4) / 0.6);
        this.armR.rotation.x = swing;
        if (holding) this.armL.rotation.x = swing;
      }
      if ((p.punchT ?? 0) > 0) {
        // alternating jabs: right, left, right, left...
        const arm = p.punchArm ? this.armL : this.armR;
        const t = 1 - p.punchT / 0.3;
        arm.rotation.x = t < 0.3 ? lerp(0, -1.9, t / 0.3) : lerp(-1.9, 0.7, (t - 0.3) / 0.7);
      }
      // hoisted in someone's grip: dangle and flail until you fight back
      if (p.heldBy && p.y > 0.6) {
        this.legL.rotation.x = Math.sin(this.time * 16) * 0.8;
        this.legR.rotation.x = -Math.sin(this.time * 16) * 0.8;
        if (!(p.punchT > 0)) {
          this.armL.rotation.x = -0.5 + Math.sin(this.time * 13) * 0.4;
          this.armR.rotation.x = -0.5 - Math.sin(this.time * 13) * 0.4;
        }
      }
      // upright <-> flat-on-back blend + running lean, jump-arc pitch & bob
      const airPitch = airborne ? clamp(-p.vy * 0.045, -0.4, 0.5) : 0;
      this.pose.rotation.x = lerp(0.16 * run + airPitch, -Math.PI / 2 + 0.12, this.lie);
      this.pose.position.y = lerp(Math.abs(Math.sin(this.phase)) * 0.09 * run, 0.28, this.lie);
    }
    // out cold on the ground: a faint dazed wriggle as they come to
    this.pose.rotation.z = knocked && p.y <= 0.05 ? Math.sin(this.time * 20) * 0.12 : 0;

    // blink (eyes stay shut while knocked out)
    this.blinkT -= dt;
    this.blinkAt -= dt;
    if (this.blinkAt <= 0) {
      this.blinkAt = 1.8 + Math.random() * 3;
      this.blinkT = 0.12;
    }
    const eyeY = knocked || this.blinkT > 0 ? 0.15 : 1;
    for (const e of this.eyes) e.scale.y = eyeY;

    // hat flourishes (halo floats)
    if (this.hat) {
      for (const c of this.hat.children) {
        if (c.userData.float) c.position.y = 0.34 + Math.sin(this.time * 3) * 0.04;
      }
    }

    // spawn-protection flicker
    g.visible = p.invuln > 0.05 ? Math.floor(this.time * 12) % 2 === 0 : true;
  }

  dispose() {
    this.name.material.map?.dispose();
    this.name.material.dispose();
    this.scene.remove(this.group);
  }
}
