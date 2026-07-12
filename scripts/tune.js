// Physics tuning harness (deep-research-report.md §Tuning and Testing):
// runs the common physics core through simple analytic test cases and
// checks measured behavior against theory. Run after ANY physics/config
// change: `npm run tune`. Exits non-zero on failure.
//
//   restitution: bounce peak of a dropped body ≈ e² · h
//   friction:    slide stop time ≈ v / (μ·g)
//   impulse:     equal-mass head-on collision (e=0) -> both stop,
//                momentum conserved
//   ballistics:  a throw solved for range R lands ≈ R away
//   spring:      grab constraint settles quickly without orbiting
//   punch/blast: impulse tables (eyeball columns for game feel)

import { CONFIG } from '../src/core/config.js';
import {
  integrateBody, collideBodies, blastKick, springDamper, invMass,
} from '../src/game/physics.js';

const MAT = CONFIG.physics.materials;
const world = {
  gravity: CONFIG.world.gravity,
  maxSpeed: CONFIG.physics.maxSpeed,
  sleepSpeed: CONFIG.physics.sleepSpeed,
};
const level = { bounds: { w: 500, d: 500 }, solids: [] }; // bare floor
const dt = 1 / 60;
const g = -world.gravity;

let failures = 0;
function check(name, measured, expected, tolFrac, note = '') {
  const ok = Math.abs(measured - expected) <= Math.abs(expected) * tolFrac;
  if (!ok) failures++;
  console.log(
    `${ok ? 'PASS' : 'FAIL'}  ${name}: measured ${measured.toFixed(2)}, theory ${expected.toFixed(2)} (±${tolFrac * 100}%) ${note}`,
  );
}

// ---- 1. restitution: drop a bomb from h, bounce peak should be ~ e²h
{
  const h = 3;
  const b = { x: 0, z: 0, y: h + MAT.bomb.radius, vx: 0, vz: 0, vy: 0 };
  let bounced = false;
  let peak = 0;
  for (let i = 0; i < 600; i++) {
    const out = integrateBody(level, world, b, MAT.bomb, dt, { restY: MAT.bomb.radius });
    if (out.bounced) bounced = true;
    else if (bounced) peak = Math.max(peak, b.y - MAT.bomb.radius);
    if (bounced && b.vy === 0 && b.y <= MAT.bomb.radius + 0.001) break;
  }
  // damping + discrete steps eat a little energy beyond e²
  check('bomb bounce peak (e²h)', peak, MAT.bomb.restitution ** 2 * h, 0.25);
}

// ---- 2. Coulomb friction: slide stop time ≈ v/(μg)
{
  const v0 = 10;
  const b = { x: 0, z: 0, y: MAT.bomb.radius, vx: v0, vz: 0, vy: 0 };
  let t = 0;
  while (Math.hypot(b.vx, b.vz) > 0.01 && t < 5) {
    integrateBody(level, world, b, MAT.bomb, dt, { restY: MAT.bomb.radius });
    t += dt;
  }
  check('bomb slide stop time (v/μg)', t, v0 / (MAT.bomb.friction * g), 0.2);

  const f = { x: 0, z: 0, y: 0, vx: v0, vz: 0, vy: 0 };
  t = 0;
  while (Math.hypot(f.vx, f.vz) > 0.01 && t < 5) {
    integrateBody(level, world, f, MAT.flag, dt);
    t += dt;
  }
  check('flag slide stop time (v/μg)', t, v0 / (MAT.flag.friction * g), 0.2, '(heavy flag plants fast)');
}

// ---- 3. impulse exchange: equal masses, head-on, e=0 -> both stop
{
  const a = { x: -1, z: 0, y: 0, vx: 5, vz: 0, vy: 0 };
  const b = { x: 1, z: 0, y: 0, vx: -5, vz: 0, vy: 0 };
  let j = 0;
  for (let i = 0; i < 60 && !j; i++) {
    a.x += a.vx * dt;
    b.x += b.vx * dt;
    j = collideBodies(a, MAT.player, b, MAT.player, { e: 0 });
  }
  const pTotal = MAT.player.mass * (a.vx + b.vx);
  check('head-on momentum after collision', pTotal, 0, 0.001, `(j=${j.toFixed(1)}, both stop)`);
  check('head-on residual speed (e=0)', Math.abs(a.vx) + Math.abs(b.vx), 0, 0.001);
}

