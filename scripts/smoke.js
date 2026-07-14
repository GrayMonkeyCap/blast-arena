// Headless sanity run of the pure-sim layer (the same code the server runs).
// Simulates ~4 minutes of a match with an idle human + bots and reports what
// happened, then walks every powerup through a deterministic harness. Any
// exception here means the game logic is broken.
import { GameHost } from '../src/game/host.js';
import { grantPowerup, damagePlayer } from '../src/game/sim.js';

const assert = (cond, msg) => { if (!cond) throw new Error(msg); };

const host = new GameHost();
host.addHuman({ name: 'Smoke', cos: { hat: 'none', skin: '#ffd29c' } });
host.fillBots();

const seen = {};
let snapshotBytes = 0;
for (let i = 0; i < 60 * 240; i++) {
  host.step(1 / 60);
  for (const ev of host.drainEvents()) seen[ev.t] = (seen[ev.t] ?? 0) + 1;
  if (i === 600) snapshotBytes = JSON.stringify(host.sim.state).length;
}

const s = host.sim.state;
console.log('events seen:', seen);
console.log('phase:', s.phase, '| scores:', s.scores, '| players:', s.players.length, '| snapshot bytes:', snapshotBytes);
for (const p of s.players) {
  console.log(` ${p.id} ${p.name} [${p.team}] state=${p.state} hp=${Math.round(p.hp)} pos=(${p.x.toFixed(1)},${p.z.toFixed(1)})`);
}
if (!seen.explode || !seen.throw) throw new Error('bots never fought');
if (!seen.flagSteal) throw new Error('no flag was ever stolen');
if (!seen.punch) throw new Error('bots never punched');
if (!seen.powerupSpawn) throw new Error('no powerup boxes ever dropped');
if (!seen.powerup) throw new Error('nobody ever picked up a powerup');

// --- powerups: deterministic per-type harness (one idle human per team,
// no bots — grant effects directly and drive the sim tick by tick)
{
  const host = new GameHost({ teamSize: 1 });
  const sim = host.sim;
  const events = [];
  const run = (t) => {
    for (let i = 0; i < Math.round(t * 60); i++) host.step(1 / 60);
    events.push(...host.drainEvents());
  };
  const sawEvent = (t, match = () => true) => {
    events.push(...host.drainEvents());
    return events.some((ev) => ev.t === t && match(ev));
  };

  host.addHuman({ name: 'A', cos: {} });
  host.addHuman({ name: 'B', cos: {} });
  const [p, q] = sim.state.players;
  run(3.4); // countdown -> play
  p.invuln = 0; q.invuln = 0;

  // triple bombs, land mines, boxing gloves — grants and 20s wear-off
  grantPowerup(sim, p, 'triple');
  grantPowerup(sim, p, 'mines');
  grantPowerup(sim, p, 'gloves');
  assert(p.bombCount === 3, 'triple should allow 3 live bombs');
  assert(p.mines === 3, 'mines powerup should grant 3');
  assert(p.glovesT === 20, 'gloves should last 20s');
  run(20.2);
  assert(p.bombCount === 1 && p.glovesT === 0, 'triple/gloves should wear off after 20s');
  assert(p.mines === 3, 'land mines are ammo — they must NOT wear off');

  // shield: absorbs damage AND knockback whole; the breaking hit only
  // leaks what exceeds hp+spillover (65+50)
  grantPowerup(sim, p, 'shield');
  assert(p.shieldHp === 65, 'shield should start at 65 (BombSquad 650/10)');
  damagePlayer(sim, p, 30, 5, 0, 0, 'test');
  assert(Math.round(p.hp) === 100 && p.shieldHp === 35, 'shield should eat the whole hit');
  assert(p.vx === 0, 'shield should eat the knockback too');
  damagePlayer(sim, p, 200, 0, 0, 0, 'test');
  assert(p.shieldHp === 0, 'big hit should break the shield');
  assert(p.state === 'ko', 'spillover past 50 should still kill (200-35-50=115)');
  assert(sawEvent('shieldDown'), 'shieldDown event should fire');
  run(5.6); // respawn
  assert(p.state === 'alive' && p.shieldHp === 0 && p.mines === 0, 'death must clear powerups');
  p.invuln = 0;

  // curse: 5s countdown, then a blast and a KO — unless a med-pack cures it
  grantPowerup(sim, p, 'curse');
  assert(p.curseT === 5, 'curse should start its 5s countdown');
  run(5.2);
  assert(p.state === 'ko', 'the curse should have gone off');
  assert(sawEvent('ko', (ev) => ev.cause === 'curse'), 'KO cause should be curse');
  assert(sawEvent('explode', (ev) => ev.kind === 'curse'), 'curse should explode');
  run(5.6);
  p.invuln = 0;
  damagePlayer(sim, p, 40, 0, 0, 0, 'test');
  grantPowerup(sim, p, 'curse');
  grantPowerup(sim, p, 'health');
  assert(p.curseT === 0 && p.hp === 100, 'a med-pack should heal AND cure the curse');
  run(6);
  assert(p.state === 'alive', 'cured player should live');

  // ice bomb: freezes (after its halved damage), a hard hit shatters
  sim.state.bombs.push({ id: 'ti', kind: 'ice', x: q.x + 0.7, z: q.z, y: 0.32, vx: 0, vz: 0, vy: 0, fuse: 0.05, arm: 0, holder: null, owner: null, stuckTo: null });
  run(0.5);
  assert(q.frozenT > 0, 'ice blast should freeze');
  assert(q.hp < 100, 'ice blast should still hurt (half damage)');
  damagePlayer(sim, q, 25, 0, 0, 0, 'punch');
  assert(q.state === 'ko' && sawEvent('shatter'), 'a 25-dmg hit on a frozen player should shatter');
  run(5.6);
  q.invuln = 0;

  // impact bomb: no fuse to speak of — it detonates on landing
  sim.state.bombs.push({ id: 'tp', kind: 'impact', x: 0, z: 0, y: 2.5, vx: 0, vz: 0, vy: 0, fuse: 20, arm: 0, holder: null, owner: null, stuckTo: null });
  run(1);
  assert(sawEvent('explode', (ev) => ev.kind === 'impact'), 'impact bomb should explode on touching the floor');

  // land mine: sits silent until ANY body touches it
  sim.state.bombs.push({ id: 'tm', kind: 'mine', x: p.x + 0.5, z: p.z, y: 0.32, vx: 0, vz: 0, vy: 0, fuse: null, arm: 0, holder: null, owner: null, stuckTo: null });
  run(0.3);
  assert(sawEvent('explode', (ev) => ev.kind === 'mine'), 'stepping on an armed mine should detonate it');
  assert(p.state === 'ko', 'a point-blank mine (2.5x damage) should kill');
  run(5.6);

  // sticky bomb: splats onto whoever it hits and rides them to the boom
  // (rolled along the floor — an airborne one would splat where it lands)
  sim.state.bombs.push({ id: 'ts', kind: 'sticky', x: q.x - 2, z: q.z, y: 0.32, vx: 8, vz: 0, vy: 0, fuse: 1.2, arm: 0, holder: null, owner: null, stuckTo: null });
  run(0.5);
  const sticky = sim.state.bombs.find((b) => b.id === 'ts');
  assert(sticky && sticky.stuckTo === q.id, 'sticky bomb should stick to the player it hits');
  const hpBefore = q.hp;
  run(1.2);
  assert(q.hp < hpBefore || q.state === 'ko', 'the stuck bomb should blow up on its wearer');

  console.log('powerup harness: triple/mines/gloves/shield/curse/health/ice/impact/mine/sticky OK');
}

