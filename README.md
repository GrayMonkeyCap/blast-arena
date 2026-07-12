# 💣 Blast Arena — Capture the Flag

A Bombsquad-inspired 3D arena game that runs entirely in the browser.
Two teams, **two flags** (BombSquad rules): steal the enemy flag and bring
it to your base — but you can only score while your **own** flag is home.
First to 3 captures (or the best score at 3:00) wins. Solo vs bots works
offline; online multiplayer ships with a zero-dependency Node server.

Momentum is the whole game: running feeds punch damage, throw distance and
knockback; jumps preserve it; and everything — bombs, flags, even grabbed
players — is a physics object you can hurl downfield. Movement and combat
are matched value-for-value against BombSquad's open-source engine — see
[docs/bombsquad-parity.md](docs/bombsquad-parity.md) for the derivations.

![tech](https://img.shields.io/badge/three.js-r170-blue) — no build step,
no runtime CDN, no npm dependencies (three.js is vendored).

## Run it

```bash
npm install        # one-time: fetches three.js for vendoring (already vendored in repo)
npm start          # → http://localhost:8090  (game + multiplayer server)
```

Open the URL, pick a hat, hit **PLAY VS BOTS** — or **PLAY ONLINE** with a
room code and share `http://<your-lan-ip>:8090` with friends on your
network (phones included; the game is a mobile-first PWA-style page).

Solo play also works from any static file host — the Node server is only
required for online rooms.

```bash
npm run smoke      # headless sim sanity check (bots play 4 minutes + lab checks)
npm run tune       # physics core vs analytic theory (run after tuning)
```

## 🧪 Physics Lab

The menu's **PHYSICS LAB** is the dedicated playtest / physics-verification
space — a small dojo arena with knockback measurement rings, two practice
flags, a collision wall/crates, and an open rim for void tests. Two modes:
**training doll** (stationary dummy that ragdolls and walks back to its
post — consistent, repeatable interaction tests) and **live bot** (a
fighter that punches, bombs and grab-throws you). The on-screen lab panel
shows per-hit impulses, measured knockback distance/airtime, an event
ticker, slow-motion and scene reset. Test all future physics changes here.

## Controls

| Action | PC | Touch |
|---|---|---|
| Move (keys sprint; hold Shift to walk) | WASD / arrows | left joystick — push to the rim to run |
| Aim | mouse | drag the 💣 button |
| Bomb — pull out a LIT bomb (fuse burns in your hands!); press again to throw it. Throws whatever you hold (flag! player!) | left click | tap 💣, tap again (or drag + release) to throw |
| Punch (fists alternate; can't swing while holding something) | right click or F | 👊 button |
| Grab — steal flag, pick up bombs, hoist players overhead; press again to throw | E | ✋ button |
| Jump | Space | ⬆️ button |
| Exit match | Esc | ✕ button |

## The rules (BombSquad CTF)

- **Steal** the enemy flag with grab; **score** by getting it into your
  base — carried or *thrown in* — but only while your own flag is home.
- **Touch** your own dropped flag to return it instantly; untouched flags
  fly home after 30 seconds. Flags that fall off the map return at once.
- The flag is a **physics object**: it slides, bounces, gets punched and
  blast-shoved, and its carrier can **throw it downfield** (it inherits
  your full running momentum — the classic flag-relay play).
- **Punch power = your momentum.** A standing jab tickles (~4%); a full
  sprint punch takes ~40% and knocks the target **out cold** — an
  unconscious ragdoll for up to ~3s that wakes with its remaining hp.
- **Any damage makes you drop** whatever you're holding — flag, bomb, or
  a squirming player. There's no health regen; damage sticks until you
  respawn. Friendly fire is real, exactly as chaotic as it sounds.
- **Bombs**: the bomb button pulls out a *lit* bomb (3s fuse, burning in
  your hands — cook it for airbursts, or die holding it). One live bomb
  per player until yours goes off. Blasts pop everything up and out with
  equal force, chain nearby bombs, and a point-blank hit is lethal.
- **Impacts hurt**: wall slams at sprint speed, long falls, and being hit
  by a flying body all deal damage (with a mercy rule so ordinary bumps
  don't kill).
- **Grab players**: they dangle overhead — still punching (one clean
  pummel forces the drop), still able to grab you back — until you throw
  them (toward the void, ideally).

## What's in the box

- **Capture the Flag** on **Foundry Court**: three lanes, crate cover,
  bunker chokepoints, and open rim sections where knockback means a long
  fall.
- Expressive toon characters (run/jump/punch/throw/carry/stumble/KO
  animations, blinking, X-eyes) with team kits and cosmetic hats/skins.
- Physics bombs with burning fuses, escalating danger pulse, chain
  reactions, knockback, camera shake, scorch marks.
- 2v2 online multiplayer (server-authoritative, bots fill empty slots,
  scales via `teamSize` in `src/core/config.js`); bots steal, escort,
  chase thieves, return flags and throw momentum punches.
- All audio synthesized at runtime with WebAudio — zero asset downloads.

## Extending

Levels, game modes, and cosmetics are data + registry entries — see
[ARCHITECTURE.md](ARCHITECTURE.md) for the module map and step-by-step
extension recipes.
