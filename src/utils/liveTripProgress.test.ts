import test from 'node:test';
import assert from 'node:assert/strict';
import { RouteSegment, TramStop } from '../types/tram';
import {
  computeSegmentLiveProgress,
  pluralizeStopsRu,
  projectPointOntoStopsPolyline,
} from './liveTripProgress';

const sampleStops: TramStop[] = [
  { id: 's0', name: 'Магазин', district: 'Центр', lat: 55.000, lng: 73.350 },
  { id: 's1', name: '24-я Северная', district: 'Центр', lat: 55.005, lng: 73.350 },
  { id: 's2', name: '20-я Северная', district: 'Центр', lat: 55.010, lng: 73.350 },
  { id: 's3', name: 'Октябрьская улица', district: 'Центр', lat: 55.015, lng: 73.350 },
];

const stopsById = Object.fromEntries(sampleStops.map((s) => [s.id, s]));

const sampleSegment: RouteSegment = {
  id: 'seg-1-bus_63',
  kind: 'tram',
  fromStop: sampleStops[0],
  toStop: sampleStops[3],
  stopIds: ['s0', 's1', 's2', 's3'],
  stopsCount: 3,
  durationMinutes: 8,
  directionNote: 'Автобус · 63',
};

test('pluralizeStopsRu handles Russian singular/paucal/plural correctly', () => {
  assert.equal(pluralizeStopsRu(1), 'остановка');
  assert.equal(pluralizeStopsRu(2), 'остановки');
  assert.equal(pluralizeStopsRu(4), 'остановки');
  assert.equal(pluralizeStopsRu(5), 'остановок');
  assert.equal(pluralizeStopsRu(7), 'остановок');
  assert.equal(pluralizeStopsRu(11), 'остановок');
  assert.equal(pluralizeStopsRu(21), 'остановка');
});

test('projectPointOntoStopsPolyline projects coordinates smoothly along route corridor', () => {
  const atStart = projectPointOntoStopsPolyline({ lat: 55.000, lng: 73.350 }, sampleStops);
  assert.ok(atStart);
  assert.ok(atStart.progressFraction < 0.02);
  assert.equal(atStart.currentStopIndex, 0);
  assert.ok(atStart.distanceToRouteMeters < 10);

  const atMid = projectPointOntoStopsPolyline({ lat: 55.0075, lng: 73.3505 }, sampleStops);
  assert.ok(atMid);
  assert.ok(Math.abs(atMid.progressFraction - 0.5) < 0.03);
  assert.equal(atMid.currentStopIndex, 1);

  const atEnd = projectPointOntoStopsPolyline({ lat: 55.015, lng: 73.350 }, sampleStops);
  assert.ok(atEnd);
  assert.ok(atEnd.progressFraction > 0.98);
  assert.equal(atEnd.currentStopIndex, 3);
});

test('computeSegmentLiveProgress prefers GPS within corridor and falls back smoothly to timer when away', () => {
  const gpsLocked = computeSegmentLiveProgress({
    segment: sampleSegment,
    stopsById,
    elapsedSeconds: 30,
    userCoords: { lat: 55.010, lng: 73.3502, accuracyMeters: 12 },
  });
  assert.equal(gpsLocked.trackingSource, 'gps');
  assert.ok(Math.abs(gpsLocked.progressFraction - 2 / 3) < 0.05);
  assert.equal(gpsLocked.stopsRemaining, 1);
  assert.equal(gpsLocked.minutesRemaining, 3);

  const farAway = computeSegmentLiveProgress({
    segment: sampleSegment,
    stopsById,
    elapsedSeconds: 240, // halfway through 8 min (480 sec)
    userCoords: { lat: 59.934, lng: 30.335 }, // SPb coords while riding in Omsk
  });
  assert.equal(farAway.trackingSource, 'timer');
  assert.ok(Math.abs(farAway.progressFraction - 0.5) < 0.01);
  assert.equal(farAway.minutesRemaining, 4);
  assert.ok((farAway.distanceToRouteMeters ?? 0) > 100000);
});
