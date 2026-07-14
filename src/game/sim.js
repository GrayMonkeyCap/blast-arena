// The authoritative game simulation. Pure JS, fixed-timestep, no DOM and no
// three.js — the browser runs it directly for solo play and the Node server
// runs the identical code for online play. All gameplay randomness lives
// here (never in the renderer).
//
// The mechanics mirror BombSquad's open-source engine (see
// docs/bombsquad-parity.md for the value-by-value derivation):
//   - roller-ball locomotion: walk, then a run "gear" that winds up with
//     speed; no steering mid-air; gentle skid stops
//   - punches ride body momentum: standing jabs tickle, sprint punches take
//     ~40% and knock people out cold
//   - a single hard hit = KNOCKOUT (unconscious ragdoll, wakes with hp)
//   - ANY damage drops whatever you're holding
//   - the bomb button pulls out a LIT bomb held overhead (fuse burns in
//     hand); pressing again throws it — running throws go far
//   - blasts kick every body equally (mass-normalized) and pop them upward
//   - impact damage: wall slams, hard landings, flying-body collisions
//
// State shape (everything JSON-serializable — this IS the network snapshot):
//   players[]: { id, name, team, bot, cos, x, z, y, vx, vz, vy, face, hp,
//                state('alive'|'ko'), respawn, invuln, knockT,
//                carryFlag('red'|'blue'|null), heldBomb, heldPlayer, heldBy,
//                heldT, throwT, punchCd, punchT, jumpCd, impactCd, gearSpd,
//                koT, hurtT, spd }
//   bombs[]:   { id, x, z, y, vx, vz, vy, fuse, holder, owner }
//   flags:     owned by the active mode (see modes/ctf.js)
//
// Game modes plug in via an object with hooks:
//   { id, init(sim), tick(sim, dt), onKO(sim, p),
//     tryGrab?(sim, p) -> bool          — grab button, before bombs/players
//     dropCarried?(sim, p, vx, vz)      — carrier lost their flag
//     throwCarried?(sim, p, dir, vel)   — throw with {vx, vz, vy}
//     onExplosion?(sim, x, z, radius)   — push mode objects (flags) around
//     onPunchObject?(sim, x, z, r, dir, vFist, invFist)

import { clamp, norm2, angleLerp } from '../core/math.js';
import {
  integrateBody, collideBodies, applyImpulse, blastKick, invMass,
} from './physics.js';

const HIT_CREDIT = 5; // seconds a recent attacker stays eligible for the kill credit

const EMPTY_INPUT = { mx: 0, mz: 0, ax: 0, az: 0, ad: 7, run: 0, throw: false, grab: false, punch: false, jump: false };

export function createSim({ level, mode, config }) {
  const sim = {
    level,
    mode,
    config,
    // physics world params + materials, precomputed for the hot path
    world: {
      gravity: config.world.gravity,
      maxSpeed: config.physics.maxSpeed,
      sleepSpeed: config.physics.sleepSpeed,
    },
    mats: config.physics.materials,
    state: {
      tick: 0,
      phase: 'countdown', // countdown -> play -> over -> (auto reset)
      countdown: config.rules.countdown,
      timeLeft: config.rules.roundTime,
      overT: 0,
      scores: { red: 0, blue: 0 },
      winner: null,
      players: [],
      bombs: [],
      flags: null,
    },
    events: [], // transient per-tick events, drained by the host
    nextId: 1,
    prevIn: new Map(), // last button state per player (edge detection)
    punchHits: new Map(), // per-swing hit sets (one hit per target per swing)
    spawnIdx: { red: 0, blue: 0 },
  };
  mode.init(sim);
  return sim;
}

export const emit = (sim, ev) => sim.events.push(ev);

export const overFloor = (level, x, z) =>
  Math.abs(x) <= level.bounds.w / 2 && Math.abs(z) <= level.bounds.d / 2;

export function addPlayer(sim, { name, team, bot = false, cos }) {
  const id = 'p' + sim.nextId++;
  const p = {
    id, name, team, bot, cos,
    x: 0, z: 0, y: 0, vx: 0, vz: 0, vy: 0,
    face: team === 'red' ? Math.PI / 2 : -Math.PI / 2, // face the enemy base
    hp: sim.config.player.hp,
    state: 'alive', respawn: 0, invuln: sim.config.player.invulnTime,
    knockT: 0,
    lastHitBy: null, lastHitByT: 0,
    carryFlag: null, heldBomb: null, heldPlayer: null, heldBy: null,
    heldT: 9, throwT: 0, punchCd: 0, punchT: 0, punchArm: 0,
    jumpCd: 0, impactCd: 0, gearSpd: 0, koT: 0, hurtT: 0, spd: 0,
  };
  placeAtSpawn(sim, p);
  sim.state.players.push(p);
  emit(sim, { t: 'spawn', id, team, x: p.x, z: p.z });
  return id;
}

