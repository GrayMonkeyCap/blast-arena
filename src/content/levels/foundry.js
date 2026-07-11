// Level: FOUNDRY COURT — the launch arena for Grab the Flag.
//
// Levels are pure data. The sim collides against `solids`, the renderer
// builds meshes from the same list (styled by `kind`), and modes read
// `flag` / `bases` / `spawns`. A new level = a new file like this one,
// registered in levels/index.js. No code changes required anywhere else.
//
// Layout intent (x is the long axis, red west / blue east):
//   - Three lanes: a wide center court plus top/bottom flanking lanes,
//     carved by four mid walls.
//   - Center flag guarded by two pillars — approaching through the middle
//     is fast but exposed.
//   - Crate clusters give bomb cover at the lane mouths.
//   - Each base sits behind a split bunker wall with a narrow center gap:
//     the natural chokepoint where defenders make their stand.
//   - The arena floats over a void. Guard rails cover most of the rim, but
//     the middle of each long edge is OPEN: a well-placed bomb can knock a
//     flag carrier clean off the map.

const crateCluster = (cx, cz, sx, sz) => [
  { x: cx, z: cz, w: 1.4, d: 1.4, h: 1.4, kind: 'crate' },
  { x: cx + 1.45 * sx, z: cz - 0.35 * sz, w: 1.4, d: 1.4, h: 1.4, kind: 'crate' },
  { x: cx - 0.25 * sx, z: cz + 1.4 * sz, w: 1.1, d: 1.1, h: 1.1, kind: 'crate' },
];

export const foundry = {
  id: 'foundry',
  name: 'Foundry Court',
  description: 'A floating forge platform. Three lanes, one flag, long falls.',
  bounds: { w: 46, d: 30 }, // floor extents; beyond this you fall

  theme: {
    floor: '#8e9cab',
    floorDark: '#77848f',
    line: '#dfe7ee',
    wall: '#5d6b7c',
    wallTop: '#8fa0b3',
    crate: '#b07a3f',
    pillar: '#4c5666',
    rail: '#39424f',
    sky: '#0f1626',
    horizon: '#25355c',
    lamp: '#ffd9a0',
  },

  // each team's flag stand sits on its base pad (two-flag CTF)
  flags: {
    red: { x: -19.5, z: 0 },
    blue: { x: 19.5, z: 0 },
  },

  bases: {
    red: { x: -19.5, z: 0, r: 3.0 },
    blue: { x: 19.5, z: 0, r: 3.0 },
  },

  spawns: {
    red: [
      { x: -20.5, z: -2.6 }, { x: -20.5, z: 2.6 },
      { x: -18.4, z: -4.9 }, { x: -18.4, z: 4.9 },
    ],
    blue: [
      { x: 20.5, z: -2.6 }, { x: 20.5, z: 2.6 },
      { x: 18.4, z: -4.9 }, { x: 18.4, z: 4.9 },
    ],
  },

  // Collision boxes. `h` matters: airborne bombs (and launched players)
  // travel over anything lower than their height.
  solids: [
    // mid walls carving the three lanes
    { x: -7, z: -5, w: 6, d: 1.1, h: 1.7, kind: 'wall' },
    { x: 7, z: -5, w: 6, d: 1.1, h: 1.7, kind: 'wall' },
    { x: -7, z: 5, w: 6, d: 1.1, h: 1.7, kind: 'wall' },
    { x: 7, z: 5, w: 6, d: 1.1, h: 1.7, kind: 'wall' },
    // flag guard pillars
    { x: 0, z: -9.5, w: 1.7, d: 1.7, h: 2.6, kind: 'pillar' },
    { x: 0, z: 9.5, w: 1.7, d: 1.7, h: 2.6, kind: 'pillar' },
    // crate cover at the lane mouths
    ...crateCluster(-11.5, -8, 1, 1),
    ...crateCluster(11.5, -8, -1, 1),
    ...crateCluster(-11.5, 8, 1, -1),
    ...crateCluster(11.5, 8, -1, -1),
    // base bunkers (split walls, narrow center gap = defense chokepoint)
    { x: -14, z: -3.1, w: 1.1, d: 3.8, h: 1.6, kind: 'wall' },
    { x: -14, z: 3.1, w: 1.1, d: 3.8, h: 1.6, kind: 'wall' },
    { x: 14, z: -3.1, w: 1.1, d: 3.8, h: 1.6, kind: 'wall' },
    { x: 14, z: 3.1, w: 1.1, d: 3.8, h: 1.6, kind: 'wall' },
    // guard rails — low, so blast knockback can still launch you over.
    // Long-edge rails deliberately stop short of midfield: the center of
    // each long edge is an open drop.
    { x: -22.55, z: 0, w: 0.6, d: 29.6, h: 0.8, kind: 'rail' },
    { x: 22.55, z: 0, w: 0.6, d: 29.6, h: 0.8, kind: 'rail' },
    { x: -15.4, z: -14.55, w: 14.9, d: 0.6, h: 0.8, kind: 'rail' },
    { x: 15.4, z: -14.55, w: 14.9, d: 0.6, h: 0.8, kind: 'rail' },
    { x: -15.4, z: 14.55, w: 14.9, d: 0.6, h: 0.8, kind: 'rail' },
    { x: 15.4, z: 14.55, w: 14.9, d: 0.6, h: 0.8, kind: 'rail' },
  ],

  decor: {
    lamps: [
      { x: -21, z: -12.8 }, { x: 21, z: -12.8 },
      { x: -21, z: 12.8 }, { x: 21, z: 12.8 },
    ],
    banners: [
      { x: -22.3, z: -7.5, team: 'red' }, { x: -22.3, z: 7.5, team: 'red' },
      { x: 22.3, z: -7.5, team: 'blue' }, { x: 22.3, z: 7.5, team: 'blue' },
    ],
  },
};
