// Main menu: name + character customization (hats/skins from the cosmetics
// registry — new entries appear automatically), level card (from the level
// registry, ready for a level-select grid later), play vs bots, and online
// play with a room code.

import { HATS, SKINS } from '../content/cosmetics.js';
import { LEVELS, DEFAULT_LEVEL } from '../content/levels/index.js';

export function createMenu(uiRoot, profile, { onPlayLocal, onPlayOnline, onPlayLab, onClickSound }) {
  const el = document.createElement('div');
  el.className = 'menu';
  const level = LEVELS[DEFAULT_LEVEL];

  el.innerHTML = `
    <div class="menu-card">
      <h1 class="title">BLAST<span>ARENA</span></h1>
      <div class="subtitle">GRAB THE FLAG</div>

      <label class="field">
        <span>NAME</span>
        <input class="name-input" maxlength="12" placeholder="Player" />
      </label>

      <div class="field"><span>HAT</span><div class="hat-row"></div></div>
      <div class="field"><span>SKIN</span><div class="skin-row"></div></div>

      <div class="level-card">
        <div class="level-name">📍 ${level.name}</div>
        <div class="level-desc">${level.description} · 2v2 · first to 3 captures</div>
      </div>

      <button class="play-btn">▶&nbsp; PLAY VS BOTS</button>

      <div class="online-row">
        <input class="room-input" maxlength="12" placeholder="room code" value="main" />
        <button class="online-btn">🌐 PLAY ONLINE</button>
      </div>
      <div class="lab-row">
        <span class="lab-label">🧪 PHYSICS LAB</span>
        <button class="lab-mode-btn btn-duel">🤖 live bot</button>
        <button class="lab-mode-btn btn-doll">🎯 training doll</button>
      </div>
      <div class="menu-err hidden"></div>

      <details class="help">
        <summary>How to play</summary>
        <div class="help-cols">
          <div><b>⌨️ Keyboard</b><br>WASD / arrows — move · mouse — aim<br>Left click — throw (bomb, or whatever you hold)<br>Right click / F — punch (fists alternate)<br>E — grab: steal flag, pick up live bombs, hoist players overhead · press again to toss<br>Space — jump</div>
          <div><b>📱 Touch</b><br>Left side — joystick<br>💣 tap quick-throw · drag to aim<br>👊 punch · ✋ grab · ⬆️ jump</div>
        </div>
        <div class="help-rules">Steal the enemy flag and carry (or throw!) it to your base — but you can only score while your own flag is home. Touch your dropped flag to return it. Momentum is everything: running jump-punches hit like a truck, and everything you throw inherits your speed. Grabbed someone? They ride overhead until you toss (grab) or hurl (throw) them — but they can punch your hp down, or grab you back into a ground grapple where movement is a two-player tug-of-war. Mind the open edges.</div>
      </details>
    </div>
  `;
  uiRoot.appendChild(el);

  const nameInput = el.querySelector('.name-input');
  const hatRow = el.querySelector('.hat-row');
  const skinRow = el.querySelector('.skin-row');
  const err = el.querySelector('.menu-err');

  nameInput.value = profile.name;
  nameInput.addEventListener('input', () => {
    profile.name = nameInput.value.trim() || 'Player';
    profile.save();
  });

  function renderPickers() {
    hatRow.innerHTML = '';
    for (const hat of HATS) {
      const b = document.createElement('button');
      b.className = `chip ${profile.cos.hat === hat.id ? 'sel' : ''}`;
      b.textContent = hat.icon;
      b.title = hat.name;
      b.addEventListener('click', () => {
        profile.cos.hat = hat.id;
        profile.save();
        onClickSound?.();
        renderPickers();
      });
      hatRow.appendChild(b);
    }
    skinRow.innerHTML = '';
    for (const skin of SKINS) {
      const b = document.createElement('button');
      b.className = `chip swatch ${profile.cos.skin === skin ? 'sel' : ''}`;
      b.style.background = skin;
      b.addEventListener('click', () => {
        profile.cos.skin = skin;
        profile.save();
        onClickSound?.();
        renderPickers();
      });
      skinRow.appendChild(b);
    }
  }
  renderPickers();

  el.querySelector('.play-btn').addEventListener('click', () => {
    onClickSound?.();
    onPlayLocal();
  });
  el.querySelector('.btn-duel').addEventListener('click', () => {
    onClickSound?.();
    onPlayLab('duel');
  });
  el.querySelector('.btn-doll').addEventListener('click', () => {
    onClickSound?.();
    onPlayLab('doll');
  });
  const onlineBtn = el.querySelector('.online-btn');
  onlineBtn.addEventListener('click', async () => {
    onClickSound?.();
    err.classList.add('hidden');
    onlineBtn.disabled = true;
    onlineBtn.textContent = '⏳ connecting…';
    try {
      await onPlayOnline(el.querySelector('.room-input').value.trim() || 'main');
    } catch (e) {
      err.textContent = '⚠️ Could not reach the game server. Online play needs the bundled server — run `npm start` and open the game from there.';
      err.classList.remove('hidden');
    } finally {
      onlineBtn.disabled = false;
      onlineBtn.textContent = '🌐 PLAY ONLINE';
    }
  });

  return {
    show() { el.classList.remove('hidden'); },
    hide() { el.classList.add('hidden'); },
  };
}
