import type { CityConfig } from '../data/cities';
import {
  AccentSeed,
  AppTheme,
  CityId,
  GeneratedTrip,
  Landmark,
  ModeBalance,
  RouteSegment,
  TramRoute,
  TramStop,
  WeatherMode,
} from '../types/tram';
import { DEFAULT_MODE_BALANCE } from './mapGeometry';

export type EmbedSegmentKind = RouteSegment['kind'];

export interface EmbedMapInitialParams {
  city: CityId;
  theme: AppTheme;
  accentSeed: AccentSeed;
  showAllNetwork: boolean;
  includePlanned2027: boolean;
  startStopId: string | null;
  destinationStopId: string | null;
  mapPickMode: 'start' | 'destination' | null;
  selectedLandmarkId: string | null;
}

export interface EmbeddedSegmentPayload {
  id?: string;
  kind: EmbedSegmentKind;
  routeId?: string | null;
  routeNumber?: string | null;
  route?: TramRoute | null;
  fallbackModeName?: string | null;
  fromStopId?: string;
  toStopId?: string;
  stopIds?: string[];
  stopsCount?: number;
  durationMinutes?: number;
  directionNote?: string;
}

export interface EmbeddedTripPayload {
  id?: string;
  city?: CityId;
  createdAt?: string;
  createdAtIso?: string;
  startStopId?: string;
  endStopId?: string;
  isLoop?: boolean;
  totalDurationMinutes?: number;
  transferCount?: number;
  usedFallbackTransit?: boolean;
  weatherMode?: WeatherMode;
  dayOfWeek?: number;
  timeMinutes?: number;
  variantTitle?: string | null;
  variantBadge?: string | null;
  arrivalTimeMinutes?: number | null;
  targetDeadlineMinutes?: number | null;
  walkDistanceKm?: number | null;
  landmarkIds?: string[];
  segments?: EmbeddedSegmentPayload[];
}

export interface EmbedMapRuntimeState extends EmbedMapInitialParams {
  tripPayload: EmbeddedTripPayload | GeneratedTrip | null;
  hasBridgeState: boolean;
}

export interface EmbeddedMapStatePatch {
  city?: unknown;
  theme?: unknown;
  accentSeed?: unknown;
  showAllNetwork?: unknown;
  includePlanned2027?: unknown;
  startStopId?: unknown;
  destinationStopId?: unknown;
  mapPickMode?: unknown;
  selectedLandmarkId?: unknown;
  trip?: EmbeddedTripPayload | GeneratedTrip | null;
}

const VALID_ACCENTS: readonly AccentSeed[] = [
  'neva',
  'sage',
  'terracotta',
  'indigo',
  'amber',
];

export function isMapOnlySearch(search: string): boolean {
  const params = new URLSearchParams(search.startsWith('?') ? search : `?${search}`);
  const mapOnly = params.get('mapOnly');
  const embedMap = params.get('embedMap');
  return mapOnly === '1' || mapOnly === 'true' || embedMap === '1' || embedMap === 'true';
}

function parseBoolParam(raw: string | null, fallback: boolean): boolean {
  if (raw === null) return fallback;
  if (raw === '0' || raw === 'false') return false;
  if (raw === '1' || raw === 'true') return true;
  return fallback;
}

export function normalizeCityId(raw: unknown, fallback: CityId = 'spb'): CityId {
  return raw === 'omsk' ? 'omsk' : raw === 'spb' ? 'spb' : fallback;
}

export function normalizeTheme(raw: unknown, fallback: AppTheme = 'light'): AppTheme {
  if (raw === 'dark' || raw === 'tram' || raw === 'light') return raw;
  return fallback;
}

export function normalizeAccentSeed(
  raw: unknown,
  fallback: AccentSeed = 'neva'
): AccentSeed {
  if (typeof raw === 'string' && (VALID_ACCENTS as readonly string[]).includes(raw)) {
    return raw as AccentSeed;
  }
  return fallback;
}

export function parseMapOnlyInitialParams(search: string): EmbedMapInitialParams {
  const params = new URLSearchParams(search.startsWith('?') ? search : `?${search}`);
  const pickRaw = params.get('mapPickMode');
  const mapPickMode =
    pickRaw === 'start' || pickRaw === 'destination' ? pickRaw : null;

  return {
    city: normalizeCityId(params.get('city'), 'spb'),
    theme: normalizeTheme(params.get('theme'), 'light'),
    accentSeed: normalizeAccentSeed(params.get('accent'), 'neva'),
    showAllNetwork: parseBoolParam(params.get('showAll'), true),
    includePlanned2027: parseBoolParam(params.get('includePlanned2027'), true),
    startStopId: params.get('startStopId')?.trim() || null,
    destinationStopId: params.get('destinationStopId')?.trim() || null,
    mapPickMode,
    selectedLandmarkId: params.get('selectedLandmarkId')?.trim() || null,
  };
}