// --- physics lab: duel variant — the fighter bot must actually fight
{
  const { makeLabConfig } = await import('../src/game/modes/sandbox.js');
  const lab = new GameHost({ levelId: 'dojo', modeId: 'sandbox-duel', config: makeLabConfig(), teamSize: 1 });
  lab.addHuman({ name: 'Tester', cos: { hat: 'none', skin: '#fff' } });
  lab.fillBots();
  const labSeen = {};
  for (let i = 0; i < 60 * 60; i++) {
    lab.step(1 / 60);
    for (const ev of lab.drainEvents()) labSeen[ev.t] = (labSeen[ev.t] ?? 0) + 1;
  }
  console.log('lab duel events:', labSeen);
  if (!labSeen.punch) throw new Error('lab fighter never punched');
  if (!labSeen.throw) throw new Error('lab fighter never threw a bomb');
  if (lab.sim.state.phase !== 'play') throw new Error('lab round ended (should be endless)');
}

// --- physics lab: doll variant — doll must hold still, then walk back home
{
  const { makeLabConfig } = await import('../src/game/modes/sandbox.js');
  const lab = new GameHost({ levelId: 'dojo', modeId: 'sandbox-doll', config: makeLabConfig(), teamSize: 1 });
  lab.addHuman({ name: 'Tester', cos: { hat: 'none', skin: '#fff' } });
  lab.fillBots();
  for (let i = 0; i < 60 * 3; i++) lab.step(1 / 60);
  const doll = lab.sim.state.players.find((p) => p.bot);
  const post = { x: doll.x, z: doll.z };
  if (Math.hypot(doll.vx, doll.vz) > 0.01) throw new Error('doll fidgets');
  // blast the doll away; it should ragdoll, then walk itself back
  lab.sim.state.bombs.push({ id: 'test', x: doll.x + 0.8, z: doll.z, y: 0.3, vx: 0, vz: 0, vy: 0, fuse: 0.02, holder: null });
  for (let i = 0; i < 60 * 8; i++) lab.step(1 / 60);
  lab.drainEvents();
  const back = Math.hypot(doll.x - post.x, doll.z - post.z);
  console.log(`lab doll: blasted, returned to ${back.toFixed(2)}u from post`);
  if (back > 1.5) throw new Error('doll never walked back to its post');
}

// --- death match: bots-only brawl, frags must accrue, no flags in play
{
  const dm = new GameHost({ modeId: 'deathmatch', teamSize: 2 });
  dm.fillBots();
  const dmSeen = {};
  for (let i = 0; i < 60 * 120; i++) {
    dm.step(1 / 60);
    for (const ev of dm.drainEvents()) dmSeen[ev.t] = (dmSeen[ev.t] ?? 0) + 1;
  }
  console.log('death match:', dmSeen, 'scores', dm.sim.state.scores);
  if (!dmSeen.frag) throw new Error('death match: no frags happened');
  if (dm.sim.state.flags !== null) throw new Error('death match: flags should be null');
}

// --- skyhaven: death match on the new arena — bots must fight, level+mode wired right
{
  const sky = new GameHost({ levelId: 'skyhaven', modeId: 'deathmatch', teamSize: 2 });
  sky.fillBots();
  const tally = {};
  for (let i = 0; i < 60 * 60; i++) {
    sky.step(1 / 60);
    for (const ev of sky.drainEvents()) tally[ev.t] = (tally[ev.t] ?? 0) + 1;
  }
  console.log('skyhaven death match:', tally, 'scores', sky.sim.state.scores);
  if (sky.sim.level.id !== 'skyhaven') throw new Error('skyhaven: wrong level loaded');
  if (sky.sim.state.flags !== null) throw new Error('skyhaven DM: flags should be null');
  if (!tally.frag && !tally.explode) throw new Error('skyhaven DM: nothing happened');
}

console.log('SMOKE OK');
