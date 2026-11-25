import { useEffect, useMemo, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { X } from 'lucide-react';
import {
  AppTheme,
  CityId,
  GeneratedTrip,
  Landmark,
  TramRoute,
  TramStop,
} from '../types/tram';
import { smoothLatLngPolyline } from '../utils/mapGeometry';
import { findNearestStops } from '../utils/tramRouter';

interface LeafletTramMapProps {
  city: CityId;
  cityCenter: { lat: number; lng: number };
  stops: TramStop[];
  routes: TramRoute[];
  trip: GeneratedTrip | null;
  landmarks: Landmark[];
  showAllNetwork: boolean;
  startStopId: string;
  destinationStopId: string | null;
  mapPickMode: 'start' | 'destination' | null;
  selectedLandmarkId: string | null;
  theme: AppTheme;
  onSelectLandmark: (landmark: Landmark | null) => void;
  onPickStop: (stopId: string) => void;
}

function escapeHtml(raw: string): string {
  return raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export function LeafletTramMap({
  cityCenter,
  stops,
  routes,
  trip,
  landmarks,
  showAllNetwork,
  startStopId,
  destinationStopId,
  mapPickMode,
  selectedLandmarkId,
  theme,
  onSelectLandmark,
  onPickStop,
}: LeafletTramMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const tileLayerRef = useRef<L.TileLayer | null>(null);
  const bgLayerRef = useRef<L.LayerGroup | null>(null);
  const tripLayerRef = useRef<L.LayerGroup | null>(null);
  const markersLayerRef = useRef<L.LayerGroup | null>(null);

  const stopsRef = useRef(stops);
  stopsRef.current = stops;
  const mapPickModeRef = useRef(mapPickMode);
  mapPickModeRef.current = mapPickMode;
  const onPickStopRef = useRef(onPickStop);
  onPickStopRef.current = onPickStop;
  const onSelectLandmarkRef = useRef(onSelectLandmark);
  onSelectLandmarkRef.current = onSelectLandmark;

  const stopsById = useMemo(() => {
    const dict: Record<string, TramStop> = {};
    for (const s of stops) dict[s.id] = s;
    return dict;
  }, [stops]);

  const activeLandmark = useMemo(
    () => landmarks.find((l) => l.id === selectedLandmarkId) ?? null,
    [landmarks, selectedLandmarkId]
  );

  const visibleStops = useMemo(() => {
    const tripIds = new Set<string>();
    if (trip) {
      for (const seg of trip.segments) {
        for (const id of seg.stopIds) tripIds.add(id);
      }
    }
    return stops.filter(
      (s) =>
        s.id === startStopId ||
        s.id === destinationStopId ||
        tripIds.has(s.id) ||
        Boolean(mapPickMode) ||
        showAllNetwork
    );
  }, [stops, trip, startStopId, destinationStopId, mapPickMode, showAllNetwork]);

  const transferLabelsByStopId = useMemo(() => {
    const mapObj: Record<string, string> = {};
    if (!trip) return mapObj;
    for (let i = 1; i < trip.segments.length; i++) {
      const prevSeg = trip.segments[i - 1];
      const nextSeg = trip.segments[i];
      const fromLabel =
        prevSeg.route?.number ?? (prevSeg.kind === 'walk' ? 'Пешком' : 'Транспорт');
      const toLabel =
        nextSeg.route?.number ?? (nextSeg.kind === 'walk' ? 'Пешком' : 'Транспорт');
      mapObj[nextSeg.fromStop.id] = `${fromLabel} → ${toLabel}`;
    }
    return mapObj;
  }, [trip]);

  // Initialize Leaflet map once
  useEffect(() => {
    const el = containerRef.current;
    if (!el || mapRef.current) return;

    const map = L.map(el, {
      center: [cityCenter.lat, cityCenter.lng],
      zoom: 12,
      zoomControl: false,
      attributionControl: false,
    });

    L.control.zoom({ position: 'bottomright' }).addTo(map);
    L.control
      .attribution({ position: 'bottomright', prefix: false })
      .addAttribution('© OpenStreetMap')
      .addTo(map);

    bgLayerRef.current = L.layerGroup().addTo(map);
    tripLayerRef.current = L.layerGroup().addTo(map);
    markersLayerRef.current = L.layerGroup().addTo(map);

    map.on('click', (ev: L.LeafletMouseEvent) => {
      const mode = mapPickModeRef.current;
      if (!mode) return;
      const nearest = findNearestStops(
        ev.latlng.lat,
        ev.latlng.lng,
        stopsRef.current,
        1,
        mode === 'destination'
      )[0];
      if (nearest) {
        onPickStopRef.current(nearest.stop.id);
      }
    });

    mapRef.current = map;

    const observer = new ResizeObserver(() => {
      map.invalidateSize({ animate: false });
    });
    observer.observe(el);

    return () => {
      observer.disconnect();
      map.remove();
      mapRef.current = null;
      tileLayerRef.current = null;
      bgLayerRef.current = null;
      tripLayerRef.current = null;
      markersLayerRef.current = null;
    };
  }, []);

  // Use OpenStreetMap tiles (with CARTO dark/voyager fallback on tile error)
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    if (tileLayerRef.current) {
      map.removeLayer(tileLayerRef.current);
    }

    const primaryUrl =
      theme === 'dark'
        ? 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'
        : 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
    const fallbackUrl =
      theme === 'dark'
        ? 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
        : 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png';

    let switchedToFallback = false;
    const layer = L.tileLayer(primaryUrl, {
      subdomains: theme === 'dark' ? 'abcd' : 'abc',
      maxZoom: 19,
    });

    layer.on('tileerror', () => {
      if (!switchedToFallback && mapRef.current === map) {
        switchedToFallback = true;
        layer.setUrl(fallbackUrl);
      }
    });

    layer.addTo(map);
    tileLayerRef.current = layer;
  }, [theme]);

  // Center on city when there is no active trip
  useEffect(() => {
    const map = mapRef.current;
    if (!map || trip) return;
    map.setView([cityCenter.lat, cityCenter.lng], 12, { animate: true });
  }, [cityCenter.lat, cityCenter.lng, trip]);

  // Render full background network polylines
  useEffect(() => {
    const group = bgLayerRef.current;
    if (!group) return;
    group.clearLayers();
    if (!showAllNetwork) return;

    for (const route of routes) {
      const rawPath = route.stopIds
        .map((id) => stopsById[id])
        .filter(Boolean)
        .map((s) => ({ lat: s.lat, lng: s.lng }));
      if (rawPath.length < 2) continue;

      const smoothed = smoothLatLngPolyline(rawPath, 3, 0.22);
      const latLngs = smoothed.map((p) => [p.lat, p.lng] as [number, number]);
      L.polyline(latLngs, {
        color: route.color,
        weight: route.type === 'metro' ? 2.8 : route.type === 'bus' ? 1.6 : 2.3,
        opacity: route.planned2027
          ? trip
            ? 0.35
            : 0.65
          : route.type === 'bus'
            ? trip
              ? 0.14
              : 0.32
            : trip
              ? 0.25
              : 0.55,
        dashArray: route.planned2027 ? '6 6' : undefined,
        interactive: false,
      }).addTo(group);
    }
  }, [routes, stopsById, showAllNetwork, trip]);

  // Render active trip segments & fit bounds
  useEffect(() => {
    const map = mapRef.current;
    const group = tripLayerRef.current;
    if (!map || !group) return;
    group.clearLayers();
    if (!trip) return;

    const bounds = L.latLngBounds([]);

    for (const seg of trip.segments) {
      const rawPath = seg.stopIds
        .map((id) => stopsById[id])
        .filter(Boolean)
        .map((s) => ({ lat: s.lat, lng: s.lng }));
      if (rawPath.length < 2) continue;

      const smoothed = smoothLatLngPolyline(rawPath, 4, 0.24);
      const latLngs = smoothed.map((p) => {
        const pt: [number, number] = [p.lat, p.lng];
        bounds.extend(pt);
        return pt;
      });

      const isTram = seg.kind === 'tram';
      const strokeColor = isTram
        ? seg.route?.color ?? '#7C1D26'
        : seg.kind === 'walk'
          ? '#059669'
          : '#d97706';

      // White casing under line for contrast against street labels
      L.polyline(latLngs, {
        color: '#ffffff',
        weight: isTram ? 8.5 : 6.5,
        opacity: 0.85,
        lineCap: 'round',
        lineJoin: 'round',
        interactive: false,
      }).addTo(group);

      L.polyline(latLngs, {
        color: strokeColor,
        weight: isTram ? 5.2 : 4,
        opacity: 0.95,
        dashArray: isTram
          ? seg.route?.planned2027
            ? '8 5'
            : undefined
          : seg.kind === 'walk'
            ? '2 8'
            : '8 6',
        lineCap: 'round',
        lineJoin: 'round',
        interactive: false,
      }).addTo(group);
    }

    if (bounds.isValid()) {
      map.fitBounds(bounds, { padding: [48, 48], maxZoom: 15 });
    }
  }, [trip, stopsById]);

  // Render stop markers and landmark ★ markers
  useEffect(() => {
    const group = markersLayerRef.current;
    if (!group) return;
    group.clearLayers();

    for (const stop of visibleStops) {
      const isStart = stop.id === startStopId;
      const isDest =
        stop.id === destinationStopId || (trip && trip.endStop.id === stop.id);
      const transferLabel = transferLabelsByStopId[stop.id];
      const showBadge = isStart || isDest || Boolean(transferLabel);

      const icon = showBadge
        ? L.divIcon({
            className: 'relsokhod-leaflet-badge',
            html: `<div style="display:inline-flex;align-items:center;white-space:nowrap;border-radius:9999px;border:2px solid #ffffff;padding:2px 9px;font-size:11px;font-weight:700;box-shadow:0 2px 6px rgba(0,0,0,0.28);background:${
              isStart ? '#15803d' : isDest ? '#b91c1c' : '#1e293b'
            };color:#ffffff;transform:translate(-50%,-50%);">${escapeHtml(
              isStart ? 'Старт' : isDest ? 'Финиш' : transferLabel ?? ''
            )}</div>`,
            iconSize: [0, 0],
            iconAnchor: [0, 0],
          })
        : L.divIcon({
            className: 'relsokhod-leaflet-dot',
            html: `<div style="width:11px;height:11px;border-radius:${
              stop.stopKind === 'metro' ? '3px' : '9999px'
            };border:2px solid #ffffff;box-shadow:0 1px 3px rgba(0,0,0,0.3);background:${
              stop.isolatedFromMainTram ? '#d97706' : '#334155'
            };transform:translate(-50%,-50%);"></div>`,
            iconSize: [0, 0],
            iconAnchor: [0, 0],
          });

      const marker = L.marker([stop.lat, stop.lng], {
        icon,
        title: stop.name,
        zIndexOffset: isStart || isDest ? 900 : transferLabel ? 650 : 120,
      });
      marker.on('click', () => onPickStopRef.current(stop.id));
      marker.addTo(group);
    }

    for (const lm of landmarks) {
      const selected = lm.id === selectedLandmarkId;
      const icon = L.divIcon({
        className: 'relsokhod-leaflet-star',
        html: `<div style="width:26px;height:26px;display:flex;align-items:center;justify-content:center;border-radius:9999px;border:2px solid #ffffff;font-size:12px;font-weight:700;color:#ffffff;box-shadow:0 2px 6px rgba(0,0,0,0.3);background:${
          selected ? '#7C1D26' : '#B45309'
        };transform:translate(-50%,-50%);">★</div>`,
        iconSize: [0, 0],
        iconAnchor: [0, 0],
      });
      const marker = L.marker([lm.lat, lm.lng], {
        icon,
        title: lm.name,
        zIndexOffset: selected ? 1000 : 400,
      });
      marker.on('click', () =>
        onSelectLandmarkRef.current(selected ? null : lm)
      );
      marker.addTo(group);
    }
  }, [
    visibleStops,
    landmarks,
    startStopId,
    destinationStopId,
    trip,
    transferLabelsByStopId,
    selectedLandmarkId,
  ]);

  return (
    <div
      data-testid="leaflet-street-map"
      className="relative h-full w-full overflow-hidden"
      style={{ backgroundColor: 'var(--map-land)' }}
    >
      <div ref={containerRef} className="h-full w-full" />

      {/* Compact Active Route Legend */}
      {trip && (
        <div
          className="pointer-events-none absolute top-3 left-3 z-[500] flex max-w-[calc(100%-4.5rem)] flex-wrap items-center gap-1.5 rounded-lg border px-2.5 py-1.5 shadow-sm backdrop-blur-sm"
          style={{
            backgroundColor: 'var(--bg-surface)',
            borderColor: 'var(--border-hairline)',
            opacity: 0.94,
          }}
        >
          {trip.segments.map((seg, idx) => (
            <span key={seg.id} className="inline-flex items-center gap-1 text-xs font-semibold">
              {idx > 0 && (
                <span aria-hidden="true" style={{ color: 'var(--text-muted)' }}>
                  →
                </span>
              )}
              <span
                className="font-mono-num rounded px-1.5 py-0.5 text-[11px] font-bold text-white"
                style={{
                  backgroundColor:
                    seg.kind === 'tram'
                      ? seg.route?.color ?? '#7C1D26'
                      : seg.kind === 'walk'
                        ? '#059669'
                        : '#d97706',
                }}
              >
                {seg.kind === 'tram'
                  ? seg.route
                    ? seg.route.type === 'metro' ||
                      seg.route.type === 'trolleybus' ||
                      seg.route.type === 'bus'
                      ? seg.route.number
                      : `№${seg.route.number}`
                    : 'Линия'
                  : seg.kind === 'walk'
                    ? 'Пешком'
                    : 'Стыковка'}
              </span>
            </span>
          ))}
        </div>
      )}

      {mapPickMode && (
        <div
          className="absolute top-3 left-1/2 z-[500] -translate-x-1/2 rounded-lg px-3.5 py-2 text-xs font-medium shadow-md"
          style={{
            backgroundColor: 'var(--accent-primary)',
            color: 'var(--accent-on-primary)',
          }}
        >
          {mapPickMode === 'start'
            ? 'Кликните по карте или остановке, чтобы задать старт'
            : 'Кликните по карте или остановке, чтобы задать конечную точку'}
        </div>
      )}

      {/* Selected Landmark Preview Card */}
      {activeLandmark && (
        <div
          className="absolute bottom-14 left-3 z-[500] w-72 max-w-[calc(100vw-4.5rem)] overflow-hidden rounded-xl border shadow-lg sm:w-80"
          style={{
            backgroundColor: 'var(--bg-surface)',
            borderColor: 'var(--border-hairline)',
          }}
        >
          {activeLandmark.photoUrl && (
            <div className="relative h-32 w-full overflow-hidden">
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
              <h4
                className="text-sm font-semibold leading-snug"
                style={{ color: 'var(--text-primary)' }}
              >
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
