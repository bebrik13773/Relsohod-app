import { RouteSegment, TramStop } from '../types/tram';
import { haversineKm } from './tramRouter';

export interface LiveGeoCoords {
  lat: number;
  lng: number;
  accuracyMeters?: number;
}

export interface SegmentLiveProgress {
  progressFraction: number; // 0..1 continuously along vertical route line
  currentStopIndex: number; // 0..stops.length-1
  nextStopIndex: number;
  stopsRemaining: number;
  minutesRemaining: number;
  trackingSource: 'gps' | 'timer';
  distanceToRouteMeters: number | null;
  accuracyMeters: number | null;
  stops: TramStop[];
}

export function pluralizeStopsRu(count: number): string {
  const n = Math.abs(Math.floor(count));
  const mod100 = n % 100;
  const mod10 = n % 10;
  if (mod100 >= 11 && mod100 <= 14) return 'остановок';
  if (mod10 === 1) return 'остановка';
  if (mod10 >= 2 && mod10 <= 4) return 'остановки';
  return 'остановок';
}

export function resolveSegmentStops(
  segment: RouteSegment,
  stopsById: Record<string, TramStop>
): TramStop[] {
  const resolved: TramStop[] = [];
  for (const id of segment.stopIds) {
    const found = stopsById[id];
    if (found && resolved[resolved.length - 1]?.id !== found.id) {
      resolved.push(found);
    }
  }
  if (resolved.length === 0) {
    resolved.push(segment.fromStop);
    if (segment.toStop.id !== segment.fromStop.id) {
      resolved.push(segment.toStop);
    }
  } else if (resolved.length === 1 && segment.toStop.id !== resolved[0].id) {
    resolved.push(segment.toStop);
  }
  return resolved;
}

/**
 * Projects a GPS coordinate onto a piecewise-linear sequence of stops.
 * Returns normalized progress [0..1] along cumulative corridor distance plus perpendicular offset in meters.
 */
export function projectPointOntoStopsPolyline(
  coords: LiveGeoCoords,
  stops: TramStop[]
): {
  progressFraction: number;
  currentStopIndex: number;
  distanceToRouteMeters: number;
} | null {
  if (stops.length === 0) return null;
  if (stops.length === 1) {
    return {
      progressFraction: 0,
      currentStopIndex: 0,
      distanceToRouteMeters: Math.round(haversineKm(coords, stops[0]) * 1000),
    };
  }

  const cumDistKm: number[] = [0];
  for (let i = 0; i < stops.length - 1; i++) {
    cumDistKm.push(cumDistKm[i] + haversineKm(stops[i], stops[i + 1]));
  }
  const totalDistKm = cumDistKm[cumDistKm.length - 1];
  if (totalDistKm <= 0.005) {
    return {
      progressFraction: 0,
      currentStopIndex: 0,
      distanceToRouteMeters: Math.round(haversineKm(coords, stops[0]) * 1000),
    };
  }

  // Local equirectangular projection around user latitude for accurate dot product
  const refLatRad = (coords.lat * Math.PI) / 180;
  const kmPerDegLat = 111.32;
  const kmPerDegLng = 111.32 * Math.max(0.15, Math.cos(refLatRad));

  const toXY = (pt: { lat: number; lng: number }) => ({
    x: (pt.lng - coords.lng) * kmPerDegLng,
    y: (pt.lat - coords.lat) * kmPerDegLat,
  });

  let bestDistKm = Infinity;
  let bestProjectedDistKm = 0;

  for (let i = 0; i < stops.length - 1; i++) {
    const a = toXY(stops[i]);
    const b = toXY(stops[i + 1]);
    const abX = b.x - a.x;
    const abY = b.y - a.y;
    const abLenSq = abX * abX + abY * abY;
    const t =
      abLenSq <= 1e-10
        ? 0
        : Math.max(0, Math.min(1, (-a.x * abX + -a.y * abY) / abLenSq));
    const projX = a.x + t * abX;
    const projY = a.y + t * abY;
    const distKm = Math.hypot(projX, projY);

    if (distKm < bestDistKm) {
      bestDistKm = distKm;
      const segLenKm = cumDistKm[i + 1] - cumDistKm[i];
      bestProjectedDistKm = cumDistKm[i] + t * segLenKm;
    }
  }

  const progressFraction = Math.max(0, Math.min(1, bestProjectedDistKm / totalDistKm));

  // Determine current stop index along cumulative stop distances
  let currentStopIndex = 0;
  for (let i = 0; i < stops.length; i++) {
    if (cumDistKm[i] <= bestProjectedDistKm + 0.045) {
      currentStopIndex = i;
    }
  }
  if (progressFraction >= 0.985) {
    currentStopIndex = stops.length - 1;
  } else if (currentStopIndex >= stops.length - 1) {
    currentStopIndex = stops.length - 2;
  }

  return {
    progressFraction,
    currentStopIndex,
    distanceToRouteMeters: Math.round(bestDistKm * 1000),
  };
}

