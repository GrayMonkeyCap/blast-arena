// Virtual touch controls, BombSquad-style:
//   - left half of the screen: dynamic-origin joystick for movement
//   - a four-button diamond on the right:
//       💣 BOMB (top): tap = quick throw; press-and-drag = aim (a reticle
//          appears in-world), release = throw at the aimed spot
//       👊 PUNCH (left), ✋ GRAB (right), ⬆ JUMP (bottom)
// Multi-touch safe via pointerId tracking.

export function createTouchControls(uiRoot) {
  const root = document.createElement('div');
  root.className = 'touch';
  root.innerHTML = `
    <div class="joy-zone"></div>
    <div class="joy"><div class="joy-knob"></div></div>
    <button class="tbtn tbtn-bomb" aria-label="throw">💣</button>
    <button class="tbtn tbtn-punch" aria-label="punch">👊</button>
    <button class="tbtn tbtn-grab" aria-label="grab">✋</button>
    <button class="tbtn tbtn-jump" aria-label="jump">⬆️</button>
  `;
  uiRoot.appendChild(root);

  const joyZone = root.querySelector('.joy-zone');
  const joyEl = root.querySelector('.joy');
  const knob = root.querySelector('.joy-knob');
  const bombBtn = root.querySelector('.tbtn-bomb');

  const JOY_R = 56; // px travel
  const AIM_DEAD = 20; // px before a bomb-press becomes an aim-drag
  const AIM_MAX = 110; // px = full throw range

  const state = {
    joy: { active: false, x: 0, z: 0 },
    aim: { active: false, x: 0, z: 0, len: 0 },
  };
  let joyId = null;
  let joyOrigin = null;
  let bombId = null;
  let bombStart = null;
  let pendingThrow = null; // set on bomb release, consumed by input.js
  const pending = { grab: false, punch: false, jump: false };

  // --- joystick (dynamic origin anywhere on the left half)
  joyZone.addEventListener('pointerdown', (e) => {
    if (joyId !== null) return;
    joyId = e.pointerId;
    try { joyZone.setPointerCapture(e.pointerId); } catch { /* synthetic events */ }
    joyOrigin = { x: e.clientX, y: e.clientY };
    joyEl.style.display = 'block';
    joyEl.style.left = `${e.clientX}px`;
    joyEl.style.top = `${e.clientY}px`;
    knob.style.transform = 'translate(-50%,-50%)';
    state.joy.active = true;
    state.joy.x = 0;
    state.joy.z = 0;
  });
  joyZone.addEventListener('pointermove', (e) => {
    if (e.pointerId !== joyId) return;
    let dx = e.clientX - joyOrigin.x;
    let dy = e.clientY - joyOrigin.y;
    const len = Math.hypot(dx, dy);
    if (len > JOY_R) {
      dx = (dx / len) * JOY_R;
      dy = (dy / len) * JOY_R;
    }
    knob.style.transform = `translate(calc(-50% + ${dx}px), calc(-50% + ${dy}px))`;
    state.joy.x = dx / JOY_R;
    state.joy.z = dy / JOY_R; // screen down = world +z
  });
  const joyEnd = (e) => {
    if (e.pointerId !== joyId) return;
    joyId = null;
    state.joy.active = false;
    state.joy.x = 0;
    state.joy.z = 0;
    joyEl.style.display = 'none';
  };
  joyZone.addEventListener('pointerup', joyEnd);
  joyZone.addEventListener('pointercancel', joyEnd);

  // --- bomb button: tap = quick throw, drag = aim then throw on release
  bombBtn.addEventListener('pointerdown', (e) => {
    if (bombId !== null) return;
    bombId = e.pointerId;
    try { bombBtn.setPointerCapture(e.pointerId); } catch { /* synthetic events */ }
    bombStart = { x: e.clientX, y: e.clientY };
    bombBtn.classList.add('active');
  });
  bombBtn.addEventListener('pointermove', (e) => {
    if (e.pointerId !== bombId) return;
    const dx = e.clientX - bombStart.x;
    const dy = e.clientY - bombStart.y;
    const len = Math.hypot(dx, dy);
    if (len > AIM_DEAD) {
      state.aim.active = true;
      state.aim.x = dx / len;
      state.aim.z = dy / len;
      state.aim.len = Math.min(1, (len - AIM_DEAD) / (AIM_MAX - AIM_DEAD));
    } else {
      state.aim.active = false;
    }
  });
  const bombEnd = (e) => {
    if (e.pointerId !== bombId) return;
    bombId = null;
    bombBtn.classList.remove('active');
    pendingThrow = state.aim.active
      ? { aimX: state.aim.x, aimZ: state.aim.z, len: state.aim.len }
      : { aimX: 0, aimZ: 0, len: 0 }; // quick throw: sim uses facing
    state.aim.active = false;
    state.aim.len = 0;
  };
  bombBtn.addEventListener('pointerup', bombEnd);
  bombBtn.addEventListener('pointercancel', (e) => {
    if (e.pointerId !== bombId) return;
    bombId = null;
    bombBtn.classList.remove('active');
    state.aim.active = false;
  });

  // --- simple tap buttons: punch / grab / jump
  for (const [name, sel] of [['punch', '.tbtn-punch'], ['grab', '.tbtn-grab'], ['jump', '.tbtn-jump']]) {
    const btn = root.querySelector(sel);
    btn.addEventListener('pointerdown', (e) => {
      e.preventDefault();
      pending[name] = true;
      btn.classList.add('active');
      setTimeout(() => btn.classList.remove('active'), 150);
    });
  }

  const consume = (name) => {
    const v = pending[name];
    pending[name] = false;
    return v;
  };

  return {
    joy: state.joy,
    aim: state.aim,
    consumeThrow() {
      const t = pendingThrow;
      pendingThrow = null;
      return t;
    },
    consumeGrab: () => consume('grab'),
    consumePunch: () => consume('punch'),
    consumeJump: () => consume('jump'),
    dispose() {
      root.remove();
    },
  };
}
