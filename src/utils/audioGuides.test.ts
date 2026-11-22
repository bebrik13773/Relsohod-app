import test from 'node:test';
import assert from 'node:assert/strict';
import { CITIES } from '../data/cities';
import { OFFICIAL_AUDIO_GUIDES, matchAudioGuidesForTrip } from '../data/audioGuides';
import { generateTramAdventure } from './tramRouter';
import { GeneratedTrip } from '../types/tram';

function makeSyntheticSpbTrip(
  tramLinesUsed: string[],
  districtsVisited: string[]
): GeneratedTrip {
  const stop = CITIES.spb.stops[0];
  return {
    id: `test-${tramLinesUsed.join('-')}`,
    city: 'spb',
    createdAt: '2026-11-22T12:00:00Z',
    startStop: stop,
    endStop: stop,
    segments: [],
    tramLinesUsed,
    transferCount: 0,
    totalDurationMinutes: 45,
    usedFallbackTransit: false,
    districtsVisited,
    landmarks: [],
    dayOfWeek: 3,
    timeMinutes: 14 * 60,
  };
}

test('official audio guide catalog uses valid HTTPS URLs on curated portals', () => {
  assert.ok(OFFICIAL_AUDIO_GUIDES.length >= 10);
  for (const guide of OFFICIAL_AUDIO_GUIDES) {
    assert.ok(guide.url.startsWith('https://'), `Expected https URL for ${guide.id}`);
    assert.ok(guide.title.length > 5);
    assert.ok(guide.portalName.length > 3);
  }
});

test('SPb route using Tram №3 ranks official GET Route 3 audio guide first', () => {
  const trip = makeSyntheticSpbTrip(['3'], ['Адмиралтейский', 'Центральный']);
  const matched = matchAudioGuidesForTrip(trip, 3);
  assert.equal(matched.length, 3);
  assert.equal(matched[0].id, 'spb-get-route-3');
  assert.ok(matched[0].matchReason.includes('№ 3'));
  assert.ok(matched.every((g) => g.city === 'spb'));
});

test('SPb southwest route on Tram №52 or №60 ranks KGIOP 52/60 audio guide first', () => {
  const trip = makeSyntheticSpbTrip(['52', '60'], ['Кировский', 'Красносельский']);
  const matched = matchAudioGuidesForTrip(trip, 3);
  assert.equal(matched[0].id, 'spb-kgiop-52-60');
  assert.ok(matched[0].matchReason.includes('52'));
});

test('SPb Retro Tram Т1 prioritizes Gorelektrotrans / Museum audio guides', () => {
  const trip = makeSyntheticSpbTrip(['Т1'], ['Васильевский остров', 'Петроградская сторона']);
  const matched = matchAudioGuidesForTrip(trip, 3);
  assert.equal(matched[0].id, 'spb-get-museum-channel');
  const topIds = matched.map((g) => g.id);
  assert.ok(topIds.includes('spb-get-museum-site'));
});

test('Omsk generated trip returns Omsk official portal audio guides only', () => {
  const outcome = generateTramAdventure({
    city: 'omsk',
    stops: CITIES.omsk.stops,
    routes: CITIES.omsk.routes,
    landmarks: CITIES.omsk.landmarks,
    startStopId: CITIES.omsk.defaultStartStopId,
    theme: 'any',
    durationPreset: 'medium',
    dayOfWeek: 3,
    timeMinutes: 14 * 60,
    allowFallbackTransit: false,
    seed: 77,
  });
  assert.equal(outcome.status, 'ok');
  const matched = matchAudioGuidesForTrip(outcome.trip, 3);
  assert.equal(matched.length, 3);
  assert.ok(matched.every((g) => g.city === 'omsk'));
  assert.ok(matched.some((g) => g.url.includes('izi.travel')));
});

test('preferredRouteNumber promotes active Live Trip segment audio guide to the top', () => {
  const multiLineTrip = makeSyntheticSpbTrip(
    ['3', '41', '60', '52', '36'],
    ['Адмиралтейский', 'Кировский', 'Красносельский']
  );
  const stepOnRoute3 = matchAudioGuidesForTrip(multiLineTrip, 1, '3')[0];
  assert.equal(stepOnRoute3.id, 'spb-get-route-3');
  assert.ok(stepOnRoute3.matchReason.includes('Сейчас по шагу: трамвай № 3'));

  const stepOnRoute52 = matchAudioGuidesForTrip(multiLineTrip, 1, '52')[0];
  assert.equal(stepOnRoute52.id, 'spb-kgiop-52-60');
  assert.ok(stepOnRoute52.matchReason.includes('Сейчас по шагу: трамвай № 52'));
});