function isAlreadyHydratedTrip(
  raw: EmbeddedTripPayload | GeneratedTrip
): raw is GeneratedTrip {
  const candidate = raw as GeneratedTrip;
  return (
    Boolean(candidate.startStop && typeof candidate.startStop.lat === 'number') &&
    Boolean(candidate.endStop && typeof candidate.endStop.lat === 'number') &&
    Array.isArray(candidate.segments) &&
    candidate.segments.every(
      (s) => Boolean(s.fromStop && typeof s.fromStop.lat === 'number' && s.toStop)
    )
  );
}

export function hydrateEmbeddedTrip(
  raw: EmbeddedTripPayload | GeneratedTrip | null | undefined,
  cityCfg: CityConfig
): GeneratedTrip | null {
  if (!raw) return null;
  if (isAlreadyHydratedTrip(raw)) {
    return raw;
  }

  const stopsById = new Map<string, TramStop>();
  for (const s of cityCfg.stops) stopsById.set(s.id, s);

  const routesById = new Map<string, TramRoute>();
  const routesByNumber = new Map<string, TramRoute>();
  for (const r of cityCfg.routes) {
    routesById.set(r.id, r);
    if (!routesByNumber.has(r.number)) routesByNumber.set(r.number, r);
  }

  const landmarksById = new Map<string, Landmark>();
  for (const l of cityCfg.landmarks) landmarksById.set(l.id, l);

  const rawSegments = Array.isArray(raw.segments) ? raw.segments : [];
  const hydratedSegments: RouteSegment[] = [];

  for (let i = 0; i < rawSegments.length; i++) {
    const seg = rawSegments[i];
    const stopIds = Array.isArray(seg.stopIds) ? seg.stopIds.filter((id) => stopsById.has(id)) : [];
    const fromStop =
      (seg.fromStopId ? stopsById.get(seg.fromStopId) : undefined) ??
      (stopIds.length > 0 ? stopsById.get(stopIds[0]) : undefined);
    const toStop =
      (seg.toStopId ? stopsById.get(seg.toStopId) : undefined) ??
      (stopIds.length > 0 ? stopsById.get(stopIds[stopIds.length - 1]) : undefined);

    if (!fromStop || !toStop) continue;

    const finalStopIds =
      stopIds.length >= 2 ? stopIds : [fromStop.id, toStop.id];
    const route =
      seg.route ??
      (seg.routeId ? routesById.get(seg.routeId) : undefined) ??
      (seg.routeNumber ? routesByNumber.get(seg.routeNumber) : undefined);

    hydratedSegments.push({
      id: seg.id || `embed-seg-${i}`,
      kind:
        seg.kind === 'walk' || seg.kind === 'fallback_transit'
          ? seg.kind
          : 'tram',
      route: route ?? undefined,
      fallbackModeName: seg.fallbackModeName ?? undefined,
      fromStop,
      toStop,
      stopIds: finalStopIds,
      stopsCount:
        typeof seg.stopsCount === 'number'
          ? seg.stopsCount
          : Math.max(0, finalStopIds.length - 1),
      durationMinutes:
        typeof seg.durationMinutes === 'number' ? seg.durationMinutes : 10,
      directionNote: seg.directionNote ?? '',
    });
  }

  if (hydratedSegments.length === 0) return null;

  const startStop =
    (raw.startStopId ? stopsById.get(raw.startStopId) : undefined) ??
    hydratedSegments[0].fromStop;
  const endStop =
    (raw.endStopId ? stopsById.get(raw.endStopId) : undefined) ??
    hydratedSegments[hydratedSegments.length - 1].toStop;

  const usedLines = Array.from(
    new Set(
      hydratedSegments
        .map((s) => s.route?.number)
        .filter((n): n is string => Boolean(n))
    )
  );

  const visitedDistricts = Array.from(
    new Set(
      hydratedSegments.flatMap((s) =>
        s.stopIds
          .map((id) => stopsById.get(id)?.district)
          .filter((d): d is string => Boolean(d))
      )
    )
  );

  const resolvedLandmarks: Landmark[] = [];
  if (Array.isArray(raw.landmarkIds) && raw.landmarkIds.length > 0) {
    for (const id of raw.landmarkIds) {
      const lm = landmarksById.get(id);
      if (lm) resolvedLandmarks.push(lm);
    }
  } else {
    const tripStopSet = new Set(hydratedSegments.flatMap((s) => s.stopIds));
    for (const lm of cityCfg.landmarks) {
      if (lm.nearbyStopIds.some((sid: string) => tripStopSet.has(sid))) {
        resolvedLandmarks.push(lm);
      }
    }
  }

  const modeBalance: ModeBalance = { ...DEFAULT_MODE_BALANCE };
  const totalDuration =
    typeof raw.totalDurationMinutes === 'number'
      ? raw.totalDurationMinutes
      : hydratedSegments.reduce((acc, s) => acc + s.durationMinutes, 0);

  return {
    id: raw.id || `embed-trip-${cityCfg.id}`,
    city: normalizeCityId(raw.city, cityCfg.id),
    createdAt: raw.createdAt || raw.createdAtIso || new Date().toISOString(),
    startStop,
    endStop,
    segments: hydratedSegments,
    tramLinesUsed: usedLines,
    transferCount:
      typeof raw.transferCount === 'number'
        ? raw.transferCount
        : Math.max(0, hydratedSegments.length - 1),
    totalDurationMinutes: totalDuration,
    usedFallbackTransit:
      typeof raw.usedFallbackTransit === 'boolean'
        ? raw.usedFallbackTransit
        : hydratedSegments.some((s) => s.kind === 'fallback_transit'),
    isLoop: Boolean(raw.isLoop),
    weatherMode:
      raw.weatherMode === 'rain' || raw.weatherMode === 'frost'
        ? raw.weatherMode
        : 'clear',
    modeBalance,
    districtsVisited: visitedDistricts,
    landmarks: resolvedLandmarks,
    dayOfWeek: typeof raw.dayOfWeek === 'number' ? raw.dayOfWeek : 1,
    timeMinutes: typeof raw.timeMinutes === 'number' ? raw.timeMinutes : 12 * 60,
    variantTitle: raw.variantTitle ?? undefined,
    variantBadge: raw.variantBadge ?? undefined,
    arrivalTimeMinutes:
      typeof raw.arrivalTimeMinutes === 'number' ? raw.arrivalTimeMinutes : undefined,
    targetDeadlineMinutes:
      typeof raw.targetDeadlineMinutes === 'number' ? raw.targetDeadlineMinutes : undefined,
    walkDistanceKm:
      typeof raw.walkDistanceKm === 'number' ? raw.walkDistanceKm : undefined,
  };
}

