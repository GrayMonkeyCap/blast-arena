// Game-mode registry. New modes register here and become selectable by id.
import { CtfMode } from './ctf.js';
import { DeathMatchMode } from './deathmatch.js';
import { SandboxDuel, SandboxDoll } from './sandbox.js';

export const MODES = {
  ctf: CtfMode,
  deathmatch: DeathMatchMode,
  'sandbox-duel': SandboxDuel,
  'sandbox-doll': SandboxDoll,
};
export const DEFAULT_MODE = 'ctf';
