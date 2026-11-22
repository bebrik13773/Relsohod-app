import test from 'node:test';
import assert from 'node:assert/strict';
import { CITIES } from '../data/cities';
import { generateTramAdventure } from './tramRouter';
import {
  BACKUP_FORMAT,
  buildBackupFilename,
  createBackupPayload,
  isAcceptedBackupFilename,
  parseAndValidateBackup,
  serializeBackup,
} from './backup';

test('buildBackupFilename formats dated .relsohod filename and validates extensions', () => {
  const sampleDate = new Date(2026, 10, 22, 9, 15, 0);
  assert.equal(buildBackupFilename(sampleDate), 'export-2026-11-22.relsohod');
  assert.equal(isAcceptedBackupFilename('export-2026-11-22.relsohod'), true);
  assert.equal(isAcceptedBackupFilename('export-2026-11-22.relsokhod'), true);
  assert.equal(isAcceptedBackupFilename('backup.json'), true);
  assert.equal(isAcceptedBackupFilename('notes.txt'), false);
});

test('round-trip backup export and restore preserves settings, saved routes, history, and photos', () => {
  const cfg = CITIES.spb;
  const outcome = generateTramAdventure({
    city: 'spb',
    stops: cfg.stops,
    routes: cfg.routes,
    landmarks: cfg.landmarks,
    startStopId: cfg.defaultStartStopId,
    destinationStopId: null,
    theme: 'any',
    durationPreset: 'medium',
    dayOfWeek: 3,
    timeMinutes: 14 * 60,
    allowFallbackTransit: true,
    seed: 42,
  });
  assert.equal(outcome.status, 'ok');

  const envelope = createBackupPayload({
    settings: {
      city: 'omsk',
      theme: 'dark',
      accentSeed: 'terracotta',
      photoReminderMinutes: 45,
      onboardingDone: true,
    },
    plannerState: {
      city: 'omsk',
      startStopId: CITIES.omsk.defaultStartStopId,
      destinationStopId: null,
      destinationAddressLabel: '',
      themeFilter: 'parks',
      durationPreset: 'long',
      dayOfWeek: 5,
      timeMinutes: 16 * 60,
      allowFallbackTransit: true,
      modeBalance: { tram: 0.6, transit: 0.1, walk: 0.3 },
      showAllNetwork: true,
      loopMode: true,
      weatherMode: 'rain',
      preferSpecialFleet: true,
      onlyWithPhotos: false,
      landmarkCategories: ['park', 'cafe'],
    },
    savedRoutes: [
      {
        id: 'saved-1',
        customTitle: 'Тестовый маршрут',
        note: 'С фото',
        savedAt: '2026-11-22T10:00:00Z',
        trip: outcome.trip,
      },
    ],
    tripHistory: [
      {
        id: 'hist-1',
        city: 'spb',
        completedAt: '2026-11-22T11:30:00Z',
        trip: outcome.trip,
        photos: [
          {
            id: 'p-1',
            dataUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
            caption: 'Снимок у Невы',
            takenAt: '2026-11-22T11:25:00Z',
          },
        ],
      },
    ],
  });

  const raw = serializeBackup(envelope);
  const parsed = parseAndValidateBackup(raw);
  assert.equal(parsed.ok, true);
  if (!parsed.ok) return;

  assert.equal(parsed.data.settings.city, 'omsk');
  assert.equal(parsed.data.settings.theme, 'dark');
  assert.equal(parsed.data.settings.accentSeed, 'terracotta');
  assert.equal(parsed.data.settings.photoReminderMinutes, 45);
  assert.equal(parsed.data.plannerState.loopMode, true);
  assert.equal(parsed.data.plannerState.weatherMode, 'rain');
  assert.equal(parsed.data.savedRoutes.length, 1);
  assert.equal(parsed.data.savedRoutes[0].customTitle, 'Тестовый маршрут');
  assert.equal(parsed.data.tripHistory.length, 1);
  assert.equal(parsed.data.tripHistory[0].photos.length, 1);
  assert.equal(parsed.data.summary.photosCount, 1);
});

