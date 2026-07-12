// The authoritative game simulation. Pure JS, fixed-timestep, no DOM and no
// three.js — the browser runs it directly for solo play and the Node server
// runs the identical code for online play. All gameplay randomness lives
// here (never in the renderer).
//
// BombSquad-style momentum model: running builds kinetic energy that feeds
// punches, throws and knockback. Hard impulses break grabs and stagger
// (stumble) whoever they hit — an arcade approximation of BombSquad's
// active-ragdoll balance loss.
//
// State shape (everything JSON-serializable — this IS the network snapshot):
//   players[]: { id, name, team, bot, cos, x, z, y, vx, vz, vy, face, hp,
//                state('alive'|'ko'), respawn, invuln, stumbleT,
//                carryFlag('red'|'blue'|null), heldBomb, heldPlayer, heldBy,
//                throwCd, throwT, punchCd, punchT, koT, hurtT, spd }
//   bombs[]:   { id, x, z, y, vx, vz, vy, fuse, holder }
//   flags:     owned by the active mode (see modes/ctf.js)
//
// Game modes plug in via an object with hooks:
//   { id, init(sim), tick(sim, dt), onKO(sim, p),
//     tryGrab?(sim, p) -> bool          — grab button, before bombs/players
//     dropCarried?(sim, p, vx, vz)      — carrier lost their flag
//     throwCarried?(sim, p, dir, power) — throw button while carrying
//     onExplosion?(sim, x, z, radius)   — push mode objects (flags) around

import { clamp, norm2, angleLerp } from '../core/math.js';
import {
  integrateBody, collideBodies, applyImpulse, blastImpulse, invMass,
} from './physics.js';

const EMPTY_INPUT = { mx: 0, mz: 0, ax: 0, az: 0, ad: 7, throw: false, grab: false, punch: false, jump: false };

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
    stumbleT: 0,
    carryFlag: null, heldBomb: null, heldPlayer: null, heldBy: null,
    throwCd: 0, throwT: 0, punchCd: 0, punchT: 0, punchArm: 0, koT: 0, hurtT: 0, spd: 0,
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
  p.throwCd = Math.max(0, p.throwCd - dt);
  p.throwT = Math.max(0, p.throwT - dt);
  p.punchCd = Math.max(0, p.punchCd - dt);
  p.punchT = Math.max(0, p.punchT - dt);
  p.invuln = Math.max(0, p.invuln - dt);
  p.hurtT = Math.max(0, p.hurtT - dt);
  p.stumbleT = Math.max(0, p.stumbleT - dt);

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

  if (p.hurtT <= 0 && p.hp < cfg.hp) p.hp = Math.min(cfg.hp, p.hp + cfg.regen * dt);

  // steering: full authority on the ground, reduced mid-air, a wiggle while
  // held by someone, and none at all while stumbling (balance lost)
  const onGround = p.y <= 0.001;
  const m = norm2(i.mx || 0, i.mz || 0);
  const mlen = Math.min(1, Math.hypot(i.mx || 0, i.mz || 0));
  let speed = cfg.speed;
  if (p.carryFlag) speed *= cfg.carrySpeedMult;
  if (p.heldPlayer) speed *= sim.config.grab.holderSpeedMult;
  // grapple control: hoisted overhead you're a passenger; in a MUTUAL
  // grapple both wrestle at full strength (the pair moves by the average)
  const mutualGrapple = p.heldBy && p.heldPlayer === p.heldBy;
  let ctrl = onGround ? 1 : cfg.airControl;
  if (p.heldBy && !mutualGrapple) ctrl *= 0.15;
  if (p.stumbleT > 0 || frozen) ctrl = 0;
  const acc = (mlen > 0.01 ? cfg.accel : cfg.friction) * ctrl;
  p.vx += clamp(m.x * mlen * speed - p.vx, -acc * dt, acc * dt);
  p.vz += clamp(m.z * mlen * speed - p.vz, -acc * dt, acc * dt);

  // while control is active, "muscles" own horizontal speed — physics
  // ground friction only takes over when balance is lost (stumble/airborne)
  integrateBody(sim.level, sim.world, p, sim.mats.player, dt, {
    wallE: 0, // characters don't bounce off walls, they slide along them
    noGroundFriction: ctrl > 0.5,
  });

  if (p.y < sim.config.world.fallY) {
    koPlayer(sim, p, 'fall');
    return;
  }

  // face where you run (throws/punches snap facing to the aim; mobile
  // aim-stick sets i.aiming so you strafe while lining up a shot)
  let fx = 0, fz = 0;
  if (i.aiming && (i.ax || i.az)) { fx = i.ax; fz = i.az; }
  else if (mlen > 0.05) { fx = m.x; fz = m.z; }
  if ((fx || fz) && p.stumbleT <= 0) p.face = angleLerp(p.face, Math.atan2(fx, fz), Math.min(1, 14 * dt));
  p.spd = Math.hypot(p.vx, p.vz);

  if (frozen) return;

  const prev = sim.prevIn.get(p.id) ?? EMPTY_INPUT;
  const throwEdge = i.throw && !prev.throw;
  const grabEdge = i.grab && !prev.grab;
  const punchEdge = i.punch && !prev.punch;
  const jumpEdge = i.jump && !prev.jump;
  sim.prevIn.set(p.id, { throw: !!i.throw, grab: !!i.grab, punch: !!i.punch, jump: !!i.jump });

  if (p.stumbleT > 0) return; // no actions while staggered

  if (jumpEdge && onGround) {
    p.vy = cfg.jumpVel;
    p.y = 0.02; // leave the floor; momentum is preserved
    emit(sim, { t: 'jump', id: p.id, x: p.x, z: p.z });
  }
  if (punchEdge) doPunch(sim, p, i);
  if (throwEdge) doThrow(sim, p, i);
  if (grabEdge) doGrab(sim, p);
}

