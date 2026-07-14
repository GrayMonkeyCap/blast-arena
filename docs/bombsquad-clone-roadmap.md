# BombSquad full-clone roadmap

Goal: a complete BombSquad copy — **all mechanics, all game modes, all
maps, all items, all bot types** — built on Blast Arena's existing engine.
Content lists below are taken from the BombSquad engine source
(github.com/efroemling/ballistica, MIT); mechanics values are derived in
[bombsquad-parity.md](bombsquad-parity.md). Ground truth is always the
Ballistica source, never memory or gameplay videos.

Legend: ✅ done · 🔨 in progress (spawned task) · ⬜ not started.

## Confirmed scope (2026-07-12)

- **Ceiling: everything, including the cloud layer (§6 Tier C).** Full
  parity is the target — mechanics, all modes/maps/bots/characters, the
  meta shell, *and* accounts, tournaments, leaderboards and online v2.
  Tier C is the longest pole and effectively its own project; it comes
  last, but it is in scope.
- **Fidelity: gameplay-faithful, our own stylized art.** Match every
  map's collision layout, flow and mode-support and every character's
  role — but art and audio stay our toon/WebAudio style, not pixel/asset
  recreations of BombSquad. This is a deliberate decision, not a gap.
- **Sequencing: none started yet.** This roadmap is the current
  deliverable; individual phases kick off on request.

---

## 0. Where we are

The architecture already has the three seams a clone needs (see
ARCHITECTURE.md): **pure serializable sim**, **transport interface**,
**view layer**. Levels, modes and cosmetics are already *data + registry*
entries, so most of this roadmap is content authored against stable seams,
not core-engine surgery.

**Core mechanics already matched to BombSquad** (✅):
roller-ball movement (walk/run gear, no air control), momentum punch,
knockout ragdoll, universal 45° throw with full momentum inheritance,
lit-bomb-in-hand with cooking + one-live-bomb rule, mass-normalized blasts
with linear falloff, chain reactions, impact damage (walls/falls/slams)
with mercy rule, damage-drops-held-items, no-regen, grabs (overhead carry +
mutual grapple), CTF two-flag rules incl. thrown-flag scoring, spawn invuln,
respawn timing.

**In flight** (🔨): powerup system (`task_887dc988`, running now) — boxing
gloves, shield, health, curse, and the special-bomb types below.

**Landed since**: **Death Match** (§2a) — team kills, first to
`killsToWin × largest-team-size` wins. This also added **kill attribution**
to the sim core (`player.lastHitBy`/`lastHitByT`: attacker id threaded
through punches and bombs, preserved through falls so a shove-off-the-edge
credits the shover) — a primitive DeathMatch, and later modes
(Elimination, Chosen One), build on. Selectable from a new menu mode
picker; flag UI hides itself for flag-less modes.

---

## 1. Complete the item / combat system

Everything a powerup can grant. Most of this rides in the spawned powerup
task; the remaining pieces are the special bomb behaviors and status
effects. Source: `actor/bomb.py`, `actor/powerupbox.py`, `actor/spaz.py`.

| Item | Behavior | Source | State |
|---|---|---|---|
| Triple bombs | `bomb_count` 1→3 | powerupbox/spaz | 🔨 |
| Boxing gloves | punch power ×1.4, cooldown 300ms, super-punch | spaz `equip_boxing_gloves` | 🔨 |
| Shield | 650 shield-hp bubble, decays, spillover at 500 | spaz `set_shielded` | 🔨 |
| Health pack | full heal | spaz | 🔨 |
| Curse | 5s countdown → self-explode unless health grabbed | spaz `curse` | 🔨 |
| **Ice bomb** | freeze on blast; frozen bodies shatter on hard hit | bomb `ice`, freeze/shatter | ⬜ |
| **Impact bomb** | explodes on contact after 0.2s arm | bomb `impact` | ⬜ |
| **Sticky bomb** | sticks to surfaces/players, 3s fuse | bomb `sticky` + splat | ⬜ |
| **Land mine** | armed after 1.25s, triggered by contact, ×3 pack | bomb `land_mine` | ⬜ |
| **TNT box** | map-spawned, respawning, ×2 blast, big shake | bomb `tnt` + `TNTSpawner` | ⬜ |
| Powerup box | floating crate, spawn schedule + distribution | `powerupbox.py` | 🔨 |

New mechanics this phase introduces to the sim core:
- **Freeze / shatter** state (ice): frozen = locked rigid, `>200` dmg or
  hp≤0 while frozen shatters into pieces.
- **Status timers** on players (shield hp, boxing gloves, curse countdown).
- **Contact-triggered bombs** (impact/mine/sticky): the bomb needs
  collision callbacks the sim can raise (`onBombContact`).

---

## 2. Game modes (all 18)

Modes plug in via the existing hook object (`modes/index.js`). Build in
this order — versus first (reuses CTF-era systems), co-op last (needs the
bot-wave system + campaign scaffolding).

