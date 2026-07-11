// In-match HUD: scoreboard + round timer + flag status, event feed, big
// center announcements (countdown / GOAL / winner), respawn overlay, HP bar,
// exit + mute buttons. Pure DOM over the canvas.

import { TEAMS } from '../core/config.js';

export function createHud(uiRoot, { onExit, onMute, muted }) {
  const el = document.createElement('div');
  el.className = 'hud';
  el.innerHTML = `
    <div class="topbar">
      <div class="score score-red">0</div>
      <div class="mid">
        <div class="timer">3:00</div>
        <div class="flag-ind"><span class="fi fi-red">⚑</span><span class="fi fi-blue">⚑</span></div>
      </div>
      <div class="score score-blue">0</div>
    </div>
    <div class="feed"></div>
    <div class="center"></div>
    <div class="respawn hidden"></div>
    <div class="hpwrap"><div class="hpbar"></div></div>
    <div class="hud-corner">
      <button class="hud-btn btn-mute">${muted ? '🔇' : '🔊'}</button>
      <button class="hud-btn btn-exit">✕</button>
    </div>
    <div class="connecting hidden">Connecting…</div>
    <div class="overlay-over hidden"></div>
  `;
  uiRoot.appendChild(el);

  const q = (sel) => el.querySelector(sel);
  const scoreRed = q('.score-red');
  const scoreBlue = q('.score-blue');
  const timer = q('.timer');
  const flagInd = q('.flag-ind');
  const feed = q('.feed');
  const center = q('.center');
  const respawn = q('.respawn');
  const hpbar = q('.hpbar');
  const connecting = q('.connecting');
  const overPanel = q('.overlay-over');

  q('.btn-exit').addEventListener('click', onExit);
  q('.btn-mute').addEventListener('click', (e) => {
    e.currentTarget.textContent = onMute() ? '🔇' : '🔊';
  });

  let lastCountdown = -1;
  let centerTimer = null;
  let overShown = false;

  function showCenter(html, cls = '', ms = 1300) {
    center.innerHTML = html;
    center.className = `center pop ${cls}`;
    clearTimeout(centerTimer);
    if (ms) centerTimer = setTimeout(() => { center.className = 'center'; center.innerHTML = ''; }, ms);
  }

  function pushFeed(html, cls = '') {
    const item = document.createElement('div');
    item.className = `feed-item ${cls}`;
    item.innerHTML = html;
    feed.appendChild(item);
    while (feed.children.length > 4) feed.firstChild.remove();
    setTimeout(() => { item.classList.add('fade'); setTimeout(() => item.remove(), 500); }, 3200);
  }

  const name = (ev) => `<b style="color:${TEAMS[ev.team]?.color ?? '#fff'}">${ev.name ?? ''}</b>`;

  return {
    update(view, myId) {
      scoreRed.textContent = view.scores.red;
      scoreBlue.textContent = view.scores.blue;

      if (view.phase === 'countdown') {
        const n = Math.max(1, Math.ceil(view.countdown));
        timer.textContent = 'READY';
        if (n !== lastCountdown) {
          lastCountdown = n;
          showCenter(String(n), 'big', 900);
        }
      } else if (view.lab) {
        lastCountdown = -1;
        timer.textContent = 'LAB'; // endless sandbox session
        timer.classList.remove('urgent');
      } else {
        lastCountdown = -1;
        const t = Math.max(0, Math.ceil(view.timeLeft));
        timer.textContent = `${Math.floor(t / 60)}:${String(t % 60).padStart(2, '0')}`;
        timer.classList.toggle('urgent', t <= 20 && view.phase === 'play');
      }

      // per-team flag status: solid = home, blink = loose, hollow-pulse =
      // being carried by the enemy (you can't score till it's back!)
      if (view.flags) {
        for (const team of ['red', 'blue']) {
          const f = view.flags[team];
          const el = flagInd.querySelector(`.fi-${team}`);
          el.style.color = TEAMS[team].color;
          el.classList.toggle('blink', f.st === 'drop');
          el.classList.toggle('carried', f.st === 'carry');
        }
      }

      const me = view.players.find((p) => p.id === myId);
      if (me) {
        hpbar.style.width = `${Math.max(0, me.hp)}%`;
        hpbar.classList.toggle('low', me.hp < 35);
        if (me.state === 'ko' && view.phase !== 'over') {
          respawn.classList.remove('hidden');
          respawn.innerHTML = `💥 KNOCKED OUT<span>back in ${Math.max(1, Math.ceil(me.respawn))}…</span>`;
        } else {
          respawn.classList.add('hidden');
        }
      }

      if (view.phase === 'over') {
        // build the card once (rewriting every frame restarts its pop
        // animation and freezes it at the first keyframe), tick only the text
        if (!overShown) {
          overShown = true;
          const w = view.winner;
          const title = w === 'draw'
            ? 'DRAW!'
            : `<span style="color:${TEAMS[w].color}">${TEAMS[w].name}</span> WINS!`;
          overPanel.innerHTML = `
            <div class="over-card">
              <div class="over-title">${title}</div>
              <div class="over-score">${view.scores.red} — ${view.scores.blue}</div>
              <div class="over-next"></div>
            </div>`;
          overPanel.classList.remove('hidden');
        }
        overPanel.querySelector('.over-next').textContent =
          `Next round in ${Math.max(1, Math.ceil(view.overT))}…`;
      } else if (overShown) {
        overShown = false;
        overPanel.classList.add('hidden');
      }
    },

    pushEvents(events, view, myId) {
      const tname = (team) => `<b style="color:${TEAMS[team].color}">${TEAMS[team].name}</b>`;
      const pname = (id) => {
        const p = view.players.find((p) => p.id === id);
        return p ? `<b style="color:${TEAMS[p.team].color}">${p.name}</b>` : 'Someone';
      };
      for (const ev of events) {
        switch (ev.t) {
          case 'flagSteal':
            pushFeed(`🚩 ${pname(ev.id)} stole the ${tname(ev.team)} flag!`);
            break;
          case 'flagThrow':
            pushFeed(`🚩 ${pname(ev.id)} hurled the ${tname(ev.team)} flag!`);
            break;
          case 'flagDrop':
            pushFeed(`🚩 The ${tname(ev.team)} flag is loose!`);
            break;
          case 'flagReturn':
            pushFeed(ev.by
              ? `🏳️ <b>${ev.by}</b> returned the ${tname(ev.team)} flag`
              : `🏳️ The ${tname(ev.team)} flag returned home`);
            break;
          case 'flagVoid':
            pushFeed(`🕳️ The ${tname(ev.team)} flag fell into the void!`);
            break;
          case 'scoreBlocked':
            if (ev.id === myId) pushFeed('🚫 Your flag must be home to score!', 'warn');
            break;
          case 'score':
            pushFeed(`🏆 ${name(ev)} captures for ${TEAMS[ev.team].name}!`);
            showCenter('CAPTURE!', `goal-${ev.team}`, 1500);
            break;
          case 'ko':
            pushFeed(
              ev.cause === 'fall' ? `🕳️ ${name(ev)} fell into the void`
              : ev.cause === 'punch' ? `👊 ${name(ev)} got knocked out`
              : `💥 ${name(ev)} was blown up`,
            );
            break;
          case 'playerThrow':
            pushFeed(`🤾 ${pname(ev.id)} threw ${pname(ev.target)}!`);
            break;
          case 'leave':
            pushFeed(`👋 ${ev.name} left`);
            break;
        }
      }
    },

    setConnecting(on) {
      connecting.classList.toggle('hidden', !on);
    },

    dispose() {
      clearTimeout(centerTimer);
      el.remove();
    },
  };
}
