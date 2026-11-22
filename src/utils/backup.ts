import { CITIES } from '../data/cities';
import {
  AccentSeed,
  AppTheme,
  CityId,
  DurationPreset,
  GeneratedTrip,
  Landmark,
  LandmarkCategory,
  ModeBalance,
  PersistedPlannerState,
  RouteSegment,
  RouteThemeFilter,
  SavedRoute,
  TramRoute,
  TramRouteType,
  TramStop,
  TripHistoryEntry,
  TripPhotoMemory,
  WeatherMode,
} from '../types/tram';
import { normalizeModeBalance } from './mapGeometry';

export const BACKUP_FORMAT = 'relsokhod-backup';
export const BACKUP_VERSION = 1;
export const MAX_BACKUP_BYTES = 35 * 1024 * 1024;
const MAX_PHOTOS_PER_IMPORT = 120;

export interface BackupSettingsPayload {
  city: CityId;
  theme: AppTheme;
  accentSeed: AccentSeed;
  photoReminderMinutes: number;
  onboardingDone: boolean;
}

export interface BackupPlannerCityPayload {
  startStopId: string;
  destinationStopId: string | null;
  destinationAddressLabel: string;
  themeFilter: RouteThemeFilter;
  durationPreset: DurationPreset;
  dayOfWeek: number;
  timeMinutes: number;
  allowFallbackTransit: boolean;
  modeBalance: ModeBalance;
  showAllNetwork: boolean;
  loopMode: boolean;
  weatherMode: WeatherMode;
  preferSpecialFleet: boolean;
}

export interface BackupEnvelope {
  format: typeof BACKUP_FORMAT;
  version: number;
  exportedAt: string;
  source: 'web' | 'android';
  settings: BackupSettingsPayload;
  planner: {
    active: PersistedPlannerState;
    spb?: BackupPlannerCityPayload;
    omsk?: BackupPlannerCityPayload;
  };
  savedRoutes: SavedRoute[];
  tripHistory: TripHistoryEntry[];
}

export interface NormalizedBackup {
  exportedAt: string;
  source: 'web' | 'android';
  settings: BackupSettingsPayload;
  plannerState: PersistedPlannerState;
  savedRoutes: SavedRoute[];
  tripHistory: TripHistoryEntry[];
  summary: {
    savedRoutesCount: number;
    historyCount: number;
    photosCount: number;
    city: CityId;
  };
}

export type BackupParseResult =
  | { ok: true; data: NormalizedBackup }
  | { ok: false; error: string };

const VALID_ACCENTS: AccentSeed[] = ['neva', 'sage', 'terracotta', 'indigo', 'amber'];
const VALID_THEMES: AppTheme[] = ['light', 'dark', 'tram'];
const VALID_ROUTE_THEMES: RouteThemeFilter[] = ['any', 'center', 'parks', 'suburbs', 'maritime'];
const VALID_DURATIONS: DurationPreset[] = ['any', 'short', 'medium', 'long'];
const VALID_WEATHERS: WeatherMode[] = ['clear', 'rain', 'frost'];
const VALID_CATEGORIES: LandmarkCategory[] = ['architecture', 'park', 'hidden', 'cafe', 'grocery'];
const VALID_REMINDERS = [0, 15, 30, 45, 60];
const SAFE_IMAGE_DATA_URL = /^data:image\/(png|jpeg|jpg|webp|gif);base64,[A-Za-z0-9+/=]+$/i;

export function buildBackupFilename(date: Date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `export-${year}-${month}-${day}.relsohod`;
}

export function isAcceptedBackupFilename(filename: string): boolean {
  const lower = filename.trim().toLowerCase();
  return lower.endsWith('.relsohod') || lower.endsWith('.relsokhod') || lower.endsWith('.json');
}