### 2a. Versus — team & free-for-all
| Mode | Core rule | Source | State |
|---|---|---|---|
| Death Match | team kills, first to `killsToWin × team size` | `deathmatch.py` | ✅ |
| Capture the Flag | two-flag, done | `capturetheflag.py` | ✅ |
| Team Flag / Keep Away | hold the neutral flag to bank time | `keepaway.py` | ⬜ |
| King of the Hill | hold the zone to bank time | `kingofthehill.py` | ⬜ |
| Conquest | capture all flags on the map | `conquest.py` | ⬜ |
| Assault | reach & bomb the enemy base | `assault.py` | ⬜ |
| Elimination | shared lives, last team standing | `elimination.py` | ⬜ |
| Chosen One | be "it" longest; killing the chosen makes you it | `chosenone.py` | ⬜ |
| Hockey | puck into the goal (ice friction, no bombs) | `hockey.py` | ⬜ |
| Football | carry/throw the "ball" (flag) to the end zone | `football.py` | ⬜ |
| Race | laps around checkpoints | `race.py` | ⬜ |

Reusable sim primitives these need: **scoring zones** (KotH/Keep Away),
**flag networks** (Conquest), **shared-lives** (Elimination), **an "it"
token** (Chosen One), **a puck/ball body** (Hockey/Football — already have
flag-as-physics-object), **checkpoints + laps** (Race), **map hockey
friction flag** (already read `is_hockey` in spaz punch).

### 2b. Co-op (vs waves of bots)
| Mode | Core rule | Source | State |
|---|---|---|---|
| Onslaught | survive escalating bot waves | `onslaught.py` | ⬜ |
| Runaround | tower-defense: keep bots off the path | `runaround.py` | ⬜ |
| Meteor Shower | survive falling bombs | `meteorshower.py` | ⬜ |
| Target Practice | bomb the spawning targets | `targetpractice.py` | ⬜ |
| The Last Stand | score as much as you can before dying | `thelaststand.py` | ⬜ |
| Ninja Fight | melee-only bot survival | `ninjafight.py` | ⬜ |
| Easter Egg Hunt | collect eggs (seasonal) | `easteregghunt.py` | ⬜ |

Co-op depends on **§4 bot roster** and a **wave/spawn director**
(`actor/spawner.py`, per-mode wave tables) plus a **co-op scoring/level
progression** shell.

---

## 3. Maps (all 17)

Maps are `solids[]` + bases + spawns + flag/zone points + decor + theme
(see the "add a level" recipe in ARCHITECTURE.md). Each map advertises the
mode types it supports — author geometry to match. Fidelity target: match
the **collision layout and play flow**, art can be stylized.

| Map | Supports | State |
|---|---|---|
| Football Stadium | melee, football, team_flag, keep_away | ⬜ |
| Hockey Stadium | melee, hockey, team_flag, keep_away | ⬜ |
| Bridgit | melee, team_flag, keep_away | ⬜ |
| Big G | race, melee, keep_away, team_flag, koth, conquest | ⬜ |
| Roundabout | melee, keep_away, team_flag | ⬜ |
| Monkey Face | melee, keep_away, team_flag | ⬜ |
| Zigzag | melee, keep_away, team_flag, conquest, koth | ⬜ |
| The Pad | melee, keep_away, team_flag, koth | ⬜ |
| Doom Shroom | melee, keep_away, team_flag | ⬜ |
| Lake Frigid | melee, keep_away, team_flag, race | ⬜ |
| Tip Top | melee, keep_away, team_flag, koth | ⬜ |
| Crag Castle | melee, keep_away, team_flag, conquest | ⬜ |
| Tower D | (co-op: Runaround) | ⬜ |
| Happy Thoughts | melee, keep_away, team_flag, conquest, koth | ⬜ |
| Step Right Up | melee, keep_away, team_flag, conquest | ⬜ |
| Courtyard | melee, keep_away, team_flag | ⬜ |
| Rampage | melee, keep_away, team_flag | ⬜ |

(We currently ship three originals: Foundry Court (CTF), the Dojo lab
level, and **Skyhaven** — a floating-island Death Match arena (central
plaza + ruins/garden/cliffs/watchtower zones, instanced modular props,
cloud-sea backdrop). These are originals, not part of the canon 17; keep
or retire them once the canon set exists. Skyhaven also proved out the
extended prop renderer — `render/props.js` (reusable low-poly, instanced
builders) + new solid `kind`s and decor types — which the canon maps reuse.)

Map data BombSquad attaches that our level format needs to grow:
**per-map spawn/flag/scoring-zone sets keyed by mode**, **map-specific
materials** (ice friction on Hockey/Lake Frigid), **powerup spawn points**,
and **region volumes** (goals, end zones, KotH zone, conquest flag pads).

---

## 4. Bot roster (6 archetypes × modifiers)

The match/lab bots become a proper roster. Archetypes from `spazbot.py`;
modifiers = Lite / Pro / Static / Shielded, plus a difficulty scalar.

