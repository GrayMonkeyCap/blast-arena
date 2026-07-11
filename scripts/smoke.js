// Headless sanity run of the pure-sim layer (the same code the server runs).
// Simulates ~4 minutes of a match with an idle human + bots and reports what
// happened. Any exception here means the game logic is broken.
import { GameHost } from '../src/game/host.js';

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

console.log('SMOKE OK');
