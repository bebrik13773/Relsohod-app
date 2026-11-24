export type AppTheme = 'tram' | 'light' | 'dark';

export type AccentSeed = 'neva' | 'sage' | 'terracotta' | 'indigo' | 'amber';

export type CityId = 'spb' | 'omsk';

export type WeatherMode = 'clear' | 'rain' | 'frost';

export type RouteThemeFilter = 'any' | 'center' | 'parks' | 'suburbs' | 'maritime';

export type DurationPreset = 'any' | 'short' | 'medium' | 'long';

export type LandmarkCategory = 'architecture' | 'park' | 'hidden' | 'cafe' | 'grocery';

export type TramRouteType = 'regular' | 'circular' | 'retro' | 'chizhik' | 'metro' | 'trolleybus';

export type StopKind = 'tram' | 'metro' | 'trolleybus' | 'hub';

export type TimePlanningMode = 'duration' | 'deadline';

export interface TramStop {
  id: string;
  name: string;
  district: string;
  lat: number;
  lng: number;
  /** Distinguishes tram stops, metro stations, trolleybus stops, or multimodal hubs */
  stopKind?: StopKind;
  /** Stops sharing the same hubId can be walked between smoothly during transfers */
  hubId?: string;
  /** Marks stops outside the main continuous transit network (reached only via fallback transit) */
  isolatedFromMainTram?: boolean;
  /** Marks stops opening / connecting in projects planned by Summer 2027 */
  planned2027?: boolean;
  plannedLabel?: string;
}

export interface TramSchedule {
  /** 0 = Sunday, 1 = Monday, ... 6 = Saturday */
  daysOfWeek: number[];
  /** Minutes from 00:00 (e.g. 6*60+11 = 371) */
  startMinutes: number;
  /** Minutes from 00:00; values > 1440 indicate service past midnight (e.g. 24*60+30 = 1470) */
  endMinutes: number;
  intervalMinutes: number;
  note: string;
}

export interface TramRoute {
  id: string;
  number: string;
  title: string;
  operator:
    | 'Горэлектротранс'
    | 'Чижик (ТКК)'
    | 'БалтНедвижСервис («Славянка»)'
    | 'Петербургский метрополитен'
    | 'МП «Электрический транспорт»';
  type: TramRouteType;
  color: string;
  themes: Exclude<RouteThemeFilter, 'any'>[];
  schedule: TramSchedule;
  /** Ordered sequence of stop IDs. For circular routes, first === last. */
  stopIds: string[];
  /** Average minutes between consecutive stops in this route */
  minutesPerHop: number;
  /** Marks lines or extensions scheduled by Summer 2027 */
  planned2027?: boolean;
  plannedLabel?: string;
}

export interface Landmark {
  id: string;
  name: string;
  category: LandmarkCategory;
  lat: number;
  lng: number;
  district: string;
  description: string;
  nearbyStopIds: string[];
  photoUrl: string | null;
}

export interface RouteSegment {
  id: string;
  kind: 'tram' | 'walk' | 'fallback_transit';
  route?: TramRoute;
  fallbackModeName?: string;
  fromStop: TramStop;
  toStop: TramStop;
  stopIds: string[];
  stopsCount: number;
  durationMinutes: number;
  directionNote: string;
}

export interface ModeBalance {
  /** Fraction 0..1 for tram preference */
  tram: number;
  /** Fraction 0..1 for bus/metro/trolleybus preference */
  transit: number;
  /** Fraction 0..1 for walking preference */
  walk: number;
}

export interface GeneratedTrip {
  id: string;
  city?: CityId;
  createdAt: string;
  startStop: TramStop;
  endStop: TramStop;
  segments: RouteSegment[];
  tramLinesUsed: string[];
  transferCount: number;
  totalDurationMinutes: number;
  usedFallbackTransit: boolean;
  isLoop?: boolean;
  weatherMode?: WeatherMode;
  modeBalance?: ModeBalance;
  districtsVisited: string[];
  landmarks: Landmark[];
  dayOfWeek: number;
  timeMinutes: number;
  /** Short title & badge when choosing among 3 generated alternatives */
  variantTitle?: string;
  variantBadge?: string;
  /** Computed arrival clock in minutes (0..1439) */
  arrivalTimeMinutes?: number;
  /** Target deadline clock when timeMode === 'deadline' */
  targetDeadlineMinutes?: number;
  /** Total walking distance across walk segments in km */
  walkDistanceKm?: number;
  /** Up to 3 distinct route alternatives (each item omits nested alternatives) */
  alternatives?: GeneratedTrip[];
}

export type GenerationOutcome =
  | {
      status: 'ok';
      trip: GeneratedTrip;
    }
  | {
      status: 'needs_fallback';
      reason: string;
      nearestTramStop: TramStop | null;
    }
  | {
      status: 'no_service';
      reason: string;
    };

export interface SavedRoute {
  id: string;
  customTitle: string;
  note: string;
  savedAt: string;
  trip: GeneratedTrip;
}

export interface TripPhotoMemory {
  id: string;
  dataUrl: string;
  caption: string;
  takenAt: string;
}

export interface TripHistoryEntry {
  id: string;
  city: CityId;
  completedAt: string;
  trip: GeneratedTrip;
  photos: TripPhotoMemory[];
}

export interface PersistedPlannerState {
  city: CityId;
  startStopId: string;
  destinationStopId: string | null;
  destinationAddressLabel: string;
  themeFilter: RouteThemeFilter;
  durationPreset: DurationPreset;
  timeMode: TimePlanningMode;
  deadlineMinutes: number;
  dayOfWeek: number;
  timeMinutes: number;
  allowFallbackTransit: boolean;
  includePlanned2027: boolean;
  modeBalance: ModeBalance;
  showAllNetwork: boolean;
  loopMode: boolean;
  weatherMode: WeatherMode;
  preferSpecialFleet: boolean;
  onlyWithPhotos: boolean;
  landmarkCategories: LandmarkCategory[];
}

export interface OfficialAudioGuide {
  id: string;
  city: CityId;
  title: string;
  portalName: string;
  url: string;
  durationLabel: string;
  description: string;
  matchRouteNumbers: string[];
  matchDistricts: string[];
  matchLandmarkIds?: string[];
  isCityOverview?: boolean;
}

export interface MatchedAudioGuide extends OfficialAudioGuide {
  matchReason: string;
  matchScore: number;
}