// Player-vs-player: impulse collisions (equal masses exchange momentum,
// report §Gameplay Collision Scenarios). e=0 between bodies that are just
// bumping; the interesting case is a launched body slamming into someone —
// if the collision impulse is big enough it staggers them and knocks
// whatever they were holding loose. Yes, a thrown player is a projectile.
function playerCollisions(sim, players) {
  const mat = sim.mats.player;
  const gcfg = sim.config;
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
      for (const p of [a, b]) {
        if (j >= gcfg.grab.breakImpulse) breakGrabs(sim, p, p.vx, p.vz);
        if (j >= gcfg.player.stumbleImpulse && p.stumbleT <= 0) {
          p.stumbleT = gcfg.player.stumbleTime;
          emit(sim, { t: 'stumble', id: p.id, x: p.x, z: p.z });
        }
      }
      if (j >= gcfg.player.stumbleImpulse) emit(sim, { t: 'bodySlam', x: a.x, z: a.z, j: Math.round(j) });
    }
  }
}

// Grab constraints, two regimes:
//   ONE-WAY: the victim is hoisted OVERHEAD like a carried item. Until they
//     react they simply ride along — but they can punch down at the grabber
//     (chip damage) or grab back to start a grapple.
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

// Anything in your hands (flag, bomb, player) comes loose. Called on KO and
// on any hit hard enough to break a grip — the flag then flies as a free
// physics object with your momentum.
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

