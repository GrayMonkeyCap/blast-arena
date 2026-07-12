# BombSquad gameplay parity — research & derivations

Blast Arena's mechanics are matched against **BombSquad's own engine
source** (the MIT-licensed [Ballistica](https://github.com/efroemling/ballistica)
project by Eric Froemling), which is far more precise than eyeballing
gameplay videos. This document records where each value comes from and how
it maps onto our sim, so future tuning stays anchored to ground truth.
Wiki/tutorial descriptions (e.g. "punches do more damage the faster your
fists are moving"; the tutorial dummy loses ~40% to a running punch) were
used to sanity-check the derived numbers.

Files referenced below (paths within the Ballistica repo):

- `src/ballistica/scene_v1/node/spaz_node.cc` — movement, jump, throw,
  punch power, knockout
- `src/ballistica/scene_v1/dynamics/rigid_body.cc` — impulse/damage math,
  blast falloff
- `src/ballistica/scene_v1/dynamics/dynamics.cc` — world constants
- `src/assets/ba_data/python/bascenev1lib/actor/spaz.py`, `spazfactory.py`
  — hp, cooldowns, punch message values, pickup rules
- `src/assets/ba_data/python/bascenev1lib/actor/bomb.py` — fuses, radii,
  blast magnitudes, chain reactions
- `src/assets/ba_data/python/bascenev1lib/game/capturetheflag.py`,
  `actor/flag.py`, `node/flag_node.cc` — CTF rules, flag body
- `src/assets/ba_data/python/bascenev1/_gameactivity.py` — respawn times

Scale conventions: BombSquad's 1000-hp scale maps to our 100-hp scale
(÷10). World units are comparable (their run speed ≈ ours; gravity −20 in
both after this change).

## Movement (spaz_node.cc)

BombSquad characters roll on a motorized ball (r = 0.3):

| Mechanic | BombSquad source | Blast Arena |
|---|---|---|
| Walk speed | motor target `7.68` rad/s × 0.3 r ≈ **2.3 m/s** | `player.walkSpeed 2.3` |
| Run speed | walk + `gear_high · run_gas · 15` → **≈ 6.8 m/s** | `player.runSpeed 6.8` |
| Run wind-up | `speed_smoothed_` (0.985/step up, 0.94 down), `gear_high = min(1, smoothed/7)` | `gearUp/gearDown/gearSpeed`, same smoothing at 60Hz |
| Motor force | `dParamFMax 15` (finite → wide turns at speed) | `player.accel 15` |
| Stopping | brake motor `10 × 0.4` × (stick released) — a gentle skid | `player.brakeDecel 8` |
| Air control | none — the ball has no traction off the ground | `player.airControl 0` |
| Run trigger | hold-any-button on pads; stick deflection on touch | keys sprint (Shift walks); touch stick walk-inner/run-outer |
| Jump | 7-step roller spring push, ~1.05m apex; `_jump_cooldown 250` ms | `jumpVel 6.5` (g −20 → 1.06m), `jumpCooldown 0.25` |
| Gravity | `dWorldSetGravity(0, -20, 0)` | `world.gravity -20` |
| Hold-position / picked-up | motor force → 0 (`balance_ = 0` when grabbed) | held victims get zero steering |

## Punch (spaz.py + spaz_node.cc + rigid_body.cc)

- Cooldown **400 ms** (`factory.punch_cooldown`); gloves 300 ms (no gloves
  here — no powerups yet). → `punch.cooldown 0.4`.
- The swing lasts ~35 steps with a live fist sphere (r 0.25) tracking the
  arm; one hit per node per swing. → swing window + per-swing hit sets.
- `punch_power` follows a sine over the 200 ms swing (0.7 → 1.0 → 0.7):
  connecting mid-swing hits hardest. → `timing` factor in `resolvePunch`.
- Damage: `HitMessage(magnitude = power·angular·110, velocity_magnitude =
  power·40)`; the receiving body computes `damage = 0.22 × Σ ApplyImpulse`
  with **head hits ×5**, and `ApplyImpulse` adds `|v_rel| × velocity_mag` —
  i.e. damage is dominated by **how fast the puncher's body moves**
  (`punch_momentum_linear` ≈ 1.8× body speed; spinning adds the angular
  term — the famous spin-punch). We can't spin, so we fit the curve:
  `dmg = (4 + 5.5·|v3D|) × timing`, capped 60 →
  standing ≈ 4 (a tickle), sprint ≈ 41 (the tutorial's "~40%"),
  sprint-jump ≈ 49.
- Knockback force = `total_mag × 1.8` along the fist direction with fy ×2
  → `kbPerDmg 0.28` + `liftFrac 0.45` (sprint punch Δv ≈ 11.6 + pop-up).
- Self kick-back: constant force 400 opposite (halved on ice) →
  `selfKick 1.2` Δv on first contact.
- **No punching while holding anything** (`!holding_something_` gate).
- A held victim can still punch the grabber (chip damage).
- No team filtering anywhere in the hit path — **friendly fire is real**.

## Knockout (spaz_node.cc)

A single hard hit puts a spaz out cold — unconscious full ragdoll, all
inputs ignored, wakes with remaining hp:

- `knockout = clamp(nodeDamage × 0.02 − 20, current, 40)` — on our hp scale
  `units = dmg × 0.909 − 20`, so hits under ~22 hp never knock out.
- Ticks down every 5 steps grounded / 10 airborne → `unitsPerSec 12`,
  halved in the air; max knockout 40/12 ≈ **3.3 s**.
- Knockout cancels a mid-swing punch and blocks punch/jump/pickup/bomb.

This replaces our old "stumble" (0.7s stagger at a fixed impulse
threshold). The old KO-means-dead flow stays for hp ≤ 0.

## Damage rules (spaz.py)

- `default_hitpoints = 1000` → hp 100. **No regeneration** — the only
  healing in BombSquad is the med-pack powerup.
- **Any damage > 0 drops whatever you hold** (`hold_node = None`), so
  grip-breaks are damage-driven, not impulse-threshold-driven.
- Impact damage: head jolts (Δv > 3) accumulate and dispatch as `impact`
  hits; the **mercy rule** softens lethal impacts:
  `dmg = max(dmg − 200, hp − 10)` → ours: `max(dmg − 20, hp − 1)`.
  Applied to wall slams (`wallMinDv 5`), hard landings (`floorMinDv 9` —
  legs cushion normal jumps), and body-vs-body slams (`pairMinDv 4`).
- Spawn invincibility: exactly **1.0 s** (`spaz.py` timer). Invincible
  players can't be damaged or picked up.
- Respawn: teams-game default **5 s** for 2-player teams
  (`_gameactivity.py`: 3/5/6/7s by team size × the Respawn Times setting).

## Bombs (bomb.py, spaz.py)

- Fuse **3.0 s**, and it burns while held — the bomb button *pulls out a
  lit bomb held overhead*; pressing again (or pickup) throws it. Cooking
  is a core skill; so is dying with a bomb in your hand.
- `default_bomb_count = 1`: one live bomb per player until yours explodes
  (count restored via the bomb's death action) → `bomb.perPlayer 1`.
- Blast radius **2.0** (normal). Ours is 2.5 — our bodies are fatter
  (r 0.55 vs ~0.3–0.4) and we have no per-limb hit detection, so the extra
  0.5 approximates limb reach. Damage falls **linearly to zero** at the
  edge (`amt = 1 − d/r`, no floor); point-blank ≈ 2000 node units ≈ lethal.
- Blast force is scaled by the target's mass (`this_mag = mag·amt·mass`) —
  every body gets the **same Δv**, with horizontal ×0.5 / upward ×2.0
  ("pop things up"): → `blastKick()` with `blastDvXZ 7.5 / blastDvY 9.5`.
- Bombs caught in a blast are kicked and **detonate 0.1–0.2 s later**
  (random) — chain reactions. Punch hits do *not* trigger normal bombs.

## Throws (spaz_node.cc)

- One universal throw: bomb button *and* pickup button hurl whatever is
  held. Direction from the stick (or facing), force ≈ 45° up-forward.
- `throw_power = 0.8 × (0.6 + 0.4 × stickMag)` → power range ≈ 0.6–1.0 of
  max → `throw.speedMin 6 / speedMax 10` on our aim-distance mapping.
- Held < 500 ms → power scaled ×0.4–1.0 (`since_pick_up / 500`) →
  `quickWindow 0.5 / quickMin 0.4`.
- The throw force adds to the carried object's velocity → **full momentum
  inheritance** (`inherit 1.0`): a sprint throw sails ~2× a standing one.
- Thrower kick-back −0.25× → `kickback 1.2` Δv.
- Thrown players: same throw, heavier (`playerMult 0.65`), tumble on
  release.

## Pickup (spaz.py, spaz_node.cc)

- Pickup attempts rate-limited (~0.66 s, `kPickupCooldown 40` steps);
  hitbox appears 4 steps after the press.
- Can't pick up invincible (spawn-protected) players.
- A picked-up spaz loses all steering (`balance_ = 0`) but keeps punching
  — one clean pummel forces a drop (any damage drops held things).
- Held things ride overhead (`hold_height 1.08` above the torso).
- While holding a flag, pickup won't swap it for something else.

## Capture the Flag (capturetheflag.py)

| Rule | BombSquad default | Blast Arena |
|---|---|---|
| Score to win | 3 | `rules.captureLimit 3` |
| Flag Touch Return Time | 0 (instant return on touch) | `flag.returnOnTouch true` |
| Flag Idle Return Time | 30 s | `flag.idleReturn 30` |
| Score gate | own flag must be at base | same |
| Scoring trigger | the enemy flag **entering the base region** — carried *or loose* (a thrown flag can score) | carrier check + loose-flag base entry |
| On score | both flags reset; carrier credited (+50) | same (lastCarrier credited) |
| Flag off the map | dies → respawns at base | same |
| Flag body | cylinder, mass r 0.3 × h 1.0, density 1 (light) | `materials.flag mass 1.0` |
| Time limit | none by default (configurable) | 180 s (product choice) |

## Known remaining gaps (not yet 1:1)

- **Powerups** — boxing gloves, shields, sticky/ice/impact bombs, land
  mines, TNT crates, med-packs, curse. A large system (spawn schedule,
  per-type bombs, shield hp 650 with spillover); not implemented.
- **Spin punches** — we have no free spinning, so the angular-momentum
  damage term is folded into the speed curve. Max gloveless damage is
  slightly lower than a perfect BombSquad spin punch.
- **True per-limb ragdoll** — knockout/KO poses are animated, not
  simulated; per-part (head ×5) damage is approximated by the whole-body
  hit model.
- **Mutual grabs** — in BombSquad both players can hold each other
  overhead simultaneously (chaotic dangling); we resolve it as a grounded
  wrestling lock.
- **Epic mode, co-op, other modes** — out of scope.

## How to re-verify

`npm run tune` asserts the headline numbers (sprint punch ≈ 41, standing
jab 4, point-blank blast lethal + uniform Δv, sprint throw ≈ 2× standing).
`npm run smoke` plays a full bot match headlessly (bombs are pulled, cooked
and thrown; knockouts and impacts fire). The Physics Lab (menu → 🧪) is the
hands-on space: the lab panel now logs `knockout`, `impact dmg=…` and
`bomb out` events with per-hit damage.
