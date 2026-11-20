import { useEffect, useMemo, useRef } from 'react';
import {
  AccentSeed,
  AppTheme,
  CityId,
  DurationPreset,
  GeneratedTrip,
  GenerationOutcome,
  Landmark,
  LandmarkCategory,
  RouteThemeFilter,
  TramStop,
  WeatherMode,
} from '../types/tram';
import { filterLandmarks } from '../utils/tramRouter';
import { triggerHaptic } from '../utils/haptics';
import { PlannerParamsView } from './PlannerParamsView';
import { PlannerItineraryView } from './PlannerItineraryView';
import { PlannerPlacesView } from './PlannerPlacesView';

export type PlannerSubView = 'params' | 'route' | 'places';

interface RoutePlannerPanelProps {
  city: CityId;
  subView: PlannerSubView;
  onChangeSubView: (view: PlannerSubView) => void;
  stops: TramStop[];
  landmarks: Landmark[];
  startStopId: string;
  onChangeStartStop: (id: string) => void;
  destinationStopId: string | null;
  destinationAddressLabel: string;
  onChangeDestinationStop: (id: string | null, addressLabel?: string) => void;
  themeFilter: RouteThemeFilter;
  onChangeThemeFilter: (theme: RouteThemeFilter) => void;
  durationPreset: DurationPreset;
  onChangeDurationPreset: (preset: DurationPreset) => void;
  dayOfWeek: number;
  onChangeDayOfWeek: (day: number) => void;
  timeMinutes: number;
  onChangeTimeMinutes: (minutes: number) => void;
  allowFallbackTransit: boolean;
  onChangeAllowFallbackTransit: (allow: boolean) => void;
  showAllNetwork: boolean;
  onChangeShowAllNetwork: (show: boolean) => void;
  loopMode: boolean;
  onChangeLoopMode: (loop: boolean) => void;
  weatherMode: WeatherMode;
  onChangeWeatherMode: (weather: WeatherMode) => void;
  preferSpecialFleet: boolean;
  onChangePreferSpecialFleet: (prefer: boolean) => void;
  mapPickMode: 'start' | 'destination' | null;
  onToggleMapPickMode: (mode: 'start' | 'destination' | null) => void;
  onDetectLocation: () => void;
  geoStatus: { state: 'idle' | 'loading' | 'error' | 'ok'; message?: string };
  onGenerate: (overrideFallback?: boolean, overrideDay?: number, overrideTime?: number) => void;
  onExtendLastSegment: () => void;
  onStartLiveTrip: () => void;
  onOpenPostcard: () => void;
  outcome: GenerationOutcome | null;
  onSaveTrip: (trip: GeneratedTrip, customTitle: string, note: string) => void;
  isCurrentTripSaved: boolean;
  selectedLandmarkId: string | null;
  onSelectLandmark: (landmark: Landmark) => void;
  onlyWithPhotos: boolean;
  onChangeOnlyWithPhotos: (only: boolean) => void;
  landmarkCategories: LandmarkCategory[];
  onToggleLandmarkCategory: (category: LandmarkCategory) => void;
  theme: AppTheme;
  accentSeed: AccentSeed;
  onChangeAccentSeed: (accent: AccentSeed) => void;
}

