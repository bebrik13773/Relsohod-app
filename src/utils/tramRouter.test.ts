import test from 'node:test';
import assert from 'node:assert/strict';
import { SPB_LANDMARKS, SPB_STOPS, SPB_TRAM_ROUTES } from '../data/spbTramNetwork';
import { OMSK_LANDMARKS, OMSK_STOPS, OMSK_TRAM_ROUTES } from '../data/omskTramNetwork';
import { detectCityFromCoords } from '../data/cities';
import { buildYandexMapsRouteUrl } from './yandexMaps';
import {
  extendTripLastSegment,
  filterLandmarks,
  findNearestStops,
  generateTramAdventure,
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
