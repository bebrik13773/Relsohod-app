import { useCallback, useEffect, useMemo, useState } from 'react';
import { CITIES } from '../data/cities';
import { Landmark } from '../types/tram';
import {
  EmbedMapRuntimeState,
  EmbeddedMapStatePatch,
  hydrateEmbeddedTrip,
  mergeEmbeddedMapState,
  parseMapOnlyInitialParams,
} from '../utils/mapEmbed';
import { TramMap } from './TramMap';

interface RelsokhodAndroidBridge {
  onMapReady?: () => void;
  onStopPicked?: (stopId: string) => void;
  onLandmarkSelected?: (landmarkId: string) => void;
}

declare global {
  interface Window {
    RelsokhodAndroid?: RelsokhodAndroidBridge;
    __relsokhodSetMapState?: (raw: unknown) => void;
  }
}

export function EmbedMapView() {
  const [state, setState] = useState<EmbedMapRuntimeState>(() => {
    const initial = parseMapOnlyInitialParams(
      typeof window !== 'undefined' ? window.location.search : ''
    );
    return {
      ...initial,
      tripPayload: null,
      hasBridgeState: false,
    };
  });

  const applyPatch = useCallback((raw: unknown) => {
    let parsed: unknown = raw;
    if (typeof raw === 'string') {
      try {
        parsed = JSON.parse(raw);
      } catch {
        return;
      }
    }
    if (!parsed || typeof parsed !== 'object') return;
    setState((prev) => mergeEmbeddedMapState(prev, parsed as EmbeddedMapStatePatch));
  }, []);

  // Register live JS bridge for Android WebView & iframe postMessage
  useEffect(() => {
    window.__relsokhodSetMapState = applyPatch;

    const handleMessage = (event: MessageEvent) => {
      const data = event.data;
      if (!data || typeof data !== 'object') return;
      if (data.type === 'relsokhod:map-state' && data.payload) {
        applyPatch(data.payload);
      }
    };
    window.addEventListener('message', handleMessage);

    try {
      window.RelsokhodAndroid?.onMapReady?.();
    } catch {
      // Ignore bridge invocation errors outside Android WebView
    }

    return () => {
      window.removeEventListener('message', handleMessage);
      if (window.__relsokhodSetMapState === applyPatch) {
        delete window.__relsokhodSetMapState;
      }
    };
  }, [applyPatch]);

  // Sync CSS theme & Material You accent tokens without touching localStorage
  useEffect(() => {
    const root = document.documentElement;
    root.dataset.theme = state.theme;
    root.dataset.accent = state.accentSeed;
    if (state.theme === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }, [state.theme, state.accentSeed]);

  const cityCfg = CITIES[state.city];

  const visibleStops = useMemo(
    () =>
      state.includePlanned2027
        ? cityCfg.stops
        : cityCfg.stops.filter((s) => !s.planned2027),
    [cityCfg.stops, state.includePlanned2027]
  );

  const visibleRoutes = useMemo(() => {
    if (state.includePlanned2027) return cityCfg.routes;
    const stopSet = new Set(visibleStops.map((s) => s.id));
    return cityCfg.routes.filter(
      (r) => !r.planned2027 && r.stopIds.every((id) => stopSet.has(id))
    );
  }, [cityCfg.routes, state.includePlanned2027, visibleStops]);

  const hydratedTrip = useMemo(
    () => hydrateEmbeddedTrip(state.tripPayload, cityCfg),
    [state.tripPayload, cityCfg]
  );

  const startStopId = useMemo(() => {
    if (state.startStopId && visibleStops.some((s) => s.id === state.startStopId)) {
      return state.startStopId;
    }
    if (hydratedTrip) return hydratedTrip.startStop.id;
    return cityCfg.defaultStartStopId;
  }, [state.startStopId, visibleStops, hydratedTrip, cityCfg.defaultStartStopId]);

  const mapLandmarks = useMemo(() => {
    if (hydratedTrip && hydratedTrip.landmarks.length > 0) {
      return hydratedTrip.landmarks;
    }
    return cityCfg.landmarks;
  }, [hydratedTrip, cityCfg.landmarks]);

  const handleSelectLandmark = useCallback((lm: Landmark | null) => {
    const nextId = lm ? lm.id : null;
    setState((prev) => ({ ...prev, selectedLandmarkId: nextId }));
    try {
      window.RelsokhodAndroid?.onLandmarkSelected?.(nextId ?? '');
    } catch {
      // Ignore bridge errors
    }
  }, []);

  const handlePickStop = useCallback((stopId: string) => {
    setState((prev) => {
      if (prev.mapPickMode === 'start') {
        return { ...prev, startStopId: stopId, mapPickMode: null };
      }
      if (prev.mapPickMode === 'destination') {
        return { ...prev, destinationStopId: stopId, mapPickMode: null };
      }
      return prev;
    });
    try {
      window.RelsokhodAndroid?.onStopPicked?.(stopId);
    } catch {
      // Ignore bridge errors
    }
  }, []);

  return (
    <main
      data-testid="embed-map-only"
      aria-label={`Интерактивная карта (${cityCfg.name})`}
      className="fixed inset-0 h-[100dvh] w-screen overflow-hidden"
      style={{ backgroundColor: 'var(--bg-page)' }}
    >
      <TramMap
        city={state.city}
        cityCenter={cityCfg.center}
        stops={visibleStops}
        routes={visibleRoutes}
        trip={hydratedTrip}
        landmarks={mapLandmarks}
        showAllNetwork={state.showAllNetwork}
        startStopId={startStopId}
        destinationStopId={state.destinationStopId}
        mapPickMode={state.mapPickMode}
        selectedLandmarkId={state.selectedLandmarkId}
        theme={state.theme}
        onSelectLandmark={handleSelectLandmark}
        onPickStop={handlePickStop}
      />
    </main>
  );
}
