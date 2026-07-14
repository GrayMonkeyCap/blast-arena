// Level: SKYHAVEN — a Death Match arena on a colorful island in a cloud sea.
//
// Levels are pure data. The sim collides against `solids`, a renderer builds
// meshes from the same list (styled by `kind`) plus the `decor` scenery, and
// modes read `flags` / `bases` / `spawns`. This file adds no code anywhere.
//
// Layout intent (bright afternoon; the island is VISUAL dressing over the
// square sim floor — the rectangle rim is a lethal drop, marked by decor.edges,
// never walled). Compass ↔ axes:  -x = WEST, +x = EAST, -z = NORTH, +z = SOUTH.
//
//   - A large CENTRAL PLAZA (engraved stone) is the main combat zone: an open
//     center holding the PREMIUM powerup, guarded (not camped) by four broken
//     columns, ringed by waist-high blocks at the four bridge mouths and a
//     broken kerb at the corners. Four wide (>4m) cardinal BRIDGES lead out.
//   - Four themed sections ring the plaza, each reachable ≥3 ways (a plaza
//     bridge + the two diagonal corner routes that loop between neighbors):
//       NORTH  ruined courtyard  — collapsed walls (central breach), broken
//                                  columns, arches, rubble, cracked floor.
//       SOUTH  garden terrace    — grass, trees (trunks = cover), a decorative
//                                  pond (visual, walk-through) on stepping
//                                  stones, flowers, stone lanterns.
//       EAST   rocky cliffside   — boulders forming two open "cave" gap routes.
//       WEST   watchtower yard   — stacked crates, barrels, a broken tower,
//                                  scaffolding and banners.
//   - Fairness is structural: PLAZA cover has 90° rotational symmetry; each
//     themed zone is the 180° twin of the opposite zone (identical footprint,
//     themed `kind`). So every lane, cover slot and powerup mirrors red↔blue.
//   - Spawns hug OPPOSITE flanks (red west/SW, blue east/NE), tucked behind
//     home cover, off every powerup and out of the center crossfire. The
//     bases sit due west/east so two-flag CTF could also run here.

// --- symmetry helpers (levels are data; these just stamp out fair copies) ---

// 90° rotation about origin. Used to make plaza cover 4-fold symmetric.
const rot90 = (x, z) => [-z, x];

// Four copies of a box at 90° steps (footprint rotates with it) → any plaza
// cover is identical looking N/E/S/W, so no cardinal approach is favored.
const fourfold = (x, z, w, d, h, kind) => {
  const out = [];
  let cx = x, cz = z, cw = w, cd = d;
  for (let i = 0; i < 4; i++) {
    out.push({ x: cx, z: cz, w: cw, d: cd, h, kind });
    [cx, cz] = rot90(cx, cz);
    [cw, cd] = [cd, cw];
  }
  return out;
};

// A box and its 180° twin in the opposite zone. Same footprint (so the two
// lanes are geometrically identical), but each end gets its own themed kind.
const pair = (x, z, w, d, h, kindA, kindB) => [
  { x, z, w, d, h, kind: kindA },
  { x: -x, z: -z, w, d, h, kind: kindB },
];