export function createBackupPayload(params: {
  settings: BackupSettingsPayload;
  plannerState: PersistedPlannerState;
  savedRoutes: SavedRoute[];
  tripHistory: TripHistoryEntry[];
  exportedAt?: string;
}): BackupEnvelope {
  const cityPayload: BackupPlannerCityPayload = {
    startStopId: params.plannerState.startStopId,
    destinationStopId: params.plannerState.destinationStopId,
    destinationAddressLabel: params.plannerState.destinationAddressLabel,
    themeFilter: params.plannerState.themeFilter,
    durationPreset: params.plannerState.durationPreset,
    dayOfWeek: params.plannerState.dayOfWeek,
    timeMinutes: params.plannerState.timeMinutes,
    allowFallbackTransit: params.plannerState.allowFallbackTransit,
    modeBalance: normalizeModeBalance(params.plannerState.modeBalance),
    showAllNetwork: params.plannerState.showAllNetwork,
    loopMode: params.plannerState.loopMode,
    weatherMode: params.plannerState.weatherMode,
    preferSpecialFleet: params.plannerState.preferSpecialFleet,
  };

  return {
    format: BACKUP_FORMAT,
    version: BACKUP_VERSION,
    exportedAt: params.exportedAt ?? new Date().toISOString(),
    source: 'web',
    settings: { ...params.settings },
    planner: {
      active: { ...params.plannerState },
      [params.settings.city]: cityPayload,
    },
    savedRoutes: params.savedRoutes,
    tripHistory: params.tripHistory,
  };
}

export function serializeBackup(envelope: BackupEnvelope): string {
  return JSON.stringify(envelope, null, 2);
}

function asRecord(val: unknown): Record<string, unknown> | null {
  if (!val || typeof val !== 'object' || Array.isArray(val)) return null;
  return val as Record<string, unknown>;
}

function asString(val: unknown, fallback = ''): string {
  return typeof val === 'string' ? val : fallback;
}

function asNumber(val: unknown, fallback: number): number {
  return typeof val === 'number' && Number.isFinite(val) ? val : fallback;
}

function asBoolean(val: unknown, fallback: boolean): boolean {
  return typeof val === 'boolean' ? val : fallback;
}

function normalizeCityId(val: unknown): CityId {
  return val === 'omsk' ? 'omsk' : 'spb';
}

function normalizeTheme(val: unknown): AppTheme {
  return typeof val === 'string' && VALID_THEMES.includes(val as AppTheme)
    ? (val as AppTheme)
    : 'light';
}

function normalizeAccent(val: unknown): AccentSeed {
  return typeof val === 'string' && VALID_ACCENTS.includes(val as AccentSeed)
    ? (val as AccentSeed)
    : 'neva';
}

function normalizeStop(raw: unknown, city: CityId): TramStop | null {
  const rec = asRecord(raw);
  if (!rec) return null;
  const id = asString(rec.id).trim();
  const name = asString(rec.name).trim();
  if (!id || !name) return null;

  const catalogStop = CITIES[city].stops.find((s) => s.id === id);
  const lat = asNumber(rec.lat, catalogStop?.lat ?? CITIES[city].center.lat);
  const lng = asNumber(rec.lng, catalogStop?.lng ?? CITIES[city].center.lng);

  return {
    id,
    name,
    district: asString(rec.district, catalogStop?.district ?? ''),
    lat,
    lng,
    hubId: typeof rec.hubId === 'string' && rec.hubId.trim() ? rec.hubId : catalogStop?.hubId,
    isolatedFromMainTram: asBoolean(
      rec.isolatedFromMainTram,
      catalogStop?.isolatedFromMainTram ?? false
    ),
  };
}

