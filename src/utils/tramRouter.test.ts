import test from 'node:test';
import assert from 'node:assert/strict';
import { SPB_LANDMARKS, SPB_STOPS, SPB_TRAM_ROUTES } from '../data/spbTramNetwork';
import { OMSK_LANDMARKS, OMSK_STOPS, OMSK_TRAM_ROUTES } from '../data/omskTramNetwork';
import { detectCityFromCoords } from '../data/cities';
import { buildYandexMapsRouteUrl } from './yandexMaps';
import {
  calculateTripDistanceKm,
  computeDeadlineBudgetMinutes,
  computeSavedRoutesStats,
  extendTripLastSegment,
  filterLandmarks,
  findNearestStops,
  generateTramAdventure,
  isCityTransitRoute,
  isRouteActiveAt,
} from './tramRouter';

test('Tram A runs only on weekdays between 06:11 and 21:27', () => {
  const tramA = SPB_TRAM_ROUTES.find((r) => r.number === 'А');
  assert.ok(tramA, 'Tram A route should exist');

  // Wednesday at 12:30 -> active
  assert.equal(isRouteActiveAt(tramA, 3, 12 * 60 + 30), true);
  // Monday at 06:11 -> active
  assert.equal(isRouteActiveAt(tramA, 1, 6 * 60 + 11), true);
  // Friday at 22:00 -> inactive
  assert.equal(isRouteActiveAt(tramA, 5, 22 * 60), false);
  // Saturday at 14:00 -> inactive
  assert.equal(isRouteActiveAt(tramA, 6, 14 * 60), false);
  // Sunday at 11:00 -> inactive
  assert.equal(isRouteActiveAt(tramA, 0, 11 * 60), false);
});

test('Regular tram routes handle after-midnight service carryover', () => {
  const tram3 = SPB_TRAM_ROUTES.find((r) => r.number === '3');
  assert.ok(tram3);
  // 00:15 should still be active (within 24:20 cutoff)
  assert.equal(isRouteActiveAt(tram3, 2, 15), true);
  // 03:00 should be inactive
  assert.equal(isRouteActiveAt(tram3, 2, 3 * 60), false);
});

test('findNearestStops locates closest tram stop to Sennaya Square coordinates', () => {
  const nearest = findNearestStops(59.927, 30.320, SPB_STOPS, 2);
  assert.equal(nearest[0].stop.id, 'sennaya_pl');
  assert.ok(nearest[0].distanceKm < 0.2);
});

test('Randomizer generates multi-transfer tram trip across districts and excludes Tram A on weekends', () => {
  const weekendOutcome = generateTramAdventure({
    stops: SPB_STOPS,
    routes: SPB_TRAM_ROUTES,
    landmarks: SPB_LANDMARKS,
    startStopId: 'reka_okkervil',
    theme: 'any',
    durationPreset: 'medium',
    dayOfWeek: 6, // Saturday
    timeMinutes: 13 * 60,
    allowFallbackTransit: false,
    seed: 42,
  });

  assert.equal(weekendOutcome.status, 'ok');
  if (weekendOutcome.status === 'ok') {
    assert.equal(weekendOutcome.trip.tramLinesUsed.includes('А'), false);
    assert.ok(weekendOutcome.trip.segments.some((s) => s.kind === 'tram'));
  }
});

test('Destination routing triggers needs_fallback when isolated and resolves via fallback when enabled', () => {
  // Petergof has no direct tram rail
  const withoutFallback = generateTramAdventure({
    stops: SPB_STOPS,
    routes: SPB_TRAM_ROUTES,
    landmarks: SPB_LANDMARKS,
    startStopId: 'sennaya_pl',
    destinationStopId: 'petergof_park',
    theme: 'any',
    durationPreset: 'any',
    dayOfWeek: 3,
    timeMinutes: 14 * 60,
    allowFallbackTransit: false,
    seed: 101,
  });
  assert.equal(withoutFallback.status, 'needs_fallback');

  const withFallback = generateTramAdventure({
    stops: SPB_STOPS,
    routes: SPB_TRAM_ROUTES,
    landmarks: SPB_LANDMARKS,
    startStopId: 'sennaya_pl',
    destinationStopId: 'petergof_park',
    theme: 'any',
    durationPreset: 'any',
    dayOfWeek: 3,
    timeMinutes: 14 * 60,
    allowFallbackTransit: true,
    seed: 101,
  });
  assert.equal(withFallback.status, 'ok');
  if (withFallback.status === 'ok') {
    assert.equal(withFallback.trip.usedFallbackTransit, true);
    const tramSegs = withFallback.trip.segments.filter((s) => s.kind === 'tram');
    const fallbackSegs = withFallback.trip.segments.filter((s) => s.kind === 'fallback_transit');
    // Must use trams for the bulk of the journey and minimal fallback only at the end
    assert.ok(tramSegs.length >= 1);
    assert.equal(fallbackSegs.length, 1);
  }
});

