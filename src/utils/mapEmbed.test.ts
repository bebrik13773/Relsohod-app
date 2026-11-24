import test from 'node:test';
import assert from 'node:assert/strict';
import { CITIES } from '../data/cities';
import {
  EmbedMapRuntimeState,
  hydrateEmbeddedTrip,
  isMapOnlySearch,
  mergeEmbeddedMapState,
  parseMapOnlyInitialParams,
} from './mapEmbed';

test('isMapOnlySearch detects mapOnly=1 / embedMap=1 and ignores normal searches', () => {
  assert.equal(isMapOnlySearch('?mapOnly=1'), true);
  assert.equal(isMapOnlySearch('?city=omsk&mapOnly=true'), true);
  assert.equal(isMapOnlySearch('?embedMap=1&theme=dark'), true);
  assert.equal(isMapOnlySearch('?autostart=1'), false);
  assert.equal(isMapOnlySearch(''), false);
});

test('parseMapOnlyInitialParams parses query overrides cleanly with safe fallbacks', () => {
  const parsed = parseMapOnlyInitialParams(
    '?mapOnly=1&city=omsk&theme=dark&accent=sage&showAll=0&includePlanned2027=0&startStopId=omsk_cirk&destinationStopId=omsk_kot&mapPickMode=destination&selectedLandmarkId=lm_1'
  );
  assert.equal(parsed.city, 'omsk');
  assert.equal(parsed.theme, 'dark');
  assert.equal(parsed.accentSeed, 'sage');
  assert.equal(parsed.showAllNetwork, false);
  assert.equal(parsed.includePlanned2027, false);
  assert.equal(parsed.startStopId, 'omsk_cirk');
  assert.equal(parsed.destinationStopId, 'omsk_kot');
  assert.equal(parsed.mapPickMode, 'destination');
  assert.equal(parsed.selectedLandmarkId, 'lm_1');

  const defaults = parseMapOnlyInitialParams('?mapOnly=1&city=unknown&theme=invalid');
  assert.equal(defaults.city, 'spb');
  assert.equal(defaults.theme, 'light');
  assert.equal(defaults.accentSeed, 'neva');
  assert.equal(defaults.showAllNetwork, true);
  assert.equal(defaults.includePlanned2027, true);
  assert.equal(defaults.mapPickMode, null);
});

test('hydrateEmbeddedTrip resolves compact Android segment descriptors against SPb network data', () => {
  const spb = CITIES.spb;
  const route3 = spb.routes.find((r) => r.number === '3');
  assert.ok(route3);
  assert.ok(route3.stopIds.length >= 2);

  const fromId = route3.stopIds[0];
  const toId = route3.stopIds[route3.stopIds.length - 1];

  const trip = hydrateEmbeddedTrip(
    {
      id: 'android-trip-1',
      startStopId: fromId,
      endStopId: toId,
      totalDurationMinutes: 28,
      transferCount: 1,
      dayOfWeek: 3,
      timeMinutes: 14 * 60,
      segments: [
        {
          id: 'seg-1',
          kind: 'tram',
          routeId: route3.id,
          fromStopId: fromId,
          toStopId: toId,
          stopIds: route3.stopIds,
          stopsCount: route3.stopIds.length - 1,
          durationMinutes: 20,
          directionNote: 'Трамвай №3',
        },
        {
          id: 'seg-2',
          kind: 'walk',
          fromStopId: toId,
          toStopId: fromId,
          stopIds: [toId, fromId],
          stopsCount: 1,
          durationMinutes: 8,
          directionNote: 'Пешая связка',
        },
      ],
    },
    spb
  );

  assert.ok(trip);
  assert.equal(trip.id, 'android-trip-1');
  assert.equal(trip.segments.length, 2);
  assert.equal(trip.segments[0].route?.id, route3.id);
  assert.equal(trip.segments[1].kind, 'walk');
  assert.deepEqual(trip.tramLinesUsed, [route3.number]);
  assert.ok(trip.districtsVisited.length >= 1);
});

test('mergeEmbeddedMapState updates partial patches and clears stale trip on city switch', () => {
  const initial: EmbedMapRuntimeState = {
    city: 'spb',
    theme: 'light',
    accentSeed: 'neva',
    showAllNetwork: true,
    includePlanned2027: true,
    startStopId: 'sennaya',
    destinationStopId: null,
    mapPickMode: null,
    selectedLandmarkId: null,
    tripPayload: { id: 'spb-trip', segments: [] },
    hasBridgeState: false,
  };

  const updated = mergeEmbeddedMapState(initial, {
    theme: 'tram',
    mapPickMode: 'start',
    showAllNetwork: false,
  });
  assert.equal(updated.theme, 'tram');
  assert.equal(updated.mapPickMode, 'start');
  assert.equal(updated.showAllNetwork, false);
  assert.equal(updated.city, 'spb');
  assert.equal(updated.hasBridgeState, true);

  const switchedCity = mergeEmbeddedMapState(updated, {
    city: 'omsk',
    mapPickMode: null,
  });
  assert.equal(switchedCity.city, 'omsk');
  assert.equal(switchedCity.startStopId, null);
  assert.equal(switchedCity.tripPayload, null);
  assert.equal(switchedCity.mapPickMode, null);
});