export function mergeEmbeddedMapState(
  prev: EmbedMapRuntimeState,
  patch: EmbeddedMapStatePatch
): EmbedMapRuntimeState {
  const nextCity =
    patch.city !== undefined ? normalizeCityId(patch.city, prev.city) : prev.city;
  const cityChanged = nextCity !== prev.city;

  const pickRaw = patch.mapPickMode;
  const nextPickMode =
    patch.mapPickMode === undefined
      ? prev.mapPickMode
      : pickRaw === 'start' || pickRaw === 'destination'
        ? pickRaw
        : null;

  return {
    city: nextCity,
    theme:
      patch.theme !== undefined ? normalizeTheme(patch.theme, prev.theme) : prev.theme,
    accentSeed:
      patch.accentSeed !== undefined
        ? normalizeAccentSeed(patch.accentSeed, prev.accentSeed)
        : prev.accentSeed,
    showAllNetwork:
      typeof patch.showAllNetwork === 'boolean'
        ? patch.showAllNetwork
        : prev.showAllNetwork,
    includePlanned2027:
      typeof patch.includePlanned2027 === 'boolean'
        ? patch.includePlanned2027
        : prev.includePlanned2027,
    startStopId:
      patch.startStopId === undefined
        ? cityChanged
          ? null
          : prev.startStopId
        : typeof patch.startStopId === 'string' && patch.startStopId.trim()
          ? patch.startStopId.trim()
          : null,
    destinationStopId:
      patch.destinationStopId === undefined
        ? cityChanged
          ? null
          : prev.destinationStopId
        : typeof patch.destinationStopId === 'string' && patch.destinationStopId.trim()
          ? patch.destinationStopId.trim()
          : null,
    mapPickMode: nextPickMode,
    selectedLandmarkId:
      patch.selectedLandmarkId === undefined
        ? cityChanged
          ? null
          : prev.selectedLandmarkId
        : typeof patch.selectedLandmarkId === 'string' && patch.selectedLandmarkId.trim()
          ? patch.selectedLandmarkId.trim()
          : null,
    tripPayload:
      patch.trip !== undefined ? patch.trip : cityChanged ? null : prev.tripPayload,
    hasBridgeState: true,
  };
}