function normalizeRoute(raw: unknown, city: CityId): TramRoute | undefined {
  const rec = asRecord(raw);
  if (!rec) return undefined;
  const id = asString(rec.id).trim();
  const number = asString(rec.number).trim();
  const title = asString(rec.title).trim();
  if (!id || !number) return undefined;

  const catalogRoute = CITIES[city].routes.find((r) => r.id === id);
  const schedRec = asRecord(rec.schedule);
  const rawType = asString(rec.type, catalogRoute?.type ?? 'regular');
  const routeType: TramRouteType =
    rawType === 'circular' || rawType === 'retro' || rawType === 'chizhik' ? rawType : 'regular';

  const color =
    asString(rec.color).trim() ||
    asString(rec.colorHex).trim() ||
    catalogRoute?.color ||
    '#0f5e8c';

  const daysRaw = Array.isArray(schedRec?.daysOfWeek) ? schedRec.daysOfWeek : [];
  const days = daysRaw
    .filter((d): d is number => typeof d === 'number' && Number.isInteger(d) && d >= 0 && d <= 6);

  const stopIdsRaw = Array.isArray(rec.stopIds) ? rec.stopIds : [];
  const stopIds = stopIdsRaw.filter((s): s is string => typeof s === 'string' && s.length > 0);

  const themesRaw = Array.isArray(rec.themes) ? rec.themes : [];
  const themes = themesRaw.filter(
    (t): t is Exclude<RouteThemeFilter, 'any'> =>
      typeof t === 'string' && ['center', 'parks', 'suburbs', 'maritime'].includes(t)
  );

  return {
    id,
    number,
    title: title || catalogRoute?.title || `Маршрут №${number}`,
    operator: catalogRoute?.operator ?? (city === 'omsk' ? 'МП «Электрический транспорт»' : 'Горэлектротранс'),
    type: routeType,
    color,
    themes: themes.length > 0 ? themes : (catalogRoute?.themes ?? ['center']),
    schedule: {
      daysOfWeek: days.length > 0 ? days : (catalogRoute?.schedule.daysOfWeek ?? [0, 1, 2, 3, 4, 5, 6]),
      startMinutes: asNumber(schedRec?.startMinutes, catalogRoute?.schedule.startMinutes ?? 360),
      endMinutes: asNumber(schedRec?.endMinutes, catalogRoute?.schedule.endMinutes ?? 1440),
      intervalMinutes: asNumber(schedRec?.intervalMinutes, catalogRoute?.schedule.intervalMinutes ?? 12),
      note: asString(schedRec?.note, asString(schedRec?.label, catalogRoute?.schedule.note ?? '')),
    },
    stopIds: stopIds.length > 0 ? stopIds : (catalogRoute?.stopIds ?? []),
    minutesPerHop: asNumber(rec.minutesPerHop, catalogRoute?.minutesPerHop ?? 3),
  };
}

function normalizeSegment(raw: unknown, city: CityId): RouteSegment | null {
  const rec = asRecord(raw);
  if (!rec) return null;
  const fromStop = normalizeStop(rec.fromStop, city);
  const toStop = normalizeStop(rec.toStop, city);
  if (!fromStop || !toStop) return null;

  const rawKind = asString(rec.kind).toLowerCase();
  const kind: RouteSegment['kind'] =
    rawKind === 'walk'
      ? 'walk'
      : rawKind === 'fallback_transit'
        ? 'fallback_transit'
        : 'tram';

  const stopIdsRaw = Array.isArray(rec.stopIds) ? rec.stopIds : [];
  const stopIds = stopIdsRaw.filter((s): s is string => typeof s === 'string' && s.length > 0);

  return {
    id: asString(rec.id) || `seg-${Math.random().toString(36).slice(2, 8)}`,
    kind,
    route: kind === 'tram' ? normalizeRoute(rec.route, city) : undefined,
    fallbackModeName:
      typeof rec.fallbackModeName === 'string' && rec.fallbackModeName.trim()
        ? rec.fallbackModeName
        : undefined,
    fromStop,
    toStop,
    stopIds: stopIds.length > 0 ? stopIds : [fromStop.id, toStop.id],
    stopsCount: asNumber(rec.stopsCount, Math.max(0, stopIds.length - 1)),
    durationMinutes: Math.max(1, asNumber(rec.durationMinutes, 5)),
    directionNote: asString(rec.directionNote),
  };
}

