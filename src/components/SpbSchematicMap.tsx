import React, { useEffect, useMemo, useState } from 'react';
import { Minus, Plus, RotateCcw, X } from 'lucide-react';
import { CityId, GeneratedTrip, Landmark, TramRoute, TramStop } from '../types/tram';
import {
  buildSmoothSvgPath,
  computeRouteLaneOffsets,
  offsetPolylinePoints,
} from '../utils/mapGeometry';

interface SpbSchematicMapProps {
  city: CityId;
  stops: TramStop[];
  routes: TramRoute[];
  trip: GeneratedTrip | null;
  landmarks: Landmark[];
  showAllNetwork: boolean;
  startStopId: string;
  destinationStopId: string | null;
  mapPickMode: 'start' | 'destination' | null;
  selectedLandmarkId: string | null;
  onSelectLandmark: (landmark: Landmark | null) => void;
  onPickStop: (stopId: string) => void;
}

const SPB_BOUNDS = {
  minLat: 59.72,
  maxLat: 60.08,
  minLng: 29.88,
  maxLng: 30.53,
};

const SVG_W = 1000;
const SVG_H = 760;

interface BoundsBox {
  minLat: number;
  maxLat: number;
  minLng: number;
  maxLng: number;
}

function projectWithBounds(
  lat: number,
  lng: number,
  bounds: BoundsBox
): { x: number; y: number } {
  const nx = (lng - bounds.minLng) / Math.max(0.01, bounds.maxLng - bounds.minLng);
  const ny = 1 - (lat - bounds.minLat) / Math.max(0.01, bounds.maxLat - bounds.minLat);
  return {
    x: 40 + nx * (SVG_W - 80),
    y: 40 + ny * (SVG_H - 80),
  };
}

// Stylized Neva River + Gulf of Finland path in SPb geographic coordinates
const NEVA_MAIN_COORDS: Array<[number, number]> = [
  [59.83, 30.52],
  [59.885, 30.465],
  [59.918, 30.405],
  [59.946, 30.395],
  [59.949, 30.345],
  [59.942, 30.308],
  [59.932, 30.255],
  [59.92, 30.18],
];

const MALAYA_NEVA_COORDS: Array<[number, number]> = [
  [59.943, 30.308],
  [59.954, 30.285],
  [59.968, 30.245],
  [59.972, 30.195],
];

const BOLSHAYA_NEVKA_COORDS: Array<[number, number]> = [
  [59.953, 30.345],
  [59.975, 30.335],
  [59.982, 30.282],
  [59.98, 30.22],
];

const GULF_COAST_COORDS: Array<[number, number]> = [
  [60.02, 29.88],
  [59.99, 30.18],
  [59.96, 30.215],
  [59.92, 30.18],
  [59.87, 30.16],
  [59.855, 30.05],
  [59.885, 29.88],
];

// Stylized Irtysh & Om rivers for Omsk
const IRTYSH_COORDS: Array<[number, number]> = [
  [54.92, 73.375],
  [54.955, 73.368],
  [54.985, 73.356],
  [55.01, 73.315],
  [55.04, 73.265],
  [55.07, 73.24],
];

const OM_RIVER_COORDS: Array<[number, number]> = [
  [54.982, 73.362],
  [54.985, 73.405],
  [54.981, 73.455],
  [54.978, 73.49],
];

function coordsToPath(coords: Array<[number, number]>, bounds: BoundsBox, closed = false): string {
  const pts = coords.map(([lat, lng]) => projectWithBounds(lat, lng, bounds));
  const smooth = buildSmoothSvgPath(pts, 0.22);
  return closed ? `${smooth} Z` : smooth;
}

