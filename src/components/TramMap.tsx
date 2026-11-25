import { useState } from 'react';
import { AppTheme, CityId, GeneratedTrip, Landmark, TramRoute, TramStop } from '../types/tram';
import { LeafletTramMap } from './LeafletTramMap';
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

export function TramMap(props: TramMapProps) {
  const [viewMode, setViewMode] = useState<'map' | 'schematic'>('map');

  return (
    <div className="relative h-full w-full">
      <div
        className="absolute bottom-3 left-3 z-[600] inline-flex rounded-xl border p-0.5 shadow-sm"
        style={{
          backgroundColor: 'var(--bg-surface)',
          borderColor: 'var(--border-hairline)',
        }}
      >
        <button
          type="button"
          onClick={() => setViewMode('map')}
          className="cursor-pointer rounded-lg px-2.5 py-1 text-[11px] font-semibold transition-colors"
          style={{
            backgroundColor: viewMode === 'map' ? 'var(--md-primary-container)' : 'transparent',
            color: viewMode === 'map' ? 'var(--md-on-primary-container)' : 'var(--text-secondary)',
          }}
        >
          Карта
        </button>
        <button
          type="button"
          onClick={() => setViewMode('schematic')}
          className="cursor-pointer rounded-lg px-2.5 py-1 text-[11px] font-semibold transition-colors"
          style={{
            backgroundColor: viewMode === 'schematic' ? 'var(--md-primary-container)' : 'transparent',
            color: viewMode === 'schematic' ? 'var(--md-on-primary-container)' : 'var(--text-secondary)',
          }}
        >
          Схема
        </button>
      </div>

      {viewMode === 'schematic' ? (
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
      ) : (
        <LeafletTramMap {...props} />
      )}
    </div>
  );
}