function normalizeLandmark(raw: unknown, city: CityId): Landmark | null {
  const rec = asRecord(raw);
  if (!rec) return null;
  const id = asString(rec.id).trim();
  const name = asString(rec.name).trim();
  if (!id || !name) return null;

  const catalogLm = CITIES[city].landmarks.find((l) => l.id === id);
  const rawCategory = asString(rec.category, catalogLm?.category ?? 'architecture');
  const category: LandmarkCategory = VALID_CATEGORIES.includes(rawCategory as LandmarkCategory)
    ? (rawCategory as LandmarkCategory)
    : 'architecture';

  const nearbyRaw = Array.isArray(rec.nearbyStopIds) ? rec.nearbyStopIds : [];
  const nearbyStopIds = nearbyRaw.filter((s): s is string => typeof s === 'string' && s.length > 0);

  return {
    id,
    name,
    category,
    lat: asNumber(rec.lat, catalogLm?.lat ?? CITIES[city].center.lat),
    lng: asNumber(rec.lng, catalogLm?.lng ?? CITIES[city].center.lng),
    district: asString(rec.district, catalogLm?.district ?? ''),
    description: asString(rec.description, catalogLm?.description ?? ''),
    nearbyStopIds: nearbyStopIds.length > 0 ? nearbyStopIds : (catalogLm?.nearbyStopIds ?? []),
    photoUrl:
      typeof rec.photoUrl === 'string' && rec.photoUrl.trim()
        ? rec.photoUrl
        : (catalogLm?.photoUrl ?? null),
  };
}

function normalizeTrip(raw: unknown, fallbackCity: CityId): GeneratedTrip | null {
  const rec = asRecord(raw);
  if (!rec) return null;
  const city = rec.city === 'omsk' || rec.city === 'spb' ? rec.city : fallbackCity;
  const startStop = normalizeStop(rec.startStop, city);
  const endStop = normalizeStop(rec.endStop, city);
  if (!startStop || !endStop) return null;

  const segsRaw = Array.isArray(rec.segments) ? rec.segments : [];
  const segments = segsRaw
    .map((s) => normalizeSegment(s, city))
    .filter((s): s is RouteSegment => s !== null);
  if (segments.length === 0) return null;

  const linesRaw = Array.isArray(rec.tramLinesUsed) ? rec.tramLinesUsed : [];
  const tramLinesUsed = linesRaw.filter((l): l is string => typeof l === 'string' && l.length > 0);

  const distRaw = Array.isArray(rec.districtsVisited) ? rec.districtsVisited : [];
  const districtsVisited = distRaw.filter((d): d is string => typeof d === 'string' && d.length > 0);

  const lmRaw = Array.isArray(rec.landmarks) ? rec.landmarks : [];
  const landmarks = lmRaw
    .map((l) => normalizeLandmark(l, city))
    .filter((l): l is Landmark => l !== null);

  const mbRec = asRecord(rec.modeBalance);
  const modeBalance = mbRec
    ? normalizeModeBalance({
        tram: asNumber(mbRec.tram, 0.72),
        transit: asNumber(mbRec.transit, 0.08),
        walk: asNumber(mbRec.walk, 0.2),
      })
    : undefined;

  const rawWeather = asString(rec.weatherMode, 'clear');
  const weatherMode: WeatherMode = VALID_WEATHERS.includes(rawWeather as WeatherMode)
    ? (rawWeather as WeatherMode)
    : 'clear';

  return {
    id: asString(rec.id) || `trip-${Math.random().toString(36).slice(2, 9)}`,
    city,
    createdAt: asString(rec.createdAt, asString(rec.createdAtIso, new Date().toISOString())),
    startStop,
    endStop,
    segments,
    tramLinesUsed,
    transferCount: Math.max(0, asNumber(rec.transferCount, 0)),
    totalDurationMinutes: Math.max(1, asNumber(rec.totalDurationMinutes, 20)),
    usedFallbackTransit: asBoolean(rec.usedFallbackTransit, false),
    isLoop: asBoolean(rec.isLoop, false),
    weatherMode,
    modeBalance,
    districtsVisited,
    landmarks,
    dayOfWeek: asNumber(rec.dayOfWeek, 3),
    timeMinutes: asNumber(rec.timeMinutes, 14 * 60),
  };
}