export function SpbSchematicMap({
  city,
  stops,
  routes,
  trip,
  landmarks,
  showAllNetwork,
  startStopId,
  destinationStopId,
  mapPickMode,
  selectedLandmarkId,
  onSelectLandmark,
  onPickStop,
}: SpbSchematicMapProps) {
  const [zoom, setZoom] = useState(city === 'omsk' ? 1.0 : 1.12);
  const [pan, setPan] = useState(city === 'omsk' ? { x: 0, y: 0 } : { x: -75, y: -10 });
  const [dragOrigin, setDragOrigin] = useState<{ x: number; y: number } | null>(null);

  useEffect(() => {
    setZoom(city === 'omsk' ? 1.0 : 1.12);
    setPan(city === 'omsk' ? { x: 0, y: 0 } : { x: -75, y: -10 });
  }, [city]);

  const bounds = useMemo<BoundsBox>(() => {
    if (city === 'spb' || stops.length === 0) return SPB_BOUNDS;
    let minLat = Infinity;
    let maxLat = -Infinity;
    let minLng = Infinity;
    let maxLng = -Infinity;
    for (const s of stops) {
      if (s.lat < minLat) minLat = s.lat;
      if (s.lat > maxLat) maxLat = s.lat;
      if (s.lng < minLng) minLng = s.lng;
      if (s.lng > maxLng) maxLng = s.lng;
    }
    const padLat = Math.max(0.018, (maxLat - minLat) * 0.14);
    const padLng = Math.max(0.025, (maxLng - minLng) * 0.14);
    return {
      minLat: minLat - padLat,
      maxLat: maxLat + padLat,
      minLng: minLng - padLng,
      maxLng: maxLng + padLng,
    };
  }, [city, stops]);

  const project = (lat: number, lng: number) => projectWithBounds(lat, lng, bounds);

  const stopDict = useMemo(() => {
    const dict: Record<string, TramStop> = {};
    for (const s of stops) dict[s.id] = s;
    return dict;
  }, [stops]);

  const activeLandmark = useMemo(
    () => landmarks.find((l) => l.id === selectedLandmarkId) ?? null,
    [landmarks, selectedLandmarkId]
  );

  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    const delta = e.deltaY < 0 ? 0.14 : -0.14;
    setZoom((z) => Math.min(2.8, Math.max(0.55, Number((z + delta).toFixed(2)))));
  };

  const handlePointerDown = (e: React.PointerEvent) => {
    if ((e.target as HTMLElement).closest('button')) return;
    setDragOrigin({ x: e.clientX - pan.x, y: e.clientY - pan.y });
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!dragOrigin) return;
    setPan({ x: e.clientX - dragOrigin.x, y: e.clientY - dragOrigin.y });
  };

  const handlePointerUp = () => setDragOrigin(null);

  const routeLaneOffsets = useMemo(
    () => computeRouteLaneOffsets(routes, 3.4),
    [routes]
  );

  const nevaPath = useMemo(() => coordsToPath(NEVA_MAIN_COORDS, bounds), [bounds]);
  const malayaNevaPath = useMemo(() => coordsToPath(MALAYA_NEVA_COORDS, bounds), [bounds]);
  const nevkaPath = useMemo(() => coordsToPath(BOLSHAYA_NEVKA_COORDS, bounds), [bounds]);
  const gulfCoastPath = useMemo(() => coordsToPath(GULF_COAST_COORDS, bounds, true), [bounds]);
  const irtyshPath = useMemo(() => coordsToPath(IRTYSH_COORDS, bounds), [bounds]);
  const omRiverPath = useMemo(() => coordsToPath(OM_RIVER_COORDS, bounds), [bounds]);

  const tripStopIds = useMemo(() => {
    const ids = new Set<string>();
    if (!trip) return ids;
    for (const seg of trip.segments) {
      for (const id of seg.stopIds) ids.add(id);
    }
    return ids;
  }, [trip]);

  return (
    <div
      data-testid="spb-schematic-map"
      className="relative h-full w-full overflow-hidden select-none"
      style={{ backgroundColor: 'var(--map-land)' }}
      onWheel={handleWheel}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerLeave={handlePointerUp}
    >
      {/* Interactive SVG Geographic Canvas */}
      <svg
        viewBox={`0 0 ${SVG_W} ${SVG_H}`}
        preserveAspectRatio="xMidYMid slice"
        className="h-full w-full cursor-grab active:cursor-grabbing"
        role="img"
        aria-label={
          city === 'omsk'
            ? 'Интерактивная карта трамвайной сети Омска'
            : 'Интерактивная карта трамвайной сети Санкт-Петербурга'
        }
      >
        <g
          transform={`translate(${SVG_W / 2 + pan.x}, ${SVG_H / 2 + pan.y}) scale(${zoom}) translate(${-SVG_W / 2}, ${-SVG_H / 2})`}
        >
          {/* Subtle base canvas */}
          <rect x={0} y={0} width={SVG_W} height={SVG_H} fill="var(--map-land)" />

          {city === 'spb' ? (
            <>
              <path d={gulfCoastPath} fill="var(--map-water)" />
              <path
                d={nevaPath}
                fill="none"
                stroke="var(--map-water)"
                strokeWidth={22}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              <path
                d={malayaNevaPath}
                fill="none"
                stroke="var(--map-water)"
                strokeWidth={14}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              <path
                d={nevkaPath}
                fill="none"
                stroke="var(--map-water)"
                strokeWidth={11}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              <text x={555} y={325} fontSize={11} fill="var(--map-label)" opacity={0.55} fontWeight={600}>
                Центр
              </text>
              <text x={450} y={295} fontSize={10} fill="var(--map-label)" opacity={0.55} fontWeight={600}>
                Васильевский о-в
              </text>
              <text x={540} y={248} fontSize={10} fill="var(--map-label)" opacity={0.55} fontWeight={600}>
                Петроградская ст.
              </text>
              <text x={735} y={345} fontSize={10} fill="var(--map-label)" opacity={0.55} fontWeight={600}>
                Охта и Ржевка
              </text>
              <text x={600} y={585} fontSize={10} fill="var(--map-label)" opacity={0.55} fontWeight={600}>
                Купчино
              </text>
              <text x={245} y={535} fontSize={10} fill="var(--map-label)" opacity={0.55} fontWeight={600}>
                Стрельна / Петергоф
              </text>
            </>
          ) : (
            <>
              <path
                d={irtyshPath}
                fill="none"
                stroke="var(--map-water)"
                strokeWidth={24}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              <path
                d={omRiverPath}
                fill="none"
                stroke="var(--map-water)"
                strokeWidth={11}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              <text x={515} y={445} fontSize={11} fill="var(--map-label)" opacity={0.55} fontWeight={600}>
                Центр / Цирк
              </text>
              <text x={615} y={255} fontSize={10} fill="var(--map-label)" opacity={0.55} fontWeight={600}>
                Амурский посёлок
              </text>
              <text x={665} y={410} fontSize={10} fill="var(--map-label)" opacity={0.55} fontWeight={600}>
                Октябрьский округ
              </text>
              <text x={535} y={590} fontSize={10} fill="var(--map-label)" opacity={0.55} fontWeight={600}>
                Ленинский округ
              </text>
              <text x={265} y={185} fontSize={10} fill="var(--map-label)" opacity={0.55} fontWeight={600}>
                Нефтяники
              </text>
            </>
          )}

          {/* Smooth, Lane-Separated Full Network Overlay */}
          {showAllNetwork &&
            routes.map((route) => {
              const rawPts = route.stopIds
                .map((id) => stopDict[id])
                .filter(Boolean)
                .map((s) => project(s.lat, s.lng));
              if (rawPts.length < 2) return null;
              const offsetPts = offsetPolylinePoints(rawPts, routeLaneOffsets[route.id] ?? 0);
              const d = buildSmoothSvgPath(offsetPts, 0.24);
              const baseOpacity =
                route.type === 'metro'
                  ? trip
                    ? 0.16
                    : 0.34
                  : route.type === 'bus'
                    ? trip
                      ? 0.1
                      : 0.24
                    : trip
                      ? 0.2
                      : 0.45;
              return (
                <path
                  key={`bg-${route.id}`}
                  d={d}
                  fill="none"
                  stroke={route.color}
                  strokeWidth={route.type === 'metro' ? 2.6 : route.type === 'bus' ? 1.5 : 2.2}
                  strokeOpacity={baseOpacity}
                  strokeDasharray={route.planned2027 ? '6 4' : undefined}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              );
            })}

          {/* Smooth Active Generated Trip Segments with Casing */}
          {trip?.segments.map((seg, idx) => {
            const rawPts = seg.stopIds
              .map((id) => stopDict[id])
              .filter(Boolean)
              .map((s) => project(s.lat, s.lng));
            if (rawPts.length < 2) return null;
            const segOffset =
              seg.kind === 'tram' && seg.route
                ? (routeLaneOffsets[seg.route.id] ?? 0) * 0.65
                : seg.kind === 'walk'
                  ? 4.5
                  : -4.5;
            const pts = offsetPolylinePoints(rawPts, segOffset);
            const d = buildSmoothSvgPath(pts, 0.25);
            const isTram = seg.kind === 'tram';
            const strokeColor = isTram
              ? seg.route?.color || 'var(--accent-primary)'
              : seg.kind === 'walk'
                ? '#059669'
                : '#d97706';

            return (
              <g key={seg.id}>
                <path
                  d={d}
                  fill="none"
                  stroke="var(--bg-surface)"
                  strokeOpacity={0.85}
                  strokeWidth={isTram ? 8.5 : 6.2}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
                <path
                  d={d}
                  fill="none"
                  stroke={strokeColor}
                  strokeWidth={isTram ? 5.2 : 3.6}
                  strokeDasharray={
                    isTram
                      ? seg.route?.planned2027
                        ? '8 5'
                        : undefined
                      : seg.kind === 'walk'
                        ? '2 7'
                        : '9 6'
                  }
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
                {/* Transfer Marker */}
                {idx > 0 && (
                  <g
                    transform={`translate(${project(seg.fromStop.lat, seg.fromStop.lng).x}, ${project(seg.fromStop.lat, seg.fromStop.lng).y})`}
                  >
                    <circle r={7} fill="#ffffff" stroke="var(--text-primary)" strokeWidth={2.5} />
                  </g>
                )}
              </g>
            );
          })}

          {/* Tram Stops */}
          {stops.map((stop) => {
            const { x, y } = project(stop.lat, stop.lng);
            const isStart = stop.id === startStopId;
            const isDest = stop.id === destinationStopId || (trip && trip.endStop.id === stop.id);
            const isOnTrip = tripStopIds.has(stop.id);
            const r = isStart || isDest ? 7 : isOnTrip ? 5 : 3.6;

            if (!showAllNetwork && !isStart && !isDest && !isOnTrip && !mapPickMode && !stop.isolatedFromMainTram) {
              // Keep non-active hub stops subtly clickable
              return (
                <circle
                  key={stop.id}
                  cx={x}
                  cy={y}
                  r={2.8}
                  fill="var(--text-muted)"
                  fillOpacity={0.38}
                  className="cursor-pointer"
                  onClick={() => onPickStop(stop.id)}
                >
                  <title>{stop.name}</title>
                </circle>
              );
            }

            return (
              <g
                key={stop.id}
                transform={`translate(${x}, ${y})`}
                className="cursor-pointer"
                onClick={() => onPickStop(stop.id)}
              >
                <circle
                  r={r + 4}
                  fill="transparent"
                />
                {stop.stopKind === 'metro' ? (
                  <rect
                    x={-r}
                    y={-r}
                    width={r * 2}
                    height={r * 2}
                    rx={2.5}
                    fill={
                      isStart
                        ? '#15803d'
                        : isDest
                          ? '#b91c1c'
                          : 'var(--bg-surface)'
                    }
                    stroke={isStart || isDest ? '#ffffff' : 'var(--text-primary)'}
                    strokeWidth={isStart || isDest ? 2.2 : 1.6}
                  />
                ) : (
                  <circle
                    r={r}
                    fill={
                      isStart
                        ? '#15803d'
                        : isDest
                          ? '#b91c1c'
                          : stop.isolatedFromMainTram
                            ? '#d97706'
                            : 'var(--bg-surface)'
                    }
                    stroke={isStart || isDest ? '#ffffff' : 'var(--text-primary)'}
                    strokeWidth={isStart || isDest ? 2.2 : 1.5}
                  />
                )}
                {(isStart || isDest || zoom >= 1.45) && (
                  <text
                    x={9}
                    y={3}
                    fontSize={isStart || isDest ? 10.5 : 9}
                    fontWeight={isStart || isDest ? 700 : 500}
                    fill="var(--text-primary)"
                    paintOrder="stroke"
                    stroke="var(--bg-surface)"
                    strokeWidth={3}
                  >
                    {stop.name}
                  </text>
                )}
              </g>
            );
          })}

          {/* Segment route & walk duration pills rendered above stop dots so nothing occludes labels */}
          {trip?.segments.map((seg) => {
            const rawPts = seg.stopIds
              .map((id) => stopDict[id])
              .filter(Boolean)
              .map((s) => project(s.lat, s.lng));
            if (rawPts.length < 2) return null;
            const segOffset =
              seg.kind === 'tram' && seg.route
                ? (routeLaneOffsets[seg.route.id] ?? 0) * 0.65
                : seg.kind === 'walk'
                  ? 4.5
                  : -4.5;
            const pts = offsetPolylinePoints(rawPts, segOffset);
            const midPt = pts[Math.floor(pts.length / 2)];
            const isTram = seg.kind === 'tram';
            const strokeColor = isTram
              ? seg.route?.color || 'var(--accent-primary)'
              : seg.kind === 'walk'
                ? '#059669'
                : '#d97706';

            if (isTram && seg.route) {
              const pillLabel = seg.route.number;
              const pillWidth = Math.max(28, pillLabel.length * 7.5 + 12);
              return (
                <g key={`pill-${seg.id}`} transform={`translate(${midPt.x}, ${midPt.y})`}>
                  <rect
                    x={-pillWidth / 2}
                    y={-11}
                    width={pillWidth}
                    height={22}
                    rx={6}
                    fill={strokeColor}
                    stroke="#ffffff"
                    strokeWidth={1.5}
                  />
                  <text
                    x={0}
                    y={4}
                    textAnchor="middle"
                    fontSize={10.5}
                    fontWeight={700}
                    fill="#ffffff"
                    className="font-mono-num"
                  >
                    {pillLabel}
                  </text>
                </g>
              );
            }
            return (
              <g key={`pill-${seg.id}`} transform={`translate(${midPt.x}, ${midPt.y})`}>
                <rect
                  x={-22}
                  y={-10}
                  width={44}
                  height={20}
                  rx={10}
                  fill={strokeColor}
                  stroke="#ffffff"
                  strokeWidth={1.5}
                />
                <text
                  x={0}
                  y={3.5}
                  textAnchor="middle"
                  fontSize={9.5}
                  fontWeight={700}
                  fill="#ffffff"
                  className="font-mono-num"
                >
                  {seg.kind === 'walk' ? `${seg.durationMinutes}м` : 'Трансп.'}
                </text>
              </g>
            );
          })}

          {/* Landmark Markers */}
          {landmarks.map((lm) => {
            const { x, y } = project(lm.lat, lm.lng);
            const selected = lm.id === selectedLandmarkId;
            return (
              <g
                key={lm.id}
                transform={`translate(${x}, ${y - 10})`}
                className="cursor-pointer"
                onClick={() => onSelectLandmark(selected ? null : lm)}
              >
                <circle
                  r={selected ? 9.5 : 7}
                  fill={selected ? 'var(--accent-primary)' : 'var(--accent-secondary)'}
                  stroke="#ffffff"
                  strokeWidth={2}
                />
                <circle r={2.3} fill="#ffffff" />
                <title>{lm.name}</title>
              </g>
            );
          })}
        </g>
      </svg>

      {/* Pick mode banner */}
      {mapPickMode && (
        <div
          className="absolute top-3 left-1/2 -translate-x-1/2 rounded-lg px-3.5 py-2 text-xs font-medium shadow-md"
          style={{
            backgroundColor: 'var(--accent-primary)',
            color: 'var(--accent-on-primary)',
          }}
        >
          {mapPickMode === 'start'
            ? 'Кликните по любой остановке на карте, чтобы задать старт'
            : 'Кликните по остановке на карте, чтобы задать конечную точку'}
        </div>
      )}

      {/* Map zoom controls */}
      <div
        className="absolute right-3 bottom-3 flex flex-col overflow-hidden rounded-lg border shadow-sm"
        style={{
          backgroundColor: 'var(--bg-surface)',
          borderColor: 'var(--border-hairline)',
        }}
      >
        <button
          type="button"
          aria-label="Приблизить карту"
          onClick={() => setZoom((z) => Math.min(2.8, Number((z + 0.25).toFixed(2))))}
          className="cursor-pointer p-2.5 transition-colors hover:opacity-80"
          style={{ color: 'var(--text-primary)' }}
        >
          <Plus className="h-4 w-4" />
        </button>
        <button
          type="button"
          aria-label="Отдалить карту"
          onClick={() => setZoom((z) => Math.max(0.55, Number((z - 0.25).toFixed(2))))}
          className="cursor-pointer border-t p-2.5 transition-colors hover:opacity-80"
          style={{ color: 'var(--text-primary)', borderColor: 'var(--border-hairline)' }}
        >
          <Minus className="h-4 w-4" />
        </button>
        <button
          type="button"
          aria-label="Сбросить масштаб карты"
          onClick={() => {
            setZoom(city === 'omsk' ? 1.0 : 1.12);
            setPan(city === 'omsk' ? { x: 0, y: 0 } : { x: -75, y: -10 });
          }}
          className="cursor-pointer border-t p-2.5 transition-colors hover:opacity-80"
          style={{ color: 'var(--text-primary)', borderColor: 'var(--border-hairline)' }}
        >
          <RotateCcw className="h-4 w-4" />
        </button>
      </div>

      {/* Selected Landmark Preview Card */}
      {activeLandmark && (
        <div
          className="absolute bottom-14 left-3 z-30 w-72 max-w-[calc(100vw-4.5rem)] overflow-hidden rounded-xl border shadow-lg sm:w-80"
          style={{
            backgroundColor: 'var(--bg-surface)',
            borderColor: 'var(--border-hairline)',
          }}
        >
          {activeLandmark.photoUrl && (
            <div className="relative h-36 w-full overflow-hidden">
              <img
                src={activeLandmark.photoUrl}
                alt={activeLandmark.name}
                referrerPolicy="no-referrer"
                className="h-full w-full object-cover"
              />
            </div>
          )}
          <div className="p-3">
            <div className="flex items-start justify-between gap-2">
              <h4 className="text-sm font-semibold leading-snug" style={{ color: 'var(--text-primary)' }}>
                {activeLandmark.name}
              </h4>
              <button
                type="button"
                aria-label="Закрыть карточку места"
                onClick={() => onSelectLandmark(null)}
                className="cursor-pointer rounded p-1 hover:opacity-75"
                style={{ color: 'var(--text-muted)' }}
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <p className="mt-1 text-xs" style={{ color: 'var(--text-muted)' }}>
              {activeLandmark.district}
            </p>
            <p className="mt-1.5 text-xs leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
              {activeLandmark.description}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
