import {
  AccentSeed,
  AppTheme,
  CityId,
  PersistedPlannerState,
  SavedRoute,
  TripHistoryEntry,
} from '../types/tram';
import { APP_VERSION_STORAGE_KEY } from './changelog';

const SAVED_ROUTES_KEY = 'spb_tram_saved_routes_v1';
const THEME_STORAGE_KEY = 'spb_tram_theme_v1';
const ACCENT_STORAGE_KEY = 'spb_tram_accent_v1';
const CITY_STORAGE_KEY = 'relsokhod_city_v1';
const ONBOARDING_DONE_KEY = 'relsokhod_onboarding_done_v1';
const PLANNER_STATE_KEY = 'relsokhod_planner_state_v1';
const TRIP_HISTORY_KEY = 'relsokhod_trip_history_v1';
const PHOTO_REMINDER_MINS_KEY = 'relsokhod_photo_reminder_mins_v1';

export function loadSavedRoutes(): SavedRoute[] {
  try {
    const raw = window.localStorage.getItem(SAVED_ROUTES_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function persistSavedRoutes(routes: SavedRoute[]): void {
  try {
    window.localStorage.setItem(SAVED_ROUTES_KEY, JSON.stringify(routes));
  } catch {
    // Ignore storage quota errors cleanly
  }
}

export function loadStoredTheme(): AppTheme {
  try {
    const raw = window.localStorage.getItem(THEME_STORAGE_KEY);
    if (raw === 'tram' || raw === 'light' || raw === 'dark') {
      return raw;
    }
  } catch {
    // Ignore storage errors
  }
  return 'light';
}

export function persistTheme(theme: AppTheme): void {
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // Ignore storage errors
  }
}

export function loadStoredAccent(): AccentSeed {
  try {
    const raw = window.localStorage.getItem(ACCENT_STORAGE_KEY);
    if (
      raw === 'neva' ||
      raw === 'sage' ||
      raw === 'terracotta' ||
      raw === 'indigo' ||
      raw === 'amber'
    ) {
      return raw;
    }
  } catch {
    // Ignore storage errors
  }
  return 'neva';
}

export function persistAccent(accent: AccentSeed): void {
  try {
    window.localStorage.setItem(ACCENT_STORAGE_KEY, accent);
  } catch {
    // Ignore storage errors
  }
}

export function loadStoredCity(): CityId {
  try {
    const raw = window.localStorage.getItem(CITY_STORAGE_KEY);
    if (raw === 'spb' || raw === 'omsk') {
      return raw;
    }
  } catch {
    // Ignore storage errors
  }
  return 'spb';
}

export function persistCity(city: CityId): void {
  try {
    window.localStorage.setItem(CITY_STORAGE_KEY, city);
  } catch {
    // Ignore storage errors
  }
}

export function loadOnboardingDone(): boolean {
  try {
    if (typeof window !== 'undefined') {
      const params = new URLSearchParams(window.location.search);
      if (params.get('autostart') === '1') {
        return true;
      }
      return window.localStorage.getItem(ONBOARDING_DONE_KEY) === '1';
    }
  } catch {
    // Ignore storage errors
  }
  return false;
}

export function persistOnboardingDone(done: boolean): void {
  try {
    window.localStorage.setItem(ONBOARDING_DONE_KEY, done ? '1' : '0');
  } catch {
    // Ignore storage errors
  }
}

export function loadPlannerState(): Partial<PersistedPlannerState> | null {
  try {
    const raw = window.localStorage.getItem(PLANNER_STATE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? (parsed as Partial<PersistedPlannerState>) : null;
  } catch {
    return null;
  }
}

export function persistPlannerState(state: PersistedPlannerState): void {
  try {
    window.localStorage.setItem(PLANNER_STATE_KEY, JSON.stringify(state));
  } catch {
    // Ignore storage errors
  }
}

export function loadTripHistory(): TripHistoryEntry[] {
  try {
    const raw = window.localStorage.getItem(TRIP_HISTORY_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function persistTripHistory(entries: TripHistoryEntry[]): void {
  try {
    // Cap at 30 entries to stay well within localStorage quota when photos are attached
    window.localStorage.setItem(TRIP_HISTORY_KEY, JSON.stringify(entries.slice(0, 30)));
  } catch {
    // Ignore quota errors
  }
}

export function loadPhotoReminderMinutes(): number {
  try {
    const raw = window.localStorage.getItem(PHOTO_REMINDER_MINS_KEY);
    if (raw !== null) {
      const n = Number(raw);
      if ([0, 15, 30, 45, 60].includes(n)) return n;
    }
  } catch {
    // Ignore
  }
  return 30;
}

export function persistPhotoReminderMinutes(minutes: number): void {
  try {
    window.localStorage.setItem(PHOTO_REMINDER_MINS_KEY, String(minutes));
  } catch {
    // Ignore
  }
}

export function loadLastSeenAppVersion(): string | null {
  try {
    return window.localStorage.getItem(APP_VERSION_STORAGE_KEY);
  } catch {
    return null;
  }
}

export function persistLastSeenAppVersion(version: string): void {
  try {
    window.localStorage.setItem(APP_VERSION_STORAGE_KEY, version);
  } catch {
    // Ignore storage errors
  }
}
