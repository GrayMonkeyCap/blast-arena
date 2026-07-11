// Level: THE DOJO — the physics-lab arena (sandbox modes only).
//
// Purpose-built for interaction testing, not for fair matches:
//   - Player post (west) faces the doll/bot post (east) across an OPEN lane
//     so punches, knockback and throws travel unobstructed.
//   - Concentric measurement rings (r = 2/4/6) around the east post — read
//     knockback distances straight off the floor.
//   - One crate cluster + one wall + one pillar for collision/bounce tests.
//   - The middle of the EAST rim is open: throw the doll (or get thrown)
//     into the void to test edge KOs.
//   - Two practice flags on stands north/south of center for grab / carry /
//     throw / kick testing.

export const dojo = {
  id: 'dojo',
  name: 'The Dojo',
  description: 'Physics lab. Rings measure knockback; the east rim is open.',
  bounds: { w: 30, d: 22 },

  theme: {
    floor: '#a3937f',
    floorDark: '#8a7c6a',
    line: '#f3ead9',
    wall: '#6b5d4e',
    wallTop: '#93836f',
    crate: '#b07a3f',
    pillar: '#55483c',
    rail: '#3a332b',
    sky: '#141210',
    horizon: '#3a2f24',
    lamp: '#ffd9a0',
  },

  flags: {
    red: { x: 0, z: -7 },
    blue: { x: 0, z: 7 },
  },

  // pads double as the posts (west = player, east = doll/bot)
  bases: {
    red: { x: -7, z: 0, r: 1.7 },
    blue: { x: 7, z: 0, r: 1.7 },
  },

  spawns: {
    red: [{ x: -7, z: 0 }, { x: -9, z: -2 }],
    blue: [{ x: 7, z: 0 }, { x: 9, z: 2 }],
  },

  solids: [
    // collision test props, kept off the center lane
    { x: -11, z: -7, w: 1.4, d: 1.4, h: 1.4, kind: 'crate' },
    { x: -9.6, z: -7.4, w: 1.4, d: 1.4, h: 1.4, kind: 'crate' },
    { x: -10, z: 7, w: 5, d: 1.1, h: 1.7, kind: 'wall' },
    { x: 11, z: -7, w: 1.7, d: 1.7, h: 2.6, kind: 'pillar' },
    // rails everywhere EXCEPT the middle of the east rim (open = void tests)
    { x: -14.55, z: 0, w: 0.6, d: 21.6, h: 0.8, kind: 'rail' },
    { x: 0, z: -10.55, w: 29.6, d: 0.6, h: 0.8, kind: 'rail' },
    { x: 0, z: 10.55, w: 29.6, d: 0.6, h: 0.8, kind: 'rail' },
    { x: 14.55, z: -7.75, w: 0.6, d: 6.1, h: 0.8, kind: 'rail' },
    { x: 14.55, z: 7.75, w: 0.6, d: 6.1, h: 0.8, kind: 'rail' },
  ],

  decor: {
    lamps: [{ x: -13, z: -9 }, { x: -13, z: 9 }],
    banners: [],
    // knockback measurement rings around the east (doll) post
    rings: [
      { x: 7, z: 0, r: 2 },
      { x: 7, z: 0, r: 4 },
      { x: 7, z: 0, r: 6 },
    ],
  },
};