/**
 * Computes smooth progress along the vertical linear route, preferring live GPS
 * when user is within corridorMaxMeters (default 1500 m), otherwise smoothly interpolating from timer.
 */
export function computeSegmentLiveProgress(params: {
  segment: RouteSegment;
  stopsById: Record<string, TramStop>;
  elapsedSeconds: number;
  userCoords: LiveGeoCoords | null;
  corridorMaxMeters?: number;
}): SegmentLiveProgress {
  const {
    segment,
    stopsById,
    elapsedSeconds,
    userCoords,
    corridorMaxMeters = 1500,
  } = params;

  const stops = resolveSegmentStops(segment, stopsById);
  const totalSeconds = Math.max(60, segment.durationMinutes * 60);
  const timerProgress = Math.max(0, Math.min(1, elapsedSeconds / totalSeconds));
  const intervalsCount = Math.max(1, stops.length - 1);

  const gpsProj = userCoords ? projectPointOntoStopsPolyline(userCoords, stops) : null;

  if (gpsProj && gpsProj.distanceToRouteMeters <= corridorMaxMeters) {
    const stopIdx = gpsProj.currentStopIndex;
    const nextStopIdx = Math.min(stops.length - 1, stopIdx + 1);
    const remainingStops =
      gpsProj.progressFraction >= 0.985 ? 0 : Math.max(1, stops.length - 1 - stopIdx);
    const remainingMins =
      gpsProj.progressFraction >= 0.985
        ? 0
        : Math.max(1, Math.ceil((1 - gpsProj.progressFraction) * segment.durationMinutes));

    return {
      progressFraction: gpsProj.progressFraction,
      currentStopIndex: stopIdx,
      nextStopIndex: nextStopIdx,
      stopsRemaining: remainingStops,
      minutesRemaining: remainingMins,
      trackingSource: 'gps',
      distanceToRouteMeters: gpsProj.distanceToRouteMeters,
      accuracyMeters: userCoords?.accuracyMeters ? Math.round(userCoords.accuracyMeters) : null,
      stops,
    };
  }

  const rawIndex = Math.floor(timerProgress * intervalsCount);
  const currentStopIndex =
    timerProgress >= 0.995
      ? stops.length - 1
      : Math.min(Math.max(0, rawIndex), Math.max(0, stops.length - 2));
  const nextStopIndex = Math.min(stops.length - 1, currentStopIndex + 1);
  const stopsRemaining =
    timerProgress >= 0.995 ? 0 : Math.max(1, stops.length - 1 - currentStopIndex);
  const minutesRemaining =
    timerProgress >= 0.995 ? 0 : Math.max(1, Math.ceil((totalSeconds - elapsedSeconds) / 60));

  return {
    progressFraction: timerProgress,
    currentStopIndex,
    nextStopIndex,
    stopsRemaining,
    minutesRemaining,
    trackingSource: 'timer',
    distanceToRouteMeters: gpsProj?.distanceToRouteMeters ?? null,
    accuracyMeters: userCoords?.accuracyMeters ? Math.round(userCoords.accuracyMeters) : null,
    stops,
  };
}