export function removePlayer(sim, id) {
  const p = sim.state.players.find((p) => p.id === id);
  if (!p) return;
  breakGrabs(sim, p, 0, 0);
  sim.prevIn.delete(id);
  sim.punchHits.delete(id);
  sim.state.players = sim.state.players.filter((p) => p.id !== id);
  emit(sim, { t: 'leave', id, name: p.name });
}

function placeAtSpawn(sim, p) {
  const list = sim.level.spawns[p.team];
  const s = list[sim.spawnIdx[p.team]++ % list.length];
  p.x = s.x; p.z = s.z; p.y = 0;
  p.vx = 0; p.vz = 0; p.vy = 0;
  p.face = p.team === 'red' ? Math.PI / 2 : -Math.PI / 2;
}

const getP = (sim, id) => sim.state.players.find((p) => p.id === id);

// ---------------------------------------------------------------- main step

export function step(sim, inputs, dt) {
  const s = sim.state;
  s.tick++;

  if (s.phase === 'countdown') {
    const before = Math.ceil(s.countdown);
    s.countdown -= dt;
    const after = Math.ceil(s.countdown);
    if (after !== before && after > 0) emit(sim, { t: 'tick', n: after });
    if (s.countdown <= 0) {
      s.phase = 'play';
      emit(sim, { t: 'go' });
    }
  } else if (s.phase === 'play') {
    s.timeLeft = Math.max(0, s.timeLeft - dt);
    if (s.timeLeft <= 0) {
      const { red, blue } = s.scores;
      endRound(sim, red === blue ? 'draw' : red > blue ? 'red' : 'blue');
    }
  } else if (s.phase === 'over') {
    s.overT -= dt;
    if (s.overT <= 0) resetRound(sim);
  }

  const frozen = s.phase !== 'play';
  for (const p of s.players) {
    updatePlayer(sim, p, inputs.get(p.id) ?? EMPTY_INPUT, dt, frozen);
  }
  applyGrabs(sim, dt);
  playerCollisions(sim, s.players);
  updateBombs(sim, dt);
  sim.mode.tick(sim, dt);
}

// ------------------------------------------------------------------ players

