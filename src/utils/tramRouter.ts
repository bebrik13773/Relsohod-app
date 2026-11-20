import {
  CityId,
  DurationPreset,
  GeneratedTrip,
  GenerationOutcome,
  Landmark,
  LandmarkCategory,
  RouteSegment,
  RouteThemeFilter,
  TramRoute,
  TramStop,
  WeatherMode,
} from '../types/tram';

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

/**
 * Formats minutes from midnight (0..1500+) into HH:MM (wrapping past 24:00 cleanly).
 */
export function formatMinutesToClock(totalMinutes: number): string {
  const normalized = ((Math.round(totalMinutes) % 1440) + 1440) % 1440;
  const h = Math.floor(normalized / 60);
  const m = normalized % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

/**
 * Evaluates whether a tram route runs on the given dayOfWeek (0=Sun..6=Sat) and timeMinutes (0..1439).
 * Supports after-midnight service windows (e.g., endMinutes = 1470 means 00:30 next morning).
 */
export function isRouteActiveAt(route: TramRoute, dayOfWeek: number, timeMinutes: number): boolean {
  const { daysOfWeek, startMinutes, endMinutes } = route.schedule;

  // Normal daytime/evening check on the current day
  if (daysOfWeek.includes(dayOfWeek) && timeMinutes >= startMinutes && timeMinutes <= endMinutes) {
    return true;
  }

  // Late-night carryover from previous day (e.g., 00:15 belongs to previous day's 24:30 window)
  if (endMinutes >= 1440 && timeMinutes <= endMinutes - 1440) {
    const prevDay = (dayOfWeek + 6) % 7;
    if (daysOfWeek.includes(prevDay)) {
      return true;
    }
  }

  return false;
}

/**
 * Great-circle Haversine distance in kilometers.
 */
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
}

function buildStopMap(stops: TramStop[]): Record<string, TramStop> {
  const map: Record<string, TramStop> = {};
  for (const s of stops) {
    map[s.id] = s;
  }
  return map;
}

/**
 * Builds directed adjacency graph from active tram routes, short pedestrian hub transfers,
 * and optional emergency fallback transit bridges ONLY for isolated points.
 */