test('Late night 03:00 returns no_service status gracefully', () => {
  const outcome = generateTramAdventure({
    stops: SPB_STOPS,
    routes: SPB_TRAM_ROUTES,
    landmarks: SPB_LANDMARKS,
    startStopId: 'sennaya_pl',
    theme: 'any',
    durationPreset: 'any',
    dayOfWeek: 2,
    timeMinutes: 3 * 60,
    allowFallbackTransit: false,
    seed: 1,
  });
  assert.equal(outcome.status, 'no_service');
});

test('filterLandmarks enforces onlyWithPhotos and category filters', () => {
  const withPhotoOnly = filterLandmarks(SPB_LANDMARKS, [], true);
  assert.ok(withPhotoOnly.every((l) => Boolean(l.photoUrl)));
  assert.ok(withPhotoOnly.length < SPB_LANDMARKS.length);

  const parksAll = filterLandmarks(SPB_LANDMARKS, ['park'], false);
  assert.ok(parksAll.every((l) => l.category === 'park'));
  assert.ok(parksAll.some((l) => l.photoUrl === null));
});

test('Omsk tram network generates valid multi-line adventures and respects schedule', () => {
  const omskOutcome = generateTramAdventure({
    city: 'omsk',
    stops: OMSK_STOPS,
    routes: OMSK_TRAM_ROUTES,
    landmarks: OMSK_LANDMARKS,
    startStopId: 'omsk_cirk',
    theme: 'any',
    durationPreset: 'medium',
    dayOfWeek: 2,
    timeMinutes: 14 * 60,
    allowFallbackTransit: false,
    seed: 77,
  });

  assert.equal(omskOutcome.status, 'ok');
  if (omskOutcome.status === 'ok') {
    assert.equal(omskOutcome.trip.city, 'omsk');
    assert.ok(omskOutcome.trip.tramLinesUsed.length >= 1);
    const yandexUrl = buildYandexMapsRouteUrl(omskOutcome.trip);
    assert.ok(yandexUrl.startsWith('https://yandex.ru/maps?rtext='));
    assert.ok(yandexUrl.includes('%2C'));
    assert.ok(yandexUrl.includes('~'));
    assert.ok(yandexUrl.endsWith('&rtt=mt'));
  }
});

test('Loop mode closes circular route back to startStop and extendTripLastSegment adds legs', () => {
  const loopOutcome = generateTramAdventure({
    city: 'spb',
    stops: SPB_STOPS,
    routes: SPB_TRAM_ROUTES,
    landmarks: SPB_LANDMARKS,
    startStopId: 'finlyandsky_vokzal',
    theme: 'any',
    durationPreset: 'medium',
    dayOfWeek: 3,
    timeMinutes: 14 * 60,
    allowFallbackTransit: false,
    loopMode: true,
    seed: 19,
  });

  assert.equal(loopOutcome.status, 'ok');
  if (loopOutcome.status === 'ok') {
    assert.equal(loopOutcome.trip.endStop.id, 'finlyandsky_vokzal');
    assert.equal(loopOutcome.trip.isLoop, true);

    const extended = extendTripLastSegment({
      trip: loopOutcome.trip,
      stops: SPB_STOPS,
      routes: SPB_TRAM_ROUTES,
      landmarks: SPB_LANDMARKS,
      allowFallbackTransit: false,
      seed: 25,
    });
    assert.ok(extended, 'extendTripLastSegment should return an extended trip');
    assert.ok(extended.segments.length > loopOutcome.trip.segments.length);
    assert.equal(
      new Set(extended.segments.map((s) => s.id)).size,
      extended.segments.length,
      'extended trip segments must have unique IDs'
    );

    const extendedTwice = extendTripLastSegment({
      trip: extended,
      stops: SPB_STOPS,
      routes: SPB_TRAM_ROUTES,
      landmarks: SPB_LANDMARKS,
      allowFallbackTransit: false,
      seed: 99,
    });
    assert.ok(extendedTwice, 'second extension should succeed');
    assert.equal(
      new Set(extendedTwice.segments.map((s) => s.id)).size,
      extendedTwice.segments.length,
      'twice-extended trip segments must have unique IDs'
    );
  }
});

