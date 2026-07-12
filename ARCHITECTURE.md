# Blast Arena — Architecture

Bombsquad-inspired browser CTF (two-flag rules, momentum-driven combat).
The design goal is **one game codebase with three hard seams** —
simulation / rendering / transport — so that levels, modes, and cosmetics
are added by writing *data + registry entries*, not by touching core
systems.

```
                 ┌────────────────────────────────────────────┐
                 │              PURE SIMULATION               │
                 │  src/game/sim.js      fixed-step physics,  │
                 │  src/game/modes/*     bombs, KOs, rounds   │
                 │  src/game/bots.js     (no DOM, no three)   │
                 │  src/game/host.js  ←  GameHost: sim+bots   │
                 └───────▲────────────────────────▲───────────┘
                         │ runs in page           │ runs in Node
              ┌──────────┴─────────┐   ┌──────────┴──────────┐
              │ net/local.js       │   │ server/server.js    │
              │ (solo vs bots,     │   │ static + WebSocket  │
              │  zero latency)     │   │ rooms, 20Hz snaps   │
              └──────────▲─────────┘   └──────────▲──────────┘
                         │      same interface    │ net/ws.js (interp client)
                 ┌───────┴────────────────────────┴───────────┐
                 │                 VIEW LAYER                  │
                 │  render/world.js  syncs view-state → scene  │
                 │  render/*         characters, bombs, flag,  │
                 │  ui/*  input/*    level, effects, HUD, menu │
                 └─────────────────────────────────────────────┘
```

## The three seams

### 1. Simulation is pure and serializable
`src/game/**` has **zero** DOM/three imports. `sim.state` is a plain JSON
object — it *is* the network snapshot (`net/protocol.js` just rounds floats).
The browser runs it directly for solo play; the Node server imports the very
same modules for online play. Fixed 60Hz timestep; all gameplay randomness
lives here, never in the renderer.

### 2. Transports share one interface
```js
{ myId, levelId, setInput(input), update(dt), view() -> state|null,
  drainEvents() -> [], dispose() }
```
- `net/local.js` wraps a `GameHost` in-page (view = live sim state).
- `net/ws.js` talks to the server, renders snapshots interpolated ~100ms
  behind the newest tick (hides jitter at LAN/regional latency).
`main.js` cannot tell them apart.

### 3. Rendering consumes view-state, never game objects
`render/world.js` diffs `view().players/bombs/flag` against its entity-view
maps (create/update/dispose) each frame, and turns **sim events**
(`explode`, `score`, `ko`, …) into effects. Sound (`audio/sfx.js`, all
WebAudio-synthesized) and HUD messages hang off the same event stream.

## Extension recipes

**Add a level** — create `src/content/levels/<id>.js` (bounds, `solids[]`
boxes with a `kind` for styling, bases, spawns, flag position, decor), then
register it in `levels/index.js`. Collision *and* rendering derive from the
same `solids` list, so the level plays and draws correctly with no other
changes. Box `h` matters: bombs and launched players fly over low cover.

**Add a game mode** — create `src/game/modes/<id>.js` exporting hooks
`{ id, init(sim), tick(sim, dt), onKO(sim, p), tryGrab?(sim, p),
dropCarried?(sim, p, vx, vz), throwCarried?(sim, p, dir, power),
onExplosion?(sim, x, z, r), onPunchObject?(sim, x, z, r, dir, impulse) }`,
register in `modes/index.js`. The optional hooks let a mode own physical
objects the core knows nothing about (CTF's two flags: stolen via
`tryGrab`, knocked loose via `dropCarried` when a hard hit breaks the
carrier's grip, hurled via `throwCarried`, shoved by blasts/punches via
the last two). Keep all mode state inside `sim.state` so it serializes
into snapshots for free (see `ctf.js` — the flags live at
`sim.state.flags.{red,blue}`, each a full projectile body).

**Add a cosmetic** — add an entry to `HATS`/`SKINS` in
`src/content/cosmetics.js` and (for hats) a builder case in
`buildHat()` in `render/characters.js`. It automatically appears in the menu
picker, persists in the local profile, travels over the network, and shows
on other players. Cosmetics never affect gameplay.

**Tune gameplay** — everything numeric lives in `src/core/config.js`
(speeds, fuse, blast radius/knockback, round rules, respawn/invuln timers).

## Networking details

- Zero-dependency server: `server/server.js` implements the RFC 6455
  handshake and frame codec by hand over `node:http` (~100 lines), serves
  the static bundle, and hosts rooms (`/ws?room=<code>`).
- Client→server: `join` (name + cosmetics), `input` at ~30Hz.
- Server→client: `welcome` (id, levelId), `snap` (packed state + events) at
  20Hz. A 2v2 snapshot is ~2KB of JSON.
- Humans replace bots on join; bots replace humans on disconnect; empty
  rooms are destroyed after 60s.
- Inputs are pulses, not edges: taps are held ~120ms by `input/input.js` so
  a click is never lost between sim ticks or 30Hz sends; the sim
  edge-detects (`sim.prevIn`).

## Physics core (`src/game/physics.js`)