export function buildAdjacencyGraph(
  stops: TramStop[],
  routes: TramRoute[],
  dayOfWeek: number,
  timeMinutes: number,
  allowFallbackTransit: boolean
): {
  edgesByStop: Record<string, GraphEdge[]>;
  activeRoutes: TramRoute[];
} {
  const edgesByStop: Record<string, GraphEdge[]> = {};
  for (const s of stops) {
    edgesByStop[s.id] = [];
  }

  const activeRoutes = routes.filter((r) => isRouteActiveAt(r, dayOfWeek, timeMinutes));

  for (const route of activeRoutes) {
    const seq = route.stopIds;
    if (route.type === 'circular') {
      // Circular routes run strictly in one loop direction (clockwise)
      for (let i = 0; i < seq.length - 1; i++) {
        const fromId = seq[i];
        const toId = seq[i + 1];
        if (!edgesByStop[fromId] || !edgesByStop[toId]) continue;
        edgesByStop[fromId].push({
          toStopId: toId,
          kind: 'tram',
          route,
          durationMinutes: route.minutesPerHop,
        });
      }
    } else {
      // Bi-directional routes
      for (let i = 0; i < seq.length - 1; i++) {
        const a = seq[i];
        const b = seq[i + 1];
        if (!edgesByStop[a] || !edgesByStop[b]) continue;
        edgesByStop[a].push({
          toStopId: b,
          kind: 'tram',
          route,
          durationMinutes: route.minutesPerHop,
        });
        edgesByStop[b].push({
          toStopId: a,
          kind: 'tram',
          route,
          durationMinutes: route.minutesPerHop,
        });
      }
    }
  }

  // Add short walking transfer edges between stops within the same transfer hub or <= 0.65 km
  for (let i = 0; i < stops.length; i++) {
    for (let j = i + 1; j < stops.length; j++) {
      const a = stops[i];
      const b = stops[j];
      if (a.isolatedFromMainTram || b.isolatedFromMainTram) continue;

      const sameHub = Boolean(a.hubId && b.hubId && a.hubId === b.hubId);
      const distKm = haversineKm(a, b);
      if (sameHub || distKm <= 0.65) {
        const walkMins = Math.max(3, Math.round(distKm * 13));
        edgesByStop[a.id].push({ toStopId: b.id, kind: 'walk', durationMinutes: walkMins });
        edgesByStop[b.id].push({ toStopId: a.id, kind: 'walk', durationMinutes: walkMins });
      }
    }
  }

  // Fallback transit bridges ONLY connect isolated tram-less stops to their closest main tram stops
  if (allowFallbackTransit) {
    const mainStops = stops.filter((s) => !s.isolatedFromMainTram);
    for (const isolated of stops.filter((s) => s.isolatedFromMainTram)) {
      const closest = findNearestStops(isolated.lat, isolated.lng, mainStops, 2, false);
      for (const { stop: bridge, distanceKm } of closest) {
        const transitMins = Math.max(8, Math.round(distanceKm * 2.8 + 5));
        const modeLabel = distanceKm > 5 ? 'Автобус / пригородный экспресс' : 'Автобус / метро (стыковка)';
        edgesByStop[bridge.id].push({
          toStopId: isolated.id,
          kind: 'fallback_transit',
          fallbackModeName: modeLabel,
          durationMinutes: transitMins,
        });
        edgesByStop[isolated.id].push({
          toStopId: bridge.id,
          kind: 'fallback_transit',
          fallbackModeName: modeLabel,
          durationMinutes: transitMins,
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
}

/**
 * Merges consecutive hops on the same tram line into clean user-facing segments.
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
    } else {
      const isCircular = step.route?.type === 'circular';
      const directionNote =
        step.kind === 'tram'
          ? isCircular
            ? 'По кольцевому маршруту (по часовой стрелке)'
            : `В направлении: ${step.route?.title}`
          : step.kind === 'walk'
            ? 'Пешая пересадка между остановками'
            : `${step.fallbackModeName ?? 'Резервный транспорт'} — там, где нет трамвая`;

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
      return [12, 42];
    case 'medium':
      return [38, 82];
    case 'long':
      return [75, 190];
    case 'any':
    default:
      return [15, 180];
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
  dayOfWeek: number;
  timeMinutes: number;
  allowFallbackTransit: boolean;
  loopMode?: boolean;
  weatherMode?: WeatherMode;
  preferSpecialFleet?: boolean;
  seed?: number;
}

/**
 * Finds shortest path (Dijkstra) between startStopId and destinationStopId.
 * Strictly avoids fallback_transit unless no pure tram+walk route exists.
 */
function findPathToDestination(
  startStopId: string,
  destinationStopId: string,
  edgesByStop: Record<string, GraphEdge[]>,
  theme: RouteThemeFilter
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

  while (queue.length > 0) {
    queue.sort((a, b) => a.cost - b.cost);
    const curr = queue.shift()!;

    if (curr.stopId === destinationStopId && curr.steps.length > 0) {
      return curr.steps;
    }

    const neighbors = edgesByStop[curr.stopId] || [];
    for (const edge of neighbors) {
      // Prevent trivial immediate backtrack on same stop
      if (curr.steps.length > 0 && edge.toStopId === curr.steps[curr.steps.length - 1].fromStopId) {
        continue;
      }

      let edgeCost = edge.durationMinutes;
      const routeId = edge.route?.id ?? null;

      if (edge.kind === 'tram') {
        // Transfer penalty discourages unnecessary hopping on every stop
        if (curr.lastRouteId && curr.lastRouteId !== routeId) {
          edgeCost += 7;
        }
        // Slight preference for selected theme
        if (theme !== 'any' && edge.route?.themes.includes(theme)) {
          edgeCost *= 0.82;
        }
      } else if (edge.kind === 'walk') {
        edgeCost += 8;
      } else if (edge.kind === 'fallback_transit') {
        // Massive penalty: used ONLY if tram+walk cannot physically reach the target
        edgeCost += 5000;
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
          },
        ],
      });
    }
  }

  return null;
}

export function generateTramAdventure(params: GenerateTripParams): GenerationOutcome {
  const {
    city,
    stops,
    routes,
    landmarks,
    startStopId,
    destinationStopId,
    theme,
    durationPreset,
    dayOfWeek,
    timeMinutes,
    allowFallbackTransit,
    loopMode = false,
    weatherMode = 'clear',
    preferSpecialFleet = false,
    seed,
  } = params;

  const stopMap = buildStopMap(stops);
  const startStop = stopMap[startStopId];
  if (!startStop) {
    return { status: 'no_service', reason: 'Выбранная стартовая остановка не найдена.' };
  }

  const { edgesByStop, activeRoutes } = buildAdjacencyGraph(
    stops,
    routes,
    dayOfWeek,
    timeMinutes,
    allowFallbackTransit
  );

  if (activeRoutes.length === 0) {
    return {
      status: 'no_service',
      reason: `В выбранное время (${formatMinutesToClock(timeMinutes)}) трамваи уже завершили движение. Выберите дневное или вечернее время.`,
    };
  }

  // Case 1: User specified an explicit destination stop (disabled when loopMode explicitly requested)
  if (destinationStopId && destinationStopId !== startStopId && !loopMode) {
    const destStop = stopMap[destinationStopId];
    if (!destStop) {
      return { status: 'no_service', reason: 'Конечная остановка не найдена.' };
    }

    const steps = findPathToDestination(startStopId, destinationStopId, edgesByStop, theme);
    if (!steps) {
      const closestMain = findNearestStops(destStop.lat, destStop.lng, stops, 1, false)[0]?.stop ?? null;
      return {
        status: 'needs_fallback',
        reason: `До точки «${destStop.name}» нельзя добраться только на трамвае и пешком. Включите резервный транспорт, чтобы добавить стыковку от ближайшего трамвая.`,
        nearestTramStop: closestMain,
      };
    }

    const segments = collapseRawStepsToSegments(steps, stopMap);
    return {
      status: 'ok',
      trip: assembleTrip(segments, landmarks, stopMap, dayOfWeek, timeMinutes, seed, {
        city,
        isLoop: false,
        weatherMode,
      }),
    };
  }

  // Case 2: Randomizer adventure mode (or Loop mode!)
  const rand = createPrng(seed);
  const [minMins, maxMins] = getDurationRange(durationPreset);

  // Check if startStop has any active tram edges
  const startEdges = edgesByStop[startStopId] || [];
  if (startEdges.length === 0) {
    return {
      status: 'no_service',
      reason: `От остановки «${startStop.name}» в ${formatMinutesToClock(timeMinutes)} (${DAY_NAMES_SHORT[dayOfWeek]}) нет работающих трамвайных рейсов. Попробуйте другую остановку или дневное время.`,
    };
  }

  interface CandidateWalk {
    steps: RawStep[];
    score: number;
    duration: number;
  }

  const candidates: CandidateWalk[] = [];

  for (let attempt = 0; attempt < 110; attempt++) {
    const steps: RawStep[] = [];
    const visitedStops: Record<string, number> = { [startStopId]: 1 };
    let currId = startStopId;
    let currRouteId: string | null = null;
    let totalDuration = 0;
    let transfers = 0;

    const maxHops = durationPreset === 'short' ? 7 : durationPreset === 'medium' ? 14 : 22;

    for (let hop = 0; hop < maxHops; hop++) {
      const allNeighbors = edgesByStop[currId] || [];
      const neighbors = allNeighbors.filter((e) => e.kind !== 'fallback_transit');
      if (neighbors.length === 0) break;

      const weighted = neighbors.map((edge) => {
        let w = 1;
        const visitCount = visitedStops[edge.toStopId] ?? 0;

        // Prevent immediate 2-hop ping-pong back to previous stop
        if (steps.length > 0 && edge.toStopId === steps[steps.length - 1].fromStopId) {
          w *= 0.01;
        }

        const completingCircularLoop =
          edge.route?.type === 'circular' &&
          edge.toStopId === startStopId &&
          steps.length >= 4;

        const completingWalkLoop =
          loopMode && edge.toStopId === startStopId && steps.length >= 3;

        if (completingWalkLoop) {
          w *= 14;
        } else if (visitCount > 0 && !completingCircularLoop) {
          w *= 0.06;
        }

        if (edge.kind === 'tram') {
          if (currRouteId && edge.route?.id === currRouteId) {
            w *= 3.2;
          } else if (currRouteId && edge.route?.id !== currRouteId) {
            w *= transfers >= 3 ? 0.25 : 1.15;
          }
          if (theme !== 'any' && edge.route?.themes.includes(theme)) {
            w *= 4.5;
          }
          if (preferSpecialFleet && edge.route && edge.route.type !== 'regular') {
            w *= 5.0;
          }
          if (weatherMode === 'rain' || weatherMode === 'frost') {
            if (edge.route && edge.route.schedule.intervalMinutes <= 11) {
              w *= 1.8;
            }
          } else if (
            weatherMode === 'clear' &&
            edge.route?.themes.some((t) => t === 'parks' || t === 'maritime')
          ) {
            w *= 1.45;
          }
        } else {
          // Walk transfer penalty adapts to weather
          w *= weatherMode === 'rain' || weatherMode === 'frost' ? 0.2 : 0.55;
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
        durationMinutes: chosen.durationMinutes,
      });

      totalDuration += chosen.durationMinutes;
      if (chosen.kind === 'tram') {
        currRouteId = chosen.route?.id ?? currRouteId;
      }
      currId = chosen.toStopId;
      visitedStops[currId] = (visitedStops[currId] ?? 0) + 1;

      if (loopMode && currId === startStopId && steps.length >= 3) {
        break;
      }
      if (totalDuration >= maxMins) break;
      if (!loopMode && totalDuration >= minMins && rand() < 0.28 && steps.length >= 3) break;
    }

    // If loopMode didn't land back at startStopId yet, try closing the loop via shortest path
    if (loopMode && steps.length >= 2 && steps[steps.length - 1].toStopId !== startStopId) {
      const returnSteps = findPathToDestination(
        steps[steps.length - 1].toStopId,
        startStopId,
        edgesByStop,
        theme
      );
      if (returnSteps && returnSteps.length >= 1 && returnSteps.length <= 8) {
        const filteredReturn = returnSteps.filter((s) => s.kind !== 'fallback_transit');
        if (filteredReturn.length === returnSteps.length) {
          for (const rs of filteredReturn) {
            steps.push(rs);
            totalDuration += rs.durationMinutes;
          }
        }
      }
    }

    if (steps.length < 2) continue;

    while (steps.length > 1 && steps[steps.length - 1].kind === 'walk') {
      totalDuration -= steps[steps.length - 1].durationMinutes;
      steps.pop();
    }

    const segments = collapseRawStepsToSegments(steps, stopMap);
    const tramSegs = segments.filter((s) => s.kind === 'tram');
    if (tramSegs.length === 0) continue;

    const districts = new Set(segments.flatMap((s) => [s.fromStop.district, s.toStop.district]));
    const themeMatches = tramSegs.filter(
      (s) => theme === 'any' || s.route?.themes.includes(theme)
    ).length;

    let score = districts.size * 12 + tramSegs.length * 6 + themeMatches * 15;
    if (loopMode) {
      const closedLoop = segments[segments.length - 1].toStop.id === startStopId;
      const distinctRoutes = new Set(tramSegs.map((s) => s.route?.id)).size;
      if (closedLoop) score += 180;
      if (distinctRoutes >= 2) score += 45;
    }
    if (preferSpecialFleet) {
      const specialCount = tramSegs.filter((s) => s.route && s.route.type !== 'regular').length;
      score += specialCount * 35;
    }
    if (totalDuration >= minMins && totalDuration <= maxMins) {
      score += 30;
    } else {
      score -= Math.abs(totalDuration - (minMins + maxMins) / 2) * 0.5;
    }

    candidates.push({ steps, score, duration: totalDuration });
  }

  if (candidates.length === 0) {
    return {
      status: 'no_service',
      reason: 'Не удалось построить маршрут из этой точки в выбранное время. Попробуйте сменить остановку или фильтр времени.',
    };
  }

  candidates.sort((a, b) => b.score - a.score);
  const topPool = candidates.slice(0, Math.min(4, candidates.length));
  const picked = topPool[Math.floor(rand() * topPool.length)];
  const segments = collapseRawStepsToSegments(picked.steps, stopMap);

  return {
    status: 'ok',
    trip: assembleTrip(segments, landmarks, stopMap, dayOfWeek, timeMinutes, seed, {
      city,
      isLoop: loopMode && segments[segments.length - 1].toStop.id === startStopId,
      weatherMode,
    }),
  };
}

/**
 * Idea 7 ("Колесо удачи"): Extends the current trip by 1 more tram leg from endStop,
 * prioritizing a transfer onto a different tram line not used on the last segment.
 */
export function extendTripLastSegment(params: {
  trip: GeneratedTrip;
  stops: TramStop[];
  routes: TramRoute[];
  landmarks: Landmark[];
  allowFallbackTransit: boolean;
  weatherMode?: WeatherMode;
  preferSpecialFleet?: boolean;
  seed?: number;
}): GeneratedTrip | null {
  const {
    trip,
    stops,
    routes,
    landmarks,
    allowFallbackTransit,
    weatherMode = 'clear',
    preferSpecialFleet = false,
    seed,
  } = params;

  const stopMap = buildStopMap(stops);
  const { edgesByStop } = buildAdjacencyGraph(
    stops,
    routes,
    trip.dayOfWeek,
    trip.timeMinutes,
    allowFallbackTransit
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
  const targetHops = 2 + Math.floor(rand() * 3); // 2 to 4 hops

  for (let hop = 0; hop < targetHops; hop++) {
    const rawNeighbors = (edgesByStop[currId] || []).filter(
      (e) => e.kind !== 'fallback_transit'
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
      durationMinutes: chosen.durationMinutes,
    });
    visited.add(chosen.toStopId);
    currId = chosen.toStopId;
  }

  while (addedSteps.length > 0 && addedSteps[addedSteps.length - 1].kind === 'walk') {
    addedSteps.pop();
  }
  if (addedSteps.length === 0) return null;

  const extraSegments = collapseRawStepsToSegments(addedSteps, stopMap);
  const combinedSegments = [...trip.segments, ...extraSegments];

  return assembleTrip(combinedSegments, landmarks, stopMap, trip.dayOfWeek, trip.timeMinutes, seed, {
    city: trip.city,
    isLoop: combinedSegments[combinedSegments.length - 1].toStop.id === combinedSegments[0].fromStop.id,
    weatherMode,
  });
}

function assembleTrip(
  segments: RouteSegment[],
  allLandmarks: Landmark[],
  _stopMap: Record<string, TramStop>,
  dayOfWeek: number,
  timeMinutes: number,
  seed?: number,
  meta?: { city?: CityId; isLoop?: boolean; weatherMode?: WeatherMode }
): GeneratedTrip {
  const normalizedSegments: RouteSegment[] = segments.map((seg, idx) => ({
    ...seg,
    id: `seg-${idx + 1}-${seg.route?.id ?? seg.kind}`,
  }));

  const tramSegments = normalizedSegments.filter((s) => s.kind === 'tram');
  const transferCount = Math.max(0, tramSegments.length - 1);
  const transferWaitMinutes = transferCount * 5;
  const totalDurationMinutes =
    normalizedSegments.reduce((acc, s) => acc + s.durationMinutes, 0) + transferWaitMinutes;

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
    districtsVisited: Array.from(districtsSet),
    landmarks: matchedLandmarks,
    dayOfWeek,
    timeMinutes,
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
