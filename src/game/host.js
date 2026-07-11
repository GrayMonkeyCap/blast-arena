// GameHost: one running match. Owns the sim, the bot brains, and the input
// map. The browser wraps a GameHost directly for solo play; the server wraps
// one per room for online play — identical game logic in both.

import { CONFIG } from '../core/config.js';
import { LEVELS, DEFAULT_LEVEL } from '../content/levels/index.js';
import { MODES, DEFAULT_MODE } from './modes/index.js';
import { createSim, addPlayer, removePlayer, step } from './sim.js';
import { createBotBrain } from './bots.js';
import { BOT_NAMES, randomCos } from '../content/cosmetics.js';

export class GameHost {
  constructor({ levelId = DEFAULT_LEVEL, modeId = DEFAULT_MODE, config = CONFIG, teamSize = CONFIG.teamSize } = {}) {
    this.levelId = levelId;
    this.modeId = modeId;
    this.config = config;
    this.teamSize = teamSize;
    this.dt = 1 / config.tickRate;
    this.sim = createSim({ level: LEVELS[levelId], mode: MODES[modeId], config });
    this.inputs = new Map();
    this.brains = new Map();
    this.acc = 0;
    this.botNames = [...BOT_NAMES].sort(() => Math.random() - 0.5);
  }

  teamCount(team) {
    return this.sim.state.players.filter((p) => p.team === team).length;
  }

  humanCount(team) {
    return this.sim.state.players.filter((p) => p.team === team && !p.bot).length;
  }

  addHuman({ name, cos }) {
    // join the team with fewer humans (ties -> fewer players overall)
    const team =
      this.humanCount('red') !== this.humanCount('blue')
        ? this.humanCount('red') < this.humanCount('blue') ? 'red' : 'blue'
        : this.teamCount('red') <= this.teamCount('blue') ? 'red' : 'blue';
    if (this.teamCount(team) >= this.teamSize) {
      const bot = this.sim.state.players.find((p) => p.team === team && p.bot);
      if (bot) this.remove(bot.id);
    }
    return addPlayer(this.sim, { name: name || 'Player', team, bot: false, cos });
  }

  addBot(team) {
    const name = this.sim.mode.variant === 'doll' ? 'Doll' : (this.botNames.pop() ?? 'Bot-' + this.sim.nextId);
    const id = addPlayer(this.sim, { name, team, bot: true, cos: randomCos() });
    // modes may supply their own bot brains (sandbox doll/fighter)
    this.brains.set(id, this.sim.mode.createBrain?.(id) ?? createBotBrain(id));
    return id;
  }

  fillBots() {
    while (this.teamCount('red') < this.teamSize) this.addBot('red');
    while (this.teamCount('blue') < this.teamSize) this.addBot('blue');
  }

  remove(id) {
    this.inputs.delete(id);
    this.brains.delete(id);
    removePlayer(this.sim, id);
  }

  // Human leaves an online match: a bot takes over their slot.
  replaceWithBot(id) {
    const p = this.sim.state.players.find((p) => p.id === id);
    if (!p) return;
    const team = p.team;
    this.remove(id);
    if (this.teamCount(team) < this.teamSize) this.addBot(team);
  }

  setInput(id, input) {
    this.inputs.set(id, input);
  }

  step(dtWall) {
    this.acc = Math.min(this.acc + dtWall, 0.25);
    while (this.acc >= this.dt) {
      for (const [id, brain] of this.brains) this.inputs.set(id, brain.think(this.sim, this.dt));
      step(this.sim, this.inputs, this.dt);
      this.acc -= this.dt;
    }
  }

  drainEvents() {
    const out = this.sim.events;
    this.sim.events = [];
    return out;
  }
}
