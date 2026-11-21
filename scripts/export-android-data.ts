import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { CITIES } from '../src/data/cities';

const outPath = resolve('android/app/src/main/assets/tram_network.json');
mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(outPath, JSON.stringify({ cities: Object.values(CITIES) }, null, 2), 'utf8');

console.log(
  `Exported tram_network.json: SPb (${CITIES.spb.stops.length} stops, ${CITIES.spb.routes.length} routes, ${CITIES.spb.landmarks.length} landmarks), Omsk (${CITIES.omsk.stops.length} stops, ${CITIES.omsk.routes.length} routes, ${CITIES.omsk.landmarks.length} landmarks).`
);