| Archetype | Behavior | State |
|---|---|---|
| Bomber | throws bombs at range (our current bot ≈ this) | ⬜ formalize |
| Brawler | closes and punches (Kronk), high aggression | ⬜ |
| Charger | speed melee, never throws | ⬜ |
| Bouncy | constantly jumps, melee | ⬜ |
| Trigger | uses impact bombs | ⬜ (needs §1 impact bomb) |
| Sticky | runs and throws sticky bombs | ⬜ (needs §1 sticky bomb) |
| Explodey | charges and self-detonates (cursed) | ⬜ (needs §1 curse) |
| Demo | randomized traits for FFA variety | ⬜ |

Modifiers: **Static** (doesn't chase), **Shielded** (spawns with §1
shield), **Pro** (runs + boxing gloves + faster), **Lite** (weaker/slower).
Needs a bot factory + a difficulty tier system (co-op waves scale these).

---

## 5. Characters & cosmetics

Cosmetics are already data + a `buildHat()` case (ARCHITECTURE.md recipe);
extend to full BombSquad-style characters (color + highlight + style +
voice). BombSquad ships Spaz, Kronk, Zoe, Snake Shadow, Mel, Jack Morgan,
Bones, Bernard, Frosty, Santa, Pixel, etc. — **purely cosmetic**, so this
is authoring, not new systems. Add: per-character color/highlight/style,
pickup/attack/death voice sets (all WebAudio-synth), and the appearance
registry (`spazappearance.py` analog). Some archetype bots want specific
looks (Brawler=Kronk, Bouncy=bunny).

---

## 6. Meta systems (the "game around the game")

This is where "a full copy" balloons far past gameplay. All three tiers
are **in scope** (confirmed 2026-07-12); tiers only describe build order
and risk, not what's included. Tier C is the last and largest phase.

| System | What it is | Source | Tier |
|---|---|---|---|
| Playlists | pick a sequence of mode+map+settings | `bascenev1` sessions | **A — core** |
| Session flow | series → games → scoreboard → next | `DualTeamSession`, `FreeForAllSession` | A |
| Per-game settings UI | score-to-win, time limit, respawn, epic mode | mode `available_settings` | A |
| Scoreboard & stats | per-player kills/deaths/score, MVP | `actor/scoreboard.py` | A |
| Co-op campaign | level ladder, unlocks, star ratings | co-op sessions | **B — big** |
| Character/powerup unlock store | tickets, purchases | classic store | B |
| Tournaments / leagues | ranked, leaderboards, cloud | master-server | **C — server infra** |
| Accounts / cloud sync | login, profiles, cross-device | plus/accounts | C |
| Party / online v2 | BombSquad's connect-by-address + party UI | connection layer | C |
| Gamepad / local multi | many controllers on one screen | input | A/B |

We already have a zero-dep LAN server (Tier-C-lite). BombSquad's full
online (accounts, matchmaking, cloud) is a separate mountain.

---

## 7. Audio / visual polish

All synthesized already; expand to BombSquad's coverage: per-action voice
barks, ice/shatter/freeze SFX, shield hum/hit/break, curse tick, powerup
jingle, TNT double-boom, announcer/zoom messages ("CAPTURE!", countdowns),
fireworks on win, camera shake tiers. Source cues: `spazfactory.py`,
`bomb.py`, `capturetheflag.py`.

---

## Suggested build order (dependency-first)

1. **Finish §1 items** — powerups (in flight) + the 4 special bombs +
   freeze/shatter + TNT. Unlocks Trigger/Sticky/Explodey bots and several
   modes' flavor. *(largest core-engine work left)*
2. **§6 Tier A shell** — sessions, playlists, per-game settings, scoreboard.
   Turns "one hardcoded match" into "pick any mode+map". Do this before
   mass-authoring modes so each new mode is immediately playable/testable.
3. **§2a versus modes** — DeathMatch → KotH/KeepAway → Conquest/Assault →
   Elimination/Chosen One → Hockey/Football/Race. Each is one mode file +
   any new sim primitive (zones, lives, puck, laps).
4. **§3 maps** — author in the order modes need them; start with the few
   that cover the most mode types (Big G, Happy Thoughts, Zigzag).
5. **§4 bot roster + §2b co-op** — roster first, then the wave director,
   then Onslaught → Runaround → the rest.
6. **§5 characters** + **§7 polish** — continuous, parallelizable.
7. **§6 Tier B/C** — campaign/unlocks, then cloud/accounts/tournaments
   (in scope; the final and largest phase).

## Reality check / risks

- **Scale.** BombSquad is ~15 years of work. Mechanics + versus modes +
  maps + bots (§1–4) is a large but tractable project on this engine.
  Co-op campaign and especially cloud/accounts/tournaments (§6 B/C) are
  each their own project.
- **No per-limb ragdoll.** A handful of BombSquad visuals (limb-accurate
  ragdoll, exact shatter) are approximated; gameplay stays faithful.
- **Assets.** We synthesize audio and stylize art — geometry/flow is
  matched, pixel-exact art/models are out of scope by design.
- **Networking.** Modes must keep all state in `sim.state` (serializable)
  or online play desyncs — the same discipline the current code follows.

## How each piece gets verified

Every mode/map/item lands with: a `tune`/`smoke` assertion where it's
numeric, a Physics-Lab or bot-match exercise for behavior, and a parity
note in `bombsquad-parity.md` citing its Ballistica source.