function updatePlayer(sim, p, i, dt, frozen) {
  const cfg = sim.config.player;
  p.throwT = Math.max(0, p.throwT - dt);
  p.punchCd = Math.max(0, p.punchCd - dt);
  p.punchT = Math.max(0, p.punchT - dt);
  p.jumpCd = Math.max(0, p.jumpCd - dt);
  p.impactCd = Math.max(0, p.impactCd - dt);
  p.invuln = Math.max(0, p.invuln - dt);
  p.hurtT = Math.max(0, p.hurtT - dt);
  p.lastHitByT = Math.max(0, p.lastHitByT - dt);
  if (p.lastHitByT <= 0) p.lastHitBy = null;
  p.heldT += dt;

  const onGround = p.y <= 0.001;
  // knockout wears off at full rate on the ground, half rate airborne
  // (BombSquad decrements every 5 steps grounded, 10 airborne)
  p.knockT = Math.max(0, p.knockT - dt * (onGround ? 1 : 0.5));
  if (p.knockT > 0) p.punchT = 0; // a knockout cancels a mid-swing punch

  if (p.state === 'ko') {
    p.koT += dt;
    // limp body keeps sliding where the blast sent it; Coulomb ground
    // friction (μ·g) brings it to rest naturally
    integrateBody(sim.level, sim.world, p, sim.mats.player, dt, { wallE: 0 });
    p.spd = 0;
    p.respawn -= dt;
    if (p.respawn <= 0 && sim.state.phase !== 'over') respawnPlayer(sim, p);
    return;
  }

  // (no hp regen — BombSquad damage is permanent until you respawn)

  // --- locomotion: BombSquad's roller-ball model. Stick magnitude walks;
  // the run gear engages as smoothed speed builds (slow spool-up, quick
  // drop), motor force is finite (wide turns at speed), releasing the stick
  // is a gentle braked skid, and mid-air there is NO steering at all.
  const m = norm2(i.mx || 0, i.mz || 0);
  const mlen = Math.min(1, Math.hypot(i.mx || 0, i.mz || 0));
  const run = clamp(i.run ?? 1, 0, 1);

  const spd2 = Math.hypot(p.vx, p.vz);
  const sm = spd2 > p.gearSpd ? cfg.gearUp : cfg.gearDown;
  p.gearSpd = sm * p.gearSpd + (1 - sm) * spd2;
  const gear = Math.min(1, p.gearSpd / cfg.gearSpeed);

  const mutualGrapple = p.heldBy && p.heldPlayer === p.heldBy;
  let ctrl = onGround ? 1 : cfg.airControl;
  if (p.heldBy && !mutualGrapple) ctrl = 0; // hoisted overhead: a passenger
  if (p.knockT > 0 || frozen) ctrl = 0; // out cold / round frozen

  if (mlen > 0.01 && ctrl > 0) {
    const target = mlen * (cfg.walkSpeed + gear * run * (cfg.runSpeed - cfg.walkSpeed));
    const a = cfg.accel * ctrl * dt;
    p.vx += clamp(m.x * target - p.vx, -a, a);
    p.vz += clamp(m.z * target - p.vz, -a, a);
  } else if (ctrl > 0) {
    const a = cfg.brakeDecel * ctrl * dt;
    p.vx += clamp(-p.vx, -a, a);
    p.vz += clamp(-p.vz, -a, a);
  }

  // while control is active, "muscles" own horizontal speed — physics
  // ground friction only takes over when balance is lost (knockout/airborne)
  const out = integrateBody(sim.level, sim.world, p, sim.mats.player, dt, {
    wallE: 0, // characters don't bounce off walls, they slide along them
    noGroundFriction: ctrl > 0.5,
  });

  // impact damage (BombSquad head-jolt model): wall slams and hard landings
  if (p.impactCd <= 0) {
    const icfg = cfg.impact;
    let dmg = 0;
    if (out.wallImpact >= icfg.wallMinDv) dmg = Math.max(dmg, (out.wallImpact - icfg.wallMinDv) * icfg.dmgPerDv);
    if (out.floorImpact >= icfg.floorMinDv) dmg = Math.max(dmg, (out.floorImpact - icfg.floorMinDv) * icfg.dmgPerDv);
    if (dmg >= 1) impactDamage(sim, p, dmg);
  }
  if (p.state !== 'alive') return; // a lethal impact can end the update here

  if (p.y < sim.config.world.fallY) {
    koPlayer(sim, p, 'fall');
    return;
  }

  // face where you run (throws/punches snap facing to the aim; mobile
  // aim-stick sets i.aiming so you strafe while lining up a shot)
  let fx = 0, fz = 0;
  if (i.aiming && (i.ax || i.az)) { fx = i.ax; fz = i.az; }
  else if (mlen > 0.05) { fx = m.x; fz = m.z; }
  if ((fx || fz) && p.knockT <= 0) p.face = angleLerp(p.face, Math.atan2(fx, fz), Math.min(1, 14 * dt));
  p.spd = Math.hypot(p.vx, p.vz);

  // live fist: the swing is a moving collider that connects mid-arc
  if (p.punchT > 0 && !p.heldBy) resolvePunch(sim, p);

  if (frozen) return;

  const prev = sim.prevIn.get(p.id) ?? EMPTY_INPUT;
  const throwEdge = i.throw && !prev.throw;
  const grabEdge = i.grab && !prev.grab;
  const punchEdge = i.punch && !prev.punch;
  const jumpEdge = i.jump && !prev.jump;
  sim.prevIn.set(p.id, { throw: !!i.throw, grab: !!i.grab, punch: !!i.punch, jump: !!i.jump });

  if (p.knockT > 0) return; // out cold: no actions

  if (jumpEdge && onGround && p.jumpCd <= 0) {
    p.jumpCd = cfg.jumpCooldown;
    p.vy = cfg.jumpVel;
    p.y = 0.02; // leave the floor; momentum is preserved
    emit(sim, { t: 'jump', id: p.id, x: p.x, z: p.z });
  }
  if (punchEdge) doPunch(sim, p, i);
  if (throwEdge) doThrow(sim, p, i);
  if (grabEdge) doGrab(sim, p, i);
}

// Player-vs-player: impulse collisions (equal masses exchange momentum).
// e=0 between bodies that are just bumping; the interesting case is a
// launched body slamming into someone — BombSquad deals IMPACT damage from
// the jolt (and any damage makes both drop whatever they held). Yes, a
// thrown player is a weapon.
function playerCollisions(sim, players) {
  const mat = sim.mats.player;
  const icfg = sim.config.player.impact;
  for (let i = 0; i < players.length; i++) {
    const a = players[i];
    if (a.state !== 'alive') continue;
    for (let k = i + 1; k < players.length; k++) {
      const b = players[k];
      if (b.state !== 'alive') continue;
      // grab pairs are SUPPOSED to be touching
      if (a.heldPlayer === b.id || b.heldPlayer === a.id) continue;
      const j = collideBodies(a, mat, b, mat, { e: 0 });
      if (j <= 0) continue;
      const dv = j * invMass(mat); // per-body velocity jolt
      if (dv < icfg.pairMinDv) continue;
      const dmg = (dv - icfg.pairMinDv) * icfg.dmgPerDv;
      if (dmg < 1) continue;
      for (const p of [a, b]) {
        if (p.impactCd <= 0) impactDamage(sim, p, dmg);
      }
      emit(sim, { t: 'bodySlam', x: a.x, z: a.z, j: Math.round(dv) });
    }
  }
}