export function RoutePlannerPanel({
  city,
  subView,
  onChangeSubView,
  stops,
  landmarks,
  startStopId,
  onChangeStartStop,
  destinationStopId,
  destinationAddressLabel,
  onChangeDestinationStop,
  themeFilter,
  onChangeThemeFilter,
  durationPreset,
  onChangeDurationPreset,
  dayOfWeek,
  onChangeDayOfWeek,
  timeMinutes,
  onChangeTimeMinutes,
  allowFallbackTransit,
  onChangeAllowFallbackTransit,
  showAllNetwork,
  onChangeShowAllNetwork,
  loopMode,
  onChangeLoopMode,
  weatherMode,
  onChangeWeatherMode,
  preferSpecialFleet,
  onChangePreferSpecialFleet,
  mapPickMode,
  onToggleMapPickMode,
  onDetectLocation,
  geoStatus,
  onGenerate,
  onExtendLastSegment,
  onStartLiveTrip,
  onOpenPostcard,
  outcome,
  onSaveTrip,
  isCurrentTripSaved,
  selectedLandmarkId,
  onSelectLandmark,
  onlyWithPhotos,
  onChangeOnlyWithPhotos,
  landmarkCategories,
  onToggleLandmarkCategory,
  theme,
  accentSeed,
  onChangeAccentSeed,
}: RoutePlannerPanelProps) {
  const scrollBodyRef = useRef<HTMLDivElement | null>(null);
  const paramsScrollRef = useRef(0);

  useEffect(() => {
    if (!scrollBodyRef.current) return;
    if (subView === 'params') {
      scrollBodyRef.current.scrollTop = paramsScrollRef.current;
    } else {
      scrollBodyRef.current.scrollTop = 0;
    }
  }, [subView]);

  const visibleLandmarks = useMemo(() => {
    if (!outcome || outcome.status !== 'ok') return [];
    return filterLandmarks(outcome.trip.landmarks, landmarkCategories, onlyWithPhotos);
  }, [outcome, landmarkCategories, onlyWithPhotos]);

  const segmentsCount = outcome?.status === 'ok' ? outcome.trip.segments.length : 0;

  return (
    <aside
      aria-label="Панель генератора трамвайных маршрутов"
      className="flex h-full w-full flex-col border-r lg:w-[460px] xl:w-[500px] shrink-0"
      style={{
        backgroundColor: 'var(--bg-canvas)',
        borderColor: 'var(--border-hairline)',
      }}
    >
      {/* Material You 3-tab segmented switcher */}
      <div
        className="border-b px-4 py-3 shrink-0"
        style={{
          backgroundColor: 'var(--bg-surface)',
          borderColor: 'var(--border-hairline)',
        }}
      >
        <div
          role="tablist"
          aria-label="Разделы планировщика"
          className="grid grid-cols-3 gap-1 rounded-2xl p-1"
          style={{
            backgroundColor: 'var(--bg-elevated)',
            border: '1px solid var(--border-hairline)',
          }}
        >
          <button
            type="button"
            role="tab"
            aria-selected={subView === 'params'}
            onClick={() => {
              triggerHaptic('light');
              onChangeSubView('params');
            }}
            className="m3-tactile min-h-[38px] cursor-pointer rounded-xl px-2 py-1.5 text-xs font-semibold whitespace-nowrap"
            style={{
              backgroundColor:
                subView === 'params' ? 'var(--md-primary-container)' : 'transparent',
              color:
                subView === 'params' ? 'var(--md-on-primary-container)' : 'var(--text-muted)',
            }}
          >
            Параметры
          </button>

          <button
            type="button"
            role="tab"
            aria-selected={subView === 'route'}
            onClick={() => {
              triggerHaptic('light');
              onChangeSubView('route');
            }}
            className="m3-tactile min-h-[38px] cursor-pointer rounded-xl px-2 py-1.5 text-xs font-semibold whitespace-nowrap"
            style={{
              backgroundColor:
                subView === 'route' ? 'var(--md-primary-container)' : 'transparent',
              color:
                subView === 'route' ? 'var(--md-on-primary-container)' : 'var(--text-muted)',
            }}
          >
            Маршрут{segmentsCount > 0 ? ` (${segmentsCount})` : ''}
          </button>

          <button
            type="button"
            role="tab"
            aria-selected={subView === 'places'}
            onClick={() => {
              triggerHaptic('light');
              onChangeSubView('places');
            }}
            className="m3-tactile min-h-[38px] cursor-pointer rounded-xl px-2 py-1.5 text-xs font-semibold whitespace-nowrap"
            style={{
              backgroundColor:
                subView === 'places' ? 'var(--md-primary-container)' : 'transparent',
              color:
                subView === 'places' ? 'var(--md-on-primary-container)' : 'var(--text-muted)',
            }}
          >
            Места{outcome?.status === 'ok' ? ` (${visibleLandmarks.length})` : ''}
          </button>
        </div>
      </div>

      {/* Active Sub-View Body */}
      <div
        ref={scrollBodyRef}
        onScroll={(e) => {
          if (subView === 'params') {
            paramsScrollRef.current = e.currentTarget.scrollTop;
          }
        }}
        className="min-h-0 flex-1 overflow-y-auto p-4 lg:p-5"
      >
        {subView === 'params' && (
          <PlannerParamsView
            city={city}
            stops={stops}
            landmarks={landmarks}
            startStopId={startStopId}
            onChangeStartStop={onChangeStartStop}
            destinationStopId={destinationStopId}
            destinationAddressLabel={destinationAddressLabel}
            onChangeDestinationStop={onChangeDestinationStop}
            themeFilter={themeFilter}
            onChangeThemeFilter={onChangeThemeFilter}
            durationPreset={durationPreset}
            onChangeDurationPreset={onChangeDurationPreset}
            dayOfWeek={dayOfWeek}
            onChangeDayOfWeek={onChangeDayOfWeek}
            timeMinutes={timeMinutes}
            onChangeTimeMinutes={onChangeTimeMinutes}
            allowFallbackTransit={allowFallbackTransit}
            onChangeAllowFallbackTransit={onChangeAllowFallbackTransit}
            showAllNetwork={showAllNetwork}
            onChangeShowAllNetwork={onChangeShowAllNetwork}
            loopMode={loopMode}
            onChangeLoopMode={onChangeLoopMode}
            weatherMode={weatherMode}
            onChangeWeatherMode={onChangeWeatherMode}
            preferSpecialFleet={preferSpecialFleet}
            onChangePreferSpecialFleet={onChangePreferSpecialFleet}
            mapPickMode={mapPickMode}
            onToggleMapPickMode={onToggleMapPickMode}
            onDetectLocation={onDetectLocation}
            geoStatus={geoStatus}
            onGenerate={() => {
              onGenerate();
              onChangeSubView('route');
            }}
            theme={theme}
            accentSeed={accentSeed}
            onChangeAccentSeed={onChangeAccentSeed}
          />
        )}

        {subView === 'route' && (
          <PlannerItineraryView
            city={city}
            outcome={outcome}
            onGenerate={onGenerate}
            onExtendLastSegment={onExtendLastSegment}
            onStartLiveTrip={onStartLiveTrip}
            onOpenPostcard={onOpenPostcard}
            onChangeAllowFallbackTransit={onChangeAllowFallbackTransit}
            onChangeDayOfWeek={onChangeDayOfWeek}
            onChangeTimeMinutes={onChangeTimeMinutes}
            onSaveTrip={onSaveTrip}
            isCurrentTripSaved={isCurrentTripSaved}
            onEditParams={() => onChangeSubView('params')}
          />
        )}

        {subView === 'places' && (
          <PlannerPlacesView
            landmarks={visibleLandmarks}
            selectedLandmarkId={selectedLandmarkId}
            onSelectLandmark={onSelectLandmark}
            onlyWithPhotos={onlyWithPhotos}
            onChangeOnlyWithPhotos={onChangeOnlyWithPhotos}
            landmarkCategories={landmarkCategories}
            onToggleLandmarkCategory={onToggleLandmarkCategory}
          />
        )}
      </div>
    </aside>
  );
}
