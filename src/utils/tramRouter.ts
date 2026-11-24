import {
  CityId,
  DurationPreset,
  GeneratedTrip,
  GenerationOutcome,
  Landmark,
  LandmarkCategory,
  ModeBalance,
  RouteSegment,
  RouteThemeFilter,
  SavedRoute,
  TimePlanningMode,
  TramRoute,
  TramStop,
  WeatherMode,
} from '../types/tram';
import { DEFAULT_MODE_BALANCE, normalizeModeBalance } from './mapGeometry';

export const DAY_NAMES_SHORT = ['Вс', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'] as const;
export const DAY_NAMES_FULL = [
  'Воскресенье',
  'Понедельник',
  'Вторник',
  'Среда',
  'Четверг',
  'Пятница',
  'Суббота',
] as const;

export function formatMinutesToClock(totalMinutes: number): string {
  const normalized = ((Math.round(totalMinutes) % 1440) + 1440) % 1440;
  const h = Math.floor(normalized / 60);
  const m = normalized % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

export function computeDeadlineBudgetMinutes(
  startMinutes: number,
  deadlineMinutes: number
): number {
  let diff = deadlineMinutes - startMinutes;
  if (diff <= 0) {
    diff += 1440;
  }
  return Math.max(20, Math.min(480, diff));
}

export function isCityTransitRoute(route?: TramRoute): boolean {
  return route?.type === 'metro' || route?.type === 'trolleybus';
}

export function getRouteTypeShortLabel(route: TramRoute): string {
  switch (route.type) {
    case 'metro':
      return `Метро ${route.number}`;
    case 'trolleybus':
      return `Троллейбус № ${route.number.replace(/^Тр-/, '')}`;
    case 'retro':
      return `Ретро-трамвай ${route.number}`;
    case 'chizhik':
      return `«Чижик» № ${route.number}`;
    case 'circular':
      return `Кольцевой трамвай № ${route.number}`;
    default:
      return `Трамвай № ${route.number}`;
  }
}

export function isRouteActiveAt(route: TramRoute, dayOfWeek: number, timeMinutes: number): boolean {
  const { daysOfWeek, startMinutes, endMinutes } = route.schedule;

  if (daysOfWeek.includes(dayOfWeek) && timeMinutes >= startMinutes && timeMinutes <= endMinutes) {
    return true;
  }

  if (endMinutes >= 1440 && timeMinutes <= endMinutes - 1440) {
    const prevDay = (dayOfWeek + 6) % 7;
    if (daysOfWeek.includes(prevDay)) {
      return true;
    }
  }

  return false;
}

export function haversineKm(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const R = 6371;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const sinLat = Math.sin(dLat / 2);
  const sinLng = Math.sin(dLng / 2);
  const h =
    sinLat * sinLat + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * sinLng * sinLng;
  return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

export function findNearestStops(
  lat: number,
  lng: number,
  stops: TramStop[],
  limit = 3,
  includeIsolated = false
): Array<{ stop: TramStop; distanceKm: number }> {
  return stops
    .filter((s) => includeIsolated || !s.isolatedFromMainTram)
    .map((stop) => ({ stop, distanceKm: haversineKm({ lat, lng }, stop) }))
    .sort((a, b) => a.distanceKm - b.distanceKm)
    .slice(0, limit);
}

interface GraphEdge {
  toStopId: string;
  kind: 'tram' | 'walk' | 'fallback_transit';
  route?: TramRoute;
  fallbackModeName?: string;
  durationMinutes: number;
  distanceKm: number;
}

function buildStopMap(stops: TramStop[]): Record<string, TramStop> {
  const map: Record<string, TramStop> = {};
  for (const s of stops) {
    map[s.id] = s;
  }
  return map;
}

export interface AdjacencyOptions {
  city?: CityId;
  maxWalkKm?: number;
  allowCityTransit?: boolean;
}

/**
 * Builds directed multimodal graph:
 * - Active rail/trolleybus lines respecting schedule & after-midnight carryover
 * - Hub transfers + sparse scenic walking corridors (top-8 closest per stop within maxWalkKm)
 * - Isolated fallback transit bridges (when allowFallbackTransit=true)
 * - Optional city-wide bus connectors between distant hubs (when allowCityTransit=true)
 */
export function buildAdjacencyGraph(
  stops: TramStop[],
  routes: TramRoute[],
  dayOfWeek: number,
  timeMinutes: number,
  allowFallbackTransit: boolean,
  options?: AdjacencyOptions
): {
  edgesByStop: Record<string, GraphEdge[]>;
  activeRoutes: TramRoute[];
} {
  const edgesByStop: Record<string, GraphEdge[]> = {};
  for (const s of stops) {
    edgesByStop[s.id] = [];
  }

  const stopMap = buildStopMap(stops);
  const activeRoutes = routes.filter((r) => isRouteActiveAt(r, dayOfWeek, timeMinutes));

  const directLinePairs = new Set<string>();

  for (const route of activeRoutes) {
    const seq = route.stopIds;
    if (route.type === 'circular') {
      for (let i = 0; i < seq.length - 1; i++) {
        const fromId = seq[i];
        const toId = seq[i + 1];
        if (!edgesByStop[fromId] || !edgesByStop[toId]) continue;
        const dist = haversineKm(stopMap[fromId], stopMap[toId]);
        edgesByStop[fromId].push({
          toStopId: toId,
          kind: 'tram',
          route,
          durationMinutes: route.minutesPerHop,
          distanceKm: dist,
        });
        directLinePairs.add(`${fromId}|${toId}`);
        directLinePairs.add(`${toId}|${fromId}`);
      }
    } else {
      for (let i = 0; i < seq.length - 1; i++) {
        const a = seq[i];
        const b = seq[i + 1];
        if (!edgesByStop[a] || !edgesByStop[b]) continue;
        const dist = haversineKm(stopMap[a], stopMap[b]);
        edgesByStop[a].push({
          toStopId: b,
          kind: 'tram',
          route,
          durationMinutes: route.minutesPerHop,
          distanceKm: dist,
        });
        edgesByStop[b].push({
          toStopId: a,
          kind: 'tram',
          route,
          durationMinutes: route.minutesPerHop,
          distanceKm: dist,
        });
        directLinePairs.add(`${a}|${b}`);
        directLinePairs.add(`${b}|${a}`);
      }
    }
  }

  // Pedestrian transfers (sameHub) + sparse closest scenic walk links within maxWalkKm
  const maxWalkKm = options?.maxWalkKm ?? 0.65;
  const mainStops = stops.filter((s) => !s.isolatedFromMainTram);
  const addedWalkPairs = new Set<string>();

  const pushWalkPair = (a: TramStop, b: TramStop, distKm: number) => {
    const key = a.id < b.id ? `${a.id}|${b.id}` : `${b.id}|${a.id}`;
    if (addedWalkPairs.has(key)) return;
    addedWalkPairs.add(key);
    const walkMins = Math.max(3, Math.round(distKm * 12.5));
    edgesByStop[a.id].push({
      toStopId: b.id,
      kind: 'walk',
      durationMinutes: walkMins,
      distanceKm: distKm,
    });
    edgesByStop[b.id].push({
      toStopId: a.id,
      kind: 'walk',
      durationMinutes: walkMins,
      distanceKm: distKm,
    });
  };

  for (let i = 0; i < mainStops.length; i++) {
    const a = mainStops[i];
    const nearby: Array<{ stop: TramStop; distKm: number }> = [];
    for (let j = 0; j < mainStops.length; j++) {
      if (i === j) continue;
      const b = mainStops[j];
      const distKm = haversineKm(a, b);
      const sameHub = Boolean(a.hubId && b.hubId && a.hubId === b.hubId);
      if (sameHub) {
        pushWalkPair(a, b, distKm);
      } else if (distKm <= maxWalkKm) {
        nearby.push({ stop: b, distKm });
      }
    }
    nearby.sort((u, v) => u.distKm - v.distKm);
    for (const item of nearby.slice(0, 8)) {
      pushWalkPair(a, item.stop, item.distKm);
    }
  }

  // City-wide non-rail bus/shuttle connectors when user sets substantial transit preference
  if (options?.allowCityTransit) {
    const transitLabel =
      options.city === 'omsk'
        ? 'Автобус / троллейбус (городская связка)'
        : 'Автобус / скоростная связка по городу';

    for (const a of mainStops) {
      const candidates: Array<{ stop: TramStop; distKm: number }> = [];
      for (const b of mainStops) {
        if (a.id === b.id || a.district === b.district) continue;
        if (directLinePairs.has(`${a.id}|${b.id}`)) continue;
        const distKm = haversineKm(a, b);
        if (distKm >= 2.4 && distKm <= 9.5) {
          candidates.push({ stop: b, distKm });
        }
      }
      candidates.sort((u, v) => Math.abs(u.distKm - 4.5) - Math.abs(v.distKm - 4.5));
      for (const { stop: b, distKm } of candidates.slice(0, 2)) {
        const transitMins = Math.max(7, Math.round(distKm * 2.4 + 4));
        edgesByStop[a.id].push({
          toStopId: b.id,
          kind: 'fallback_transit',
          fallbackModeName: transitLabel,
          durationMinutes: transitMins,
          distanceKm: distKm,
        });
      }
    }
  }

  // Fallback transit bridges ONLY connect isolated stops when enabled
  if (allowFallbackTransit) {
    for (const isolated of stops.filter((s) => s.isolatedFromMainTram)) {
      const closest = findNearestStops(isolated.lat, isolated.lng, mainStops, 2, false);
      for (const { stop: bridge, distanceKm } of closest) {
        const transitMins = Math.max(8, Math.round(distanceKm * 2.8 + 5));
        const modeLabel =
          distanceKm > 5 ? 'Автобус / пригородный экспресс' : 'Автобус / стыковка';
        edgesByStop[bridge.id].push({
          toStopId: isolated.id,
          kind: 'fallback_transit',
          fallbackModeName: modeLabel,
          durationMinutes: transitMins,
          distanceKm,
        });
        edgesByStop[isolated.id].push({
          toStopId: bridge.id,
          kind: 'fallback_transit',
          fallbackModeName: modeLabel,
          durationMinutes: transitMins,
          distanceKm,
        });
      }
    }
  }

  return { edgesByStop, activeRoutes };
}

interface RawStep {
  fromStopId: string;
  toStopId: string;
  kind: 'tram' | 'walk' | 'fallback_transit';
  route?: TramRoute;
  fallbackModeName?: string;
  durationMinutes: number;
  distanceKm: number;
}

/**
 * Merges consecutive hops on the same route or consecutive walk stretches into clean segments.
 */
function collapseRawStepsToSegments(
  steps: RawStep[],
  stopMap: Record<string, TramStop>
): RouteSegment[] {
  const segments: RouteSegment[] = [];

  for (const step of steps) {
    const prev = segments[segments.length - 1];
    const fromStop = stopMap[step.fromStopId];
    const toStop = stopMap[step.toStopId];
    if (!fromStop || !toStop) continue;

    if (
      prev &&
      prev.kind === 'tram' &&
      step.kind === 'tram' &&
      prev.route?.id === step.route?.id &&
      prev.toStop.id === step.fromStopId
    ) {
      prev.toStop = toStop;
      prev.stopIds.push(step.toStopId);
      prev.stopsCount += 1;
      prev.durationMinutes += step.durationMinutes;
    } else if (
      prev &&
      prev.kind === 'walk' &&
      step.kind === 'walk' &&
      prev.toStop.id === step.fromStopId
    ) {
      prev.toStop = toStop;
      prev.stopIds.push(step.toStopId);
      prev.stopsCount += 1;
      prev.durationMinutes += step.durationMinutes;
      prev.directionNote =
        prev.durationMinutes >= 12
          ? 'Пешая прогулка между остановками и районами'
          : 'Пешая пересадка между остановками';
    } else {
      let directionNote = '';
      if (step.kind === 'tram' && step.route) {
        if (step.route.type === 'metro') {
          directionNote = `Метро · ${step.route.title}`;
        } else if (step.route.type === 'trolleybus') {
          directionNote = `Троллейбус · ${step.route.title}`;
        } else if (step.route.type === 'circular') {
          directionNote = 'По кольцевому маршруту';
        } else {
          directionNote = `В направлении: ${step.route.title}`;
        }
      } else if (step.kind === 'walk') {
        directionNote =
          step.durationMinutes >= 12
            ? 'Пешая прогулка по городу'
            : 'Пешая пересадка между остановками';
      } else {
        directionNote = step.fallbackModeName ?? 'Городской транспорт';
      }

      segments.push({
        id: `seg-${segments.length + 1}-${step.route?.id ?? step.kind}`,
        kind: step.kind,
        route: step.route,
        fallbackModeName: step.fallbackModeName,
        fromStop,
        toStop,
        stopIds: [step.fromStopId, step.toStopId],
        stopsCount: 1,
        durationMinutes: step.durationMinutes,
        directionNote,
      });
    }
  }

  return segments;
}

function createPrng(seed?: number): () => number {
  if (seed === undefined) {
    return () => Math.random();
  }
  let state = seed >>> 0 || 1;
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    return state / 4294967296;
  };
}

function getDurationRange(preset: DurationPreset): [number, number] {
  switch (preset) {
    case 'short':
      return [16, 44];
    case 'medium':
      return [42, 86];
    case 'long':
      return [80, 180];
    case 'any':
    default:
      return [22, 165];
  }
}

export interface GenerateTripParams {
  city?: CityId;
  stops: TramStop[];
  routes: TramRoute[];
  landmarks: Landmark[];
  startStopId: string;
  destinationStopId?: string | null;
  theme: RouteThemeFilter;
  durationPreset: DurationPreset;
  timeMode?: TimePlanningMode;
  deadlineMinutes?: number;
  includePlanned2027?: boolean;
  dayOfWeek: number;
  timeMinutes: number;
  allowFallbackTransit: boolean;
  modeBalance?: ModeBalance;
  loopMode?: boolean;
  weatherMode?: WeatherMode;
  preferSpecialFleet?: boolean;
  generateAlternatives?: boolean;
  seed?: number;
}

interface PathFindOptions {
  avoidRouteIds?: Set<string>;
  preferVariety?: boolean;
  preferTransitRoutes?: boolean;
}

/**
 * Multi-modal shortest path (Dijkstra) adapting edge weights to the triangular ModeBalance
 * and optional route-diversity penalties for generating distinct alternatives.
 */
function findPathToDestination(
  startStopId: string,
  destinationStopId: string,
  edgesByStop: Record<string, GraphEdge[]>,
  theme: RouteThemeFilter,
  balance: ModeBalance,
  allowFallbackTransit: boolean,
  options?: PathFindOptions
): RawStep[] | null {
  interface NodeState {
    stopId: string;
    lastRouteId: string | null;
    cost: number;
    steps: RawStep[];
  }

  const bestCost: Record<string, number> = {};
  const queue: NodeState[] = [
    { stopId: startStopId, lastRouteId: null, cost: 0, steps: [] },
  ];
  bestCost[`${startStopId}::init`] = 0;

  const tramMult = 1.45 - balance.tram * 0.75; // 0.70 (max tram) .. 1.45 (no tram)
  const walkMult = 1.85 - balance.walk * 1.35; // 0.50 (max walk) .. 1.85 (no walk)
  const walkPenalty = Math.max(0, (1 - balance.walk * 1.7) * 8);
  const cityTransitActive = allowFallbackTransit || balance.transit >= 0.20;
  const transitMult = 1.65 - balance.transit * 0.95;

  while (queue.length > 0) {
    queue.sort((a, b) => a.cost - b.cost);
    const curr = queue.shift()!;

    if (curr.stopId === destinationStopId && curr.steps.length > 0) {
      return curr.steps;
    }

    const neighbors = edgesByStop[curr.stopId] || [];
    for (const edge of neighbors) {
      if (curr.steps.length > 0 && edge.toStopId === curr.steps[curr.steps.length - 1].fromStopId) {
        continue;
      }

      let edgeCost = edge.durationMinutes;
      const routeId = edge.route?.id ?? null;

      if (edge.kind === 'tram') {
        const isTransitVehicle = isCityTransitRoute(edge.route);
        if (isTransitVehicle) {
          if (options?.preferTransitRoutes) {
            edgeCost *= 0.85;
          } else {
            // Balance metro/trolleybus nicely against tram preference
            const transitFactor = Math.max(0.75, 1.45 - balance.transit * 0.85);
            edgeCost *= transitFactor;
          }
        } else {
          edgeCost *= tramMult;
        }

        if (routeId && options?.avoidRouteIds?.has(routeId)) {
          edgeCost = edgeCost * 3.4 + 18;
        }

        if (curr.lastRouteId && curr.lastRouteId !== routeId) {
          edgeCost += options?.preferVariety ? 2.5 : 6.5;
        }
        if (theme !== 'any' && edge.route?.themes.includes(theme)) {
          edgeCost *= 0.80;
        }
      } else if (edge.kind === 'walk') {
        edgeCost = edge.durationMinutes * walkMult + walkPenalty;
      } else if (edge.kind === 'fallback_transit') {
        if (!cityTransitActive) {
          edgeCost += 5000;
        } else if (balance.transit < 0.22) {
          edgeCost = edge.durationMinutes * 1.8 + 40;
        } else {
          edgeCost = edge.durationMinutes * transitMult + 5;
        }
      }

      const nextCost = curr.cost + edgeCost;
      const stateKey = `${edge.toStopId}::${routeId ?? edge.kind}`;
      if (bestCost[stateKey] !== undefined && bestCost[stateKey] <= nextCost) {
        continue;
      }
      bestCost[stateKey] = nextCost;

      queue.push({
        stopId: edge.toStopId,
        lastRouteId: routeId,
        cost: nextCost,
        steps: [
          ...curr.steps,
          {
            fromStopId: curr.stopId,
            toStopId: edge.toStopId,
            kind: edge.kind,
            route: edge.route,
            fallbackModeName: edge.fallbackModeName,
            durationMinutes: edge.durationMinutes,
            distanceKm: edge.distanceKm,
          },
        ],
      });
    }
  }

  return null;
}

function computeTripModeShares(steps: RawStep[]): ModeBalance {
  let tramMins = 0;
  let transitMins = 0;
  let walkMins = 0;
  for (const s of steps) {
    if (s.kind === 'tram') {
      if (isCityTransitRoute(s.route)) {
        transitMins += s.durationMinutes;
      } else {
        tramMins += s.durationMinutes;
      }
    } else if (s.kind === 'walk') {
      walkMins += s.durationMinutes;
    } else {
      transitMins += s.durationMinutes;
    }
  }
  const total = tramMins + transitMins + walkMins;
  if (total <= 0) return { tram: 1, transit: 0, walk: 0 };
  return {
    tram: tramMins / total,
    transit: transitMins / total,
    walk: walkMins / total,
  };
}

interface CandidateWalk {
  steps: RawStep[];
  score: number;
  duration: number;
  walkRatio: number;
  distinctLines: number;
  signature: string;
}

function getStepsSignature(steps: RawStep[]): string {
  const segKeys: string[] = [];
  for (const s of steps) {
    const key = s.kind === 'tram' ? (s.route?.id ?? 'tram') : s.kind;
    if (segKeys[segKeys.length - 1] !== key) {
      segKeys.push(key);
    }
  }
  const endId = steps[steps.length - 1]?.toStopId ?? '';
  return `${segKeys.join('>')}|${endId}`;
}

function runAdventureRollouts(params: {
  startStop: TramStop;
  stops: TramStop[];
  stopMap: Record<string, TramStop>;
  edgesByStop: Record<string, GraphEdge[]>;
  minMins: number;
  maxMins: number;
  theme: RouteThemeFilter;
  balance: ModeBalance;
  allowCityTransit: boolean;
  allowFallbackTransit: boolean;
  effectiveIsolatedFallback: boolean;
  loopMode: boolean;
  weatherMode: WeatherMode;
  preferSpecialFleet: boolean;
  rand: () => number;
  attempts?: number;
}): CandidateWalk[] {
  const {
    startStop,
    stopMap,
    edgesByStop,
    minMins,
    maxMins,
    theme,
    balance,
    allowCityTransit,
    allowFallbackTransit,
    effectiveIsolatedFallback,
    loopMode,
    weatherMode,
    preferSpecialFleet,
    rand,
    attempts = 150,
  } = params;

  const candidates: CandidateWalk[] = [];
  const maxHops = maxMins <= 45 ? 10 : maxMins <= 95 ? 17 : 26;

  for (let attempt = 0; attempt < attempts; attempt++) {
    const steps: RawStep[] = [];
    const visitedStops: Record<string, number> = { [startStop.id]: 1 };
    const usedRouteIds = new Set<string>();
    let currId = startStop.id;
    let currRouteId: string | null = null;
    let totalDuration = 0;
    let transfers = 0;

    // Slight rollout strategy diversity:
    // 0: standard balance, 1: multi-line variety, 2: extra walking, 3: explore metro/trolleybus+tram mix
    const exploreMode = attempt % 4;

    for (let hop = 0; hop < maxHops; hop++) {
      const allNeighbors = edgesByStop[currId] || [];
      const neighbors = allNeighbors.filter((e) => {
        if (e.kind !== 'fallback_transit') return true;
        if (!allowCityTransit && !effectiveIsolatedFallback) return false;
        const targetStop = stopMap[e.toStopId];
        if (targetStop?.isolatedFromMainTram && !effectiveIsolatedFallback) return false;
        return true;
      });
      if (neighbors.length === 0) break;

      const currDistFromStart = haversineKm(startStop, stopMap[currId]);
      const currShares = computeTripModeShares(steps);

      const weighted = neighbors.map((edge) => {
        let w = 1.0;
        const nextStop = stopMap[edge.toStopId];
        const visitCount = visitedStops[edge.toStopId] ?? 0;

        if (steps.length > 0 && edge.toStopId === steps[steps.length - 1].fromStopId) {
          w *= 0.004;
        }
        const recentStops = steps.slice(-3).map((s) => s.fromStopId);
        const completingCircularLoop =
          edge.route?.type === 'circular' &&
          edge.toStopId === startStop.id &&
          steps.length >= 4;
        const completingWalkLoop =
          loopMode && edge.toStopId === startStop.id && steps.length >= 4;

        if (completingWalkLoop) {
          w *= 18;
        } else if (visitCount > 0 && !completingCircularLoop) {
          w *= recentStops.includes(edge.toStopId) ? 0.008 : 0.04;
        }

        const nextDistFromStart = nextStop ? haversineKm(startStop, nextStop) : currDistFromStart;
        if (!loopMode || totalDuration < minMins * 0.55) {
          if (nextDistFromStart > currDistFromStart + 0.15) {
            w *= 1.45;
          }
        } else if (loopMode && totalDuration >= minMins * 0.55) {
          if (nextDistFromStart < currDistFromStart) {
            w *= 2.1;
          }
        }

        const tramDeficit = Math.max(0.05, balance.tram - currShares.tram + 0.35);
        const walkDeficit = Math.max(0.04, balance.walk - currShares.walk + 0.25);
        const transitDeficit = Math.max(0.02, balance.transit - currShares.transit + 0.22);

        if (edge.kind === 'tram') {
          const isTransitVeh = isCityTransitRoute(edge.route);
          if (isTransitVeh) {
            const transitBoost = exploreMode === 3 ? 1.8 : 1.0;
            w *= (0.28 + balance.transit * 2.8) * (1 + transitDeficit) * transitBoost;
          } else {
            w *= (0.4 + balance.tram * 2.8) * (1 + tramDeficit);
          }

          if (currRouteId && edge.route?.id === currRouteId) {
            w *= exploreMode === 1 ? 2.3 : 4.1;
          } else if (currRouteId && edge.route?.id !== currRouteId) {
            if (edge.route && usedRouteIds.has(edge.route.id)) {
              w *= 0.12;
            } else {
              w *= transfers >= 4 ? 0.32 : exploreMode === 1 ? 1.45 : 1.05;
            }
          }

          if (theme !== 'any' && edge.route?.themes.includes(theme)) {
            w *= 3.8;
          }
          if (
            preferSpecialFleet &&
            edge.route &&
            (edge.route.type === 'retro' ||
              edge.route.type === 'chizhik' ||
              edge.route.type === 'circular')
          ) {
            w *= 4.6;
          }
          if (weatherMode === 'rain' || weatherMode === 'frost') {
            if (edge.route && edge.route.schedule.intervalMinutes <= 11) {
              w *= 1.7;
            }
          } else if (
            weatherMode === 'clear' &&
            edge.route?.themes.some((t) => t === 'parks' || t === 'maritime')
          ) {
            w *= 1.35;
          }
        } else if (edge.kind === 'walk') {
          const prevWasWalk = steps.length > 0 && steps[steps.length - 1].kind === 'walk';
          const walkMultMode = exploreMode === 2 ? 1.45 : 1.0;
          const walkBase =
            balance.walk < 0.12
              ? 0.22
              : (0.4 + balance.walk * 4.4) * (1 + walkDeficit * 2.2) * walkMultMode;
          w *= walkBase;

          if (edge.distanceKm >= 0.65 && (balance.walk >= 0.22 || exploreMode === 2)) {
            w *= 2.4;
          }
          if (prevWasWalk && balance.walk < 0.55) {
            w *= 0.35;
          }
          if (weatherMode === 'rain' || weatherMode === 'frost') {
            w *= balance.walk >= 0.5 ? 0.65 : 0.25;
          }
        } else {
          const prevWasTransit =
            steps.length > 0 && steps[steps.length - 1].kind === 'fallback_transit';
          const transitBase =
            balance.transit < 0.18
              ? 0.04
              : (0.35 + balance.transit * 4.4) * (1 + transitDeficit * 2.3);
          w *= transitBase;
          if (prevWasTransit && balance.transit < 0.6) {
            w *= 0.2;
          }
        }

        return { edge, w };
      });

      const totalW = weighted.reduce((acc, item) => acc + item.w, 0);
      if (totalW <= 0) break;

      let pick = rand() * totalW;
      let chosen = weighted[0].edge;
      for (const item of weighted) {
        pick -= item.w;
        if (pick <= 0) {
          chosen = item.edge;
          break;
        }
      }

      if (chosen.kind === 'tram' && currRouteId && chosen.route?.id !== currRouteId) {
        transfers += 1;
      }

      steps.push({
        fromStopId: currId,
        toStopId: chosen.toStopId,
        kind: chosen.kind,
        route: chosen.route,
        fallbackModeName: chosen.fallbackModeName,
        durationMinutes: chosen.durationMinutes,
        distanceKm: chosen.distanceKm,
      });

      totalDuration += chosen.durationMinutes;
      if (chosen.kind === 'tram' && chosen.route?.id) {
        currRouteId = chosen.route.id;
        usedRouteIds.add(chosen.route.id);
      }

      currId = chosen.toStopId;
      visitedStops[currId] = (visitedStops[currId] ?? 0) + 1;

      if (loopMode && currId === startStop.id && steps.length >= 4) {
        break;
      }
      if (totalDuration >= maxMins) break;
      if (!loopMode && totalDuration >= minMins && rand() < 0.26 && steps.length >= 3) break;
    }

    if (loopMode && steps.length >= 2 && steps[steps.length - 1].toStopId !== startStop.id) {
      const returnSteps = findPathToDestination(
        steps[steps.length - 1].toStopId,
        startStop.id,
        edgesByStop,
        theme,
        balance,
        allowFallbackTransit
      );
      if (returnSteps && returnSteps.length >= 1 && returnSteps.length <= 8) {
        const filtered = allowCityTransit
          ? returnSteps
          : returnSteps.filter((s) => s.kind !== 'fallback_transit');
        if (filtered.length === returnSteps.length) {
          for (const rs of filtered) {
            steps.push(rs);
            totalDuration += rs.durationMinutes;
          }
        }
      }
    }

    if (steps.length < 2) continue;

    while (
      steps.length > 2 &&
      steps[steps.length - 1].kind === 'walk' &&
      balance.walk < 0.55
    ) {
      const removed = steps.pop()!;
      totalDuration -= removed.durationMinutes;
    }

    const segments = collapseRawStepsToSegments(steps, stopMap);
    const tramSegs = segments.filter((s) => s.kind === 'tram');
    const walkSegs = segments.filter((s) => s.kind === 'walk');
    const transitSegs = segments.filter((s) => s.kind === 'fallback_transit');

    if (tramSegs.length === 0 && balance.tram >= 0.45) continue;

    const actualShares = computeTripModeShares(steps);
    const shareError =
      Math.abs(actualShares.tram - balance.tram) +
      Math.abs(actualShares.transit - balance.transit) +
      Math.abs(actualShares.walk - balance.walk);
    const modeFitScore = Math.max(0, 1 - shareError * 0.5) * 145;

    const districts = new Set(segments.flatMap((s) => [s.fromStop.district, s.toStop.district]));
    const distinctTramRoutes = new Set(tramSegs.map((s) => s.route?.id).filter(Boolean)).size;
    const themeMatches = tramSegs.filter(
      (s) => theme === 'any' || s.route?.themes.includes(theme)
    ).length;

    const maxSpreadKm = segments.reduce(
      (acc, seg) => Math.max(acc, haversineKm(startStop, seg.toStop)),
      0
    );

    let score =
      modeFitScore +
      districts.size * 15 +
      distinctTramRoutes * 18 +
      themeMatches * 14 +
      Math.min(8, maxSpreadKm) * 5;

    if (balance.walk >= 0.28 && walkSegs.length >= 1) {
      score += 36;
    }
    if (balance.transit >= 0.25 && (transitSegs.length >= 1 || actualShares.transit >= 0.2)) {
      score += 36;
    }
    const singleHopTrams = tramSegs.filter((s) => s.stopsCount <= 1).length;
    score -= singleHopTrams * 8;
    if (tramSegs.length > distinctTramRoutes) {
      score -= (tramSegs.length - distinctTramRoutes) * 20;
    }

    if (loopMode) {
      const closedLoop = segments[segments.length - 1].toStop.id === startStop.id;
      if (closedLoop) score += 190;
      if (distinctTramRoutes >= 2) score += 45;
    }
    if (preferSpecialFleet) {
      const specialCount = tramSegs.filter(
        (s) => s.route && (s.route.type === 'retro' || s.route.type === 'chizhik')
      ).length;
      score += specialCount * 35;
    }
    if (totalDuration >= minMins && totalDuration <= maxMins) {
      score += 35;
    } else {
      score -= Math.abs(totalDuration - (minMins + maxMins) / 2) * 0.65;
    }

    candidates.push({
      steps,
      score,
      duration: totalDuration,
      walkRatio: actualShares.walk,
      distinctLines: distinctTramRoutes,
      signature: getStepsSignature(steps),
    });
  }

  candidates.sort((a, b) => b.score - a.score);
  return candidates;
}

export function generateTramAdventure(params: GenerateTripParams): GenerationOutcome {
  const {
    city,
    stops: rawStops,
    routes: rawRoutes,
    landmarks,
    startStopId,
    destinationStopId,
    theme,
    durationPreset,
    timeMode = 'duration',
    deadlineMinutes,
    includePlanned2027 = true,
    dayOfWeek,
    timeMinutes,
    allowFallbackTransit,
    modeBalance: rawBalance,
    loopMode = false,
    weatherMode = 'clear',
    preferSpecialFleet = false,
    generateAlternatives = true,
    seed,
  } = params;

  const stops = includePlanned2027
    ? rawStops
    : rawStops.filter((s) => !s.planned2027);
  const visibleStopIds = new Set(stops.map((s) => s.id));
  const routes = includePlanned2027
    ? rawRoutes
    : rawRoutes.filter((r) => !r.planned2027 && r.stopIds.every((id) => visibleStopIds.has(id)));

  const balance = normalizeModeBalance(rawBalance ?? DEFAULT_MODE_BALANCE);
  const stopMap = buildStopMap(stops);
  const startStop = stopMap[startStopId];
  if (!startStop) {
    return {
      status: 'no_service',
      reason: 'Выбранная стартовая остановка не найдена (возможно, она относится к линии 2027 года).',
    };
  }

  const targetDeadlineMinutes =
    timeMode === 'deadline' && typeof deadlineMinutes === 'number'
      ? ((Math.round(deadlineMinutes) % 1440) + 1440) % 1440
      : undefined;

  const deadlineBudget =
    targetDeadlineMinutes !== undefined
      ? computeDeadlineBudgetMinutes(timeMinutes, targetDeadlineMinutes)
      : undefined;

  // Wider walking corridor when user prefers walking or has ample free time before a deadline
  const maxWalkKm =
    balance.walk >= 0.35 || (deadlineBudget !== undefined && deadlineBudget >= 110)
      ? 2.8
      : balance.walk >= 0.16
        ? 1.65
        : 0.72;
  const allowCityTransit = balance.transit >= 0.20;
  const effectiveIsolatedFallback = allowFallbackTransit || balance.transit >= 0.28;

  const { edgesByStop, activeRoutes } = buildAdjacencyGraph(
    stops,
    routes,
    dayOfWeek,
    timeMinutes,
    effectiveIsolatedFallback,
    { city, maxWalkKm, allowCityTransit }
  );

  if (activeRoutes.length === 0 && balance.walk < 0.65) {
    return {
      status: 'no_service',
      reason: `В выбранное время (${formatMinutesToClock(timeMinutes)}) транспорт завершил движение. Выберите дневное время или сдвиньте треугольник к «Пешком».`,
    };
  }

  // Case 1: Explicit A -> B destination routing (with up to 3 distinct alternatives!)
  if (destinationStopId && destinationStopId !== startStopId && !loopMode) {
    const destStop = stopMap[destinationStopId];
    if (!destStop) {
      return { status: 'no_service', reason: 'Конечная остановка не найдена.' };
    }

    const baseSteps = findPathToDestination(
      startStopId,
      destinationStopId,
      edgesByStop,
      theme,
      balance,
      allowFallbackTransit
    );
    if (!baseSteps) {
      const closestMain =
        findNearestStops(destStop.lat, destStop.lng, stops, 1, false)[0]?.stop ?? null;
      return {
        status: 'needs_fallback',
        reason: `До точки «${destStop.name}» нельзя добраться только на рельсах и пешком. Включите резервный транспорт или сдвиньте треугольник в сторону «Транспорт».`,
        nearestTramStop: closestMain,
      };
    }

    const buildBaseTrip = (
      steps: RawStep[],
      variantTitle: string,
      variantBadge: string
    ): GeneratedTrip => {
      const segs = collapseRawStepsToSegments(steps, stopMap);
      return assembleTrip(segs, landmarks, stopMap, dayOfWeek, timeMinutes, seed, {
        city,
        isLoop: false,
        weatherMode,
        modeBalance: balance,
        variantTitle,
        variantBadge,
        targetDeadlineMinutes,
      });
    };

    const altTrips: GeneratedTrip[] = [];
    const seenSignatures = new Set<string>();

    const pushAlternative = (
      steps: RawStep[] | null,
      variantTitle: string,
      variantBadge: string
    ) => {
      if (!steps || steps.length === 0) return;
      const sig = getStepsSignature(steps);
      if (seenSignatures.has(sig)) return;
      seenSignatures.add(sig);
      altTrips.push(buildBaseTrip(steps, variantTitle, variantBadge));
    };

    const baseTrip = buildBaseTrip(
      baseSteps,
      'Оптимальный путь',
      'Прямой'
    );

    if (generateAlternatives) {
      // Variant A: Early exit at adjacent stop (0.85..3.4 km from dest) + scenic walk
      if (!destStop.isolatedFromMainTram) {
        const adjacentStops = stops
          .filter(
            (s) =>
              !s.isolatedFromMainTram &&
              s.id !== destStop.id &&
              s.id !== startStop.id
          )
          .map((s) => ({ stop: s, distKm: haversineKm(s, destStop) }))
          .filter((item) => item.distKm >= 0.85 && item.distKm <= 3.4)
          .sort((a, b) => Math.abs(a.distKm - 1.75) - Math.abs(b.distKm - 1.75));

        for (const cand of adjacentStops.slice(0, 6)) {
          const toNeighbor = findPathToDestination(
            startStopId,
            cand.stop.id,
            edgesByStop,
            theme,
            { ...balance, walk: Math.min(balance.walk, 0.25) },
            allowFallbackTransit,
            { preferVariety: true }
          );
          if (toNeighbor && toNeighbor.length >= 1) {
            const walkMins = Math.max(9, Math.round(cand.distKm * 13));
            const combined: RawStep[] = [
              ...toNeighbor,
              {
                fromStopId: cand.stop.id,
                toStopId: destStop.id,
                kind: 'walk',
                durationMinutes: walkMins,
                distanceKm: cand.distKm,
              },
            ];
            if (getStepsSignature(combined) !== getStepsSignature(baseSteps)) {
              pushAlternative(
                combined,
                'Выход на соседней + пешком',
                `+${Math.round(cand.distKm * 10) / 10} км пешком`
              );
              break;
            }
          }
        }
      }

      // Variant B: Different lines / no boredom (avoid lines used in direct trip)
      const baseRouteIds = new Set(
        baseSteps.map((s) => s.route?.id).filter((id): id is string => Boolean(id))
      );
      if (baseRouteIds.size > 0) {
        const variedSteps = findPathToDestination(
          startStopId,
          destinationStopId,
          edgesByStop,
          theme,
          { tram: 0.45, transit: Math.max(0.3, balance.transit), walk: 0.25 },
          allowFallbackTransit,
          { avoidRouteIds: baseRouteIds, preferVariety: true }
        );
        if (variedSteps) {
          pushAlternative(variedSteps, 'Разными линиями', 'Без скуки');
        }
      }

      // Variant C: Scenic deadline-filler or direct baseline
      if (
        deadlineBudget !== undefined &&
        baseTrip.totalDurationMinutes < deadlineBudget * 0.65 &&
        !destStop.isolatedFromMainTram
      ) {
        const detourMax = Math.max(25, deadlineBudget - baseTrip.totalDurationMinutes - 14);
        const detourMin = Math.max(18, Math.round(detourMax * 0.55));
        const rand = createPrng(seed ?? 42);
        const detours = runAdventureRollouts({
          startStop,
          stops,
          stopMap,
          edgesByStop,
          minMins: detourMin,
          maxMins: detourMax,
          theme,
          balance,
          allowCityTransit,
          allowFallbackTransit,
          effectiveIsolatedFallback,
          loopMode: false,
          weatherMode,
          preferSpecialFleet,
          rand,
          attempts: 45,
        });
        for (const d of detours.slice(0, 6)) {
          const tailStopId = d.steps[d.steps.length - 1].toStopId;
          if (tailStopId === destinationStopId) continue;
          const tailToDest = findPathToDestination(
            tailStopId,
            destinationStopId,
            edgesByStop,
            theme,
            balance,
            allowFallbackTransit
          );
          if (tailToDest && tailToDest.length > 0) {
            const fullSteps = [...d.steps, ...tailToDest];
            const totalEst = fullSteps.reduce((acc, s) => acc + s.durationMinutes, 0) + 10;
            if (totalEst <= deadlineBudget - 4) {
              pushAlternative(
                fullSteps,
                `Большая прогулка к ${formatMinutesToClock(targetDeadlineMinutes!)}`,
                'По времени'
              );
              break;
            }
          }
        }
      }
    }

    // Ensure baseline optimal path is included among alternatives
    if (!seenSignatures.has(getStepsSignature(baseSteps))) {
      seenSignatures.add(getStepsSignature(baseSteps));
      altTrips.unshift(baseTrip);
    } else if (altTrips.length === 0) {
      altTrips.push(baseTrip);
    }

    // If user asked for deadline filler and we picked detour first, make sure optimal stays in list (up to 3 total)
    const trimmedAlts = altTrips.slice(0, 3).map((t) => ({ ...t, alternatives: undefined }));
    const primary = trimmedAlts[0];

    return {
      status: 'ok',
      trip: {
        ...primary,
        alternatives: trimmedAlts,
      },
    };
  }

  // Case 2: Smart Multi-Modal Adventure / Deadline / Loop Planner
  const rand = createPrng(seed);
  let [minMins, maxMins] = getDurationRange(durationPreset);
  if (deadlineBudget !== undefined) {
    const buffer = Math.min(20, Math.max(6, Math.round(deadlineBudget * 0.1)));
    maxMins = Math.max(24, deadlineBudget - buffer);
    minMins = Math.max(16, Math.round(deadlineBudget * 0.66));
  }

  const startEdges = edgesByStop[startStopId] || [];
  if (startEdges.length === 0) {
    return {
      status: 'no_service',
      reason: `От остановки «${startStop.name}» в ${formatMinutesToClock(timeMinutes)} (${DAY_NAMES_SHORT[dayOfWeek]}) нет работающих рейсов. Попробуйте другую остановку или дневное время.`,
    };
  }

  const candidates = runAdventureRollouts({
    startStop,
    stops,
    stopMap,
    edgesByStop,
    minMins,
    maxMins,
    theme,
    balance,
    allowCityTransit,
    allowFallbackTransit,
    effectiveIsolatedFallback,
    loopMode,
    weatherMode,
    preferSpecialFleet,
    rand,
    attempts: 150,
  });

  if (candidates.length === 0) {
    return {
      status: 'no_service',
      reason:
        'Не удалось построить маршрут из этой точки в выбранное время. Попробуйте сменить остановку или фильтр времени.',
    };
  }

  const topPool = candidates.slice(0, Math.min(3, candidates.length));
  const pickedPrimary = topPool[Math.floor(rand() * topPool.length)];

  const selectedCandidates: Array<{ cand: CandidateWalk; title: string; badge: string }> = [
    {
      cand: pickedPrimary,
      title:
        targetDeadlineMinutes !== undefined
          ? `К ${formatMinutesToClock(targetDeadlineMinutes)}`
          : 'Баланс по параметрам',
      badge: 'Рекомендуемый',
    },
  ];
  const usedSignatures = new Set([pickedPrimary.signature]);

  if (generateAlternatives) {
    // 2nd flavor: Walking-forward option
    const walkCandidate = [...candidates]
      .filter((c) => !usedSignatures.has(c.signature))
      .sort((a, b) => b.walkRatio - a.walkRatio || b.score - a.score)[0];
    if (walkCandidate) {
      usedSignatures.add(walkCandidate.signature);
      selectedCandidates.push({
        cand: walkCandidate,
        title: 'Больше пешком',
        badge: 'Прогулочный',
      });
    }

    // 3rd flavor: Maximum line/district diversity
    const diverseCandidate = [...candidates]
      .filter((c) => !usedSignatures.has(c.signature))
      .sort((a, b) => b.distinctLines - a.distinctLines || b.score - a.score)[0];
    if (diverseCandidate) {
      usedSignatures.add(diverseCandidate.signature);
      selectedCandidates.push({
        cand: diverseCandidate,
        title: 'Разными линиями',
        badge: 'Разнообразие',
      });
    }
  }

  const builtAlternatives = selectedCandidates.slice(0, 3).map(({ cand, title, badge }) => {
    const segs = collapseRawStepsToSegments(cand.steps, stopMap);
    return assembleTrip(segs, landmarks, stopMap, dayOfWeek, timeMinutes, seed, {
      city,
      isLoop: loopMode && segs[segs.length - 1].toStop.id === startStopId,
      weatherMode,
      modeBalance: balance,
      variantTitle: title,
      variantBadge: badge,
      targetDeadlineMinutes,
    });
  });

  const cleanAlts = builtAlternatives.map((t) => ({ ...t, alternatives: undefined }));
  const primaryTrip = cleanAlts[0];

  return {
    status: 'ok',
    trip: {
      ...primaryTrip,
      alternatives: cleanAlts,
    },
  };
}

export function extendTripLastSegment(params: {
  trip: GeneratedTrip;
  stops: TramStop[];
  routes: TramRoute[];
  landmarks: Landmark[];
  allowFallbackTransit: boolean;
  includePlanned2027?: boolean;
  weatherMode?: WeatherMode;
  preferSpecialFleet?: boolean;
  seed?: number;
}): GeneratedTrip | null {
  const {
    trip,
    stops: rawStops,
    routes: rawRoutes,
    landmarks,
    allowFallbackTransit,
    includePlanned2027 = true,
    weatherMode = 'clear',
    preferSpecialFleet = false,
    seed,
  } = params;

  const stops = includePlanned2027 ? rawStops : rawStops.filter((s) => !s.planned2027);
  const visibleStopIds = new Set(stops.map((s) => s.id));
  const routes = includePlanned2027
    ? rawRoutes
    : rawRoutes.filter((r) => !r.planned2027 && r.stopIds.every((id) => visibleStopIds.has(id)));

  const balance = normalizeModeBalance(trip.modeBalance ?? DEFAULT_MODE_BALANCE);
  const stopMap = buildStopMap(stops);
  const { edgesByStop } = buildAdjacencyGraph(
    stops,
    routes,
    trip.dayOfWeek,
    trip.timeMinutes,
    allowFallbackTransit,
    {
      city: trip.city,
      maxWalkKm: balance.walk >= 0.35 ? 2.2 : 1.1,
      allowCityTransit: balance.transit >= 0.20,
    }
  );

  const rand = createPrng(seed);
  const lastSeg = trip.segments[trip.segments.length - 1];
  const lastRouteId = lastSeg?.route?.id ?? null;
  const visited = new Set<string>();
  for (const s of trip.segments) {
    for (const id of s.stopIds) visited.add(id);
  }

  let currId = trip.endStop.id;
  const addedSteps: RawStep[] = [];
  let chosenRouteId: string | null = null;
  const targetHops = 2 + Math.floor(rand() * 3);

  for (let hop = 0; hop < targetHops; hop++) {
    const rawNeighbors = (edgesByStop[currId] || []).filter((e) =>
      balance.transit >= 0.20 || allowFallbackTransit ? true : e.kind !== 'fallback_transit'
    );
    if (rawNeighbors.length === 0) break;

    const candidates = rawNeighbors.filter((e) => {
      if (hop === 0 && e.kind === 'tram' && e.route?.id !== lastRouteId) return true;
      if (hop > 0 && chosenRouteId && e.route?.id === chosenRouteId) return true;
      return false;
    });
    const pool =
      candidates.length > 0
        ? candidates
        : rawNeighbors.filter((e) => e.kind === 'tram' && !visited.has(e.toStopId));
    const finalPool = pool.length > 0 ? pool : rawNeighbors;

    finalPool.sort((a, b) => {
      const score = (edge: GraphEdge) => {
        let s = 0;
        if (!visited.has(edge.toStopId)) s += 10;
        if (edge.kind === 'tram' && edge.route?.id !== lastRouteId) s += 8;
        if (edge.kind === 'walk' && balance.walk >= 0.35) s += 5;
        if (preferSpecialFleet && edge.route && edge.route.type !== 'regular') s += 6;
        if ((weatherMode === 'rain' || weatherMode === 'frost') && edge.kind === 'walk') s -= 6;
        return s + rand() * 3;
      };
      return score(b) - score(a);
    });

    const chosen = finalPool[0];
    if (!chosen) break;
    if (chosen.kind === 'tram' && !chosenRouteId) {
      chosenRouteId = chosen.route?.id ?? null;
    }
    addedSteps.push({
      fromStopId: currId,
      toStopId: chosen.toStopId,
      kind: chosen.kind,
      route: chosen.route,
      fallbackModeName: chosen.fallbackModeName,
      durationMinutes: chosen.durationMinutes,
      distanceKm: chosen.distanceKm,
    });
    visited.add(chosen.toStopId);
    currId = chosen.toStopId;
  }

  while (addedSteps.length > 0 && addedSteps[addedSteps.length - 1].kind === 'walk' && balance.walk < 0.55) {
    addedSteps.pop();
  }
  if (addedSteps.length === 0) return null;

  const extraSegments = collapseRawStepsToSegments(addedSteps, stopMap);
  const combinedSegments = [...trip.segments, ...extraSegments];

  return assembleTrip(combinedSegments, landmarks, stopMap, trip.dayOfWeek, trip.timeMinutes, seed, {
    city: trip.city,
    isLoop: combinedSegments[combinedSegments.length - 1].toStop.id === combinedSegments[0].fromStop.id,
    weatherMode,
    modeBalance: balance,
    variantTitle: trip.variantTitle,
    variantBadge: trip.variantBadge,
    targetDeadlineMinutes: trip.targetDeadlineMinutes,
  });
}

function assembleTrip(
  segments: RouteSegment[],
  allLandmarks: Landmark[],
  stopMap: Record<string, TramStop>,
  dayOfWeek: number,
  timeMinutes: number,
  seed?: number,
  meta?: {
    city?: CityId;
    isLoop?: boolean;
    weatherMode?: WeatherMode;
    modeBalance?: ModeBalance;
    variantTitle?: string;
    variantBadge?: string;
    targetDeadlineMinutes?: number;
  }
): GeneratedTrip {
  const normalizedSegments: RouteSegment[] = segments.map((seg, idx) => ({
    ...seg,
    id: `seg-${idx + 1}-${seg.route?.id ?? seg.kind}`,
  }));

  const tramSegments = normalizedSegments.filter((s) => s.kind === 'tram');
  const transferCount = Math.max(0, normalizedSegments.length - 1);
  const transferWaitMinutes = Math.max(0, tramSegments.length - 1) * 5;
  const totalDurationMinutes =
    normalizedSegments.reduce((acc, s) => acc + s.durationMinutes, 0) + transferWaitMinutes;

  let walkKmRaw = 0;
  for (const seg of normalizedSegments) {
    if (seg.kind !== 'walk') continue;
    if (seg.stopIds.length >= 2) {
      for (let i = 0; i < seg.stopIds.length - 1; i++) {
        const a = stopMap[seg.stopIds[i]];
        const b = stopMap[seg.stopIds[i + 1]];
        walkKmRaw += a && b ? haversineKm(a, b) : haversineKm(seg.fromStop, seg.toStop);
      }
    } else {
      walkKmRaw += haversineKm(seg.fromStop, seg.toStop);
    }
  }
  const walkDistanceKm = Math.round(walkKmRaw * 10) / 10;

  const stopIdsOnTrip = new Set<string>();
  const districtsSet = new Set<string>();
  for (const seg of normalizedSegments) {
    districtsSet.add(seg.fromStop.district);
    districtsSet.add(seg.toStop.district);
    for (const id of seg.stopIds) {
      stopIdsOnTrip.add(id);
    }
  }

  const matchedLandmarks = allLandmarks.filter((lm) =>
    lm.nearbyStopIds.some((stopId) => stopIdsOnTrip.has(stopId))
  );

  const tramLinesUsed: string[] = [];
  for (const seg of tramSegments) {
    if (seg.route && !tramLinesUsed.includes(seg.route.number)) {
      tramLinesUsed.push(seg.route.number);
    }
  }

  const arrivalTimeMinutes =
    ((Math.round(timeMinutes + totalDurationMinutes) % 1440) + 1440) % 1440;

  return {
    id: `trip-${seed ?? Date.now()}-${Math.floor((seed ?? Math.random()) * 1000)}`,
    city: meta?.city,
    createdAt: new Date().toISOString(),
    startStop: normalizedSegments[0].fromStop,
    endStop: normalizedSegments[normalizedSegments.length - 1].toStop,
    segments: normalizedSegments,
    tramLinesUsed,
    transferCount,
    totalDurationMinutes,
    usedFallbackTransit: normalizedSegments.some((s) => s.kind === 'fallback_transit'),
    isLoop: meta?.isLoop,
    weatherMode: meta?.weatherMode,
    modeBalance: meta?.modeBalance,
    districtsVisited: Array.from(districtsSet),
    landmarks: matchedLandmarks,
    dayOfWeek,
    timeMinutes,
    variantTitle: meta?.variantTitle,
    variantBadge: meta?.variantBadge,
    arrivalTimeMinutes,
    targetDeadlineMinutes: meta?.targetDeadlineMinutes,
    walkDistanceKm,
  };
}

export function filterLandmarks(
  landmarks: Landmark[],
  selectedCategories: LandmarkCategory[],
  onlyWithPhotos: boolean
): Landmark[] {
  return landmarks.filter((lm) => {
    if (onlyWithPhotos && !lm.photoUrl) {
      return false;
    }
    if (selectedCategories.length > 0 && !selectedCategories.includes(lm.category)) {
      return false;
    }
    return true;
  });
}

export interface SavedRoutesSummaryStats {
  totalRoutesCount: number;
  totalDistanceKm: number;
  totalDurationMinutes: number;
  spbRoutesCount: number;
  spbDistrictsCount: number;
  spbDistrictsTotal: number;
  spbDistrictNames: string[];
}

export function calculateTripDistanceKm(
  trip: GeneratedTrip,
  stopsById: Record<string, TramStop>
): number {
  let km = 0;
  for (const seg of trip.segments) {
    if (seg.stopIds.length >= 2) {
      let segKm = 0;
      let resolvedAll = true;
      for (let i = 0; i < seg.stopIds.length - 1; i++) {
        const a = stopsById[seg.stopIds[i]];
        const b = stopsById[seg.stopIds[i + 1]];
        if (a && b) {
          segKm += haversineKm(a, b);
        } else {
          resolvedAll = false;
          break;
        }
      }
      km += resolvedAll ? segKm : haversineKm(seg.fromStop, seg.toStop);
    } else {
      km += haversineKm(seg.fromStop, seg.toStop);
    }
  }
  return Math.round(km * 10) / 10;
}

export function computeSavedRoutesStats(
  savedRoutes: SavedRoute[],
  stopsById: Record<string, TramStop>,
  spbTotalDistrictsCount: number
): SavedRoutesSummaryStats {
  let totalDist = 0;
  let totalDurationMinutes = 0;
  let spbRoutesCount = 0;
  const spbDistrictSet = new Set<string>();

  for (const item of savedRoutes) {
    const { trip } = item;
    totalDist += calculateTripDistanceKm(trip, stopsById);
    totalDurationMinutes += trip.totalDurationMinutes;

    const tripCity = trip.city ?? 'spb';
    if (tripCity === 'spb') {
      spbRoutesCount += 1;
      for (const district of trip.districtsVisited) {
        if (district) spbDistrictSet.add(district);
      }
    }
  }

  return {
    totalRoutesCount: savedRoutes.length,
    totalDistanceKm: Math.round(totalDist * 10) / 10,
    totalDurationMinutes,
    spbRoutesCount,
    spbDistrictsCount: spbDistrictSet.size,
    spbDistrictsTotal: spbTotalDistrictsCount,
    spbDistrictNames: Array.from(spbDistrictSet).sort((a, b) => a.localeCompare(b, 'ru')),
  };
}