// Grab constraints, two regimes:
//   ONE-WAY: the victim is hoisted OVERHEAD like a carried item. Until they
//     react they simply ride along — but they can punch down at the grabber
//     (any damage forces a drop) or grab back to start a grapple.
//   MUTUAL: both players grounded in a wrestling lock, holding each other
//     with equal strength — the pair moves by the AVERAGE of both players'
//     steering, so movement is a genuine tug-of-war.
function applyGrabs(sim, dt) {
  const done = new Set();
  for (const holder of sim.state.players) {
    if (!holder.heldPlayer || done.has(holder.id)) continue;
    const t = getP(sim, holder.heldPlayer);
    if (!t || t.state !== 'alive' || holder.state !== 'alive') {
      releasePlayer(sim, holder);
      continue;
    }
    if (t.heldPlayer === holder.id) {
      // mutual grapple lock
      done.add(holder.id);
      done.add(t.id);
      // equal strength: the pair moves by the average of both muscles.
      // Shuffle friction bleeds off inherited momentum so an opposed
      // stalemate actually stalls instead of coasting.
      const damp = Math.exp(-2.5 * dt);
      const avx = ((holder.vx + t.vx) / 2) * damp;
      const avz = ((holder.vz + t.vz) / 2) * damp;
      holder.vx = t.vx = avx;
      holder.vz = t.vz = avz;
      holder.y = 0; t.y = 0;
      holder.vy = 0; t.vy = 0;
      // pin at grapple distance, face to face
      const dx = t.x - holder.x;
      const dz = t.z - holder.z;
      const d = Math.hypot(dx, dz) || 1;
      const nx = dx / d;
      const nz = dz / d;
      const midX = (holder.x + t.x) / 2;
      const midZ = (holder.z + t.z) / 2;
      const half = 0.62;
      holder.x = midX - nx * half; holder.z = midZ - nz * half;
      t.x = midX + nx * half; t.z = midZ + nz * half;
      holder.face = Math.atan2(nx, nz);
      t.face = Math.atan2(-nx, -nz);
    } else {
      // hoisted overhead: rides the grabber like a carried item
      t.x = holder.x + Math.sin(holder.face) * 0.12;
      t.z = holder.z + Math.cos(holder.face) * 0.12;
      t.y = holder.y + 2.05;
      t.vx = holder.vx; t.vz = holder.vz; t.vy = 0;
    }
  }
}

function releasePlayer(sim, holder) {
  const t = holder.heldPlayer ? getP(sim, holder.heldPlayer) : null;
  if (t && t.heldBy === holder.id) t.heldBy = null;
  holder.heldPlayer = null;
}

// Anything in your hands (flag, bomb, player) comes loose. BombSquad rule:
// called on KO and on ANY damage — the flag then flies as a free physics
// object with your momentum.
export function breakGrabs(sim, p, vx = 0, vz = 0) {
  const hadGrip = !!(p.carryFlag || p.heldBomb || p.heldPlayer || p.heldBy);
  if (p.carryFlag) sim.mode.dropCarried?.(sim, p, vx, vz);
  if (p.heldBomb) {
    const b = sim.state.bombs.find((b) => b.id === p.heldBomb);
    if (b) b.holder = null;
    p.heldBomb = null;
  }
  if (p.heldPlayer) releasePlayer(sim, p);
  if (p.heldBy) {
    const holder = getP(sim, p.heldBy);
    if (holder) releasePlayer(sim, holder);
    p.heldBy = null;
  }
  if (hadGrip) emit(sim, { t: 'gripBreak', id: p.id });
}

// ------------------------------------------------------------------ actions

// Punch, the BombSquad way: pressing punch starts a SWING; the fist is a
// live collider for ~0.3s that tracks your body, and damage rides on how
// fast the body is moving when it connects (plus a timing curve that peaks
// mid-swing). Standing jab ≈ 4hp. Sprint punch ≈ 40hp — enough to knock the
// target out cold and send them flying. You can't swing while holding
// something; a held victim can still pummel their grabber.
function doPunch(sim, p, i) {
  const cfg = sim.config.punch;
  if (p.punchCd > 0) return;

  // held in someone's grip (overhead or grapple): hammer on the grabber —
  // bodies co-move so there's no momentum, just chip damage... but ANY
  // damage forces a drop, so one clean pummel breaks you free.
  if (p.heldBy) {
    p.punchCd = cfg.cooldown;
    p.punchT = cfg.swingTime;
    p.punchArm = p.punchArm ? 0 : 1;
    const holder = getP(sim, p.heldBy);
    emit(sim, { t: 'punch', id: p.id, x: p.x, z: p.z });
    if (holder && holder.state === 'alive') {
      const dmg = cfg.dmgBase * 1.5;
      damagePlayer(sim, holder, dmg, 0, 0, 0, 'punch', p.id);
      emit(sim, { t: 'punchHit', id: p.id, target: holder.id, x: holder.x, z: holder.z, dmg: Math.round(dmg) });
    }
    return;
  }

  // BombSquad: no swinging while you're holding something
  if (p.carryFlag || p.heldBomb || p.heldPlayer) return;

  p.punchCd = cfg.cooldown;
  p.punchT = cfg.swingTime;
  p.punchArm = p.punchArm ? 0 : 1; // alternate fists: right, left, right...
  sim.punchHits.set(p.id, new Set());
  const aim = norm2(i.ax || 0, i.az || 0);
  if (aim.len > 0.01) p.face = Math.atan2(aim.x, aim.z);
  emit(sim, {
    t: 'punch', id: p.id,
    x: p.x + Math.sin(p.face) * cfg.range,
    z: p.z + Math.cos(p.face) * cfg.range,
  });
}

