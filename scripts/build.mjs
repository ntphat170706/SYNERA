import { spawnSync } from 'node:child_process';
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
for (const script of ['node_modules/typescript/bin/tsc', 'node_modules/vite/bin/vite.js']) {
  const r = spawnSync(
    process.execPath,
    [script, ...(script.includes('tsc') ? ['-b'] : ['build'])],
    { stdio: 'inherit' },
  );
  if (r.status !== 0) process.exit(r.status || 1);
}
function walk(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) =>
    entry.isDirectory() ? walk(directory + '/' + entry.name) : [directory + '/' + entry.name],
  );
}
// Keep the large landing video out of the offline application cache.
const assets = walk('dist/assets')
  .filter((path) => !path.endsWith('.mp4'))
  .map((path) => path.replace(/^dist/, ''));
const hasher = createHash('sha256');
const serviceWorker = readFileSync('public/sw.js', 'utf8');
hasher.update(serviceWorker);
for (const asset of assets) hasher.update(readFileSync('dist' + asset));
const hash = hasher.digest('hex').slice(0, 12);
const precacheDeclaration = /const PRECACHE\s*=\s*\[[^\]]*\]/;
if (!precacheDeclaration.test(serviceWorker))
  throw new Error('Service Worker precache declaration is missing');
let sw = serviceWorker
  .replace('synera-shell-v1', 'synera-shell-' + hash)
  .replace(
    precacheDeclaration,
    'const PRECACHE = ' +
      JSON.stringify(['/', '/index.html', '/icon.svg', '/manifest.webmanifest', ...assets]),
  );
writeFileSync('dist/sw.js', sw);
console.log(`Offline shell: precached ${assets.length} application assets.`);