// Punch: the fist is a moving collider (report §Punch Mechanics). Fist
// speed = swing + body speed (+ airborne bonus), and the impulse on
// whatever it hits follows  j = (1+e)·v_fist / (1/m_fist + 1/m_target) —
// damage and knockback are both derived from j, so momentum IS the weapon.
// Loose bombs and flags get smacked by the same formula (lighter -> flies).
function doPunch(sim, p, i) {
  const cfg = sim.config.punch;
  if (p.punchCd > 0) return;
  p.punchCd = cfg.cooldown;
  p.punchT = 0.3;
  p.punchArm = p.punchArm ? 0 : 1; // alternate fists: right, left, right...
  const invFist = 1 / cfg.fistMass;

  // held in someone's grip (overhead or grapple): pummel the grabber
  // directly — pure swing speed, chip damage until they let go or drop
  if (p.heldBy) {
    const holder = getP(sim, p.heldBy);
    emit(sim, { t: 'punch', id: p.id, x: p.x, z: p.z });
    if (holder && holder.state === 'alive') {
      const j = ((1 + cfg.restitution) * cfg.swingSpeed) / (invFist + invMass(sim.mats.player));
      const dmg = holder.team === p.team ? 0 : j * cfg.dmgPerImpulse;
      const fdir = { x: Math.sin(holder.face), z: Math.cos(holder.face) };
      damagePlayer(sim, holder, dmg, fdir.x * j * 0.35, fdir.z * j * 0.35, 0, 'punch');
      emit(sim, { t: 'punchHit', id: p.id, target: holder.id, x: holder.x, z: holder.z, j: Math.round(j) });
    }
    return;
  }

  const aim = norm2(i.ax || 0, i.az || 0);
  const dir = aim.len > 0.01 ? aim : { x: Math.sin(p.face), z: Math.cos(p.face) };
  p.face = Math.atan2(dir.x, dir.z);
  const fx = p.x + dir.x * cfg.range;
  const fz = p.z + dir.z * cfg.range;
  const vFist = cfg.swingSpeed + Math.hypot(p.vx, p.vz) + (p.y > 0.08 ? cfg.airBonus : 0);
  emit(sim, { t: 'punch', id: p.id, x: fx, z: fz });

  for (const o of sim.state.players) {
    if (o === p || o.state !== 'alive') continue;
    if (Math.abs(o.y - p.y) > 1.3) continue;
    if (Math.hypot(o.x - fx, o.z - fz) > cfg.radius) continue;
    const j = ((1 + cfg.restitution) * vFist) / (invFist + invMass(sim.mats.player));
    // knockback follows the swing, blended with a radial shove
    const rad = norm2(o.x - p.x, o.z - p.z);
    const kx = dir.x * 0.7 + rad.x * 0.3;
    const kz = dir.z * 0.7 + rad.z * 0.3;
    const dmg = o.team === p.team ? 0 : j * cfg.dmgPerImpulse; // no FF damage, full FF shove
    damagePlayer(sim, o, dmg, kx * j, kz * j, j * cfg.liftFrac, 'punch');
    emit(sim, { t: 'punchHit', id: p.id, target: o.id, x: o.x, z: o.z, j: Math.round(j) });
  }
  // smack loose bombs (light: they really fly — Δv = j/m)
  for (const b of sim.state.bombs) {
    if (b.holder) continue;
    if (Math.hypot(b.x - fx, b.z - fz) > cfg.radius) continue;
    const j = ((1 + cfg.restitution) * vFist) / (invFist + invMass(sim.mats.bomb));
    applyImpulse(b, sim.mats.bomb, dir.x * j, dir.z * j, j * 0.25);
  }
  sim.mode.onPunchObject?.(sim, fx, fz, cfg.radius, dir, vFist, invFist);
}