// Resolve fist contacts each tick of the active swing window. One hit per
// target per swing; the timing factor peaks mid-swing (BombSquad
// punch_power: 0.7 -> 1.0 -> 0.7 over 200ms).
function resolvePunch(sim, p) {
  const cfg = sim.config.punch;
  const age = cfg.swingTime - p.punchT;
  if (age < cfg.windowStart || age > cfg.windowEnd) return;
  const hits = sim.punchHits.get(p.id);
  if (!hits) return;

  const dir = { x: Math.sin(p.face), z: Math.cos(p.face) };
  const fx = p.x + dir.x * cfg.range;
  const fz = p.z + dir.z * cfg.range;
  const tNorm = Math.min(1, age / 0.2);
  const timing = 0.7 + 0.3 * (0.5 + 0.5 * Math.sin(tNorm * 2 * Math.PI - Math.PI / 2));
  const v3 = Math.hypot(p.vx, p.vz, p.vy); // jumps count toward momentum
  const matP = sim.mats.player;

  for (const o of sim.state.players) {
    if (o === p || o.state !== 'alive' || hits.has(o.id)) continue;
    if (Math.abs(o.y - p.y) > 1.3) continue;
    if (Math.hypot(o.x - fx, o.z - fz) > cfg.fistRadius + matP.radius) continue;
    hits.add(o.id);
    // damage rides on body momentum — friendly fire is real in BombSquad
    const dmg = Math.min(cfg.dmgCap, (cfg.dmgBase + cfg.dmgPerSpeed * v3) * timing);
    // knockback follows the swing, blended with a radial shove, and pops up
    const rad = norm2(o.x - p.x, o.z - p.z);
    const kx = dir.x * 0.7 + rad.x * 0.3;
    const kz = dir.z * 0.7 + rad.z * 0.3;
    const dv = dmg * cfg.kbPerDmg;
    damagePlayer(sim, o, dmg, kx * dv, kz * dv, dv * cfg.liftFrac, 'punch', p.id);
    emit(sim, { t: 'punchHit', id: p.id, target: o.id, x: o.x, z: o.z, dmg: Math.round(dmg) });
    if (hits.size === 1) {
      // recoil on the first contact only (BombSquad kick_back)
      p.vx -= dir.x * cfg.selfKick;
      p.vz -= dir.z * cfg.selfKick;
    }
  }

  // smack loose objects with the fist-as-impulse model (light props fly —
  // punches shove bombs but do NOT detonate them)
  const vFist = cfg.swingSpeed + Math.hypot(p.vx, p.vz);
  const invFist = 1 / cfg.fistMass;
  for (const b of sim.state.bombs) {
    if (b.holder || hits.has(b.id)) continue;
    if (Math.hypot(b.x - fx, b.z - fz) > cfg.fistRadius + sim.mats.bomb.radius + 0.15) continue;
    hits.add(b.id);
    const j = ((1 + cfg.restitution) * vFist) / (invFist + invMass(sim.mats.bomb));
    applyImpulse(b, sim.mats.bomb, dir.x * j, dir.z * j, j * 0.25);
  }
  sim.mode.onPunchObject?.(sim, fx, fz, cfg.fistRadius + 0.45, dir, vFist, invFist);
}

// Throw button, the BombSquad bomb button: with something in hand it hurls
// it; empty-handed it pulls out a LIT bomb held overhead. The fuse starts
// immediately and keeps burning in your hands — carry it too long and it
// takes you with it. Only one live bomb per player until yours goes off.
function doThrow(sim, p, i) {
  if (p.carryFlag || p.heldBomb || p.heldPlayer) {
    throwHeld(sim, p, i);
    return;
  }
  const cfg = sim.config.bomb;
  let live = 0;
  for (const b of sim.state.bombs) if (b.owner === p.id) live++;
  if (live >= cfg.perPlayer) return;
  const id = 'b' + sim.nextId++;
  sim.state.bombs.push({
    id,
    x: p.x + Math.sin(p.face) * 0.15,
    z: p.z + Math.cos(p.face) * 0.15,
    y: p.y + 2.05,
    vx: p.vx, vz: p.vz, vy: 0,
    fuse: cfg.fuse,
    holder: p.id,
    owner: p.id,
  });
  p.heldBomb = id;
  p.heldT = 0;
  emit(sim, { t: 'bombOut', id: p.id, x: p.x, z: p.z });
}

