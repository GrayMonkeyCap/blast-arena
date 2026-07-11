// Entry point: menu <-> match orchestration and the render loop.
// A "match" wires together: transport (local sim or server connection),
// renderer + world (visuals), input, HUD and sound. Everything a match
// creates is disposed when you exit, so menu <-> game cycles are clean.

import { LEVELS, DEFAULT_LEVEL } from './content/levels/index.js';
import { DEFAULT_COS } from './content/cosmetics.js';
import { createLocalGame } from './net/local.js';
import { connectOnline } from './net/ws.js';
import { createRenderer } from './render/renderer.js';
import { World } from './render/world.js';
import { createInput } from './input/input.js';
import { createHud } from './ui/hud.js';
import { createMenu } from './ui/menu.js';
import { createLabPanel } from './ui/labPanel.js';
import { createSfx } from './audio/sfx.js';
import { makeLabConfig } from './game/modes/sandbox.js';

const isTouch = navigator.maxTouchPoints > 0
  || matchMedia('(pointer: coarse)').matches
  || new URLSearchParams(location.search).has('touch'); // force for testing
const canvas = document.getElementById('game');
const uiRoot = document.getElementById('ui');
const sfx = createSfx();

// --- profile (name + cosmetics), persisted locally
const profile = (() => {
  let data;
  try { data = JSON.parse(localStorage.getItem('blast.profile')) ?? {}; } catch { data = {}; }
  return {
    name: data.name || 'Player',
    cos: { ...DEFAULT_COS, ...data.cos },
    save() {
      localStorage.setItem('blast.profile', JSON.stringify({ name: this.name, cos: this.cos }));
    },
  };
})();

let match = null;

const menu = createMenu(uiRoot, profile, {
  onClickSound: () => { sfx.unlock(); sfx.play('click'); },
  onPlayLocal: () => startMatch(createLocalGame({ profile })),
  onPlayLab: (variant) => startMatch(createLocalGame({
    profile,
    levelId: 'dojo',
    modeId: `sandbox-${variant}`,
    config: makeLabConfig(),
    teamSize: 1,
  })),
  onPlayOnline: async (room) => {
    const transport = await connectOnline({
      room,
      profile,
      onDropped: () => match?.exit('Connection lost'),
    });
    startMatch(transport);
  },
});

function playSfx(events, myId, myPos) {
  const spatial = (ev) => Math.max(0.15, 1 - Math.hypot(ev.x - myPos.x, ev.z - myPos.z) / 30);
  for (const ev of events) {
    switch (ev.t) {
      case 'explode': sfx.play('explode', spatial(ev)); break;
      case 'throw': sfx.play('throw', spatial(ev) * 0.9); break;
      case 'bounce': sfx.play('bounce', spatial(ev) * 0.7); break;
      case 'punch': sfx.play('punch', spatial(ev)); break;
      case 'punchHit': sfx.play('punchHit', spatial(ev)); break;
      case 'jump': if (ev.id === myId) sfx.play('jump'); break;
      case 'grabBomb': sfx.play('grab'); break;
      case 'grabPlayer': sfx.play('grabPlayer'); break;
      case 'playerThrow': sfx.play('playerThrow', spatial(ev)); break;
      case 'flagSteal': sfx.play('flagTaken'); break;
      case 'flagThrow': sfx.play('throw', spatial(ev)); break;
      case 'flagDrop': sfx.play('flagDrop'); break;
      case 'flagReturn': case 'flagVoid': sfx.play('flagReturn'); break;
      case 'score': sfx.play('score'); break;
      case 'scoreBlocked': if (ev.id === myId) sfx.play('denied'); break;
      case 'ko': sfx.play('ko'); break;
      case 'hurt': if (ev.id === myId) sfx.play('hurt'); break;
      case 'spawn': if (ev.id === myId) sfx.play('spawn'); break;
      case 'tick': sfx.play('tick'); break;
      case 'go': sfx.play('go'); break;
      case 'roundOver': sfx.play(ev.winner !== 'draw' ? 'win' : 'lose'); break;
    }
  }
}

function startMatch(transport) {
  menu.hide();
  sfx.unlock();
  canvas.classList.remove('hidden');

  const level = LEVELS[transport.levelId ?? DEFAULT_LEVEL];
  const renderer = createRenderer(canvas, { touch: isTouch, theme: level.theme });
  const world = new World(renderer.scene, level, { touch: isTouch });
  const hud = createHud(uiRoot, {
    onExit: () => exit(),
    onMute: () => sfx.toggle(),
    muted: sfx.muted,
  });
  const input = createInput({ uiRoot, isTouch });
  const labPanel = transport.modeId?.startsWith('sandbox')
    ? createLabPanel(uiRoot, transport)
    : null;

  const onKey = (e) => { if (e.code === 'Escape') exit(); };
  window.addEventListener('keydown', onKey);

  let raf = 0;
  let last = performance.now();
  let firstFrame = true;

  function frame(now) {
    raf = requestAnimationFrame(frame);
    step(now);
  }

  // One full frame: sim, input, world sync, HUD, render. Separated from the
  // rAF callback so tests (and the debug hook) can pump frames manually.
  function step(now) {
    const dt = Math.min((now - last) / 1000, 0.05);
    last = now;

    transport.update(dt * (labPanel?.timeScale ?? 1)); // lab slow-mo
    const view = transport.view();
    if (!view) {
      hud.setConnecting(true);
      renderer.render(0);
      return;
    }
    hud.setConnecting(false);

    const myId = transport.myId;
    const me = view.players.find((p) => p.id === myId);
    const myPos = me ? { x: me.x, z: me.z } : { x: 0, z: 0 };

    const sampled = input.sample({ myPos, screenToGround: renderer.screenToGround });
    transport.setInput(sampled.input);
    world.setAim(
      sampled.aimPoint,
      !!sampled.aimPoint && me?.state === 'alive' && (!isTouch || sampled.aiming),
    );

    const events = transport.drainEvents();
    world.handleEvents(events, myPos);
    hud.pushEvents(events, view, myId);
    labPanel?.update(view, events, myId, dt);
    playSfx(events, myId, myPos);

    world.sync(view, dt, myId);
    hud.update(view, myId);

    if (me) renderer.follow(me.x, me.z, me.vx * 0.22, me.vz * 0.22, dt, firstFrame);
    firstFrame = false;
    renderer.render(world.shake);
  }
  raf = requestAnimationFrame(frame);

  function exit(reason) {
    cancelAnimationFrame(raf);
    window.removeEventListener('keydown', onKey);
    transport.dispose?.();
    input.dispose();
    labPanel?.dispose();
    hud.dispose();
    renderer.dispose();
    canvas.classList.add('hidden');
    match = null;
    menu.show();
    if (reason) console.warn('[blast] left match:', reason);
  }

  match = { exit };
  window.__blast = { transport, input, world, step, renderer }; // dev/debug hook
}