One impulse-based rigid-body module moves everything — players, bombs,
flags — with per-kind **materials** (`config.physics.materials`: mass,
restitution e, friction μ, damping). Modeled on the BombSquad physics
research (`deep-research-report.md`, ODE-style dynamics):

- **Impulse collisions**: `j = -(1+e)·v_rel·n / (1/m_A + 1/m_B)`; equal
  masses exchange momentum, light props get flung (Δv = J/m). Used for
  body-vs-wall, body-vs-floor, player-vs-player, bomb-vs-player (you can
  kick a resting bomb; a thrown bomb bonks and caroms), player-vs-flag.
- **Coulomb friction**: tangential impulse clamped to μ·(normal impulse)
  at contacts, plus μ·g ground deceleration — slide distances follow
  v²/2μg, which is what `npm run tune` asserts.
- **Damping & stability**: ODE-style per-step `v *= (1-damp)`, a max-speed
  clamp, a sleep threshold for resting bodies, and substep CCD so fast
  bodies can't tunnel through thin walls.
- **Punch** = fist as a moving collider: fist speed = swing + body speed
  (+ air bonus); the impulse formula above (fist effective mass 2.5) yields
  damage AND knockback, so momentum is the weapon. Impulse thresholds — not
  damage — decide grip-breaks (`grab.breakImpulse`) and stumbles
  (`player.stumbleImpulse`), including for player-player body slams.
- **Grabs** (BombSquad carry rules): a grabbed player is hoisted OVERHEAD
  like an item and rides the grabber; anything held (flag/bomb/player) is
  carried with both hands. The victim can pummel the grabber (chip damage)
  or grab back, forcing a grounded MUTUAL grapple where the pair moves by
  the average of both players' steering (equal strength; opposed inputs
  stalemate, shuffle friction stops coasting). Grab again = mild
  momentum-flavored toss; the throw button is the strong aimed hurl —
  either from a mutual grapple breaks both grips. (`physics.springDamper`
  remains a validated utility for future soft constraints.)
- **Blasts** apply radial impulses with linear falloff; per-kind impulse
  magnitudes approximate pressure × exposed area.
- **Stumble** approximates active-ragdoll balance loss (control lost
  ~0.7s + wobble); KO is the full flop. Punch cooldown (0.75s) is longer
  than stumble recovery so there is no infinite punch-lock.

`npm run tune` runs the physics against analytic predictions (bounce peak
= e²h, stop time = v/μg, momentum conservation, throw range, spring settle)
and fails CI-style if a change breaks theory. Tune materials/impulses in
config, then run `npm run tune && npm run smoke`.

## Known limitations / next steps

- **Approximated ragdoll.** BombSquad simulates jointed active-ragdoll
  bodies; we approximate with the stumble/tumble state machine above.
  True per-limb physics would need a constraint solver in the sim layer.
- **No client-side prediction.** Online play renders ~100–150ms behind your
  input. Fine for LAN/regional; long-haul play wants prediction +
  reconciliation on the local player (the seam for it is `net/ws.js`).
- **Full-state snapshots.** Delta compression is unnecessary at this scale
  but the `protocol.js` seam is where it would go.
- **Solo pause.** The local game runs on rAF, so a hidden tab pauses the
  match (deliberate for solo). Online matches keep running server-side.
- **Gamepad** input would slot into `input/input.js` alongside keyboard.

## Testing

**All physics/interaction testing happens in the Physics Lab** — menu →
🧪 PHYSICS LAB. It is a real mode + level built on the normal extension
seams (`modes/sandbox.js` + `levels/dojo.js`), with two variants:

- **Training doll** (`sandbox-doll`): a stationary dummy at the east post.
  It takes hits, ragdolls, respawns in 1.5s and walks itself back to its
  post — repeatable, consistent interaction tests. Floor rings at r=2/4/6
  measure knockback visually.
- **Live bot** (`sandbox-duel`): a fighter brain that punches, jump-punches,
  bombs predictively and occasionally grab-throws — combat playtesting
  under pressure. Two neutral practice flags sit on stands for grab /
  carry / throw / kick testing in both variants.

The in-match **lab panel** (`ui/labPanel.js`) shows live telemetry (speed,
height, hp, held objects, stumble state), per-hit impulse j, a knockback
meter (hit → rest distance, airtime, peak height), an event ticker
(punchHit / gripBreak / stumble / grabs / throws), plus **slow-mo (×0.25)**
and **scene reset** buttons. Lab sessions are endless (no timer/score) and
use `makeLabConfig()` overrides (fast respawn, minimal spawn protection).

Automated layers underneath:

- `npm run tune` — the physics core vs analytic theory (bounce = e²h,
  stop time = v/μg, momentum conservation, throw range, spring settle).
- `npm run smoke` — headless 4-minute CTF bot match + lab-duel and
  lab-doll wiring checks (fighter fights; doll stands, ragdolls, returns).
- `window.__blast` in-page debug hook: `{ transport, input, world, step,
  renderer }`; `step(now)` pumps one full frame manually (used for
  deterministic browser testing).