// The universal throw — BombSquad hurls whatever you hold (flag, bomb,
// player) with a fixed ~45° lob. Power comes from your aim magnitude,
// momentum inheritance is FULL (running throws sail), and objects thrown
// right after pickup fly weaker (the just-picked-up penalty).
function throwHeld(sim, p, i) {
  const cfg = sim.config.throw;
  const bcfg = sim.config.bomb;
  const aim = norm2(i.ax || 0, i.az || 0);
  const dir = aim.len > 0.01 ? aim : { x: Math.sin(p.face), z: Math.cos(p.face) };
  const pf = clamp(((i.ad ?? bcfg.aimRangeMax) - bcfg.aimRangeMin) / (bcfg.aimRangeMax - bcfg.aimRangeMin), 0, 1);
  let s = cfg.speedMin + (cfg.speedMax - cfg.speedMin) * pf;
  if (p.heldT < cfg.quickWindow) {
    s *= cfg.quickMin + (1 - cfg.quickMin) * (p.heldT / cfg.quickWindow);
  }
  const sh = Math.cos(cfg.pitch) * s;
  const sv = Math.sin(cfg.pitch) * s;

  if (p.carryFlag) {
    sim.mode.throwCarried?.(sim, p, dir, {
      vx: dir.x * sh + p.vx * cfg.inherit,
      vz: dir.z * sh + p.vz * cfg.inherit,
      vy: sv,
    });
  } else if (p.heldPlayer) {
    const t = getP(sim, p.heldPlayer);
    // throwing out of a mutual grapple breaks BOTH grips
    if (t && t.heldPlayer === p.id) releasePlayer(sim, t);
    releasePlayer(sim, p);
    if (t) {
      t.vx = dir.x * sh * cfg.playerMult + p.vx * cfg.inherit;
      t.vz = dir.z * sh * cfg.playerMult + p.vz * cfg.inherit;
      t.vy = Math.max(t.vy, 0) + sv * cfg.playerMult + 1.5;
      t.y = Math.max(t.y, 0.05);
      t.knockT = Math.max(t.knockT, 0.5); // tumbles through the air
      emit(sim, { t: 'playerThrow', id: p.id, target: t.id, x: t.x, z: t.z });
    }
  } else if (p.heldBomb) {
    const b = sim.state.bombs.find((b) => b.id === p.heldBomb);
    p.heldBomb = null;
    if (b) {
      b.holder = null;
      b.x = p.x + dir.x * 0.6;
      b.z = p.z + dir.z * 0.6;
      b.y = p.y + 1.6;
      b.vx = dir.x * sh + p.vx * cfg.inherit;
      b.vz = dir.z * sh + p.vz * cfg.inherit;
      b.vy = sv;
    }
    emit(sim, { t: 'throw', id: p.id, x: p.x, z: p.z });
  }

  // thrower recoil (BombSquad kick_back on throws)
  p.vx -= dir.x * cfg.kickback;
  p.vz -= dir.z * cfg.kickback;
  p.face = Math.atan2(dir.x, dir.z);
  p.throwT = 0.35;
}

// Grab, the BombSquad pickup button: with something in hand it THROWS it
// (both buttons throw — there is one universal throw). Empty-handed: mode
// objects first (steal the enemy flag), then loose bombs, then other
// players. A player being held can grab their own holder back, turning the
// carry into a mutual grapple. Spawn-protected players can't be grabbed.
function doGrab(sim, p, i) {
  const cfg = sim.config;
  if (p.carryFlag || p.heldBomb || p.heldPlayer) {
    throwHeld(sim, p, i);
    return;
  }

  if (sim.mode.tryGrab?.(sim, p)) {
    p.heldT = 0;
    return;
  }

  let bestBomb = null;
  let bd = cfg.player.grabRange;
  for (const b of sim.state.bombs) {
    if (b.holder) continue;
    const d = Math.hypot(p.x - b.x, p.z - b.z);
    if (d < bd) { bd = d; bestBomb = b; }
  }
  if (bestBomb) {
    bestBomb.holder = p.id;
    p.heldBomb = bestBomb.id;
    p.heldT = 0;
    emit(sim, { t: 'grabBomb', id: p.id, x: p.x, z: p.z });
    return;
  }

  // grab another player (they stay live: they punch back and get thrown)
  let bestP = null;
  let pd = cfg.grab.playerRange;
  for (const o of sim.state.players) {
    if (o === p || o.state !== 'alive') continue;
    if (o.invuln > 0) continue; // can't grab spawn-protected players
    const counterGrab = o.id === p.heldBy; // reaching down at your holder
    if (o.heldBy && !(o.heldBy === p.id)) continue; // already in another grip
    if (!counterGrab && Math.abs(o.y - p.y) > 1.2) continue;
    const d = Math.hypot(p.x - o.x, p.z - o.z);
    if (d < pd) { pd = d; bestP = o; }
  }
  if (bestP) {
    p.heldPlayer = bestP.id;
    bestP.heldBy = p.id;
    p.heldT = 0;
    emit(sim, { t: 'grabPlayer', id: p.id, target: bestP.id, x: p.x, z: p.z, mutual: bestP.heldPlayer === p.id });
  }
}

