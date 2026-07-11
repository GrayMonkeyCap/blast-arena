// Procedural sound effects via WebAudio — no audio files, works offline.
// Each effect is synthesized from oscillators + filtered noise. Spatial
// falloff comes from a simple distance gain passed by the caller.

export function createSfx() {
  let ctx = null;
  let master = null;
  let muted = localStorage.getItem('blast.muted') === '1';

  function ensure() {
    if (!ctx) {
      ctx = new (window.AudioContext || window.webkitAudioContext)();
      master = ctx.createGain();
      master.gain.value = muted ? 0 : 0.5;
      master.connect(ctx.destination);
    }
    if (ctx.state === 'suspended') ctx.resume();
    return ctx;
  }

  function env(node, t0, peak, dur) {
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t0);
    g.gain.exponentialRampToValueAtTime(Math.max(peak, 0.001), t0 + 0.008);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    node.connect(g);
    g.connect(master);
    return g;
  }

  function osc(type, f0, f1, dur, peak, delay = 0) {
    const t0 = ctx.currentTime + delay;
    const o = ctx.createOscillator();
    o.type = type;
    o.frequency.setValueAtTime(f0, t0);
    o.frequency.exponentialRampToValueAtTime(Math.max(f1, 1), t0 + dur);
    env(o, t0, peak, dur);
    o.start(t0);
    o.stop(t0 + dur + 0.05);
  }

  function noise(dur, type, f0, f1, peak, delay = 0) {
    const t0 = ctx.currentTime + delay;
    const len = Math.ceil(ctx.sampleRate * dur);
    const buf = ctx.createBuffer(1, len, ctx.sampleRate);
    const data = buf.getChannelData(0);
    for (let i = 0; i < len; i++) data[i] = Math.random() * 2 - 1;
    const src = ctx.createBufferSource();
    src.buffer = buf;
    const filter = ctx.createBiquadFilter();
    filter.type = type;
    filter.frequency.setValueAtTime(f0, t0);
    filter.frequency.exponentialRampToValueAtTime(Math.max(f1, 10), t0 + dur);
    src.connect(filter);
    env(filter, t0, peak, dur);
    src.start(t0);
  }

  const fx = {
    explode(v = 1) {
      noise(0.65, 'lowpass', 1100, 90, 0.9 * v);
      osc('sine', 75, 26, 0.5, 0.8 * v);
      noise(0.08, 'highpass', 2000, 4000, 0.25 * v);
    },
    throw(v = 1) { noise(0.2, 'bandpass', 500, 2600, 0.22 * v); },
    bounce(v = 1) { noise(0.06, 'lowpass', 700, 250, 0.12 * v); },
    grab() { osc('triangle', 500, 780, 0.1, 0.25); },
    flagTaken() {
      [523, 659, 784].forEach((f, i) => osc('triangle', f, f, 0.13, 0.22, i * 0.07));
    },
    flagDrop() { osc('triangle', 392, 300, 0.2, 0.22); },
    flagReturn() {
      [330, 523].forEach((f, i) => osc('triangle', f, f, 0.12, 0.2, i * 0.08));
    },
    score() {
      [523, 659, 784, 1046].forEach((f, i) => osc('square', f, f, 0.14, 0.12, i * 0.09));
      noise(0.5, 'highpass', 3000, 6000, 0.08, 0.1);
    },
    ko() { osc('sawtooth', 260, 68, 0.42, 0.32); },
    hurt() { osc('sine', 160, 90, 0.12, 0.3); noise(0.06, 'lowpass', 500, 200, 0.2); },
    punch(v = 1) { noise(0.09, 'bandpass', 900, 2800, 0.16 * v); }, // whoosh
    punchHit(v = 1) {
      noise(0.07, 'lowpass', 700, 200, 0.35 * v); // thwack
      osc('sine', 140, 80, 0.1, 0.3 * v);
    },
    jump() { osc('triangle', 320, 520, 0.09, 0.1); },
    grabPlayer() { osc('triangle', 420, 300, 0.12, 0.22); },
    playerThrow(v = 1) { noise(0.22, 'bandpass', 400, 1800, 0.24 * v); },
    spawn() { osc('triangle', 600, 900, 0.1, 0.12); },
    tick() { osc('square', 700, 700, 0.07, 0.14); },
    go() { osc('square', 1040, 1040, 0.18, 0.16); },
    denied() { osc('square', 180, 150, 0.12, 0.18); },
    win() {
      [523, 659, 784, 1046, 784, 1046].forEach((f, i) => osc('triangle', f, f, 0.16, 0.16, i * 0.11));
    },
    lose() { [392, 330, 262].forEach((f, i) => osc('triangle', f, f, 0.22, 0.16, i * 0.16)); },
    click() { osc('square', 1500, 1200, 0.04, 0.08); },
  };

  return {
    get muted() { return muted; },
    unlock() { ensure(); },
    toggle() {
      muted = !muted;
      localStorage.setItem('blast.muted', muted ? '1' : '0');
      if (master) master.gain.value = muted ? 0 : 0.5;
      return muted;
    },
    play(name, vol = 1) {
      if (muted) return;
      try {
        ensure();
        fx[name]?.(vol);
      } catch { /* audio is never worth crashing over */ }
    },
  };
}
