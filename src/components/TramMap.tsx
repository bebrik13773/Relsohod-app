import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AdvancedMarker,
  APIProvider,
  APILoadingStatus,
  InfoWindow,
  Map as GoogleMap,
  useApiLoadingStatus,
  useMap,
  useMapsLibrary,
} from '@vis.gl/react-google-maps';
import { AppTheme, CityId, GeneratedTrip, Landmark, TramRoute, TramStop } from '../types/tram';
import { findNearestStops } from '../utils/tramRouter';
import { SpbSchematicMap } from './SpbSchematicMap';

interface TramMapProps {
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

interface PolylineDrawerProps {
  stopsById: Record<string, TramStop>;
  routes: TramRoute[];
  trip: GeneratedTrip | null;
  showAllNetwork: boolean;
}

function GoogleRoutePolylines({ stopsById, routes, trip, showAllNetwork }: PolylineDrawerProps) {
  const map = useMap();
  const mapsLib = useMapsLibrary('maps');
  const coreLib = useMapsLibrary('core');

  // Render full tram network background lines when requested
  useEffect(() => {
    if (!map || !mapsLib || !showAllNetwork) return;

    const lines: Array<{ setMap: (target: unknown) => void }> = [];
    for (const route of routes) {
      const path = route.stopIds
        .map((id) => stopsById[id])
        .filter(Boolean)
        .map((s) => ({ lat: s.lat, lng: s.lng }));
      if (path.length < 2) continue;

      const line = new mapsLib.Polyline({
        path,
        strokeColor: route.color,
        strokeOpacity: trip ? 0.25 : 0.55,
        strokeWeight: 2.5,
        map,
        zIndex: 1,
      });
      lines.push(line);
    }

    return () => {
      for (const l of lines) l.setMap(null);
    };
  }, [map, mapsLib, routes, stopsById, showAllNetwork, trip]);

  // Render active generated trip segments and fit viewport bounds
  useEffect(() => {
    if (!map || !mapsLib || !coreLib || !trip) return;

    const lines: Array<{ setMap: (target: unknown) => void }> = [];
    const bounds = new coreLib.LatLngBounds();

    for (const seg of trip.segments) {
      const path = seg.stopIds
        .map((id) => stopsById[id])
        .filter(Boolean)
        .map((s) => ({ lat: s.lat, lng: s.lng }));

      for (const pt of path) {
        bounds.extend(pt);
      }

      const isTram = seg.kind === 'tram';
      const strokeColor = isTram
        ? seg.route?.color ?? '#7C1D26'
        : seg.kind === 'walk'
          ? '#64748b'
          : '#d97706';

      const line = new mapsLib.Polyline({
        path,
        strokeColor,
        strokeOpacity: isTram ? 0.95 : 0,
        strokeWeight: isTram ? 5.5 : 0,
        icons: isTram
          ? undefined
          : [
              {
                icon: {
                  path: 'M 0,-1 0,1',
                  strokeOpacity: 0.9,
                  strokeColor,
                  scale: 3,
                },
                offset: '0',
                repeat: '14px',
              },
            ],
        map,
        zIndex: isTram ? 10 : 8,
      });
      lines.push(line);
    }

    if (!bounds.isEmpty()) {
      map.fitBounds(bounds, { top: 48, right: 48, bottom: 48, left: 48 });
    }

    return () => {
      for (const l of lines) l.setMap(null);
    };
  }, [map, mapsLib, coreLib, trip, stopsById]);

  return null;
}

function MapCenterSync({
  cityCenter,
  hasTrip,
}: {
  cityCenter: { lat: number; lng: number };
  hasTrip: boolean;
}) {
  const map = useMap();
  useEffect(() => {
    if (!map || hasTrip) return;
    map.panTo(cityCenter);
    map.setZoom(12);
  }, [map, cityCenter, hasTrip]);
  return null;
}

function GoogleMapInner(props: TramMapProps & { onFallbackNeeded: () => void }) {
  const status = useApiLoadingStatus();
  const {
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
    onFallbackNeeded,
  } = props;

  useEffect(() => {
    if (status === APILoadingStatus.FAILED || status === APILoadingStatus.AUTH_FAILURE) {
      onFallbackNeeded();
    }
  }, [status, onFallbackNeeded]);

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
      const fromLabel = prevSeg.route?.number ?? (prevSeg.kind === 'walk' ? 'Пешком' : 'Транспорт');
      const toLabel = nextSeg.route?.number ?? (nextSeg.kind === 'walk' ? 'Пешком' : 'Транспорт');
      mapObj[nextSeg.fromStop.id] = `${fromLabel} → ${toLabel}`;
    }
    return mapObj;
  }, [trip]);

  const handleMapClick = useCallback(
    (ev: { detail: { latLng?: { lat: number; lng: number } | null } }) => {
      if (!mapPickMode || !ev.detail.latLng) return;
      const nearest = findNearestStops(
        ev.detail.latLng.lat,
        ev.detail.latLng.lng,
        stops,
        1,
        mapPickMode === 'destination'
      )[0];
      if (nearest) {
        onPickStop(nearest.stop.id);
      }
    },
    [mapPickMode, stops, onPickStop]
  );

  return (
    <div className="absolute inset-0 h-full w-full">
      <GoogleMap
        defaultCenter={props.cityCenter}
        defaultZoom={12}
        mapId="DEMO_MAP_ID"
        colorScheme={theme === 'dark' ? 'DARK' : 'LIGHT'}
        gestureHandling="greedy"
        disableDefaultUI={false}
        streetViewControl={false}
        mapTypeControl={false}
        internalUsageAttributionIds={['gmp_mcp_codeassist_v1_aistudio']}
        onClick={handleMapClick}
        style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }}
      >
        <MapCenterSync cityCenter={props.cityCenter} hasTrip={Boolean(trip)} />
        <GoogleRoutePolylines
          stopsById={stopsById}
          routes={routes}
          trip={trip}
          showAllNetwork={showAllNetwork}
        />

        {visibleStops.map((stop) => {
          const isStart = stop.id === startStopId;
          const isDest = stop.id === destinationStopId || (trip && trip.endStop.id === stop.id);
          const transferLabel = transferLabelsByStopId[stop.id];
          const showBadge = isStart || isDest || Boolean(transferLabel);

          return (
            <AdvancedMarker
              key={stop.id}
              position={{ lat: stop.lat, lng: stop.lng }}
              title={stop.name}
              zIndex={isStart || isDest ? 900 : transferLabel ? 650 : 120}
              onClick={() => onPickStop(stop.id)}
            >
              {showBadge ? (
                <div
                  className="font-mono-num flex items-center gap-1 rounded-full border-2 border-white px-2.5 py-0.5 text-[11px] font-bold shadow-md"
                  style={{
                    backgroundColor: isStart
                      ? '#15803d'
                      : isDest
                        ? '#b91c1c'
                        : '#1e293b',
                    color: '#ffffff',
                  }}
                >
                  <span>
                    {isStart ? 'Старт' : isDest ? 'Финиш' : transferLabel}
                  </span>
                </div>
              ) : (
                <div
                  className="h-3 w-3 rounded-full border-2 border-white shadow-sm"
                  style={{
                    backgroundColor: stop.isolatedFromMainTram ? '#d97706' : '#334155',
                  }}
                />
              )}
            </AdvancedMarker>
          );
        })}

        {landmarks.map((lm) => (
          <AdvancedMarker
            key={lm.id}
            position={{ lat: lm.lat, lng: lm.lng }}
            title={lm.name}
            zIndex={lm.id === selectedLandmarkId ? 1000 : 400}
            onClick={() => onSelectLandmark(lm)}
          >
            <div
              className="flex h-7 w-7 items-center justify-center rounded-full border-2 border-white text-xs font-bold text-white shadow-md"
              style={{
                backgroundColor: lm.id === selectedLandmarkId ? '#7C1D26' : '#B45309',
              }}
            >
              ★
            </div>
          </AdvancedMarker>
        ))}

        {activeLandmark && (
          <InfoWindow
            position={{ lat: activeLandmark.lat, lng: activeLandmark.lng }}
            onCloseClick={() => onSelectLandmark(null)}
          >
            <div className="max-w-[240px] text-slate-900">
              {activeLandmark.photoUrl && (
                <img
                  src={activeLandmark.photoUrl}
                  alt={activeLandmark.name}
                  referrerPolicy="no-referrer"
                  className="mb-2 h-28 w-full rounded object-cover"
                />
              )}
              <div className="text-sm font-semibold">{activeLandmark.name}</div>
              <div className="mt-0.5 text-xs text-slate-500">{activeLandmark.district}</div>
              <p className="mt-1 text-xs leading-snug text-slate-700">{activeLandmark.description}</p>
            </div>
          </InfoWindow>
        )}
      </GoogleMap>

      {/* Compact Active Route Legend */}
      {trip && (
        <div
          className="pointer-events-none absolute top-3 left-3 z-10 flex max-w-[calc(100%-4.5rem)] flex-wrap items-center gap-1.5 rounded-lg border px-2.5 py-1.5 shadow-sm backdrop-blur-sm"
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
                        ? '#64748b'
                        : '#d97706',
                }}
              >
                {seg.kind === 'tram'
                  ? `№${seg.route?.number}`
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
          className="absolute top-3 left-1/2 z-10 -translate-x-1/2 rounded-lg px-3.5 py-2 text-xs font-medium shadow-md"
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
    </div>
  );
}

