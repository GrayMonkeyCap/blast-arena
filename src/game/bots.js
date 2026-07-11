// Bot AI for two-flag CTF. Bots consume the exact same input shape as
// humans — the sim can't tell them apart. Objectives, in priority order:
//   carrying the enemy flag -> run it home (loiter there if we can't score
//   because our own flag is out)
//   our flag is being carried -> hunt the thief
//   our flag is dropped -> nearest teammate goes to touch-return it
//   otherwise -> nearest teammate to the enemy flag goes stealing, the
//   rest brawl (bombs at range, punches up close)

import { clamp, norm2, circlePushOut } from '../core/math.js';
import { otherTeam } from '../core/config.js';

const ZERO = { mx: 0, mz: 0, ax: 0, az: 0, ad: 7, throw: false, grab: false, punch: false, jump: false };

const nearest = (list, to) => {
  let best = null;
  let bd = Infinity;
  for (const p of list) {
    const d = Math.hypot(p.x - to.x, p.z - to.z);
    if (d < bd) { bd = d; best = p; }
  }
  return best;
};

function blockedAhead(level, me, dir, probe) {
  const px = me.x + dir.x * probe;
  const pz = me.z + dir.z * probe;
  for (const box of level.solids) {
    if (box.h < 0.5) continue;
    if (circlePushOut(px, pz, 0.55, box)) return true;
  }
  return false;
}

const rot = (d, a) => {
  const c = Math.cos(a);
  const s = Math.sin(a);
  return { x: d.x * c - d.z * s, z: d.x * s + d.z * c };
};

export function createBotBrain(id, rng = Math.random) {
  return {
    id,
    aimErr: 0.08 + rng() * 0.14,
    aggro: 0.6 + rng() * 0.5,
    strafe: rng() < 0.5 ? -1 : 1,
    cool: 1 + rng() * 2,

    think(sim, dt) {
      const s = sim.state;
      const me = s.players.find((p) => p.id === this.id);
      if (!me || me.state !== 'alive' || s.phase !== 'play') return ZERO;
      this.cool = Math.max(0, this.cool - dt);

      const enemies = s.players.filter((p) => p.team !== me.team && p.state === 'alive');
      const mates = s.players.filter((p) => p.team === me.team && p.state === 'alive');
      const myFlag = s.flags[me.team];
      const enemyFlag = s.flags[otherTeam(me.team)];
      const myBase = sim.level.bases[me.team];

      // --- objective selection
      let target = null;
      let throwAt = null;
      let wantGrabFlag = false;
      if (me.carryFlag) {
        target = myBase; // run it home (and hold there if our flag is out)
      } else if (myFlag.st === 'carry') {
        const thief = s.players.find((p) => p.id === myFlag.carrier);
        target = thief ?? myFlag;
        throwAt = thief;
      } else if (myFlag.st === 'drop' && nearest(mates, myFlag) === me) {
        target = myFlag; // touch it to return it
      } else if (enemyFlag.st !== 'carry' && nearest(mates, enemyFlag) === me) {
        target = enemyFlag; // go steal
        wantGrabFlag = true;
      } else if (enemyFlag.st === 'carry') {
        // escort our carrier by harassing whoever is closest to them
        const carrier = s.players.find((p) => p.id === enemyFlag.carrier);
        const e = carrier ? nearest(enemies, carrier) : nearest(enemies, me);
        target = e ?? myBase;
        throwAt = e;
      } else {
        const e = nearest(enemies, me);
        target = e ?? myBase;
        throwAt = e;
      }
      if (!throwAt) {
        const e = nearest(enemies, me);
        if (e && Math.hypot(e.x - me.x, e.z - me.z) < 10.5) throwAt = e;
      }

      // --- steering
      let dir = norm2(target.x - me.x, target.z - me.z);
      // flinch away from bombs about to pop — deliberately late/imperfect,
      // a bot that always escapes the blast radius is no fun to fight
      for (const b of s.bombs) {
        if (b.holder || b.fuse > 0.6) continue;
        const dx = me.x - b.x;
        const dz = me.z - b.z;
        const d = Math.hypot(dx, dz);
        if (d < sim.config.bomb.blastRadius && d > 0.01) {
          dir.x += (dx / d) * 2.2;
          dir.z += (dz / d) * 2.2;
        }
      }
      dir = norm2(dir.x, dir.z);
      if (blockedAhead(sim.level, me, dir, 1.7)) {
        let side = rot(dir, this.strafe * 0.95);
        if (blockedAhead(sim.level, me, side, 1.7)) {
          this.strafe *= -1;
          side = rot(dir, this.strafe * 0.95);
        }
        dir = side;
      }
      // never charge off the rim
      const hw = sim.level.bounds.w / 2 - 1.3;
      const hd = sim.level.bounds.d / 2 - 1.3;
      const ahead = { x: clamp(me.x + dir.x * 2, -hw, hw), z: clamp(me.z + dir.z * 2, -hd, hd) };
      dir = norm2(ahead.x - me.x, ahead.z - me.z);

      const input = { mx: dir.x, mz: dir.z, ax: 0, az: 0, ad: 7, throw: false, grab: false, punch: false, jump: false };

      // --- melee: momentum punch when an enemy is in fist range
      if (me.punchCd <= 0) {
        const e = nearest(enemies, me);
        if (e && Math.hypot(e.x - me.x, e.z - me.z) < 1.55) {
          const a = norm2(e.x - me.x, e.z - me.z);
          input.ax = a.x;
          input.az = a.z;
          input.punch = true;
        }
      }

      // --- bombing: aim where the target will be when the bomb DETONATES
      // (fuse keeps burning after landing), not where they are now
      if (!input.punch && throwAt && !me.carryFlag && me.throwCd <= 0 && this.cool <= 0) {
        const leadT = sim.config.bomb.fuse * 0.8;
        const px = clamp(throwAt.x + (throwAt.vx || 0) * leadT, -hw, hw);
        const pz = clamp(throwAt.z + (throwAt.vz || 0) * leadT, -hd, hd);
        const tx = px - me.x;
        const tz = pz - me.z;
        const dd = Math.hypot(tx, tz);
        if (dd > 2.2 && dd < sim.config.bomb.maxRange) {
          const a = Math.atan2(tx, tz) + (Math.random() * 2 - 1) * this.aimErr;
          input.ax = Math.sin(a);
          input.az = Math.cos(a);
          input.ad = dd;
          input.throw = true;
          this.cool = 0.8 + (Math.random() * 1.6) / this.aggro;
        }
      }

      // steal needs a deliberate grab near the enemy flag
      if (wantGrabFlag && Math.hypot(enemyFlag.x - me.x, enemyFlag.z - me.z) < 1.4) {
        input.grab = true;
      }
      return input;
    },
  };
}