export const skyhaven = {
  id: 'skyhaven',
  name: 'Skyhaven',
  description:
    'A floating island above a sea of clouds. A ringed plaza links ruins, ' +
    'garden, cliffs and a watchtower yard — the whole rim is a lethal drop.',
  bounds: { w: 46, d: 46 }, // square floor; walk past it and you fall

  theme: {
    // foundry's base keys, repainted for a bright afternoon
    floor: '#cdbfa2',
    floorDark: '#b3a488',
    line: '#efe7d2',
    wall: '#9a8f7d',
    wallTop: '#c3b7a0',
    crate: '#b8813f',
    pillar: '#8d8474',
    rail: '#6f6656',
    sky: '#7db8e8', // warm bright blue
    horizon: '#bfe0f2', // lighter toward the cloud sea
    lamp: '#ffe6b0',
    // new zone-accent keys
    stone: '#c8b992', // engraved plaza kerb / low walls
    block: '#b6ab90', // waist-high stone cubes
    column: '#cfc4ad', // broken round columns / tree trunks
    barrel: '#9c6b3a', // wooden barrels
    rock: '#9aa1a6', // grey boulders
    wood: '#a9763f', // scaffolding / planks
    foliage: '#4f9d3f', // tree canopy
    grass: '#7cc257', // garden terrace
    water: '#5fb8d6', // pond
    lantern: '#ffcf7a', // stone-lantern glow
    cloud: '#f2f7fb', // the sea of clouds
  },

  // Bases due west/east so CTF could also run (Death Match ignores them).
  flags: {
    red: { x: -18.5, z: 0 },
    blue: { x: 18.5, z: 0 },
  },
  bases: {
    red: { x: -18.5, z: 0, r: 3.0 },
    blue: { x: 18.5, z: 0, r: 3.0 },
  },

  // Opposite flanks, tucked behind home cover, off the powerups & crossfire.
  spawns: {
    red: [
      { x: -20.5, z: 3.0 }, { x: -18.5, z: 5.0 },
      { x: -16.5, z: 3.5 }, { x: -21.0, z: 0.5 },
    ],
    blue: [
      { x: 20.5, z: -3.0 }, { x: 18.5, z: -5.0 },
      { x: 16.5, z: -3.5 }, { x: 21.0, z: -0.5 },
    ],
  },

  // Collision boxes. `h` gates fly-over: low cover 0.9–1.3, walls 1.5–2.0,
  // columns/towers 2.4–3.2. Players route AROUND these on one flat plane.
  solids: [
    // ---- CENTRAL PLAZA (90° rotational symmetry) ----
    // four broken columns guard the center powerup without blocking any bridge
    ...fourfold(3.6, -3.6, 1.5, 1.5, 2.6, 'column'),
    // waist-high blocks flanking each cardinal bridge mouth (gap ~4.4m)
    ...fourfold(-3.0, -7.5, 1.6, 1.6, 1.1, 'block'),
    ...fourfold(3.0, -7.5, 1.6, 1.6, 1.1, 'block'),
    // broken engraved kerb at the four plaza corners (alternating orientation)
    ...fourfold(7.5, -7.5, 2.6, 0.9, 0.95, 'stone'),

    // ---- NORTH ruins (A) / SOUTH garden (B): 180° twins ----
    // collapsed wall halves with a central breach (main N/S route) ↔ fallen logs
    ...pair(-5.5, -13.5, 4.2, 1.0, 1.5, 'wall', 'log'),
    ...pair(5.5, -13.5, 4.2, 1.0, 1.5, 'wall', 'log'),
    // flanking broken columns ↔ garden tree trunks
    ...pair(-10.0, -16.5, 1.6, 1.6, 2.5, 'column', 'column'),
    ...pair(10.0, -16.5, 1.6, 1.6, 2.5, 'column', 'column'),
    // toppled column drum ↔ garden stone
    ...pair(0.0, -18.5, 1.5, 1.5, 1.3, 'column', 'block'),

    // ---- EAST cliffside (A) / WEST watchtower yard (B): 180° twins ----
    // paired boulders ↔ crate stacks bracket the E/W lane
    ...pair(13.0, -5.0, 2.8, 2.8, 2.1, 'rock', 'crate'),
    ...pair(13.0, 5.0, 2.8, 2.8, 2.1, 'rock', 'crate'),
    // mid boulder ↔ barrel cluster splits the approach into two "cave" routes
    ...pair(14.0, 0.0, 2.2, 2.2, 1.6, 'rock', 'barrel'),
    // outer formations ↔ crate stacks (frame the base without walling it)
    ...pair(19.5, -7.0, 2.4, 2.4, 1.9, 'rock', 'crate'),
    ...pair(19.5, 7.0, 2.4, 2.4, 1.9, 'rock', 'crate'),
    // tall rock spire ↔ broken watchtower
    ...pair(18.0, 11.0, 1.8, 1.8, 3.0, 'rock', 'column'),

    // ---- CORNER ring connectors (single low cover, both routes stay open) ----
    ...pair(11.5, -11.5, 2.0, 2.0, 1.6, 'rock', 'block'), // NE rock / SW garden stone
    ...pair(-11.5, -11.5, 2.0, 2.0, 1.5, 'crate', 'rock'), // NW crate / SE rock
  ],

  decor: {
    lamps: [
      { x: 6.5, z: -6.5 }, { x: -6.5, z: -6.5 },
      { x: -6.5, z: 6.5 }, { x: 6.5, z: 6.5 },
      { x: -13, z: -2 }, { x: 13, z: 2 },
      { x: 3, z: -13 }, { x: -3, z: 13 },
    ],
    banners: [
      { x: -21, z: -3, team: 'red' }, { x: -21, z: 6, team: 'red' },
      { x: 21, z: 3, team: 'blue' }, { x: 21, z: -6, team: 'blue' },
    ],

    // NORTH ruins
    arches: [
      { x: -6, z: -11, rot: 0 }, { x: 6, z: -11, rot: 0 },
      { x: 0, z: -17, rot: 0 }, { x: -10, z: -13, rot: 1.5708 },
    ],
    rubble: [
      { x: -3, z: -12 }, { x: 3, z: -12 }, { x: -8, z: -14 }, { x: 8, z: -14 },
      { x: 0, z: -15.5 }, { x: -2, z: -19 }, { x: 4, z: -18 }, { x: -6, z: -20 },
    ],

    // SOUTH garden (trees at x:±10,z:16.5 sit on the trunk `column` solids)
    trees: [
      { x: 10, z: 16.5, scale: 1.3 }, { x: -10, z: 16.5, scale: 1.3 },
      { x: 6, z: 20, scale: 0.8 }, { x: -6, z: 20, scale: 0.8 },
      { x: 0, z: 21, scale: 0.9 },
    ],
    lanterns: [
      { x: -3, z: 9 }, { x: 3, z: 9 }, { x: -4, z: 17 }, { x: 4, z: 17 },
    ],
    flowers: [
      { x: -2, z: 10 }, { x: 2, z: 10 }, { x: -6, z: 14 }, { x: 6, z: 14 },
      { x: -3, z: 17 }, { x: 3, z: 17 }, { x: 0, z: 19 }, { x: -8, z: 20 },
      { x: 8, z: 20 },
    ],
    ponds: [{ x: 0, z: 12.5, r: 2.6 }], // VISUAL water disc — not a solid
    steppingStones: [
      { x: 0, z: 10 }, { x: 0, z: 11.5 }, { x: 0, z: 13 }, { x: 0, z: 14.5 },
      { x: -1.5, z: 12.5 }, { x: 1.5, z: 12.5 },
    ],

    // WEST watchtower yard
    scaffold: [
      { x: -16, z: -3, rot: 0, w: 3, h: 3 },
      { x: -17, z: 4, rot: 0, w: 2.5, h: 2.6 },
      { x: -12, z: -9, rot: 0, w: 2, h: 2.2 },
    ],

    // one premium dead-center; four standards ringed toward the outer corners
    powerups: [
      { x: 0, z: 0, tier: 'premium' },
      { x: 9.5, z: -9.5, tier: 'standard' },
      { x: -9.5, z: -9.5, tier: 'standard' },
      { x: 9.5, z: 9.5, tier: 'standard' },
      { x: -9.5, z: 9.5, tier: 'standard' },
    ],

    // tinted floor patch per area
    zones: [
      { x: 0, z: 0, r: 9, tint: '#c9ba90', shape: 'circle' }, // plaza stone
      { x: 0, z: -15, r: 7.5, tint: '#a7a49c', shape: 'circle' }, // ruins
      { x: 0, z: 15, r: 7.5, tint: '#8ec766', shape: 'circle' }, // garden
      { x: 15, z: 0, r: 7.5, tint: '#b6a488', shape: 'circle' }, // cliffside
      { x: -15, z: 0, r: 7.5, tint: '#c6a86a', shape: 'circle' }, // yard
    ],

    // broken cliff-rim markers along the four lethal sides (corners left open)
    edges: [
      // NORTH side
      { x: -12, z: -21.5, rot: 0, len: 6.5, style: 'ruin' },
      { x: -4, z: -21.5, rot: 0, len: 6.5, style: 'ruin' },
      { x: 4, z: -21.5, rot: 0, len: 6.5, style: 'ruin' },
      { x: 12, z: -21.5, rot: 0, len: 6.5, style: 'ruin' },
      // SOUTH side
      { x: -12, z: 21.5, rot: 0, len: 6.5, style: 'fence' },
      { x: -4, z: 21.5, rot: 0, len: 6.5, style: 'fence' },
      { x: 4, z: 21.5, rot: 0, len: 6.5, style: 'fence' },
      { x: 12, z: 21.5, rot: 0, len: 6.5, style: 'fence' },
      // EAST side
      { x: 21.5, z: -12, rot: 1.5708, len: 6.5, style: 'stone' },
      { x: 21.5, z: -4, rot: 1.5708, len: 6.5, style: 'stone' },
      { x: 21.5, z: 4, rot: 1.5708, len: 6.5, style: 'stone' },
      { x: 21.5, z: 12, rot: 1.5708, len: 6.5, style: 'stone' },
      // WEST side
      { x: -21.5, z: -12, rot: 1.5708, len: 6.5, style: 'stone' },
      { x: -21.5, z: -4, rot: 1.5708, len: 6.5, style: 'stone' },
      { x: -21.5, z: 4, rot: 1.5708, len: 6.5, style: 'stone' },
      { x: -21.5, z: 12, rot: 1.5708, len: 6.5, style: 'stone' },
    ],

    // distant low-detail scenery in the cloud sea (radius ~55–110)
    background: {
      cloudSeaY: -6,
      islands: [
        { x: -62, z: -32, y: -8, scale: 1.4 },
        { x: 78, z: 18, y: -12, scale: 1.8 },
        { x: 16, z: 72, y: -6, scale: 1.2 },
        { x: -52, z: 60, y: -14, scale: 1.6 },
        { x: 70, z: -58, y: -10, scale: 2.0 },
      ],
      peaks: [
        { x: -82, z: -70, y: -20, scale: 3.0 },
        { x: 96, z: -48, y: -18, scale: 2.6 },
        { x: -30, z: 98, y: -22, scale: 3.2 },
      ],
      waterfalls: [
        { x: -15, z: 20, y: -4, h: 16 },
        { x: 18, z: -18, y: -4, h: 14 },
      ],
    },
  },
};