// Throw button throws WHATEVER you hold — flag, grabbed player, grabbed
// bomb — or spawns and lobs a fresh bomb if your hands are free. Everything
// thrown inherits your momentum, so running throws travel much farther.
function doThrow(sim, p, i) {
  const cfg = sim.config.bomb;
  const aim = norm2(i.ax || 0, i.az || 0);
  const dir = aim.len > 0.01 ? aim : { x: Math.sin(p.face), z: Math.cos(p.face) };
  const range = clamp(i.ad ?? 7, cfg.minRange, cfg.maxRange);
  const g = -sim.config.world.gravity;
  const s = Math.sqrt((range * g) / Math.sin(2 * cfg.throwPitch));
  const sh = s * Math.cos(cfg.throwPitch);
  const sv = s * Math.sin(cfg.throwPitch);
  const hx = p.x + dir.x * 0.6;
  const hz = p.z + dir.z * 0.6;

  if (p.carryFlag) {
    // hurl the flag downfield — the classic BombSquad flag relay
    sim.mode.throwCarried?.(sim, p, dir, { sh, sv });
  } else if (p.heldPlayer) {
    const t = getP(sim, p.heldPlayer);
    releasePlayer(sim, p);
    if (t) {
      const gr = sim.config.grab;
      t.vx = dir.x * gr.throwSpeed + p.vx * gr.throwSpeedScale;
      t.vz = dir.z * gr.throwSpeed + p.vz * gr.throwSpeedScale;
      t.vy = Math.max(t.vy, 0) + 6.5;
      t.y = Math.max(t.y, 0.05);
      t.stumbleT = Math.max(t.stumbleT, 1.0); // tumbles through the air
      emit(sim, { t: 'playerThrow', id: p.id, target: t.id, x: t.x, z: t.z });
    }
  } else if (p.heldBomb) {
    // re-throw a grabbed bomb — fuse keeps burning, so this is a hot potato
    const b = sim.state.bombs.find((b) => b.id === p.heldBomb);
    p.heldBomb = null;
    if (b) {
      b.holder = null;
      b.x = hx; b.z = hz; b.y = p.y + 1.1;
      b.vx = dir.x * sh + p.vx * 0.35;
      b.vz = dir.z * sh + p.vz * 0.35;
      b.vy = sv;
    }
    emit(sim, { t: 'throw', id: p.id, x: hx, z: hz });
  } else {
    if (p.throwCd > 0) return;
    p.throwCd = sim.config.player.throwCooldown;
    sim.state.bombs.push({
      id: 'b' + sim.nextId++,
      x: hx, z: hz, y: p.y + 1.1,
      vx: dir.x * sh + p.vx * 0.35,
      vz: dir.z * sh + p.vz * 0.35,
      vy: sv,
      fuse: cfg.fuse,
      holder: null,
    });
    emit(sim, { t: 'throw', id: p.id, x: hx, z: hz });
  }
  p.face = Math.atan2(dir.x, dir.z);
  p.throwT = 0.35;
}

// Grab: with something in hand, pressing grab again TOSSES it with mild
// momentum (your speed and direction carry into it — LMB stays the strong
// aimed throw). Empty-handed: mode objects first (steal the enemy flag),
// then loose bombs, then other players. A player being held can grab their
// own holder back, turning the carry into a mutual grapple.
function doGrab(sim, p) {
  const cfg = sim.config;
  if (p.carryFlag || p.heldBomb || p.heldPlayer) {
    doToss(sim, p);
    return;
  }

  if (sim.mode.tryGrab?.(sim, p)) return;

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
    emit(sim, { t: 'grabBomb', id: p.id, x: p.x, z: p.z });
    return;
  }

  // grab another player (they stay live: they punch back and get thrown)
  let bestP = null;
  let pd = cfg.grab.playerRange;
  for (const o of sim.state.players) {
    if (o === p || o.state !== 'alive') continue;
    const counterGrab = o.id === p.heldBy; // reaching down at your holder
    if (o.heldBy && !(o.heldBy === p.id)) continue; // already in another grip
    if (!counterGrab && Math.abs(o.y - p.y) > 1.2) continue;
    const d = Math.hypot(p.x - o.x, p.z - o.z);
    if (d < pd) { pd = d; bestP = o; }
  }
  if (bestP) {
    p.heldPlayer = bestP.id;
    bestP.heldBy = p.id;
    emit(sim, { t: 'grabPlayer', id: p.id, target: bestP.id, x: p.x, z: p.z, mutual: bestP.heldPlayer === p.id });
  }
}

// Light toss: release whatever is held — flag, bomb, or hoisted player —
// with mild momentum inherited from your movement speed and direction.
function doToss(sim, p) {
  const dir = { x: Math.sin(p.face), z: Math.cos(p.face) };
  const speed = 2.5 + Math.hypot(p.vx, p.vz) * 0.6; // mild, momentum-flavored
  if (p.carryFlag) {
    sim.mode.dropCarried?.(sim, p, dir.x * speed + p.vx * 0.3, dir.z * speed + p.vz * 0.3);
  } else if (p.heldBomb) {
    const b = sim.state.bombs.find((b) => b.id === p.heldBomb);
    p.heldBomb = null;
    if (b) {
      b.holder = null;
      b.x = p.x + dir.x * 0.6;
      b.z = p.z + dir.z * 0.6;
      b.y = p.y + 1.6;
      b.vx = dir.x * speed + p.vx * 0.4;
      b.vz = dir.z * speed + p.vz * 0.4;
      b.vy = 3.2;
    }
    emit(sim, { t: 'throw', id: p.id, x: p.x, z: p.z });
  } else if (p.heldPlayer) {
    const t = getP(sim, p.heldPlayer);
    // tossing out of a mutual grapple breaks BOTH grips
    if (t && t.heldPlayer === p.id) releasePlayer(sim, t);
    releasePlayer(sim, p);
    if (t) {
      t.vx = dir.x * speed + p.vx * 0.4;
      t.vz = dir.z * speed + p.vz * 0.4;
      t.vy = 3.5;
      t.y = Math.max(t.y, 0.05);
      t.stumbleT = Math.max(t.stumbleT, 0.5);
      emit(sim, { t: 'playerThrow', id: p.id, target: t.id, x: t.x, z: t.z, mild: true });
    }
  }
  p.throwT = 0.3;
}

