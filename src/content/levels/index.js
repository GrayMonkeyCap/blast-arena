// Level registry. To add a level: create its data file next to foundry.js
// and list it here. Menus, the sim, the server and the renderer all discover
// levels through this map — nothing else needs to change.
import { foundry } from './foundry.js';
import { dojo } from './dojo.js';
import { skyhaven } from './skyhaven.js';

export const LEVELS = { foundry, dojo, skyhaven };
export const DEFAULT_LEVEL = 'foundry';
