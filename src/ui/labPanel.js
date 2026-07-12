// Physics-lab instrument panel (sandbox modes only). Live telemetry for the
// player and the test subject (doll / fighter bot), a knockback meter that
// measures hit -> rest distance and airtime, a compact event ticker, and
// lab controls (slow motion, scene reset). This panel — plus the floor
// rings in the dojo — is the measuring equipment for all physics testing.

export function createLabPanel(uiRoot, transport) {
  const el = document.createElement('div');
  el.className = 'lab';
  el.innerHTML = `
    <div class="lab-head">🧪 PHYSICS LAB <span class="lab-variant"></span></div>
    <div class="lab-stats"></div>
    <div class="lab-impact">last hit: —</div>
    <div class="lab-knock">knockback: —</div>
    <div class="lab-log"></div>
    <div class="lab-btns">
      <button class="lab-btn btn-slow">🐌 slow-mo</button>
      <button class="lab-btn btn-reset">♻️ reset scene</button>
    </div>
  `;
  uiRoot.appendChild(el);

  const stats = el.querySelector('.lab-stats');
  const impact = el.querySelector('.lab-impact');
  const knock = el.querySelector('.lab-knock');
  const log = el.querySelector('.lab-log');
  const slowBtn = el.querySelector('.btn-slow');

  let timeScale = 1;
  slowBtn.addEventListener('click', () => {
    timeScale = timeScale === 1 ? 0.25 : 1;
    slowBtn.classList.toggle('on', timeScale !== 1);
  });
  el.querySelector('.btn-reset').addEventListener('click', () => {
    const sim = transport.debug?.sim;
    if (sim) sim.mode.resetScene?.(sim);
  });

  const lines = [];
  function pushLog(text) {
    lines.push(text);
    if (lines.length > 7) lines.shift();
    log.innerHTML = lines.map((l) => `<div>${l}</div>`).join('');
  }

  // knockback meter: from the subject's hit position until it comes to rest
  let track = null; // { x, z, t, air, peakY }

  const fmt = (n, d = 1) => (n ?? 0).toFixed(d);

  return {
    get timeScale() { return timeScale; },

    update(view, events, myId, dt) {
      const me = view.players.find((p) => p.id === myId);
      const subject = view.players.find((p) => p.bot);

      // --- event ticker + knockback trigger
      for (const ev of events) {
        switch (ev.t) {
          case 'punchHit':
            pushLog(`👊 punchHit dmg=<b>${ev.dmg}</b>${ev.target === subject?.id ? ' (subject)' : ''}`);
            if (subject && ev.target === subject.id) track = { x: subject.x, z: subject.z, t: 0, air: 0, peakY: 0, label: `dmg=${ev.dmg}` };
            break;
          case 'explode':
            if (subject && Math.hypot(ev.x - subject.x, ev.z - subject.z) < 5) {
              track = { x: subject.x, z: subject.z, t: 0, air: 0, peakY: 0, label: 'blast' };
            }
            pushLog('💥 explode');
            break;
          case 'hurt':
            impact.innerHTML = `last hit: ${ev.id === subject?.id ? 'subject' : ev.id === myId ? 'YOU' : ev.id} → hp <b>${ev.hp}</b>`;
            break;
          case 'knockout': pushLog(`😵 knockout (${ev.id === subject?.id ? 'subject' : ev.id === myId ? 'you' : ev.id})`); break;
          case 'impact': pushLog(`💢 impact dmg=<b>${ev.dmg}</b> (${ev.id === subject?.id ? 'subject' : ev.id === myId ? 'you' : ev.id})`); break;
          case 'gripBreak': pushLog('✋💨 grip broken'); break;
          case 'bodySlam': pushLog(`🎳 body slam Δv=<b>${ev.j}</b>`); break;
          case 'bombOut': pushLog('💣 bomb out (fuse lit!)'); break;
          case 'grabPlayer': pushLog('🤝 player grabbed'); break;
          case 'playerThrow': pushLog('🤾 player thrown'); break;
          case 'jump': pushLog('⬆️ jump'); break;
          case 'bounce': break; // too chatty for the ticker
          case 'flagSteal': pushLog(`🚩 flag grabbed (${ev.team})`); break;
          case 'flagThrow': pushLog(`🚩 flag thrown (${ev.team})`); break;
          case 'flagReturn': pushLog(`🏳️ flag returned (${ev.team})`); break;
          case 'flagVoid': pushLog('🕳️ flag fell off!'); break;
          case 'ko': pushLog(`☠️ KO (${ev.cause})`); break;
          case 'labReset': pushLog('♻️ scene reset'); track = null; break;
        }
      }

      // --- knockback measurement
      if (track && subject) {
        track.t += dt;
        track.peakY = Math.max(track.peakY, subject.y);
        if (subject.y > 0.02) track.air += dt;
        const speed = Math.hypot(subject.vx ?? 0, subject.vz ?? 0);
        const settled = track.t > 0.15 && speed < 0.4 && subject.y <= 0.02;
        const dist = Math.hypot(subject.x - track.x, subject.z - track.z);
        if (settled || track.t > 4 || subject.state === 'ko') {
          knock.innerHTML = `knockback (${track.label}): <b>${fmt(dist)}u</b>, air ${fmt(track.air, 2)}s, peak h ${fmt(track.peakY, 2)}${subject.state === 'ko' ? ' ☠️' : ''}`;
          track = null;
        } else {
          knock.innerHTML = `knockback (${track.label}): ${fmt(dist)}u…`;
        }
      }

      // --- live telemetry
      if (me) {
        const rows = [
          `YOU&nbsp;&nbsp; spd <b>${fmt(me.spd)}</b> y <b>${fmt(me.y, 2)}</b> hp <b>${Math.round(me.hp)}</b>${me.carryFlag ? ' 🚩' : ''}${me.heldBomb ? ' 💣' : ''}${me.heldPlayer ? ' ✊' : ''}${me.knockT > 0 ? ' 😵' : ''}`,
        ];
        if (subject) {
          rows.push(
            `${subject.name.toUpperCase()} spd <b>${fmt(subject.spd)}</b> y <b>${fmt(subject.y, 2)}</b> hp <b>${Math.round(subject.hp)}</b> ${subject.state === 'ko' ? '☠️' : ''}${subject.knockT > 0 ? '😵' : ''}${subject.heldBy ? '🤝' : ''}`,
          );
        }
        rows.push(`bombs <b>${view.bombs.length}</b> · flags ${Object.values(view.flags ?? {}).map((f) => f.st).join('/')}${timeScale !== 1 ? ' · <b>SLOW-MO ×0.25</b>' : ''}`);
        stats.innerHTML = rows.join('<br>');
      }
      el.querySelector('.lab-variant').textContent = view.lab?.variant === 'doll' ? '· training doll' : '· live bot';
    },

    dispose() {
      el.remove();
    },
  };
}
