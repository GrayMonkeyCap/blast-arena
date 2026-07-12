// Central tuning for the whole game. The sim, bots, renderer and server all
// read from here, so gameplay feel is adjusted in one place. A future game
// mode or level can ship its own overrides by passing a modified config into
// GameHost — nothing reads this object through globals.
//
// Values marked "BombSquad" are matched against the open-source BombSquad
// engine (Ballistica: spaz_node.cc, spaz.py, bomb.py, rigid_body.cc,
// capturetheflag.py) — see docs/bombsquad-parity.md for the derivations.
// BombSquad's 1000-hp scale maps to our 100-hp scale (÷10).

export const CONFIG = {
  tickRate: 60, // fixed simulation step (Hz)
  snapshotRate: 20, // server -> client state broadcasts (Hz)
  teamSize: 2, // players per team (humans + bots fill the rest)

  // Common physics core (src/game/physics.js). Impulse-based collisions
  // with restitution e, Coulomb friction μ, and ODE-style per-step damping
  // v <- v*(1-damp).
  physics: {
    maxSpeed: 42, // stability clamp
    sleepSpeed: 0.15, // below this a resting body is put to sleep
    materials: {
      // characters: heavy, barely bouncy, grippy when limp (KO slides stop)
      player: { mass: 4.0, radius: 0.55, restitution: 0.12, bounceMin: 6, friction: 0.7, wallFriction: 0, linDamp: 0.002 },
      // bombs: light props — punches send them flying (Δv = J/m); modest
      // bounce, they thud and roll rather than ricochet
      bomb: { mass: 0.35, radius: 0.32, restitution: 0.35, bounceMin: 2.5, friction: 0.5, wallFriction: 0.3, linDamp: 0.004 },
      // the flag: light cylinder (BombSquad kFlagDensity 1.0, r=0.3 h=1.0),
      // high friction — slides briefly, then plants itself
      flag: { mass: 1.0, radius: 0.4, restitution: 0.3, bounceMin: 3, friction: 0.85, wallFriction: 0.4, linDamp: 0.006 },
    },
  },

  // Locomotion = BombSquad's motorized roller-ball model. You WALK at
  // walkSpeed; the run "gear" engages as your smoothed speed builds
  // (spaz_node.cc: max_vel = walk 7.68 + gear·run·15 on a r=0.3 ball →
  // 2.3 / 6.8 m/s), so a sprint winds up over ~1s. Finite motor force means
  // wide turns at speed; releasing the stick coasts to a skidding stop; and
  // the ball has no traction mid-air — jumps commit you to the arc.
  player: {
    radius: 0.55,
    walkSpeed: 2.3, // BombSquad walk (7.68 ball-ω × 0.3 r)
    runSpeed: 6.8, // BombSquad full run ((7.68+15) × 0.3)
    gearSpeed: 2.1, // smoothed speed at which the run gear is fully engaged
    gearUp: 0.985, // per-tick smoothing while speeding up (slow spool-up)
    gearDown: 0.94, // per-tick smoothing while slowing (gear drops fast)
    accel: 15, // motor force limit -> gradual accel, wide turns at speed
    brakeDecel: 8, // stick released: gentle brake + skid (BombSquad brakes)
    airControl: 0, // BombSquad: no traction mid-air, the jump arc is committed
    hp: 100, // = BombSquad 1000 hp (÷10); NO regen — damage is permanent
    respawnTime: 5, // BombSquad teams default (2-player team, Normal)
    invulnTime: 1.0, // spawn invincibility (spaz.py: exactly 1.0s)
    grabRange: 1.7,
    jumpVel: 6.5, // apex ≈ 1.05 with g=-20 (BombSquad hop height)
    jumpCooldown: 0.25, // spaz.py _jump_cooldown = 250ms
    // Knockout — BombSquad's "unconscious ragdoll" state. A single hit above
    // minDamage knocks you out cold; you wake with your remaining hp.
    // spaz_node.cc: units = nodeDmg·0.02−20 (our hp scale: dmg·0.909−20),
    // capped at 40, ticking down 12/s grounded and half that airborne.
    knockout: {
      unitsPerDamage: 0.909, // knockout units per hp of damage in one hit
      baseUnits: 20, // subtracted — hits under ~22 dmg never knock out
      maxUnits: 40, // cap -> longest knockout = 40/12 ≈ 3.3s
      unitsPerSec: 12, // wake-up rate on the ground (6/s while airborne)
    },
    // Impact damage — BombSquad hurts you when your head jolts (Δv > 3):
    // wall slams, hard landings, being hit by a flying body. The "mercy
    // rule" keeps ordinary impacts from killing (spaz.py: if it would kill,
    // damage drops to max(dmg−200, hp−10) → ÷10 on our scale).
    impact: {
      wallMinDv: 5, // Δv into a wall before it hurts (full sprint ≈ 6.8)
      floorMinDv: 9, // legs cushion landings; ~2m+ falls start to hurt
      pairMinDv: 4, // body-vs-body slams (thrown players are weapons)
      dmgPerDv: 5, // hp per m/s beyond the threshold
      cooldown: 0.4, // s between impact-damage events per player
      mercyReduce: 20, // mercy rule: dmg = max(dmg−20, hp−1) if it would kill
    },
  },

  // Punch — BombSquad's momentum weapon. Damage scales with how fast your
  // body is moving when the fist lands (spaz punch: mag + |v|·40 with the
  // head worth 5×): a standing jab tickles (~4), a full sprint punch takes
  // ~40% and knocks the target out cold, a running jump punch even more.
  // The fist is a live collider for the swing, and connecting mid-swing
  // (punch_power 0.7→1.0→0.7 over 200ms) hits hardest. You cannot punch
  // while holding anything (BombSquad: no swing while holding_something).
  punch: {
    cooldown: 0.4, // BombSquad punch_cooldown = 400ms
    swingTime: 0.3, // fist collider alive ~35 steps
    windowStart: 0.04, // swing age when the fist becomes live
    windowEnd: 0.22, // ...and when it retracts
    range: 0.85, // fist collider center, in front of the player
    fistRadius: 0.25, // BombSquad punch body: sphere r=0.25
    dmgBase: 4, // standing jab (≈40/1000 in BombSquad terms)
    dmgPerSpeed: 5.5, // + per m/s of 3D body speed (run 6.8 → ~41)
    dmgCap: 60, // gloveless ceiling
    kbPerDmg: 0.28, // knockback Δv per hp of damage (momentum IS knockback)
    liftFrac: 0.45, // upward Δv fraction (BombSquad exaggerates fy ×2)
    selfKick: 1.2, // recoil Δv on the puncher (constant, like kick_back 400)
    // object smacks (loose bombs/flags) still use the fist-as-impulse model
    fistMass: 2.5,
    swingSpeed: 8,
    restitution: 0.2,
  },

  // Grabbing (BombSquad pickup): hoist bombs, flags and PLAYERS overhead.
  // A held player is a passenger (zero steering) but can punch the grabber
  // and grab back. ANY damage makes you drop whatever you hold.
  grab: {
    playerRange: 1.45,
  },

  // The universal throw (BombSquad: bomb button AND pickup button both hurl
  // whatever you hold). Fixed ~45° lob; power comes from your aim/stick
  // magnitude, momentum inheritance is FULL — running throws go far, that's
  // the whole game. Objects thrown within quickWindow of being picked up
  // fly weaker (BombSquad's just-picked-up scale).
  throw: {
    pitch: 0.785, // ~45° launch (BombSquad throws at torso-relative 45°)
    speedMin: 6, // neutral-stick lob
    speedMax: 10, // full-power hurl (standing range ≈ 6m, running ≈ 11m)
    inherit: 1.0, // full carrier-momentum inheritance
    quickWindow: 0.5, // held less than this -> reduced power...
    quickMin: 0.4, // ...scaling from 40% up to 100%
    kickback: 1.2, // recoil Δv on the thrower
    playerMult: 0.65, // players are heavy: thrown shorter than bombs
  },

  bomb: {
    fuse: 3.0, // BombSquad: exactly 3.0s, and it burns WHILE HELD
    perPlayer: 1, // BombSquad bomb_count: one live bomb until yours explodes
    blastRadius: 2.5, // BombSquad 2.0 + reach allowance for our fatter bodies
    maxDamage: 100, // point-blank = lethal; linear falloff to ZERO at the edge
    // Blast Δv is mass-normalized (rigid_body.cc scales force by mass):
    // the SAME blast kick for players, bombs and flags — with the vertical
    // component exaggerated (fy ×2), so blasts pop things up and out.
    blastDvXZ: 7.5, // horizontal Δv at the epicenter
    blastDvY: 9.5, // vertical Δv at the epicenter
    chainFuseMin: 0.1, // bombs caught in a blast detonate after a random
    chainFuseMax: 0.2, // 0.1–0.2s (bomb.py). Punches shove but don't trigger.
    // aim-distance → throw-power mapping for the UI (cursor/drag distance)
    aimRangeMin: 2,
    aimRangeMax: 12,
  },

  // Two-flag CTF rules (capturetheflag.py defaults). The flag's physical
  // behavior (mass, bounce, slide) lives in physics.materials.flag.
  flag: {
    grabRange: 1.6, // grab-button reach for stealing the enemy flag
    touchRadius: 0.95, // touching your own dropped flag returns it
    returnOnTouch: true, // Flag Touch Return Time default 0 = instant
    idleReturn: 30, // Flag Idle Return Time default 30s
    homeDrift: 2.5, // knocked this far off its stand = counts as dropped
    dropLockout: 0.6, // nobody can re-grab in the instant after a drop
  },

  rules: {
    roundTime: 180,
    captureLimit: 3, // BombSquad Score to Win default 3
    countdown: 3,
    overTime: 7, // victory screen duration before auto-rematch
  },

  world: {
    gravity: -20, // BombSquad: dWorldSetGravity(0, -20, 0)
    fallY: -7, // fall below this off the arena edge = KO
  },
};

export const TEAMS = {
  red: { name: 'RED', color: '#ff5347', dark: '#a02620', glow: '#ff8a6e' },
  blue: { name: 'BLUE', color: '#3f8cff', dark: '#1f3f92', glow: '#7ab6ff' },
};

export const otherTeam = (t) => (t === 'red' ? 'blue' : 'red');
