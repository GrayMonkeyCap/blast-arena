// World: keeps the visual scene in sync with a view-state (either the local
// sim's state or an interpolated network snapshot — same shape either way),
// and turns sim events into effects. The renderer never reaches into game
// logic; this is the only bridge.

import * as THREE from 'three';
import { buildLevel } from './level.js';
import { CharacterView } from './characters.js';
import { BombView } from './bombs.js';
import { FlagView } from './flag.js';
import { Effects } from './effects.js';
import { TEAMS } from '../core/config.js';

export class World {
  constructor(scene, level, { touch }) {
    this.scene = scene;
    this.level = level;
    this.levelGroup = buildLevel(scene, level, { touch });
    this.effects = new Effects(scene);
    this.chars = new Map();
    this.bombViews = new Map();
    this.flagViews = {
      red: new FlagView(scene, level, 'red'),
      blue: new FlagView(scene, level, 'blue'),
    };
    this.time = 0;
    this.trailAcc = 0;

    // aim reticle for the local player
    this.reticle = new THREE.Group();
    const ring = new THREE.Mesh(
      new THREE.RingGeometry(0.34, 0.46, 24),
      new THREE.MeshBasicMaterial({ color: '#ffffff', transparent: true, opacity: 0.65, depthWrite: false }),
    );
    ring.rotation.x = -Math.PI / 2;
    const dot = new THREE.Mesh(
      new THREE.CircleGeometry(0.08, 12),
      new THREE.MeshBasicMaterial({ color: '#ffffff', transparent: true, opacity: 0.65, depthWrite: false }),
    );
    dot.rotation.x = -Math.PI / 2;
    dot.position.y = 0.005;
    this.reticle.add(ring, dot);
    this.reticle.position.y = 0.05;
    this.reticle.visible = false;
    scene.add(this.reticle);
  }

  setAim(point, visible) {
    this.reticle.visible = visible;
    if (point) this.reticle.position.set(point.x, 0.05, point.z);
  }

  sync(view, dt, myId) {
    this.time += dt;

    const seen = new Set();
    for (const p of view.players) {
      seen.add(p.id);
      let cv = this.chars.get(p.id);
      if (!cv) {
        cv = new CharacterView(this.scene, p, p.id === myId);
        this.chars.set(p.id, cv);
      }
      cv.update(p, dt);
    }
    for (const [id, cv] of this.chars) {
      if (!seen.has(id)) {
        cv.dispose();
        this.chars.delete(id);
      }
    }

    const seenB = new Set();
    for (const b of view.bombs) {
      seenB.add(b.id);
      let bv = this.bombViews.get(b.id);
      if (!bv) {
        bv = new BombView(this.scene);
        this.bombViews.set(b.id, bv);
      }
      bv.update(b, dt, this.time);
    }
    for (const [id, bv] of this.bombViews) {
      if (!seenB.has(id)) {
        bv.dispose();
        this.bombViews.delete(id);
      }
    }

    if (view.flags) {
      this.flagViews.red.setVisible(true);
      this.flagViews.blue.setVisible(true);
      this.trailAcc += dt;
      const spark = this.trailAcc > 0.09;
      if (spark) this.trailAcc = 0;
      for (const team of ['red', 'blue']) {
        const f = view.flags[team];
        this.flagViews[team].update(f, dt, this.time);
        if (spark && f && (f.st === 'carry' || (f.st === 'drop' && f.y > 0.3))) {
          this.effects.sparkle(f.x, f.y, f.z);
        }
      }
    } else {
      this.flagViews.red.setVisible(false);
      this.flagViews.blue.setVisible(false);
    }

    this.effects.update(dt);
  }

  // Turn sim events into visuals. myPos scales the camera shake by distance.
  handleEvents(events, myPos) {
    for (const ev of events) {
      switch (ev.t) {
        case 'explode': {
          this.effects.explosion(ev.x, ev.y, ev.z);
          if (myPos) {
            const d = Math.hypot(ev.x - myPos.x, ev.z - myPos.z);
            this.effects.addShake(Math.max(0, 0.55 - d * 0.02));
          }
          break;
        }
        case 'score': {
          const base = this.level.bases[ev.team];
          this.effects.confetti(base.x, base.z, TEAMS[ev.team].color);
          break;
        }
        case 'flagReturn': {
          const stand = this.level.flags[ev.team];
          if (stand) this.effects.poof(stand.x, stand.z, TEAMS[ev.team].glow);
          break;
        }
        case 'punchHit': {
          this.effects.poof(ev.x, ev.z, '#fff3c4');
          if (myPos) {
            const d = Math.hypot(ev.x - myPos.x, ev.z - myPos.z);
            this.effects.addShake(Math.max(0, 0.3 - d * 0.03));
          }
          break;
        }
        case 'spawn':
          this.effects.poof(ev.x, ev.z);
          break;
        case 'ko':
          this.effects.poof(ev.x, ev.z, '#ff9d8a');
          break;
      }
    }
  }

  get shake() {
    return this.effects.shakeAmp;
  }
}