export function TramMap(props: TramMapProps) {
  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string | undefined;
  const [useFallback, setUseFallback] = useState(
    !apiKey || apiKey === 'MY_GOOGLE_MAPS_API_KEY'
  );

  useEffect(() => {
    const onQuota = () => setUseFallback(true);
    window.addEventListener('gmp-quota-exceeded', onQuota);
    return () => window.removeEventListener('gmp-quota-exceeded', onQuota);
  }, []);

  const handleFallbackNeeded = useCallback(() => {
    setUseFallback(true);
  }, []);

  if (useFallback || !apiKey) {
    return (
      <SpbSchematicMap
        city={props.city}
        stops={props.stops}
        routes={props.routes}
        trip={props.trip}
        landmarks={props.landmarks}
        showAllNetwork={props.showAllNetwork}
        startStopId={props.startStopId}
        destinationStopId={props.destinationStopId}
        mapPickMode={props.mapPickMode}
        selectedLandmarkId={props.selectedLandmarkId}
        onSelectLandmark={props.onSelectLandmark}
        onPickStop={props.onPickStop}
      />
    );
  }

  return (
    <APIProvider apiKey={apiKey} language="ru" region="RU">
      <GoogleMapInner {...props} onFallbackNeeded={handleFallbackNeeded} />
    </APIProvider>
  );
}