// -------------------------------------------------------------------- bombs

function updateBombs(sim, dt) {
  const s = sim.state;
  const matB = sim.mats.bomb;
  const matP = sim.mats.player;
  for (let i = s.bombs.length - 1; i >= 0; i--) {
    const b = s.bombs[i];
    b.fuse -= dt; // the fuse burns whether held or flying

    if (b.holder) {
      const p = getP(sim, b.holder);
      if (!p || p.state !== 'alive') {
        b.holder = null;
      } else {
        // hoisted overhead with both hands (BombSquad carry)
        b.x = p.x + Math.sin(p.face) * 0.15;
        b.z = p.z + Math.cos(p.face) * 0.15;
        b.y = p.y + 2.05;
        b.vx = p.vx; b.vz = p.vz; b.vy = 0;
      }
    }

    if (!b.holder) {
      const out = integrateBody(sim.level, sim.world, b, matB, dt, { restY: matB.radius });
      if (out.bounced && out.impact > 3) emit(sim, { t: 'bounce', x: b.x, z: b.z });

      // bomb <-> player contact: real impulse exchange. Walking into a
      // resting bomb KICKS it ahead of you; a thrown bomb bonks whoever it
      // hits and caroms off (mass ratio does the work).
      for (const p of s.players) {
        if (p.state !== 'alive') continue;
        const j = collideBodies(b, matB, p, matP, { maxYGap: 1.4 });
        if (j > 2.5) emit(sim, { t: 'bounce', x: b.x, z: b.z });
      }

      if (b.y < sim.config.world.fallY) {
        s.bombs.splice(i, 1); // lost to the void (owner may pull a new one)
        continue;
      }
    }

    if (b.fuse <= 0) {
      s.bombs.splice(i, 1);
      explode(sim, b);
    }
  }
}

function explode(sim, b) {
  const cfg = sim.config.bomb;
  const s = sim.state;
  if (b.holder) {
    const p = getP(sim, b.holder);
    if (p) p.heldBomb = null;
  }
  emit(sim, { t: 'explode', x: b.x, z: b.z, y: Math.max(0, b.y) });

  // players: linear-falloff damage to ZERO at the edge, point-blank is
  // lethal; the velocity kick is the same for every body (mass-normalized)
  // with the vertical component exaggerated — blasts pop people up and out.
  // Friendly fire is real: your own and your teammates' bombs hurt.
  for (const p of s.players) {
    if (p.state !== 'alive') continue;
    const dx = p.x - b.x;
    const dz = p.z - b.z;
    const d = Math.hypot(dx, dz);
    if (d >= cfg.blastRadius || Math.abs(p.y + 0.8 - b.y) > 3) continue;
    const t = 1 - d / cfg.blastRadius;
    let nx = dx / (d || 1);
    let nz = dz / (d || 1);
    if (d < 0.01) {
      const a = Math.random() * Math.PI * 2;
      nx = Math.sin(a); nz = Math.cos(a);
    }
    damagePlayer(
      sim, p,
      cfg.maxDamage * t,
      nx * cfg.blastDvXZ * t,
      nz * cfg.blastDvXZ * t,
      cfg.blastDvY * t,
      'bomb',
      b.owner ?? null,
    );
  }

  // bombs caught in the blast get kicked and cook off 0.1–0.2s later —
  // chain reactions (held bombs chain too, but ride their holder)
  for (const ob of s.bombs) {
    const d = Math.hypot(ob.x - b.x, ob.z - b.z);
    if (d >= cfg.blastRadius) continue;
    if (!ob.holder) blastKick(ob, b.x, b.z, cfg.blastRadius, cfg.blastDvXZ, cfg.blastDvY * 0.6);
    ob.fuse = Math.min(ob.fuse, cfg.chainFuseMin + Math.random() * (cfg.chainFuseMax - cfg.chainFuseMin));
  }

  // let the mode blast its own objects (flags) around
  sim.mode.onExplosion?.(sim, b.x, b.z, cfg.blastRadius);
}