// ---- 4. ballistics: the universal 45° throw (BombSquad power model).
// Full-power standing throw should land ~s²/g away (+ a bit for the 1.6u
// release height); a full-sprint throw inherits carrier velocity and sails.
{
  const c = CONFIG.throw;
  const fly = (vx, vy) => {
    const b = { x: 0, z: 0, y: 1.6, vx, vz: 0, vy };
    for (let i = 0; i < 600; i++) {
      const out = integrateBody(level, world, b, MAT.bomb, dt, { restY: MAT.bomb.radius });
      if (out.bounced || (b.y <= MAT.bomb.radius + 0.001 && b.vy === 0)) return b.x;
    }
    return b.x;
  };
  const sh = Math.cos(c.pitch) * c.speedMax;
  const sv = Math.sin(c.pitch) * c.speedMax;
  const standing = fly(sh, sv);
  const running = fly(sh + CONFIG.player.runSpeed * c.inherit, sv);
  console.log(`      throw: standing ${standing.toFixed(1)}u, full-sprint ${running.toFixed(1)}u`);
  check('standing max throw range', standing, (c.speedMax ** 2) / g * Math.sin(2 * c.pitch) + 1.4, 0.25, '(45° lob + release height)');
  check('sprint throw sails much farther', running / standing, 1.9, 0.35, '(momentum inheritance)');
}

// ---- 5. spring-damper utility: displaced body settles at anchor, no orbit
// (gameplay grabs are rigid carries now; this validates the physics-core
// springDamper for future constraint use)
{
  const k = { kp: 190, kd: 20, maxF: 280 };
  const t = { x: 1.5, z: 0, y: 0, vx: 0, vz: 0, vy: 0 };
  let settle = -1;
  for (let i = 0; i < 240; i++) {
    springDamper(t, MAT.player, 0, 0, 0, 0, k, dt);
    integrateBody(level, world, t, MAT.player, dt, { noGroundFriction: true });
    const d = Math.hypot(t.x, t.z);
    const v = Math.hypot(t.vx, t.vz);
    if (d < 0.15 && v < 0.6) { settle = i * dt; break; }
  }
  const zeta = k.kd / (2 * Math.sqrt(k.kp * MAT.player.mass)); // damping ratio
  console.log(`      spring damping ratio ζ=${zeta.toFixed(2)} (underdamped swing is intended)`);
  check('grab spring settle time', settle < 0 ? 99 : settle, 1.2, 1.0, '(must settle, not orbit)');
}

// ---- 6. punch damage table (BombSquad momentum model): damage rides on
// 3D body speed, ×0.7–1.0 for swing timing; a single hit past the knockout
// threshold puts the target out cold for units/12 seconds.
{
  const c = CONFIG.punch;
  const k = CONFIG.player.knockout;
  const row = (label, v3) => {
    const dmg = Math.min(c.dmgCap, c.dmgBase + c.dmgPerSpeed * v3); // peak timing
    const dv = dmg * c.kbPerDmg;
    const units = Math.min(k.maxUnits, dmg * k.unitsPerDamage - k.baseUnits);
    const kt = units >= 1 ? units / k.unitsPerSec : 0;
    console.log(
      `      punch ${label.padEnd(16)} v=${v3.toFixed(1).padStart(4)}  dmg=${dmg.toFixed(0).padStart(3)}  Δv=${dv.toFixed(1).padStart(5)}  knockout=${kt > 0 ? kt.toFixed(2) + 's' : '   —'}`,
    );
    return dmg;
  };
  const dStand = row('standing', 0);
  row('walking', CONFIG.player.walkSpeed);
  const dRun = row('sprinting', CONFIG.player.runSpeed);
  row('sprint jump', Math.hypot(CONFIG.player.runSpeed, CONFIG.player.jumpVel * 0.7));
  check('sprint punch ≈ 40% hp (BombSquad)', dRun, 41, 0.15, '(tutorial: running punch ≈ 40%)');
  check('standing jab stays a tickle', dStand, c.dmgBase, 0.01);
  check('sprint punch knocks out', dRun * k.unitsPerDamage - k.baseUnits >= 1 ? 1 : 0, 1, 0.01, '(a clean running hit floors you)');
}

// ---- 7. blast table (BombSquad): linear damage falloff to ZERO at the
// edge, point-blank lethal; the velocity kick is the SAME for every body
// (mass-normalized force), vertical component exaggerated.
{
  const c = CONFIG.bomb;
  for (const d of [0, 0.8, 1.5, 2.2]) {
    const t = Math.max(0, 1 - d / c.blastRadius);
    console.log(
      `      blast @d=${d.toFixed(1).padStart(3)}  dmg=${(c.maxDamage * t).toFixed(0).padStart(3)}  Δv=(${(c.blastDvXZ * t).toFixed(1)} out, ${(c.blastDvY * t).toFixed(1)} up) — all bodies alike`,
    );
  }
  check('point-blank blast is lethal', c.maxDamage, CONFIG.player.hp, 0.01);
  const b = { x: 1.0, z: 0, y: MAT.bomb.radius, vx: 0, vz: 0, vy: 0 };
  const dv = blastKick(b, 0, 0, c.blastRadius, c.blastDvXZ, c.blastDvY);
  check('blastKick applies uniform Δv', Math.hypot(b.vx, b.vy), dv, 0.05, '(mass-normalized, like BombSquad)');
}

console.log(failures === 0 ? '\nTUNE OK — physics matches theory' : `\nTUNE FAILED (${failures})`);
process.exit(failures === 0 ? 0 : 1);
