# 💣 Blast Arena — Capture the Flag

A Bombsquad-inspired 3D arena game that runs entirely in the browser.
Two teams, **two flags** (BombSquad rules): steal the enemy flag and bring
it to your base — but you can only score while your **own** flag is home.
First to 3 captures (or the best score at 3:00) wins. Solo vs bots works
offline; online multiplayer ships with a zero-dependency Node server.

Momentum is the whole game: running feeds punch damage, throw distance and
knockback; jumps preserve it; and everything — bombs, flags, even grabbed
players — is a physics object you can hurl downfield.

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
| Move | WASD / arrows | left-side virtual joystick |
| Aim | mouse | drag the 💣 button |
| Throw — bomb, or whatever you hold (flag! player!) | left click | tap 💣 (quick) or drag + release (aimed) |
| Punch (fists alternate right/left) | right click or F | 👊 button |
| Grab — steal flag, pick up bombs, hoist players overhead; press again to toss with your momentum | E | ✋ button |
| Jump | Space | ⬆️ button |
| Exit match | Esc | ✕ button |

## The rules (BombSquad CTF)

- **Steal** the enemy flag with grab; **score** by bringing it to your base
  — but only while your own flag is home.
- **Touch** your own dropped flag to return it instantly; untouched flags
  fly home after 30 seconds. Flags that fall off the map return at once.
- The flag is a **physics object**: it slides, bounces, gets punched and
  blast-shoved, and its carrier can **throw it downfield** (it inherits
  your running momentum — the classic flag-relay play).
- **Punch power = your momentum.** A standing jab tickles; a running jump
  punch launches people. A hard enough hit staggers the target and knocks
  the flag (or a held bomb, or a grabbed player) right out of their hands.
- **Grab players**: they dangle from your hand — still struggling, still
  punching — until you throw them (toward the void, ideally). Mutual grabs
  are legal and exactly as chaotic as they sound.
- Live bombs can be grabbed and re-thrown; fuses (~3s) keep burning, and
  explosions chain nearby bombs.

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