// All damage funnels through here with a velocity kick (Δv). BombSquad
// rules: ANY damage drops whatever the target is holding, and a single hit
// past the knockout threshold puts them out cold — an unconscious ragdoll
// that wakes up with its remaining hp.
export function damagePlayer(sim, p, dmg, dvx, dvz, dvy, cause, by = null) {
  if (p.state !== 'alive' || p.invuln > 0) return;
  // credit a real hit to its source (never self; env/self impacts pass by=null
  // and must PRESERVE whoever last hit us, so a shove-off-the-edge gets credited)
  if (by && by !== p.id) { p.lastHitBy = by; p.lastHitByT = HIT_CREDIT; }
  p.hp -= dmg;
  p.hurtT = 1.0; // brief hit-flash window (no regen — damage is permanent)
  p.vx += dvx;
  p.vz += dvz;
  if (dvy) {
    p.vy = Math.max(p.vy, 0) + dvy;
    p.y = Math.max(p.y, 0.02);
  }
  if (dmg > 0) breakGrabs(sim, p, p.vx, p.vz);
  const k = sim.config.player.knockout;
  const units = Math.min(k.maxUnits, dmg * k.unitsPerDamage - k.baseUnits);
  if (units >= 1 && p.hp > 0) {
    const t = units / k.unitsPerSec;
    if (t > p.knockT) {
      p.knockT = t;
      emit(sim, { t: 'knockout', id: p.id, x: p.x, z: p.z });
    }
  }
  if (dmg > 0) emit(sim, { t: 'hurt', id: p.id, hp: Math.max(0, Math.round(p.hp)) });
  if (p.hp <= 0) koPlayer(sim, p, cause);
}

// Impact damage with BombSquad's mercy rule: if an ordinary impact would
// kill, it's reduced to max(dmg − mercyReduce, hp − 1) — big enough hits
// still finish the job.
function impactDamage(sim, p, dmg) {
  const icfg = sim.config.player.impact;
  p.impactCd = icfg.cooldown;
  if (dmg >= p.hp) dmg = Math.max(dmg - icfg.mercyReduce, p.hp - 1);
  if (dmg < 1) return;
  damagePlayer(sim, p, dmg, 0, 0, 0, 'impact');
  emit(sim, { t: 'impact', id: p.id, x: p.x, z: p.z, dmg: Math.round(dmg) });
}

function koPlayer(sim, p, cause) {
  if (p.state === 'ko') return;
  p.state = 'ko';
  p.hp = 0;
  p.koT = 0;
  p.knockT = 0;
  p.respawn = sim.config.player.respawnTime;
  breakGrabs(sim, p, p.vx, p.vz);
  sim.mode.onKO?.(sim, p);
  emit(sim, { t: 'ko', id: p.id, name: p.name, team: p.team, cause, x: p.x, z: p.z });
}

function respawnPlayer(sim, p) {
  placeAtSpawn(sim, p);
  p.state = 'alive';
  p.hp = sim.config.player.hp;
  p.invuln = sim.config.player.invulnTime;
  p.carryFlag = null;
  p.knockT = 0;
  p.gearSpd = 0;
  p.heldT = 9;
  p.impactCd = 0;
  p.jumpCd = 0;
  p.koT = 0;
  p.lastHitBy = null;
  p.lastHitByT = 0;
  emit(sim, { t: 'spawn', id: p.id, team: p.team, x: p.x, z: p.z });
}

// -------------------------------------------------------------- round flow

export function endRound(sim, winner) {
  const s = sim.state;
  if (s.phase === 'over') return;
  s.phase = 'over';
  s.winner = winner;
  s.overT = sim.config.rules.overTime;
  emit(sim, { t: 'roundOver', winner, scores: { ...s.scores } });
}

export function resetRound(sim) {
  const s = sim.state;
  s.phase = 'countdown';
  s.countdown = sim.config.rules.countdown;
  s.timeLeft = sim.config.rules.roundTime;
  s.scores = { red: 0, blue: 0 };
  s.winner = null;
  s.bombs = [];
  for (const p of s.players) {
    p.state = 'alive';
    p.hp = sim.config.player.hp;
    p.carryFlag = null;
    p.heldBomb = null;
    p.heldPlayer = null;
    p.heldBy = null;
    p.knockT = 0;
    p.gearSpd = 0;
    p.heldT = 9;
    p.throwT = 0; p.punchCd = 0; p.punchT = 0; p.hurtT = 0;
    p.jumpCd = 0; p.impactCd = 0;
    p.lastHitBy = null; p.lastHitByT = 0;
    p.invuln = sim.config.player.invulnTime;
    placeAtSpawn(sim, p);
  }
  sim.mode.init(sim);
  emit(sim, { t: 'newRound' });
  emit(sim, { t: 'tick', n: Math.ceil(s.countdown) });
}