test('detectCityFromCoords identifies SPb, Omsk, and other cities accurately', () => {
  assert.equal(detectCityFromCoords(59.9386, 30.3141).detected, 'spb');
  assert.equal(detectCityFromCoords(54.968, 73.385).detected, 'omsk');
  assert.equal(detectCityFromCoords(55.7558, 37.6173).detected, 'other');
});

import {
  balanceToTrianglePoint,
  buildSmoothSvgPath,
  computeRouteLaneOffsets,
  trianglePointToBalance,
} from './mapGeometry';

test('Triangular barycentric slider math stays normalized and round-trips points', () => {
  const sample = { tram: 0.5, transit: 0.2, walk: 0.3 };
  const pt = balanceToTrianglePoint(sample);
  const back = trianglePointToBalance(pt.x, pt.y);
  assert.ok(Math.abs(back.tram - sample.tram) < 0.01);
  assert.ok(Math.abs(back.transit - sample.transit) < 0.01);
  assert.ok(Math.abs(back.walk - sample.walk) < 0.01);

  // Clamping outside triangle boundary always yields valid sum === 1 and non-negative components
  const clamped = trianglePointToBalance(2.0, -1.0);
  assert.ok(clamped.tram >= 0 && clamped.transit >= 0 && clamped.walk >= 0);
  assert.ok(Math.abs(clamped.tram + clamped.transit + clamped.walk - 1) < 0.001);
});

test('Map geometry produces smooth cubic Bezier curves and symmetric corridor offsets', () => {
  const path = buildSmoothSvgPath([
    { x: 0, y: 0 },
    { x: 50, y: 40 },
    { x: 100, y: 10 },
  ]);
  assert.ok(path.startsWith('M 0.0 0.0 C '));

  const offsets = computeRouteLaneOffsets([
    { id: 'r1', stopIds: ['a', 'b', 'c'] },
    { id: 'r2', stopIds: ['a', 'b', 'd'] },
  ]);
  assert.notEqual(offsets['r1'], offsets['r2']);
});

test('High walking balance generates scenic walking segments alongside trams', () => {
  const walkTrip = generateTramAdventure({
    city: 'spb',
    stops: SPB_STOPS,
    routes: SPB_TRAM_ROUTES,
    landmarks: SPB_LANDMARKS,
    startStopId: 'sennaya_pl',
    theme: 'any',
    durationPreset: 'medium',
    dayOfWeek: 3,
    timeMinutes: 14 * 60,
    allowFallbackTransit: false,
    modeBalance: { tram: 0.25, transit: 0.05, walk: 0.70 },
    seed: 55,
  });
  assert.equal(walkTrip.status, 'ok');
  if (walkTrip.status === 'ok') {
    const walkMins = walkTrip.trip.segments
      .filter((s) => s.kind === 'walk')
      .reduce((acc, s) => acc + s.durationMinutes, 0);
    assert.ok(walkMins >= 10, `Expected substantial walking minutes, got ${walkMins}`);
  }
});

test('High transit balance unlocks city-wide multimodal metro/bus + tram combinations', () => {
  const transitTrip = generateTramAdventure({
    city: 'spb',
    stops: SPB_STOPS,
    routes: SPB_TRAM_ROUTES,
    landmarks: SPB_LANDMARKS,
    startStopId: 'sennaya_pl',
    theme: 'any',
    durationPreset: 'medium',
    dayOfWeek: 3,
    timeMinutes: 14 * 60,
    allowFallbackTransit: false,
    modeBalance: { tram: 0.14, transit: 0.44, walk: 0.42 },
    seed: 88,
  });
  assert.equal(transitTrip.status, 'ok');
  if (transitTrip.status === 'ok') {
    assert.ok(
      transitTrip.trip.segments.some(
        (s) => s.kind === 'fallback_transit' || isCityTransitRoute(s.route)
      ),
      'Expected metro/trolleybus or fallback transit connector when transit weight is 44%'
    );
  }
});