test('imports Android-formatted .relsohod backup seamlessly and strips unsafe image URLs', () => {
  const cfg = CITIES.spb;
  const start = cfg.stops[0];
  const end = cfg.stops[1];
  const route = cfg.routes[0];

  const androidBackup = JSON.stringify({
    format: BACKUP_FORMAT,
    version: 1,
    exportedAt: '2026-11-22T12:00:00Z',
    source: 'android',
    settings: {
      city: 'spb',
      theme: 'tram',
      accentSeed: 'system', // Android-only seed falls back safely on web
      photoReminderMinutes: 15,
      onboardingDone: true,
    },
    planner: {
      spb: {
        startStopId: start.id,
        destinationStopId: end.id,
        destinationAddressLabel: end.name,
        themeFilter: 'center',
        durationPreset: 'short',
        dayOfWeek: 2,
        timeMinutes: 600,
        allowFallbackTransit: false,
        modeBalance: { tram: 0.7, transit: 0.1, walk: 0.2 },
        showAllNetwork: true,
        loopMode: false,
        weatherMode: 'frost',
        preferSpecialFleet: false,
      },
    },
    savedRoutes: [
      {
        id: 'android-saved-1',
        customTitle: 'Из Android',
        note: '',
        savedAtIso: '2026-11-22T09:00:00Z',
        trip: {
          id: 'trip-a1',
          city: 'spb',
          createdAtIso: '2026-11-22T08:50:00Z',
          startStop: start,
          endStop: end,
          segments: [
            {
              id: 'seg-1',
              kind: 'TRAM',
              route: {
                id: route.id,
                number: route.number,
                title: route.title,
                colorHex: route.color,
                type: route.type,
                themes: route.themes,
                stopIds: route.stopIds,
                minutesPerHop: route.minutesPerHop,
                schedule: {
                  daysOfWeek: route.schedule.daysOfWeek,
                  startMinutes: route.schedule.startMinutes,
                  endMinutes: route.schedule.endMinutes,
                  intervalMinutes: route.schedule.intervalMinutes,
                  label: route.schedule.note,
                },
              },
              fromStop: start,
              toStop: end,
              stopIds: [start.id, end.id],
              stopsCount: 1,
              durationMinutes: 6,
              directionNote: 'В центр',
            },
          ],
          tramLinesUsed: [route.number],
          transferCount: 0,
          totalDurationMinutes: 6,
          usedFallbackTransit: false,
          isLoop: false,
          weatherMode: 'frost',
          districtsVisited: [start.district],
          landmarks: [],
          dayOfWeek: 2,
          timeMinutes: 600,
        },
      },
    ],
    tripHistory: [
      {
        id: 'hist-a1',
        completedAtIso: '2026-11-22T09:30:00Z',
        trip: {
          id: 'trip-a1',
          city: 'spb',
          createdAtIso: '2026-11-22T08:50:00Z',
          startStop: start,
          endStop: end,
          segments: [
            {
              id: 'seg-w1',
              kind: 'WALK',
              fromStop: start,
              toStop: end,
              stopIds: [start.id, end.id],
              stopsCount: 1,
              durationMinutes: 5,
              directionNote: 'Пешком',
            },
          ],
          tramLinesUsed: [],
          transferCount: 0,
          totalDurationMinutes: 5,
          usedFallbackTransit: false,
          districtsVisited: [],
          landmarks: [],
          dayOfWeek: 2,
          timeMinutes: 600,
        },
        photos: [
          {
            id: 'unsafe-photo',
            dataUrl: 'data:text/html;base64,PHNjcmlwdD4=',
            caption: 'XSS',
            takenAtIso: '2026-11-22T09:20:00Z',
          },
          {
            id: 'safe-photo',
            dataUrl: 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ==',
            caption: 'Нормальное фото',
            takenAtIso: '2026-11-22T09:22:00Z',
          },
        ],
      },
    ],
  });

  const parsed = parseAndValidateBackup(androidBackup);
  assert.equal(parsed.ok, true);
  if (!parsed.ok) return;

  assert.equal(parsed.data.source, 'android');
  assert.equal(parsed.data.settings.theme, 'tram');
  assert.equal(parsed.data.settings.accentSeed, 'neva');
  assert.equal(parsed.data.savedRoutes.length, 1);
  assert.equal(parsed.data.savedRoutes[0].trip.segments[0].kind, 'tram');
  assert.equal(parsed.data.tripHistory[0].trip.segments[0].kind, 'walk');
  assert.equal(parsed.data.tripHistory[0].photos.length, 1);
  assert.equal(parsed.data.tripHistory[0].photos[0].id, 'safe-photo');
});

test('rejects invalid json and wrong backup format cleanly', () => {
  assert.equal(parseAndValidateBackup('{bad json').ok, false);
  assert.equal(parseAndValidateBackup(JSON.stringify({ format: 'other-app', version: 1 })).ok, false);
  assert.equal(parseAndValidateBackup(JSON.stringify({ format: BACKUP_FORMAT, version: 99 })).ok, false);
});
