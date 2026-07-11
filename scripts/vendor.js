// Copies the three.js ES module build out of node_modules into vendor/,
// so the game runs from any static host with no runtime package manager or CDN.
import { copyFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
mkdirSync(path.join(root, 'vendor'), { recursive: true });
copyFileSync(
  path.join(root, 'node_modules', 'three', 'build', 'three.module.js'),
  path.join(root, 'vendor', 'three.module.js'),
);
console.log('vendored three.module.js');