test('computeSavedRoutesStats aggregates total distance and unique SPb districts only', () => {
  const spbOutcome = generateTramAdventure({
    city: 'spb',
    stops: SPB_STOPS,
    routes: SPB_TRAM_ROUTES,
    landmarks: SPB_LANDMARKS,
    startStopId: 'sennaya_pl',
    theme: 'any',
    durationPreset: 'medium',
    dayOfWeek: 3,
    timeMinutes: 14 * 60,
    allowFallbackTransit: false,
    seed: 123,
  });
  const omskOutcome = generateTramAdventure({
    city: 'omsk',
    stops: OMSK_STOPS,
    routes: OMSK_TRAM_ROUTES,
    landmarks: OMSK_LANDMARKS,
    startStopId: OMSK_STOPS[0].id,
    theme: 'any',
    durationPreset: 'short',
    dayOfWeek: 3,
    timeMinutes: 14 * 60,
    allowFallbackTransit: false,
    seed: 456,
  });
  assert.equal(spbOutcome.status, 'ok');
  assert.equal(omskOutcome.status, 'ok');
  if (spbOutcome.status === 'ok' && omskOutcome.status === 'ok') {
    const stopsById = Object.fromEntries(
      [...SPB_STOPS, ...OMSK_STOPS].map((s) => [s.id, s])
    );
    const spbTotalDistricts = new Set(SPB_STOPS.map((s) => s.district)).size;
    const saved = [
      {
        id: 'fav-1',
        customTitle: 'СПб маршрут',
        note: '',
        savedAt: '2026-11-21T00:00:00.000Z',
        trip: spbOutcome.trip,
      },
      {
        id: 'fav-2',
        customTitle: 'Омск маршрут',
        note: '',
        savedAt: '2026-11-21T00:00:00.000Z',
        trip: omskOutcome.trip,
      },
    ];
    const spbDist = calculateTripDistanceKm(spbOutcome.trip, stopsById);
    const omskDist = calculateTripDistanceKm(omskOutcome.trip, stopsById);
    assert.ok(spbDist > 0);
    assert.ok(omskDist > 0);

    const stats = computeSavedRoutesStats(saved, stopsById, spbTotalDistricts);
    assert.equal(stats.totalRoutesCount, 2);
    assert.equal(stats.spbRoutesCount, 1);
    assert.ok(Math.abs(stats.totalDistanceKm - (spbDist + omskDist)) < 0.25);
    assert.equal(stats.spbDistrictsCount, new Set(spbOutcome.trip.districtsVisited).size);
    assert.equal(stats.spbDistrictsTotal, spbTotalDistricts);
    for (const d of stats.spbDistrictNames) {
      assert.ok(spbOutcome.trip.districtsVisited.includes(d));
    }
  }
});

test('Full SPb & Omsk networks include metro M1-M6, Slavanka 2/22, trolleybuses Тр-67, and Summer 2027 tags', () => {
  const spbNumbers = new Set(SPB_TRAM_ROUTES.map((r) => r.number));
  for (const num of ['М1', 'М2', 'М3', 'М4', 'М5', 'М6', '2', '22']) {
    assert.ok(spbNumbers.has(num), `SPb must include route ${num}`);
  }
  const route2 = SPB_TRAM_ROUTES.find((r) => r.number === '2');
  const route22 = SPB_TRAM_ROUTES.find((r) => r.number === '22');
  assert.equal(Boolean(route2?.planned2027), false, 'Tram 2 (stage 1) should be active');
  assert.equal(route22?.planned2027, true, 'Tram 22 (stage 2) should be marked planned2027');

  const omskNumbers = new Set(OMSK_TRAM_ROUTES.map((r) => r.number));
  for (const num of [
    '1', '2', '4', '7', '8', '9',
    '2К', '8К',
    'Тр-2', 'Тр-3', 'Тр-4', 'Тр-4Э', 'Тр-7', 'Тр-8', 'Тр-12', 'Тр-15', 'Тр-16', 'Тр-67',
    'А-14', 'А-24', 'А-69', 'А-95', 'А-110',
  ]) {
    assert.ok(omskNumbers.has(num), `Omsk must include route ${num}`);
  }
  const omsk2k = OMSK_TRAM_ROUTES.find((r) => r.number === '2К');
  const omsk8k = OMSK_TRAM_ROUTES.find((r) => r.number === '8К');
  assert.equal(omsk2k?.planned2027, true);
  assert.equal(omsk8k?.planned2027, true);

  assert.ok(OMSK_STOPS.length >= 300, 'Expected full Omsk stop network from OOT.xls');
  const omskStopIds = new Set(OMSK_STOPS.map((s) => s.id));
  const omskStopNames = new Set(OMSK_STOPS.map((s) => s.name));
  assert.equal(omskStopIds.size, OMSK_STOPS.length, 'Omsk stop IDs must be unique');
  assert.equal(omskStopNames.size, OMSK_STOPS.length, 'Omsk stop names must be disambiguated');
  for (const route of OMSK_TRAM_ROUTES) {
    assert.ok(route.stopIds.length >= 5, `Route ${route.number} must have stops`);
    for (const sid of route.stopIds) {
      assert.ok(omskStopIds.has(sid), `Route ${route.number} stop ${sid} must exist in OMSK_STOPS`);
    }
  }

  const omskMultimodal = generateTramAdventure({
    city: 'omsk',
    stops: OMSK_STOPS,
    routes: OMSK_TRAM_ROUTES,
    landmarks: OMSK_LANDMARKS,
    startStopId: 'omsk_cirk',
    theme: 'any',
    durationPreset: 'medium',
    dayOfWeek: 3,
    timeMinutes: 14 * 60,
    allowFallbackTransit: false,
    modeBalance: { tram: 0.25, transit: 0.5, walk: 0.25 },
    seed: 314,
  });
  assert.equal(omskMultimodal.status, 'ok');
});

