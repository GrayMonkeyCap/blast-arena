// Cosmetic registry. Adding a new hat = one entry here + a builder case in
// render/characters.js buildHat(). Skins tint the head/hands. Cosmetics are
// part of a player's profile, travel over the network, and never affect
// gameplay.

export const HATS = [
  { id: 'none', name: 'Bare', icon: '🙂' },
  { id: 'cap', name: 'Cap', icon: '🧢' },
  { id: 'tophat', name: 'Top Hat', icon: '🎩' },
  { id: 'crown', name: 'Crown', icon: '👑' },
  { id: 'halo', name: 'Halo', icon: '😇' },
  { id: 'horns', name: 'Horns', icon: '😈' },
  { id: 'chef', name: 'Chef', icon: '👨‍🍳' },
];

export const SKINS = ['#ffd29c', '#f1c27d', '#c68642', '#8d5524', '#ffdbac', '#b9f2c8', '#c9b6ff'];

export const DEFAULT_COS = { hat: 'none', skin: SKINS[0] };

export function randomCos(rng = Math.random) {
  return {
    hat: HATS[Math.floor(rng() * HATS.length)].id,
    skin: SKINS[Math.floor(rng() * SKINS.length)],
  };
}

export const BOT_NAMES = [
  'Sparky', 'Fuse', 'Kaboomika', 'Dyna', 'Sizzle', 'Bombino',
  'Wick', 'Boomer', 'Nitro', 'Cherry', 'Pepper', 'Rocket',
];
