// Central tuning for the whole game. The sim, bots, renderer and server all
// read from here, so gameplay feel is adjusted in one place. A future game
// mode or level can ship its own overrides by passing a modified config into
// GameHost — nothing reads this object through globals.

export const CONFIG = {
  tickRate: 60, // fixed simulation step (Hz)
  snapshotRate: 20, // server -> client state broadcasts (Hz)
  teamSize: 2, // players per team (humans + bots fill the rest)

  // Common physics core (src/game/physics.js). Impulse-based collisions
  // with restitution e, Coulomb friction μ, and ODE-style per-step damping
  // v <- v*(1-damp) — parameter ranges follow deep-research-report.md
  // (masses: characters heavy / props light; e 0–0.8; μ 0.1–1; damp <= 0.1).
  physics: {
    maxSpeed: 42, // stability clamp (report: limit top speeds)
    sleepSpeed: 0.15, // below this a resting body is put to sleep
    materials: {
      // characters: heavy, barely bouncy, grippy when limp (KO slides stop)
      player: { mass: 4.0, radius: 0.55, restitution: 0.12, bounceMin: 6, friction: 0.7, wallFriction: 0, linDamp: 0.002 },
      // bombs: light props — blasts and punches fling them far (Δv = J/m),
      // rubbery bounce, moderate slide
      bomb: { mass: 0.35, radius: 0.32, restitution: 0.45, bounceMin: 2.5, friction: 0.5, wallFriction: 0.3, linDamp: 0.004 },
      // the flag: mid-weight and high-friction — slides briefly, then
      // plants itself ("relatively heavy, tends to stay upright")
      flag: { mass: 1.4, radius: 0.4, restitution: 0.3, bounceMin: 3, friction: 0.85, wallFriction: 0.4, linDamp: 0.006 },
    },
  },

  player: {
    radius: 0.55,
    speed: 6.4,
    accel: 46,
    friction: 22,
    airControl: 0.22, // steering authority while airborne
    carrySpeedMult: 0.88, // flag carrier is slightly slower
    hp: 100,
    regen: 5, // hp/s after regenDelay with no damage
    regenDelay: 4,
    respawnTime: 3,
    invulnTime: 2.2, // spawn protection
    grabRange: 1.7,
    throwCooldown: 1.15,
    jumpVel: 9.0,
    stumbleImpulse: 26, // impulse (N·s) above this staggers you (Δv = J/m)
    stumbleTime: 0.7, // recovers before the attacker's next punch lands
  },

  // Punch = the fist as a moving collider (report §Punch Mechanics).
  // Fist speed = swing + body speed (+ airborne bonus); the impulse follows
  //   j = (1+e) * v_fist / (1/m_fist + 1/m_target)
  // so damage and knockback fall out of momentum, not magic numbers.
  // Standing jab: j≈15 (9 dmg). Running: j≈27 (16 dmg, breaks grips,
  // stumbles). Running jump punch: j≈32 (19 dmg, launches people).
  punch: {
    cooldown: 0.75, // slower than stumble recovery: no infinite punch-lock
    range: 1.05, // fist collider center, in front of the player
    radius: 1.0,
    fistMass: 2.5, // effective mass behind the fist (arm + shoulder charge)
    swingSpeed: 8, // fist speed relative to the body
    airBonus: 3, // extra fist speed while airborne (jump punch)
    restitution: 0.2,
    dmgPerImpulse: 0.6, // hp per unit of impulse (enemies only)
    liftFrac: 0.35, // fraction of j applied as upward impulse (launch!)
  },

  // Grabbing players (BombSquad carry rules): a grabbed player is hoisted
  // OVERHEAD like an item until they react — punch back for chip damage,
  // or grab back to force a grounded mutual grapple (the pair moves by the
  // average of both players' steering). Grab again = mild momentum toss;
  // the throw button is the strong aimed hurl. Hard hits break any grip.
  grab: {
    playerRange: 1.45,
    breakImpulse: 20, // impulse that knocks things out of hands
    throwSpeed: 9,
    throwSpeedScale: 1.1, // thrown players inherit holder momentum
    holderSpeedMult: 0.85,
  },

  bomb: {
    fuse: 2.8, // "roughly three seconds", per BombSquad
    blastRadius: 4.3,
    // Blasts apply radial IMPULSES with linear falloff; Δv = J/m means the
    // same blast flings light props much farther than characters. Impulses
    // are per-kind (≈ blast pressure × exposed area — small bodies simply
    // intercept less of the shockwave).
    blastImpulse: { player: 58, bomb: 4.8, flag: 16 },
    blastLift: { player: 34, bomb: 2.3, flag: 7 },
    maxDamage: 68,
    throwPitch: 0.62, // launch elevation (rad); range maps to launch speed
    minRange: 2,
    maxRange: 12,
    chainFuse: 0.18, // nearby bombs caught in a blast cook off almost instantly
  },

  // Two-flag CTF rules. The flag's physical behavior (mass, bounce, slide)
  // lives in physics.materials.flag; these are the game-rule knobs.
  flag: {
    grabRange: 1.6, // grab-button reach for stealing the enemy flag
    touchRadius: 0.95, // touching your own dropped flag returns it
    returnOnTouch: true, // instant return on touch (server-configurable)
    idleReturn: 30, // untouched dropped flag flies home after this long
    homeDrift: 2.5, // knocked this far off its stand = counts as dropped
    throwMult: 0.8, // heavier than a bomb: shorter throw arcs
    dropLockout: 0.6, // nobody can re-grab in the instant after a drop
  },

  rules: {
    roundTime: 180,
    captureLimit: 3,
    countdown: 3,
    overTime: 7, // victory screen duration before auto-rematch
  },

  world: {
    gravity: -26,
    fallY: -7, // fall below this off the arena edge = KO
  },
};

export const TEAMS = {
  red: { name: 'RED', color: '#ff5347', dark: '#a02620', glow: '#ff8a6e' },
  blue: { name: 'BLUE', color: '#3f8cff', dark: '#1f3f92', glow: '#7ab6ff' },
};

export const otherTeam = (t) => (t === 'red' ? 'blue' : 'red');
