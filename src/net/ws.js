// Online transport: WebSocket client. The server runs the authoritative
// sim; we send our input at ~30Hz and render interpolated snapshots ~100ms
// behind the newest one, which hides network jitter at LAN/regional
// latencies. (Client-side prediction is the documented next step if
// long-haul play ever needs it — see ARCHITECTURE.md.)

import { clamp, lerp, angleLerp } from '../core/math.js';

const INTERP_DELAY_TICKS = 6; // sim runs 60Hz, snapshots every 3 ticks

function interpPlayers(aList, bList, t) {
  const byIdA = new Map(aList.map((p) => [p.id, p]));
  return bList.map((b) => {
    const a = byIdA.get(b.id);
    if (!a || a.state !== b.state) return { ...b };
    return {
      ...b, // discrete fields (hp, carryFlag, state, ...) from the newer snap
      x: lerp(a.x, b.x, t),
      z: lerp(a.z, b.z, t),
      y: lerp(a.y, b.y, t),
      spd: lerp(a.spd, b.spd, t),
      face: angleLerp(a.face, b.face, t),
    };
  });
}

function interpState(a, b, t) {
  const out = { ...b };
  out.players = interpPlayers(a.players, b.players, t);
  const byIdA = new Map(a.bombs.map((x) => [x.id, x]));
  out.bombs = b.bombs.map((bb) => {
    const aa = byIdA.get(bb.id);
    return aa
      ? { ...bb, x: lerp(aa.x, bb.x, t), z: lerp(aa.z, bb.z, t), y: lerp(aa.y, bb.y, t) }
      : { ...bb };
  });
  if (a.powerups && b.powerups) {
    const puA = new Map(a.powerups.map((x) => [x.id, x]));
    out.powerups = b.powerups.map((u) => {
      const aa = puA.get(u.id);
      return aa
        ? { ...u, x: lerp(aa.x, u.x, t), z: lerp(aa.z, u.z, t), y: lerp(aa.y, u.y, t) }
        : { ...u };
    });
  }
  if (a.flags && b.flags) {
    out.flags = {};
    for (const team of Object.keys(b.flags)) {
      const fa = a.flags[team];
      const fb = b.flags[team];
      out.flags[team] = fa && fa.st === fb.st
        ? { ...fb, x: lerp(fa.x, fb.x, t), z: lerp(fa.z, fb.z, t), y: lerp(fa.y, fb.y, t) }
        : { ...fb };
    }
  }
  return out;
}

export function connectOnline({ room, profile, onDropped }) {
  return new Promise((resolve, reject) => {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    const ws = new WebSocket(`${proto}://${location.host}/ws?room=${encodeURIComponent(room)}`);
    let settled = false;
    const snaps = [];
    const eventQ = [];
    let myId = null;
    let levelId = null;
    let lastSent = 0;
    let latestInput = null;
    let closed = false;

    const fail = (e) => {
      if (!settled) { settled = true; reject(e); }
    };
    ws.onerror = () => fail(new Error('connection failed'));
    ws.onclose = () => {
      if (!settled) return fail(new Error('connection closed'));
      if (!closed) onDropped?.();
    };

    ws.onmessage = (msg) => {
      let m;
      try { m = JSON.parse(msg.data); } catch { return; }
      if (m.t === 'welcome') {
        myId = m.id;
        levelId = m.levelId;
        settled = true;
        resolve(transport);
      } else if (m.t === 'snap') {
        snaps.push(m.s);
        if (snaps.length > 40) snaps.shift();
        if (m.e?.length) eventQ.push(...m.e);
      }
    };

    ws.onopen = () => {
      ws.send(JSON.stringify({ t: 'join', name: profile.name, cos: profile.cos }));
    };

    const transport = {
      kind: 'online',
      get myId() { return myId; },
      get levelId() { return levelId; },

      setInput(input) {
        latestInput = input;
        const now = performance.now();
        if (ws.readyState === WebSocket.OPEN && now - lastSent > 33) {
          lastSent = now;
          ws.send(JSON.stringify({ t: 'input', i: latestInput }));
        }
      },

      update() {}, // sim runs on the server

      view() {
        if (snaps.length === 0) return null;
        if (snaps.length === 1) return snaps[0];
        const latest = snaps[snaps.length - 1];
        const target = latest.tick - INTERP_DELAY_TICKS;
        for (let i = snaps.length - 1; i > 0; i--) {
          if (snaps[i - 1].tick <= target) {
            const a = snaps[i - 1];
            const b = snaps[i];
            const t = clamp((target - a.tick) / (b.tick - a.tick || 1), 0, 1);
            return interpState(a, b, t);
          }
        }
        return latest;
      },

      drainEvents() {
        return eventQ.splice(0, eventQ.length);
      },

      dispose() {
        closed = true;
        try { ws.close(); } catch { /* already gone */ }
      },
    };

    setTimeout(() => fail(new Error('timeout')), 5000);
  });
}