// -------------------------------------------------------------------- bombs

function updateBombs(sim, dt) {
  const s = sim.state;
  const matB = sim.mats.bomb;
  const matP = sim.mats.player;
  for (let i = s.bombs.length - 1; i >= 0; i--) {
    const b = s.bombs[i];
    b.fuse -= dt;

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
        s.bombs.splice(i, 1); // lost to the void
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

  // players: radial impulse + damage with linear falloff, routed through
  // damagePlayer so grip-break / stumble thresholds apply uniformly
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
      cfg.maxDamage * (0.25 + 0.75 * t),
      nx * cfg.blastImpulse.player * (0.4 + 0.6 * t),
      nz * cfg.blastImpulse.player * (0.4 + 0.6 * t),
      cfg.blastLift.player * (0.5 + 0.5 * t),
      'bomb',
    );
  }

  // shove + cook nearby bombs: chain reactions (light props really fly)
  for (const ob of s.bombs) {
    if (ob.holder) continue;
    const d = Math.hypot(ob.x - b.x, ob.z - b.z);
    if (d >= cfg.blastRadius) continue;
    blastImpulse(ob, sim.mats.bomb, b.x, b.z, cfg.blastRadius, cfg.blastImpulse.bomb, cfg.blastLift.bomb);
    ob.fuse = Math.min(ob.fuse, cfg.chainFuse + d * 0.04);
  }

  // let the mode blast its own objects (flags) around
  sim.mode.onExplosion?.(sim, b.x, b.z, cfg.blastRadius);
}

// All damage funnels through here with IMPULSE knockback (Δv = J/m).
// The impulse magnitude — not damage — decides whether the hit breaks the
// target's grip and whether they stumble (report: "impulse determines how
// far the target flies / whether they stumble / ragdoll").
export function damagePlayer(sim, p, dmg, jx, jz, jLift, cause) {
  if (p.state !== 'alive' || p.invuln > 0) return;
  p.hp -= dmg;
  p.hurtT = sim.config.player.regenDelay;
  applyImpulse(p, sim.mats.player, jx, jz, jLift);
  const j = Math.hypot(jx, jz);
  if (j >= sim.config.grab.breakImpulse) breakGrabs(sim, p, p.vx, p.vz);
  if (j >= sim.config.player.stumbleImpulse && p.hp > 0) {
    p.stumbleT = Math.max(p.stumbleT, sim.config.player.stumbleTime);
    emit(sim, { t: 'stumble', id: p.id, x: p.x, z: p.z });
  }
  if (dmg > 0) emit(sim, { t: 'hurt', id: p.id, hp: Math.max(0, Math.round(p.hp)) });
  if (p.hp <= 0) koPlayer(sim, p, cause);
}

function koPlayer(sim, p, cause) {
  if (p.state === 'ko') return;
  p.state = 'ko';
  p.hp = 0;
  p.koT = 0;
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
  p.stumbleT = 0;
  p.koT = 0;
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
    p.stumbleT = 0;
    p.throwCd = 0; p.throwT = 0; p.punchCd = 0; p.punchT = 0; p.hurtT = 0;
    p.invuln = sim.config.player.invulnTime;
    placeAtSpawn(sim, p);
  }
  sim.mode.init(sim);
  emit(sim, { t: 'newRound' });
  emit(sim, { t: 'tick', n: Math.ceil(s.countdown) });
}
