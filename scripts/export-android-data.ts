import { copyFileSync, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { build } from 'vite';
import { CITIES } from '../src/data/cities';

async function main() {
  const outPath = resolve('android/app/src/main/assets/tram_network.json');
  mkdirSync(dirname(outPath), { recursive: true });
  writeFileSync(outPath, JSON.stringify({ cities: Object.values(CITIES) }, null, 2), 'utf8');

  const changelogSrc = resolve('CHANGELOG.md');
  const changelogDest = resolve('android/app/src/main/assets/changelog.md');
  if (existsSync(changelogSrc)) {
    copyFileSync(changelogSrc, changelogDest);
  }

  console.log(
    `Exported tram_network.json & changelog.md: SPb (${CITIES.spb.stops.length} stops, ${CITIES.spb.routes.length} routes, ${CITIES.spb.landmarks.length} landmarks), Omsk (${CITIES.omsk.stops.length} stops, ${CITIES.omsk.routes.length} routes, ${CITIES.omsk.landmarks.length} landmarks).`
  );

  await build({
    configFile: resolve('vite.webmap.config.ts'),
    logLevel: 'info',
  });

  console.log('Built standalone WebView map bundle into android/app/src/main/assets/webmap/');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