function sanitizeImageDataUrl(raw: unknown): string | null {
  if (typeof raw !== 'string') return null;
  const trimmed = raw.trim();
  if (trimmed.length === 0 || trimmed.length > 10 * 1024 * 1024) return null;
  return SAFE_IMAGE_DATA_URL.test(trimmed) ? trimmed : null;
}

export function parseAndValidateBackup(rawText: string): BackupParseResult {
  if (!rawText || rawText.trim().length === 0) {
    return { ok: false, error: 'Выбранный файл пуст.' };
  }
  if (rawText.length > MAX_BACKUP_BYTES) {
    return { ok: false, error: 'Файл резервной копии слишком большой (более 35 МБ).' };
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(rawText);
  } catch {
    return { ok: false, error: 'Файл повреждён или не является корректной копией .relsohod.' };
  }

  const root = asRecord(parsed);
  if (!root) {
    return { ok: false, error: 'Неверная структура файла резервной копии.' };
  }
  if (root.format !== BACKUP_FORMAT) {
    return {
      ok: false,
      error: 'Этот файл не является резервной копией «Рельсохода» (.relsohod).',
    };
  }
  const version = asNumber(root.version, 0);
  if (version < 1 || version > BACKUP_VERSION) {
    return {
      ok: false,
      error: `Неподдерживаемая версия формата резервной копии (v${version}). Обновите приложение.`,
    };
  }

  const settingsRec = asRecord(root.settings) ?? {};
  const city = normalizeCityId(settingsRec.city);
  const theme = normalizeTheme(settingsRec.theme);
  const accentSeed = normalizeAccent(settingsRec.accentSeed);
  const rawReminder = asNumber(settingsRec.photoReminderMinutes, 30);
  const photoReminderMinutes = VALID_REMINDERS.includes(rawReminder) ? rawReminder : 30;
  const onboardingDone = asBoolean(settingsRec.onboardingDone, true);

  const plannerRoot = asRecord(root.planner) ?? {};
  const plannerRec =
    asRecord(plannerRoot.active) ??
    asRecord(plannerRoot[city]) ??
    asRecord(plannerRoot.spb) ??
    asRecord(plannerRoot.omsk) ??
    {};

  const cityCfg = CITIES[city];
  const rawStartStopId = asString(plannerRec.startStopId, cityCfg.defaultStartStopId);
  const validStartStopId = cityCfg.stops.some((s) => s.id === rawStartStopId)
    ? rawStartStopId
    : cityCfg.defaultStartStopId;

  const rawDestId =
    typeof plannerRec.destinationStopId === 'string' &&
    cityCfg.stops.some((s) => s.id === plannerRec.destinationStopId)
      ? plannerRec.destinationStopId
      : null;

  const rawThemeFilter = asString(plannerRec.themeFilter, 'any');
  const themeFilter: RouteThemeFilter = VALID_ROUTE_THEMES.includes(rawThemeFilter as RouteThemeFilter)
    ? (rawThemeFilter as RouteThemeFilter)
    : 'any';

  const rawDuration = asString(plannerRec.durationPreset, 'medium');
  const durationPreset: DurationPreset = VALID_DURATIONS.includes(rawDuration as DurationPreset)
    ? (rawDuration as DurationPreset)
    : 'medium';

  const rawWeather = asString(plannerRec.weatherMode, 'clear');
  const weatherMode: WeatherMode = VALID_WEATHERS.includes(rawWeather as WeatherMode)
    ? (rawWeather as WeatherMode)
    : 'clear';

  const mbRec = asRecord(plannerRec.modeBalance);
  const modeBalance = mbRec
    ? normalizeModeBalance({
        tram: asNumber(mbRec.tram, 0.72),
        transit: asNumber(mbRec.transit, 0.08),
        walk: asNumber(mbRec.walk, 0.2),
      })
    : normalizeModeBalance({ tram: 0.72, transit: 0.08, walk: 0.2 });

  const lmCatsRaw = Array.isArray(plannerRec.landmarkCategories)
    ? plannerRec.landmarkCategories
    : [];
  const landmarkCategories = lmCatsRaw.filter(
    (c): c is LandmarkCategory =>
      typeof c === 'string' && VALID_CATEGORIES.includes(c as LandmarkCategory)
  );

  const plannerState: PersistedPlannerState = {
    city,
    startStopId: validStartStopId,
    destinationStopId: rawDestId,
    destinationAddressLabel: asString(plannerRec.destinationAddressLabel, ''),
    themeFilter,
    durationPreset,
    dayOfWeek: Math.max(0, Math.min(6, Math.round(asNumber(plannerRec.dayOfWeek, 3)))),
    timeMinutes: Math.max(0, Math.min(1439, Math.round(asNumber(plannerRec.timeMinutes, 14 * 60)))),
    allowFallbackTransit: asBoolean(plannerRec.allowFallbackTransit, false),
    modeBalance,
    showAllNetwork: asBoolean(plannerRec.showAllNetwork, true),
    loopMode: asBoolean(plannerRec.loopMode, false),
    weatherMode,
    preferSpecialFleet: asBoolean(plannerRec.preferSpecialFleet, false),
    onlyWithPhotos: asBoolean(plannerRec.onlyWithPhotos, true),
    landmarkCategories,
  };

  const rawSaved = Array.isArray(root.savedRoutes) ? root.savedRoutes : [];
  const savedRoutes: SavedRoute[] = [];
  for (const item of rawSaved) {
    const rec = asRecord(item);
    if (!rec) continue;
    const trip = normalizeTrip(rec.trip, city);
    if (!trip) continue;
    savedRoutes.push({
      id: asString(rec.id) || `saved-${savedRoutes.length + 1}`,
      customTitle:
        asString(rec.customTitle).trim() ||
        `${trip.startStop.name.split('(')[0].trim()} → ${trip.endStop.name.split('(')[0].trim()}`,
      note: asString(rec.note, ''),
      savedAt: asString(rec.savedAt, asString(rec.savedAtIso, new Date().toISOString())),
      trip,
    });
  }

  const rawHistory = Array.isArray(root.tripHistory) ? root.tripHistory : [];
  const tripHistory: TripHistoryEntry[] = [];
  let photosCount = 0;

  for (const item of rawHistory.slice(0, 50)) {
    const rec = asRecord(item);
    if (!rec) continue;
    const trip = normalizeTrip(rec.trip, city);
    if (!trip) continue;
    const entryCity =
      rec.city === 'omsk' || rec.city === 'spb'
        ? rec.city
        : trip.city === 'omsk'
          ? 'omsk'
          : 'spb';

    const photosRaw = Array.isArray(rec.photos) ? rec.photos : [];
    const photos: TripPhotoMemory[] = [];
    for (const pItem of photosRaw) {
      if (photosCount >= MAX_PHOTOS_PER_IMPORT) break;
      const pRec = asRecord(pItem);
      if (!pRec) continue;
      const dataUrl = sanitizeImageDataUrl(pRec.dataUrl);
      if (!dataUrl) continue;
      photosCount += 1;
      photos.push({
        id: asString(pRec.id) || `photo-${photosCount}`,
        dataUrl,
        caption: asString(pRec.caption, 'Фото из поездки'),
        takenAt: asString(pRec.takenAt, asString(pRec.takenAtIso, new Date().toISOString())),
      });
    }

    tripHistory.push({
      id: asString(rec.id) || `hist-${tripHistory.length + 1}`,
      city: entryCity,
      completedAt: asString(rec.completedAt, asString(rec.completedAtIso, new Date().toISOString())),
      trip,
      photos,
    });
  }

  return {
    ok: true,
    data: {
      exportedAt: asString(root.exportedAt, new Date().toISOString()),
      source: root.source === 'android' ? 'android' : 'web',
      settings: {
        city,
        theme,
        accentSeed,
        photoReminderMinutes,
        onboardingDone,
      },
      plannerState,
      savedRoutes,
      tripHistory,
      summary: {
        savedRoutesCount: savedRoutes.length,
        historyCount: tripHistory.length,
        photosCount,
        city,
      },
    },
  };
}