test('Deadline mode, includePlanned2027 filter, and 3 route alternatives (including adjacent-stop exit + walk)', () => {
  assert.equal(computeDeadlineBudgetMinutes(15 * 60, 18 * 60), 180);
  assert.equal(computeDeadlineBudgetMinutes(23 * 60, 1 * 60), 120);

  const destOutcome = generateTramAdventure({
    city: 'spb',
    stops: SPB_STOPS,
    routes: SPB_TRAM_ROUTES,
    landmarks: SPB_LANDMARKS,
    startStopId: 'm_kupchino',
    destinationStopId: 'metro_pl_vosstaniya',
    theme: 'any',
    durationPreset: 'medium',
    timeMode: 'deadline',
    deadlineMinutes: 18 * 60,
    allowFallbackTransit: false,
    includePlanned2027: false,
    dayOfWeek: 3,
    timeMinutes: 15 * 60,
    modeBalance: { tram: 0.2, transit: 0.45, walk: 0.35 },
    seed: 515,
  });
  assert.equal(destOutcome.status, 'ok');
  if (destOutcome.status === 'ok') {
    const alts = destOutcome.trip.alternatives ?? [];
    assert.equal(alts.length, 3, 'Should produce 3 alternatives for A->B trip');
    assert.equal(destOutcome.trip.targetDeadlineMinutes, 18 * 60);
    assert.ok((destOutcome.trip.arrivalTimeMinutes ?? 9999) <= 18 * 60);
    for (const alt of alts) {
      assert.equal(
        alt.segments.some((s) => s.route?.planned2027),
        false,
        'Planned 2027 routes must be excluded when includePlanned2027=false'
      );
    }
    assert.ok(
      alts.some(
        (alt) =>
          alt.variantTitle?.includes('соседней') || alt.segments.some((s) => s.kind === 'walk')
      ),
      'Should include adjacent stop + walk or walking alternative'
    );
  }

  const randomOutcome = generateTramAdventure({
    city: 'spb',
    stops: SPB_STOPS,
    routes: SPB_TRAM_ROUTES,
    landmarks: SPB_LANDMARKS,
    startStopId: 'sennaya_pl',
    theme: 'any',
    durationPreset: 'medium',
    timeMode: 'deadline',
    deadlineMinutes: 18 * 60,
    allowFallbackTransit: false,
    dayOfWeek: 3,
    timeMinutes: 15 * 60 + 30,
    modeBalance: { tram: 0.34, transit: 0.33, walk: 0.33 },
    seed: 777,
  });
  assert.equal(randomOutcome.status, 'ok');
  if (randomOutcome.status === 'ok') {
    assert.equal(randomOutcome.trip.alternatives?.length, 3);
    assert.ok((randomOutcome.trip.arrivalTimeMinutes ?? 9999) <= 18 * 60 + 10);
  }
});
