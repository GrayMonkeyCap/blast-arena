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
  integrateBody, collideBodies, blastImpulse, springDamper, launchSpeed, invMass,
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

// ---- 4. ballistics: throw solved for range R lands ~R away
{
  const R = 8;
  const { sh, sv } = launchSpeed(R, CONFIG.bomb.throwPitch, g);
  const b = { x: 0, z: 0, y: 1.1, vx: sh, vz: 0, vy: sv };
  let landed = 0;
  for (let i = 0; i < 600; i++) {
    const out = integrateBody(level, world, b, MAT.bomb, dt, { restY: MAT.bomb.radius });
    if (out.bounced) { landed = b.x; break; }
  }
  // launched from hand height 1.1 -> flies slightly past R
  check('throw range (solved for 8)', landed, R, 0.25, '(launch is 1.1u above floor)');
}

// ---- 5. grab spring: displaced held body settles at the hand, no orbiting
{
  const k = CONFIG.grab.spring;
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

// ---- 6. punch impulse table: j = (1+e)·v_fist / (1/m_fist + 1/m_target)
{
  const c = CONFIG.punch;
  const inv = 1 / c.fistMass + invMass(MAT.player);
  const row = (label, vFist) => {
    const j = ((1 + c.restitution) * vFist) / inv;
    console.log(
      `      punch ${label.padEnd(18)} v_fist=${vFist.toFixed(1).padStart(5)}  j=${j.toFixed(1).padStart(5)}  Δv=${(j * invMass(MAT.player)).toFixed(1)}  dmg=${(j * c.dmgPerImpulse).toFixed(0).padStart(3)}  grip-break=${j >= CONFIG.grab.breakImpulse ? 'Y' : 'n'}  stumble=${j >= CONFIG.player.stumbleImpulse ? 'Y' : 'n'}`,
    );
    return j;
  };
  const jStand = row('standing', c.swingSpeed);
  const jRun = row('running', c.swingSpeed + CONFIG.player.speed);
  row('running jump', c.swingSpeed + CONFIG.player.speed + c.airBonus);
  check('running punch >> standing punch', jRun / jStand, 1.8, 0.25, '(momentum is the weapon)');
}

// ---- 7. blast Δv falloff table (players vs light props)
{
  const c = CONFIG.bomb;
  for (const d of [0, 1.5, 3, 4.2]) {
    const t = Math.max(0, 1 - d / c.blastRadius);
    const dvP = c.blastImpulse.player * (0.4 + 0.6 * t) * t > 0 ? c.blastImpulse.player * (0.4 + 0.6 * t) * invMass(MAT.player) : 0;
    const dvB = c.blastImpulse.bomb * t * invMass(MAT.bomb);
    const dvF = c.blastImpulse.flag * t * invMass(MAT.flag);
    console.log(`      blast @d=${d.toFixed(1).padStart(3)}  Δv player=${(t > 0 ? dvP : 0).toFixed(1).padStart(5)}  bomb=${dvB.toFixed(1).padStart(5)}  flag=${dvF.toFixed(1).padStart(5)}`);
  }
  const dvBomb0 = c.blastImpulse.bomb * invMass(MAT.bomb);
  check('point-blank bomb shove sane', dvBomb0, 13.7, 0.35, '(chain shoves lively, not silly)');
}

console.log(failures === 0 ? '\nTUNE OK — physics matches theory' : `\nTUNE FAILED (${failures})`);
process.exit(failures === 0 ? 0 : 1);
