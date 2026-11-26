import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { RefreshCw, ListOrdered } from 'lucide-react';
import { AnimatePresence, MotionConfig } from 'motion/react';
import { CITIES, CityDetectionResult, detectCityFromCoords } from './data/cities';
import {
  AccentSeed,
  AppTheme,
  CityId,
  DurationPreset,
  GeneratedTrip,
  GenerationOutcome,
  Landmark,
  LandmarkCategory,
  ModeBalance,
  RouteThemeFilter,
  SavedRoute,
  TimePlanningMode,
  TripHistoryEntry,
  TripPhotoMemory,
  WeatherMode,
} from './types/tram';
import { DEFAULT_MODE_BALANCE, normalizeModeBalance } from './utils/mapGeometry';
import {
  extendTripLastSegment,
  filterLandmarks,
  findNearestStops,
  generateTramAdventure,
} from './utils/tramRouter';
import {
  loadLastSeenAppVersion,
  loadOnboardingDone,
  loadPhotoReminderMinutes,
  loadPlannerState,
  loadSavedRoutes,
  loadStoredAccent,
  loadStoredCity,
  loadStoredTheme,
  loadTripHistory,
  persistAccent,
  persistCity,
  persistLastSeenAppVersion,
  persistOnboardingDone,
  persistPhotoReminderMinutes,
  persistPlannerState,
  persistSavedRoutes,
  persistTheme,
  persistTripHistory,
} from './utils/storage';
import {
  CURRENT_APP_VERSION,
  ChangelogRelease,
  LATEST_CHANGELOG,
  checkAppVersionAgainstStorage,
} from './utils/changelog';
import {
  MAX_BACKUP_BYTES,
  buildBackupFilename,
  createBackupPayload,
  isAcceptedBackupFilename,
  parseAndValidateBackup,
  serializeBackup,
} from './utils/backup';
import { triggerHaptic } from './utils/haptics';
import { ActiveTab, TopBar } from './components/TopBar';
import { MobileBottomNav, MobileNavTab } from './components/MobileBottomNav';
import { PlannerSubView, RoutePlannerPanel } from './components/RoutePlannerPanel';
import { PlannerParamsView } from './components/PlannerParamsView';
import { PlannerItineraryView } from './components/PlannerItineraryView';
import { PlannerPlacesView } from './components/PlannerPlacesView';
import { TramMap } from './components/TramMap';
import { FavoritesView } from './components/FavoritesView';
import { RoutesDirectoryView } from './components/RoutesDirectoryView';
import { OfflineIndicator } from './components/OfflineIndicator';
import { OnboardingModal } from './components/OnboardingModal';
import { CityMismatchBanner } from './components/CityMismatchBanner';
import { SettingsModal } from './components/SettingsModal';
import { HelpModal } from './components/HelpModal';
import { ChangelogModal } from './components/ChangelogModal';
import { LiveTripCompanion } from './components/LiveTripCompanion';
import { RoutePostcardModal } from './components/RoutePostcardModal';
import { StartupSplash } from './components/StartupSplash';
import { EmbedMapView } from './components/EmbedMapView';
import { isMapOnlySearch } from './utils/mapEmbed';

function getInitialScheduleSlot(): { dayOfWeek: number; timeMinutes: number } {
  const now = new Date();
  const mins = now.getHours() * 60 + now.getMinutes();
  if (mins < 6 * 60 || mins > 23 * 60 + 30) {
    return { dayOfWeek: now.getDay(), timeMinutes: 13 * 60 };
  }
  return { dayOfWeek: now.getDay(), timeMinutes: mins };
}

type MobileRouteSubView = 'map' | 'steps' | 'places';

export default function App() {
  const isMapOnly =
    typeof window !== 'undefined' && isMapOnlySearch(window.location.search);

  if (isMapOnly) {
    return (
      <MotionConfig reducedMotion="user">
        <EmbedMapView />
      </MotionConfig>
    );
  }

  return <MainRelsokhodApp />;
}

function MainRelsokhodApp() {
  const savedPlanner = useMemo(() => loadPlannerState(), []);
  const initialSlot = useMemo(() => getInitialScheduleSlot(), []);

  const initialCity: CityId =
    savedPlanner?.city === 'omsk' || savedPlanner?.city === 'spb'
      ? savedPlanner.city
      : loadStoredCity();

  const initialCityCfg = CITIES[initialCity];
  const initialStartStop =
    savedPlanner?.startStopId &&
    initialCityCfg.stops.some((s) => s.id === savedPlanner.startStopId)
      ? savedPlanner.startStopId
      : initialCityCfg.defaultStartStopId;

  const [city, setCity] = useState<CityId>(initialCity);
  const [theme, setTheme] = useState<AppTheme>(() => loadStoredTheme());
  const [accentSeed, setAccentSeed] = useState<AccentSeed>(() => loadStoredAccent());
  const [activeTab, setActiveTab] = useState<ActiveTab>('planner');
  const [plannerSubView, setPlannerSubView] = useState<PlannerSubView>('params');
  const [mobileRouteSubView, setMobileRouteSubView] = useState<MobileRouteSubView>('map');

  const [showSplash, setShowSplash] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false;
    return new URLSearchParams(window.location.search).get('autostart') !== '1';
  });
  const [previousSeenVersion, setPreviousSeenVersion] = useState<string | null>(() =>
    loadLastSeenAppVersion()
  );
  const [onboardingOpen, setOnboardingOpen] = useState<boolean>(() => !loadOnboardingDone());
  const [changelogRelease, setChangelogRelease] = useState<ChangelogRelease>(LATEST_CHANGELOG);
  const [changelogOpen, setChangelogOpen] = useState<boolean>(false);

  const checkAppVersion = useCallback(() => {
    if (typeof window === 'undefined') return;
    const isAutostart = new URLSearchParams(window.location.search).get('autostart') === '1';
    const result = checkAppVersionAgainstStorage({
      onboardingDone: loadOnboardingDone(),
      isAutostart,
    });
    setPreviousSeenVersion(result.storedVersion);
    setChangelogRelease(result.release);
    if (result.shouldShowChangelog) {
      setChangelogOpen(true);
    }
  }, []);

  useEffect(() => {
    checkAppVersion();
  }, [checkAppVersion]);
  const [settingsOpen, setSettingsOpen] = useState<boolean>(false);
  const [helpOpen, setHelpOpen] = useState<boolean>(false);
  const [liveTripOpen, setLiveTripOpen] = useState<boolean>(false);
  const [postcardOpen, setPostcardOpen] = useState<boolean>(false);

  const [startStopId, setStartStopId] = useState<string>(initialStartStop);
  const [destinationStopId, setDestinationStopId] = useState<string | null>(
    savedPlanner?.destinationStopId ?? null
  );
  const [destinationAddressLabel, setDestinationAddressLabel] = useState<string>(
    savedPlanner?.destinationAddressLabel ?? ''
  );
  const [themeFilter, setThemeFilter] = useState<RouteThemeFilter>(
    savedPlanner?.themeFilter ?? 'any'
  );
  const [durationPreset, setDurationPreset] = useState<DurationPreset>(
    savedPlanner?.durationPreset ?? 'medium'
  );
  const [timeMode, setTimeMode] = useState<TimePlanningMode>(
    savedPlanner?.timeMode === 'deadline' ? 'deadline' : 'duration'
  );
  const [deadlineMinutes, setDeadlineMinutes] = useState<number>(
    typeof savedPlanner?.deadlineMinutes === 'number'
      ? savedPlanner.deadlineMinutes
      : 18 * 60
  );
  const [dayOfWeek, setDayOfWeek] = useState<number>(
    typeof savedPlanner?.dayOfWeek === 'number' ? savedPlanner.dayOfWeek : initialSlot.dayOfWeek
  );
  const [timeMinutes, setTimeMinutes] = useState<number>(
    typeof savedPlanner?.timeMinutes === 'number'
      ? savedPlanner.timeMinutes
      : initialSlot.timeMinutes
  );
  const [allowFallbackTransit, setAllowFallbackTransit] = useState<boolean>(
    savedPlanner?.allowFallbackTransit ?? false
  );
  const [includePlanned2027, setIncludePlanned2027] = useState<boolean>(
    savedPlanner?.includePlanned2027 ?? true
  );
  const [modeBalance, setModeBalance] = useState<ModeBalance>(() =>
    savedPlanner?.modeBalance
      ? normalizeModeBalance(savedPlanner.modeBalance)
      : { ...DEFAULT_MODE_BALANCE }
  );
  const [showAllNetwork, setShowAllNetwork] = useState<boolean>(
    savedPlanner?.showAllNetwork ?? true
  );
  const [loopMode, setLoopMode] = useState<boolean>(savedPlanner?.loopMode ?? false);
  const [weatherMode, setWeatherMode] = useState<WeatherMode>(
    savedPlanner?.weatherMode ?? 'clear'
  );
  const [preferSpecialFleet, setPreferSpecialFleet] = useState<boolean>(
    savedPlanner?.preferSpecialFleet ?? false
  );
  const [mapPickMode, setMapPickMode] = useState<'start' | 'destination' | null>(null);
  const [selectedAlternativeIdx, setSelectedAlternativeIdx] = useState<number>(0);

  const [onlyWithPhotos, setOnlyWithPhotos] = useState<boolean>(
    savedPlanner?.onlyWithPhotos ?? true
  );
  const [landmarkCategories, setLandmarkCategories] = useState<LandmarkCategory[]>(
    Array.isArray(savedPlanner?.landmarkCategories) ? savedPlanner.landmarkCategories : []
  );
  const [selectedLandmarkId, setSelectedLandmarkId] = useState<string | null>(null);

  const [savedRoutes, setSavedRoutes] = useState<SavedRoute[]>(() => loadSavedRoutes());
  const [tripHistory, setTripHistory] = useState<TripHistoryEntry[]>(() => loadTripHistory());
  const [photoReminderMinutes, setPhotoReminderMinutes] = useState<number>(() =>
    loadPhotoReminderMinutes()
  );

  const [geoStatus, setGeoStatus] = useState<{
    state: 'idle' | 'loading' | 'error' | 'ok';
    message?: string;
  }>({ state: 'idle' });
  const [cityDetection, setCityDetection] = useState<CityDetectionResult | null>(null);
  const [mismatchDismissed, setMismatchDismissed] = useState<boolean>(false);

  const cityCfg = CITIES[city];

  const visibleStops = useMemo(
    () => (includePlanned2027 ? cityCfg.stops : cityCfg.stops.filter((s) => !s.planned2027)),
    [cityCfg.stops, includePlanned2027]
  );

  const visibleRoutes = useMemo(() => {
    if (includePlanned2027) return cityCfg.routes;
    const stopSet = new Set(visibleStops.map((s) => s.id));
    return cityCfg.routes.filter((r) => !r.planned2027 && r.stopIds.every((id) => stopSet.has(id)));
  }, [cityCfg.routes, includePlanned2027, visibleStops]);

  useEffect(() => {
    if (!visibleStops.some((s) => s.id === startStopId)) {
      setStartStopId(cityCfg.defaultStartStopId);
    }
    if (destinationStopId && !visibleStops.some((s) => s.id === destinationStopId)) {
      setDestinationStopId(null);
      setDestinationAddressLabel('');
    }
  }, [visibleStops, startStopId, destinationStopId, cityCfg.defaultStartStopId]);

  const [outcome, setOutcome] = useState<GenerationOutcome | null>(() =>
    generateTramAdventure({
      city: initialCity,
      stops: initialCityCfg.stops,
      routes: initialCityCfg.routes,
      landmarks: initialCityCfg.landmarks,
      startStopId: initialStartStop,
      destinationStopId: savedPlanner?.destinationStopId ?? null,
      theme: savedPlanner?.themeFilter ?? 'any',
      durationPreset: savedPlanner?.durationPreset ?? 'medium',
      timeMode: savedPlanner?.timeMode === 'deadline' ? 'deadline' : 'duration',
      deadlineMinutes:
        typeof savedPlanner?.deadlineMinutes === 'number'
          ? savedPlanner.deadlineMinutes
          : 18 * 60,
      includePlanned2027: savedPlanner?.includePlanned2027 ?? true,
      dayOfWeek:
        typeof savedPlanner?.dayOfWeek === 'number'
          ? savedPlanner.dayOfWeek
          : initialSlot.dayOfWeek,
      timeMinutes:
        typeof savedPlanner?.timeMinutes === 'number'
          ? savedPlanner.timeMinutes
          : initialSlot.timeMinutes,
      allowFallbackTransit: savedPlanner?.allowFallbackTransit ?? false,
      modeBalance: savedPlanner?.modeBalance
        ? normalizeModeBalance(savedPlanner.modeBalance)
        : DEFAULT_MODE_BALANCE,
      loopMode: savedPlanner?.loopMode ?? false,
      weatherMode: savedPlanner?.weatherMode ?? 'clear',
      preferSpecialFleet: savedPlanner?.preferSpecialFleet ?? false,
      seed: 2026,
    })
  );

  // Preserve mobile 1st-tab scroll position across tab navigation
  const mobileSearchScrollRef = useRef<HTMLDivElement | null>(null);
  const mobileSearchSavedScrollTop = useRef<number>(0);

  useLayoutEffect(() => {
    if (activeTab === 'planner' && plannerSubView === 'params' && mobileSearchScrollRef.current) {
      mobileSearchScrollRef.current.scrollTop = mobileSearchSavedScrollTop.current;
    }
  }, [activeTab, plannerSubView]);

  // Persist 1st-tab planner selections to localStorage on every change
  useEffect(() => {
    persistCity(city);
    persistPlannerState({
      city,
      startStopId,
      destinationStopId,
      destinationAddressLabel,
      themeFilter,
      durationPreset,
      timeMode,
      deadlineMinutes,
      dayOfWeek,
      timeMinutes,
      allowFallbackTransit,
      includePlanned2027,
      modeBalance,
      showAllNetwork,
      loopMode,
      weatherMode,
      preferSpecialFleet,
      onlyWithPhotos,
      landmarkCategories,
    });
  }, [
    city,
    startStopId,
    destinationStopId,
    destinationAddressLabel,
    themeFilter,
    durationPreset,
    timeMode,
    deadlineMinutes,
    dayOfWeek,
    timeMinutes,
    allowFallbackTransit,
    includePlanned2027,
    modeBalance,
    showAllNetwork,
    loopMode,
    weatherMode,
    preferSpecialFleet,
    onlyWithPhotos,
    landmarkCategories,
  ]);

  // Sync theme & Material You accent seed to documentElement
  useEffect(() => {
    const root = document.documentElement;
    root.dataset.theme = theme;
    root.dataset.accent = accentSeed;
    if (theme === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
    persistTheme(theme);
    persistAccent(accentSeed);
  }, [theme, accentSeed]);

  const handleChangeCity = useCallback(
    (nextCity: CityId) => {
      const nextCfg = CITIES[nextCity];
      setCity(nextCity);
      persistCity(nextCity);
      setStartStopId(nextCfg.defaultStartStopId);
      setDestinationStopId(null);
      setDestinationAddressLabel('');
      setSelectedLandmarkId(null);
      setMapPickMode(null);
      setMismatchDismissed(false);
      setGeoStatus({ state: 'idle' });

      const nextOutcome = generateTramAdventure({
        city: nextCity,
        stops: nextCfg.stops,
        routes: nextCfg.routes,
        landmarks: nextCfg.landmarks,
        startStopId: nextCfg.defaultStartStopId,
        destinationStopId: null,
        theme: themeFilter,
        durationPreset,
        timeMode,
        deadlineMinutes,
        includePlanned2027,
        dayOfWeek,
        timeMinutes,
        allowFallbackTransit,
        modeBalance,
        loopMode,
        weatherMode,
        preferSpecialFleet,
      });
      setSelectedAlternativeIdx(0);
      setOutcome(nextOutcome);
    },
    [
      themeFilter,
      durationPreset,
      timeMode,
      deadlineMinutes,
      includePlanned2027,
      dayOfWeek,
      timeMinutes,
      allowFallbackTransit,
      modeBalance,
      loopMode,
      weatherMode,
      preferSpecialFleet,
    ]
  );

  const handleDetectLocation = useCallback(
    (autoSelectCity = false) => {
      if (!('geolocation' in navigator)) {
        setGeoStatus({
          state: 'error',
          message: 'Геолокация не поддерживается вашим браузером.',
        });
        return;
      }

      setGeoStatus({ state: 'loading' });
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const detection = detectCityFromCoords(pos.coords.latitude, pos.coords.longitude);
          setCityDetection(detection);
          setMismatchDismissed(false);

          if (autoSelectCity && detection.detected !== 'other') {
            const targetCity = detection.detected;
            const targetCfg = CITIES[targetCity];
            const nearest = findNearestStops(
              pos.coords.latitude,
              pos.coords.longitude,
              targetCfg.stops,
              1,
              false
            )[0];
            const chosenStart = nearest ? nearest.stop.id : targetCfg.defaultStartStopId;

            setCity(targetCity);
            persistCity(targetCity);
            setStartStopId(chosenStart);
            setDestinationStopId(null);
            setDestinationAddressLabel('');
            setSelectedLandmarkId(null);
            setMapPickMode(null);

            const nextOutcome = generateTramAdventure({
              city: targetCity,
              stops: targetCfg.stops,
              routes: targetCfg.routes,
              landmarks: targetCfg.landmarks,
              startStopId: chosenStart,
              destinationStopId: null,
              theme: themeFilter,
              durationPreset,
              timeMode,
              deadlineMinutes,
              includePlanned2027,
              dayOfWeek,
              timeMinutes,
              allowFallbackTransit,
              modeBalance,
              loopMode,
              weatherMode,
              preferSpecialFleet,
            });
            setSelectedAlternativeIdx(0);
            setOutcome(nextOutcome);

            setGeoStatus({
              state: 'ok',
              message: nearest
                ? `Город выбран автоматически: ${targetCfg.name} · ближайшая остановка: ${nearest.stop.name} (${nearest.distanceKm.toFixed(1)} км)`
                : `Город выбран автоматически: ${targetCfg.name}.`,
            });
            return;
          }

          if (detection.detected === city) {
            const nearest = findNearestStops(
              pos.coords.latitude,
              pos.coords.longitude,
              CITIES[city].stops,
              1,
              false
            )[0];
            if (nearest) {
              setStartStopId(nearest.stop.id);
              setGeoStatus({
                state: 'ok',
                message: `Ближайшая остановка: ${nearest.stop.name} (${nearest.distanceKm.toFixed(1)} км)`,
              });
            }
          } else if (detection.detected === 'other') {
            setGeoStatus({
              state: 'ok',
              message: autoSelectCity
                ? 'Координаты вне СПб и Омска — выберите город вручную на следующем шаге.'
                : `Координаты вне СПб и Омска — выбрана сеть города ${CITIES[city].name}.`,
            });
          } else {
            setGeoStatus({
              state: 'ok',
              message: `Обнаружен другой город: ${CITIES[detection.detected].name}. Вы можете переключить сеть выше.`,
            });
          }
        },
        () => {
          setGeoStatus({
            state: 'error',
            message: 'Доступ к геопозиции отклонён — выберите город и остановку вручную.',
          });
        },
        { timeout: 6000 }
      );
    },
    [
      city,
      themeFilter,
      durationPreset,
      timeMode,
      deadlineMinutes,
      includePlanned2027,
      dayOfWeek,
      timeMinutes,
      allowFallbackTransit,
      modeBalance,
      loopMode,
      weatherMode,
      preferSpecialFleet,
    ]
  );

  // Passive city-mismatch check on entry if onboarding is finished & permission already granted
  useEffect(() => {
    if (onboardingOpen) return;
    if (typeof navigator === 'undefined' || !('permissions' in navigator)) return;
    navigator.permissions
      .query({ name: 'geolocation' as PermissionName })
      .then((res) => {
        if (res.state === 'granted') {
          handleDetectLocation(false);
        }
      })
      .catch(() => {
        // Ignore browsers without permissions.query support
      });
  }, [onboardingOpen, handleDetectLocation]);

  const handleExportBackup = useCallback((): string => {
    const filename = buildBackupFilename();
    const envelope = createBackupPayload({
      settings: {
        city,
        theme,
        accentSeed,
        photoReminderMinutes,
        onboardingDone: !onboardingOpen,
      },
      plannerState: {
        city,
        startStopId,
        destinationStopId,
        destinationAddressLabel,
        themeFilter,
        durationPreset,
        timeMode,
        deadlineMinutes,
        dayOfWeek,
        timeMinutes,
        allowFallbackTransit,
        includePlanned2027,
        modeBalance,
        showAllNetwork,
        loopMode,
        weatherMode,
        preferSpecialFleet,
        onlyWithPhotos,
        landmarkCategories,
      },
      savedRoutes,
      tripHistory,
    });
    const jsonText = serializeBackup(envelope);
    const blob = new Blob([jsonText], { type: 'application/octet-stream' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    return filename;
  }, [
    city,
    theme,
    accentSeed,
    photoReminderMinutes,
    onboardingOpen,
    startStopId,
    destinationStopId,
    destinationAddressLabel,
    themeFilter,
    durationPreset,
    timeMode,
    deadlineMinutes,
    dayOfWeek,
    timeMinutes,
    allowFallbackTransit,
    includePlanned2027,
    modeBalance,
    showAllNetwork,
    loopMode,
    weatherMode,
    preferSpecialFleet,
    onlyWithPhotos,
    landmarkCategories,
    savedRoutes,
    tripHistory,
  ]);

  const handleImportBackupFile = useCallback(
    async (
      file: File
    ): Promise<{ ok: true; message: string } | { ok: false; error: string }> => {
      if (!isAcceptedBackupFilename(file.name)) {
        return {
          ok: false,
          error: 'Выберите файл с расширением .relsohod (или .relsokhod).',
        };
      }
      if (file.size > MAX_BACKUP_BYTES) {
        return {
          ok: false,
          error: 'Файл резервной копии слишком большой (более 35 МБ).',
        };
      }

      try {
        const text = await file.text();
        const result = parseAndValidateBackup(text);
        if (!result.ok) {
          return { ok: false, error: result.error };
        }

        const { settings, plannerState, savedRoutes: nextSaved, tripHistory: nextHistory, summary } =
          result.data;

        setCity(settings.city);
        persistCity(settings.city);
        setTheme(settings.theme);
        persistTheme(settings.theme);
        setAccentSeed(settings.accentSeed);
        persistAccent(settings.accentSeed);
        setPhotoReminderMinutes(settings.photoReminderMinutes);
        persistPhotoReminderMinutes(settings.photoReminderMinutes);

        setStartStopId(plannerState.startStopId);
        setDestinationStopId(plannerState.destinationStopId);
        setDestinationAddressLabel(plannerState.destinationAddressLabel);
        setThemeFilter(plannerState.themeFilter);
        setDurationPreset(plannerState.durationPreset);
        setTimeMode(plannerState.timeMode);
        setDeadlineMinutes(plannerState.deadlineMinutes);
        setDayOfWeek(plannerState.dayOfWeek);
        setTimeMinutes(plannerState.timeMinutes);
        setAllowFallbackTransit(plannerState.allowFallbackTransit);
        setIncludePlanned2027(plannerState.includePlanned2027);
        setModeBalance(plannerState.modeBalance);
        setShowAllNetwork(plannerState.showAllNetwork);
        setLoopMode(plannerState.loopMode);
        setWeatherMode(plannerState.weatherMode);
        setPreferSpecialFleet(plannerState.preferSpecialFleet);
        setOnlyWithPhotos(plannerState.onlyWithPhotos);
        setLandmarkCategories(plannerState.landmarkCategories);
        persistPlannerState(plannerState);

        setSavedRoutes(nextSaved);
        persistSavedRoutes(nextSaved);
        setTripHistory(nextHistory);
        persistTripHistory(nextHistory);

        const nextCfg = CITIES[settings.city];
        const nextOutcome = generateTramAdventure({
          city: settings.city,
          stops: nextCfg.stops,
          routes: nextCfg.routes,
          landmarks: nextCfg.landmarks,
          startStopId: plannerState.startStopId,
          destinationStopId: plannerState.destinationStopId,
          theme: plannerState.themeFilter,
          durationPreset: plannerState.durationPreset,
          timeMode: plannerState.timeMode,
          deadlineMinutes: plannerState.deadlineMinutes,
          includePlanned2027: plannerState.includePlanned2027,
          dayOfWeek: plannerState.dayOfWeek,
          timeMinutes: plannerState.timeMinutes,
          allowFallbackTransit: plannerState.allowFallbackTransit,
          modeBalance: plannerState.modeBalance,
          loopMode: plannerState.loopMode,
          weatherMode: plannerState.weatherMode,
          preferSpecialFleet: plannerState.preferSpecialFleet,
        });
        setSelectedAlternativeIdx(0);
        setOutcome(nextOutcome);
        setMismatchDismissed(false);

        return {
          ok: true,
          message: `Восстановлено (${CITIES[summary.city].shortName}): маршрутов — ${summary.savedRoutesCount}, поездок в паспорте — ${summary.historyCount}, фото — ${summary.photosCount}.`,
        };
      } catch {
        return { ok: false, error: 'Не удалось прочитать выбранный файл.' };
      }
    },
    []
  );

  const handleGenerate = useCallback(
    (overrideFallback?: boolean, overrideDay?: number, overrideTime?: number) => {
      triggerHaptic('medium');
      setSelectedLandmarkId(null);
      setSelectedAlternativeIdx(0);
      const nextOutcome = generateTramAdventure({
        city,
        stops: cityCfg.stops,
        routes: cityCfg.routes,
        landmarks: cityCfg.landmarks,
        startStopId,
        destinationStopId,
        theme: themeFilter,
        durationPreset,
        timeMode,
        deadlineMinutes,
        includePlanned2027,
        dayOfWeek: overrideDay ?? dayOfWeek,
        timeMinutes: overrideTime ?? timeMinutes,
        allowFallbackTransit: overrideFallback ?? allowFallbackTransit,
        modeBalance,
        loopMode,
        weatherMode,
        preferSpecialFleet,
      });
      setOutcome(nextOutcome);
      setPlannerSubView('route');
      setMobileRouteSubView(nextOutcome.status === 'ok' ? 'map' : 'steps');
    },
    [
      city,
      cityCfg,
      startStopId,
      destinationStopId,
      themeFilter,
      durationPreset,
      timeMode,
      deadlineMinutes,
      includePlanned2027,
      dayOfWeek,
      timeMinutes,
      allowFallbackTransit,
      modeBalance,
      loopMode,
      weatherMode,
      preferSpecialFleet,
    ]
  );

  const activeTrip = useMemo(() => {
    if (outcome?.status !== 'ok') return null;
    const alts = outcome.trip.alternatives;
    if (alts && alts.length > 0) {
      return alts[Math.min(selectedAlternativeIdx, alts.length - 1)] ?? outcome.trip;
    }
    return outcome.trip;
  }, [outcome, selectedAlternativeIdx]);

  const handleExtendLastSegment = useCallback(() => {
    if (!activeTrip) return;
    const extended = extendTripLastSegment({
      trip: activeTrip,
      stops: cityCfg.stops,
      routes: cityCfg.routes,
      landmarks: cityCfg.landmarks,
      allowFallbackTransit,
      includePlanned2027,
      weatherMode,
      preferSpecialFleet,
    });
    if (extended) {
      triggerHaptic('success');
      setSelectedAlternativeIdx(0);
      setOutcome({ status: 'ok', trip: extended });
    }
  }, [activeTrip, cityCfg, allowFallbackTransit, includePlanned2027, weatherMode, preferSpecialFleet]);

  const handleToggleMapPickMode = useCallback((mode: 'start' | 'destination' | null) => {
    setMapPickMode(mode);
    if (mode !== null) {
      setPlannerSubView('route');
      setMobileRouteSubView('map');
    }
  }, []);

  const handlePickStopFromMap = useCallback(
    (stopId: string) => {
      if (mapPickMode === 'destination') {
        if (stopId !== startStopId) {
          setDestinationStopId(stopId);
          setDestinationAddressLabel('');
          if (loopMode) setLoopMode(false);
        }
        setMapPickMode(null);
      } else {
        setStartStopId(stopId);
        if (destinationStopId === stopId) {
          setDestinationStopId(null);
          setDestinationAddressLabel('');
        }
        setMapPickMode(null);
      }
      setPlannerSubView('params');
    },
    [mapPickMode, startStopId, destinationStopId, loopMode]
  );

  const handleSaveTrip = useCallback(
    (trip: GeneratedTrip, customTitle: string, note: string) => {
      triggerHaptic('success');
      const next: SavedRoute = {
        id: `saved-${trip.id}-${Date.now()}`,
        customTitle,
        note,
        savedAt: new Date().toISOString(),
        trip,
      };
      setSavedRoutes((prev) => {
        const updated = [next, ...prev];
        persistSavedRoutes(updated);
        return updated;
      });
    },
    []
  );

  const handleDeleteSavedRoute = useCallback((id: string) => {
    setSavedRoutes((prev) => {
      const updated = prev.filter((r) => r.id !== id);
      persistSavedRoutes(updated);
      return updated;
    });
  }, []);

  const handleCompleteLiveTrip = useCallback(
    (trip: GeneratedTrip, photos: TripPhotoMemory[]) => {
      const entry: TripHistoryEntry = {
        id: `hist-${trip.id}-${Date.now()}`,
        city: trip.city ?? city,
        completedAt: new Date().toISOString(),
        trip,
        photos,
      };
      setTripHistory((prev) => {
        const updated = [entry, ...prev];
        persistTripHistory(updated);
        return updated;
      });
      setLiveTripOpen(false);
      setActiveTab('favorites');
    },
    [city]
  );

  const handleAddPhotoToHistory = useCallback((entryId: string, photo: TripPhotoMemory) => {
    setTripHistory((prev) => {
      const updated = prev.map((e) =>
        e.id === entryId ? { ...e, photos: [photo, ...e.photos] } : e
      );
      persistTripHistory(updated);
      return updated;
    });
  }, []);

  const handleDeleteHistoryEntry = useCallback((entryId: string) => {
    setTripHistory((prev) => {
      const updated = prev.filter((e) => e.id !== entryId);
      persistTripHistory(updated);
      return updated;
    });
  }, []);

  const handleOpenSavedRoute = useCallback((saved: SavedRoute) => {
    const tripCity = saved.trip.city ?? 'spb';
    setCity(tripCity);
    setStartStopId(saved.trip.startStop.id);
    setDestinationStopId(saved.trip.endStop.id);
    setDestinationAddressLabel('');
    setSelectedAlternativeIdx(0);
    setOutcome({ status: 'ok', trip: saved.trip });
    setSelectedLandmarkId(null);
    setActiveTab('planner');
    setPlannerSubView('route');
    setMobileRouteSubView('map');
  }, []);

  const handleOpenHistoryTrip = useCallback((entry: TripHistoryEntry) => {
    const tripCity = entry.city ?? entry.trip.city ?? 'spb';
    setCity(tripCity);
    setStartStopId(entry.trip.startStop.id);
    setDestinationStopId(entry.trip.endStop.id);
    setDestinationAddressLabel('');
    setSelectedAlternativeIdx(0);
    setOutcome({ status: 'ok', trip: entry.trip });
    setSelectedLandmarkId(null);
    setActiveTab('planner');
    setPlannerSubView('route');
    setMobileRouteSubView('map');
  }, []);

  const handleToggleLandmarkCategory = useCallback((cat: LandmarkCategory) => {
    setLandmarkCategories((prev) =>
      prev.includes(cat) ? prev.filter((c) => c !== cat) : [...prev, cat]
    );
  }, []);

  const handleStartFromDirectory = useCallback(
    (stopId: string) => {
      setStartStopId(stopId);
      setDestinationStopId(null);
      setDestinationAddressLabel('');
      setSelectedAlternativeIdx(0);
      setActiveTab('planner');
      setPlannerSubView('route');
      setMobileRouteSubView('map');
      setOutcome(
        generateTramAdventure({
          city,
          stops: cityCfg.stops,
          routes: cityCfg.routes,
          landmarks: cityCfg.landmarks,
          startStopId: stopId,
          destinationStopId: null,
          theme: themeFilter,
          durationPreset,
          timeMode,
          deadlineMinutes,
          includePlanned2027,
          dayOfWeek,
          timeMinutes,
          allowFallbackTransit,
          modeBalance,
          loopMode,
          weatherMode,
          preferSpecialFleet,
        })
      );
    },
    [
      city,
      cityCfg,
      themeFilter,
      durationPreset,
      timeMode,
      deadlineMinutes,
      includePlanned2027,
      dayOfWeek,
      timeMinutes,
      allowFallbackTransit,
      modeBalance,
      loopMode,
      weatherMode,
      preferSpecialFleet,
    ]
  );

  const mapLandmarks = useMemo(() => {
    if (activeTrip) {
      return filterLandmarks(activeTrip.landmarks, landmarkCategories, onlyWithPhotos);
    }
    return filterLandmarks(cityCfg.landmarks, landmarkCategories, onlyWithPhotos);
  }, [activeTrip, cityCfg.landmarks, landmarkCategories, onlyWithPhotos]);

  const isCurrentTripSaved = useMemo(() => {
    if (!activeTrip) return false;
    return savedRoutes.some((s) => s.trip.id === activeTrip.id);
  }, [activeTrip, savedRoutes]);

  // Derive active mobile bottom tab
  const activeMobileTab: MobileNavTab = useMemo(() => {
    if (activeTab === 'favorites') return 'favorites';
    if (activeTab === 'directory') return 'directory';
    return plannerSubView === 'params' ? 'search' : 'route';
  }, [activeTab, plannerSubView]);

  const handleSelectMobileTab = useCallback(
    (tab: MobileNavTab) => {
      if (tab === 'favorites') {
        setActiveTab('favorites');
        return;
      }
      if (tab === 'directory') {
        setActiveTab('directory');
        return;
      }
      setActiveTab('planner');
      if (tab === 'search') {
        setPlannerSubView('params');
      } else {
        setPlannerSubView('route');
        if (outcome && outcome.status !== 'ok') {
          setMobileRouteSubView('steps');
        }
      }
    },
    [outcome]
  );

  const isMobileMapVisible =
    activeTab === 'planner' &&
    plannerSubView !== 'params' &&
    mobileRouteSubView === 'map';

  return (
    <MotionConfig reducedMotion="user">
      <div
        className={`flex min-h-screen flex-col transition-colors ${
          activeTab === 'planner' ? 'md:h-screen md:overflow-hidden' : ''
        }`}
        style={{
          backgroundColor: 'var(--bg-canvas)',
          color: 'var(--text-primary)',
        }}
      >

        <TopBar
          city={city}
          activeTab={activeTab}
          onSelectTab={(tab) => setActiveTab(tab)}
          favoritesCount={savedRoutes.length}
          theme={theme}
          onChangeTheme={setTheme}
          onOpenSettings={() => setSettingsOpen(true)}
        />

        <CityMismatchBanner
          selectedCity={city}
          detection={cityDetection}
          dismissed={mismatchDismissed}
          onSwitchCity={handleChangeCity}
          onDismiss={() => setMismatchDismissed(true)}
        />

        {activeTab === 'planner' && (
          <main className="flex min-h-0 flex-1 flex-col md:flex-row md:overflow-hidden">
            {/* Desktop Left Column: Separated 3-tab Sidebar */}
            <div className="hidden min-h-0 md:flex md:h-full">
              <RoutePlannerPanel
                city={city}
                subView={plannerSubView}
                onChangeSubView={setPlannerSubView}
                stops={visibleStops}
                landmarks={cityCfg.landmarks}
                startStopId={startStopId}
                onChangeStartStop={setStartStopId}
                destinationStopId={destinationStopId}
                destinationAddressLabel={destinationAddressLabel}
                onChangeDestinationStop={(id, label) => {
                  setDestinationStopId(id);
                  if (label !== undefined) setDestinationAddressLabel(label);
                }}
                themeFilter={themeFilter}
                onChangeThemeFilter={setThemeFilter}
                durationPreset={durationPreset}
                onChangeDurationPreset={setDurationPreset}
                timeMode={timeMode}
                onChangeTimeMode={setTimeMode}
                deadlineMinutes={deadlineMinutes}
                onChangeDeadlineMinutes={setDeadlineMinutes}
                dayOfWeek={dayOfWeek}
                onChangeDayOfWeek={setDayOfWeek}
                timeMinutes={timeMinutes}
                onChangeTimeMinutes={setTimeMinutes}
                allowFallbackTransit={allowFallbackTransit}
                onChangeAllowFallbackTransit={setAllowFallbackTransit}
                includePlanned2027={includePlanned2027}
                onChangeIncludePlanned2027={setIncludePlanned2027}
                modeBalance={modeBalance}
                onChangeModeBalance={setModeBalance}
                showAllNetwork={showAllNetwork}
                onChangeShowAllNetwork={setShowAllNetwork}
                loopMode={loopMode}
                onChangeLoopMode={setLoopMode}
                weatherMode={weatherMode}
                onChangeWeatherMode={setWeatherMode}
                preferSpecialFleet={preferSpecialFleet}
                onChangePreferSpecialFleet={setPreferSpecialFleet}
                mapPickMode={mapPickMode}
                onToggleMapPickMode={setMapPickMode}
                onDetectLocation={handleDetectLocation}
                geoStatus={geoStatus}
                onGenerate={handleGenerate}
                onExtendLastSegment={handleExtendLastSegment}
                onStartLiveTrip={() => setLiveTripOpen(true)}
                onOpenPostcard={() => setPostcardOpen(true)}
                outcome={outcome}
                activeTrip={activeTrip}
                selectedAlternativeIdx={selectedAlternativeIdx}
                onSelectAlternative={setSelectedAlternativeIdx}
                onSaveTrip={handleSaveTrip}
                isCurrentTripSaved={isCurrentTripSaved}
                selectedLandmarkId={selectedLandmarkId}
                onSelectLandmark={(lm) => setSelectedLandmarkId(lm.id)}
                onlyWithPhotos={onlyWithPhotos}
                onChangeOnlyWithPhotos={setOnlyWithPhotos}
                landmarkCategories={landmarkCategories}
                onToggleLandmarkCategory={handleToggleLandmarkCategory}
              />
            </div>

            {/* Mobile Separated Search View (preserves 1st-tab scroll & selections across tab switches) */}
            {plannerSubView === 'params' && (
              <div
                ref={mobileSearchScrollRef}
                onScroll={(e) => {
                  mobileSearchSavedScrollTop.current = e.currentTarget.scrollTop;
                }}
                className="flex-1 overflow-y-auto px-4 pt-4 pb-24 md:hidden"
              >
                <PlannerParamsView
                  city={city}
                  stops={visibleStops}
                  landmarks={cityCfg.landmarks}
                  startStopId={startStopId}
                  onChangeStartStop={setStartStopId}
                  destinationStopId={destinationStopId}
                  destinationAddressLabel={destinationAddressLabel}
                  onChangeDestinationStop={(id, label) => {
                    setDestinationStopId(id);
                    if (label !== undefined) setDestinationAddressLabel(label);
                  }}
                  themeFilter={themeFilter}
                  onChangeThemeFilter={setThemeFilter}
                  durationPreset={durationPreset}
                  onChangeDurationPreset={setDurationPreset}
                  timeMode={timeMode}
                  onChangeTimeMode={setTimeMode}
                  deadlineMinutes={deadlineMinutes}
                  onChangeDeadlineMinutes={setDeadlineMinutes}
                  dayOfWeek={dayOfWeek}
                  onChangeDayOfWeek={setDayOfWeek}
                  timeMinutes={timeMinutes}
                  onChangeTimeMinutes={setTimeMinutes}
                  allowFallbackTransit={allowFallbackTransit}
                  onChangeAllowFallbackTransit={setAllowFallbackTransit}
                  includePlanned2027={includePlanned2027}
                  onChangeIncludePlanned2027={setIncludePlanned2027}
                  modeBalance={modeBalance}
                  onChangeModeBalance={setModeBalance}
                  showAllNetwork={showAllNetwork}
                  onChangeShowAllNetwork={setShowAllNetwork}
                  loopMode={loopMode}
                  onChangeLoopMode={setLoopMode}
                  weatherMode={weatherMode}
                  onChangeWeatherMode={setWeatherMode}
                  preferSpecialFleet={preferSpecialFleet}
                  onChangePreferSpecialFleet={setPreferSpecialFleet}
                  mapPickMode={mapPickMode}
                  onToggleMapPickMode={handleToggleMapPickMode}
                  onDetectLocation={handleDetectLocation}
                  geoStatus={geoStatus}
                  onGenerate={() => handleGenerate()}
                />
              </div>
            )}

            {/* Mobile Route Sub-Bar (Map | Steps | Places) */}
            {plannerSubView !== 'params' && (
              <div
                className="border-b px-3 py-2 md:hidden"
                style={{
                  backgroundColor: 'var(--bg-surface)',
                  borderColor: 'var(--border-hairline)',
                }}
              >
                <div
                  role="tablist"
                  aria-label="Вид маршрута"
                  className="grid grid-cols-3 gap-1 rounded-full p-1"
                  style={{
                    backgroundColor: 'var(--md-surface-container)',
                  }}
                >
                  <button
                    type="button"
                    role="tab"
                    aria-selected={mobileRouteSubView === 'map'}
                    onClick={() => {
                      triggerHaptic('light');
                      setMobileRouteSubView('map');
                    }}
                    className="m3-tactile min-h-[36px] cursor-pointer rounded-full px-2 py-1 text-xs font-semibold"
                    style={{
                      backgroundColor:
                        mobileRouteSubView === 'map' ? 'var(--md-primary-container)' : 'transparent',
                      color:
                        mobileRouteSubView === 'map'
                          ? 'var(--md-on-primary-container)'
                          : 'var(--text-muted)',
                    }}
                  >
                    Карта
                  </button>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={mobileRouteSubView === 'steps'}
                    onClick={() => {
                      triggerHaptic('light');
                      setMobileRouteSubView('steps');
                    }}
                    className="m3-tactile min-h-[36px] cursor-pointer rounded-full px-2 py-1 text-xs font-semibold"
                    style={{
                      backgroundColor:
                        mobileRouteSubView === 'steps' ? 'var(--md-primary-container)' : 'transparent',
                      color:
                        mobileRouteSubView === 'steps'
                          ? 'var(--md-on-primary-container)'
                          : 'var(--text-muted)',
                    }}
                  >
                    Шаги{activeTrip ? ` (${activeTrip.segments.length})` : ''}
                  </button>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={mobileRouteSubView === 'places'}
                    onClick={() => {
                      triggerHaptic('light');
                      setMobileRouteSubView('places');
                    }}
                    className="m3-tactile min-h-[36px] cursor-pointer rounded-full px-2 py-1 text-xs font-semibold"
                    style={{
                      backgroundColor:
                        mobileRouteSubView === 'places' ? 'var(--md-primary-container)' : 'transparent',
                      color:
                        mobileRouteSubView === 'places'
                          ? 'var(--md-on-primary-container)'
                          : 'var(--text-muted)',
                    }}
                  >
                    Места{activeTrip ? ` (${mapLandmarks.length})` : ''}
                  </button>
                </div>
              </div>
            )}

            {/* Mobile Steps Sub-View */}
            {plannerSubView !== 'params' && mobileRouteSubView === 'steps' && (
              <div className="flex-1 overflow-y-auto px-4 pt-4 pb-24 md:hidden">
                <PlannerItineraryView
                  city={city}
                  outcome={outcome}
                  activeTrip={activeTrip}
                  selectedAlternativeIdx={selectedAlternativeIdx}
                  onSelectAlternative={setSelectedAlternativeIdx}
                  onGenerate={handleGenerate}
                  onExtendLastSegment={handleExtendLastSegment}
                  onStartLiveTrip={() => setLiveTripOpen(true)}
                  onOpenPostcard={() => setPostcardOpen(true)}
                  onChangeAllowFallbackTransit={setAllowFallbackTransit}
                  onChangeDayOfWeek={setDayOfWeek}
                  onChangeTimeMinutes={setTimeMinutes}
                  onSaveTrip={handleSaveTrip}
                  isCurrentTripSaved={isCurrentTripSaved}
                  onEditParams={() => setPlannerSubView('params')}
                />
              </div>
            )}

            {/* Mobile Places Sub-View */}
            {plannerSubView !== 'params' && mobileRouteSubView === 'places' && (
              <div className="flex-1 overflow-y-auto px-4 pt-4 pb-24 md:hidden">
                <PlannerPlacesView
                  landmarks={mapLandmarks}
                  selectedLandmarkId={selectedLandmarkId}
                  onSelectLandmark={(lm) => {
                    setSelectedLandmarkId(lm.id);
                    setMobileRouteSubView('map');
                  }}
                  onlyWithPhotos={onlyWithPhotos}
                  onChangeOnlyWithPhotos={setOnlyWithPhotos}
                  landmarkCategories={landmarkCategories}
                  onToggleLandmarkCategory={handleToggleLandmarkCategory}
                />
              </div>
            )}

            {/* Single Shared Map Canvas */}
            <div
              className={`${
                isMobileMapVisible ? 'flex flex-col flex-1 md:flex-row' : 'hidden md:flex md:flex-1'
              } min-h-0 w-full`}
            >
              <section
                aria-label="Карта маршрута"
                className={`relative isolate z-0 w-full shrink-0 md:block md:h-full md:flex-1 md:min-h-0 ${
                  isMobileMapVisible
                    ? activeTrip && !mapPickMode
                      ? 'block h-[calc(100dvh-252px)] min-h-[320px]'
                      : 'block h-[calc(100dvh-174px)] min-h-[360px]'
                    : 'hidden'
                }`}
              >
                <TramMap
                  city={city}
                  cityCenter={cityCfg.center}
                  stops={visibleStops}
                  routes={visibleRoutes}
                  trip={activeTrip}
                  landmarks={mapLandmarks}
                  showAllNetwork={showAllNetwork}
                  startStopId={startStopId}
                  destinationStopId={destinationStopId}
                  mapPickMode={mapPickMode}
                  selectedLandmarkId={selectedLandmarkId}
                  theme={theme}
                  onSelectLandmark={(lm: Landmark | null) =>
                    setSelectedLandmarkId(lm ? lm.id : null)
                  }
                  onPickStop={handlePickStopFromMap}
                />
              </section>

              {/* Docked Route Summary Strip Below Map on Mobile */}
              {isMobileMapVisible && activeTrip && !mapPickMode && (
                <div
                  className="flex items-center justify-between gap-2 border-t px-3 py-2.5 pb-20 md:hidden"
                  style={{
                    backgroundColor: 'var(--bg-surface)',
                    borderColor: 'var(--border-hairline)',
                  }}
                >
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-xs font-bold" style={{ color: 'var(--text-primary)' }}>
                      {activeTrip.startStop.name.split('(')[0].trim()} →{' '}
                      {activeTrip.endStop.name.split('(')[0].trim()}
                    </div>
                    <div className="font-mono-num mt-0.5 text-[11px]" style={{ color: 'var(--text-muted)' }}>
                      ~{activeTrip.totalDurationMinutes} мин · {activeTrip.tramLinesUsed.join(' → ')}
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      type="button"
                      aria-label="Другой маршрут"
                      onClick={() => handleGenerate()}
                      className="m3-tactile flex min-h-[38px] min-w-[38px] cursor-pointer items-center justify-center rounded-full"
                      style={{
                        backgroundColor: 'var(--md-surface-container-high)',
                        color: 'var(--text-primary)',
                      }}
                    >
                      <RefreshCw className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        triggerHaptic('light');
                        setMobileRouteSubView('steps');
                      }}
                      className="m3-tactile inline-flex min-h-[38px] cursor-pointer items-center gap-1 rounded-full px-3.5 py-1.5 text-xs font-semibold"
                      style={{
                        backgroundColor: 'var(--md-primary-container)',
                        color: 'var(--md-on-primary-container)',
                      }}
                    >
                      <ListOrdered className="h-3.5 w-3.5" />
                      Шаги
                    </button>
                  </div>
                </div>
              )}
            </div>
          </main>
        )}

        {activeTab === 'favorites' && (
          <main className="flex-1">
            <FavoritesView
              city={city}
              savedRoutes={savedRoutes}
              history={tripHistory}
              onOpenRoute={handleOpenSavedRoute}
              onDeleteRoute={handleDeleteSavedRoute}
              onOpenHistoryTrip={handleOpenHistoryTrip}
              onAddPhotoToHistory={handleAddPhotoToHistory}
              onDeleteHistoryEntry={handleDeleteHistoryEntry}
              onGoToPlanner={() => {
                setActiveTab('planner');
                setPlannerSubView('params');
              }}
            />
          </main>
        )}

        {activeTab === 'directory' && (
          <main className="flex-1">
            <RoutesDirectoryView
              city={city}
              routes={cityCfg.routes}
              stops={cityCfg.stops}
              dayOfWeek={dayOfWeek}
              timeMinutes={timeMinutes}
              onStartFromRoute={handleStartFromDirectory}
            />
          </main>
        )}

        <OfflineIndicator />

        <MobileBottomNav
          city={city}
          activeMobileTab={activeMobileTab}
          onSelectMobileTab={handleSelectMobileTab}
          favoritesCount={savedRoutes.length}
        />

        <OnboardingModal
          isOpen={onboardingOpen}
          selectedCity={city}
          onSelectCity={handleChangeCity}
          geoStatus={geoStatus}
          cityDetection={cityDetection}
          onRequestLocation={() => handleDetectLocation(true)}
          onImportBackupFile={handleImportBackupFile}
          onComplete={() => {
            persistOnboardingDone(true);
            persistLastSeenAppVersion(CURRENT_APP_VERSION);
            setPreviousSeenVersion(CURRENT_APP_VERSION);
            setOnboardingOpen(false);
          }}
        />

        <SettingsModal
          isOpen={settingsOpen}
          onClose={() => setSettingsOpen(false)}
          city={city}
          onChangeCity={handleChangeCity}
          theme={theme}
          onChangeTheme={setTheme}
          accentSeed={accentSeed}
          onChangeAccentSeed={setAccentSeed}
          photoReminderMinutes={photoReminderMinutes}
          onChangePhotoReminderMinutes={(mins) => {
            setPhotoReminderMinutes(mins);
            persistPhotoReminderMinutes(mins);
          }}
          onExportBackup={handleExportBackup}
          onImportBackupFile={handleImportBackupFile}
          onOpenChangelog={() => setChangelogOpen(true)}
          onOpenHelp={() => setHelpOpen(true)}
          onRestartOnboarding={() => setOnboardingOpen(true)}
        />

        <HelpModal isOpen={helpOpen} onClose={() => setHelpOpen(false)} />

        <ChangelogModal
          isOpen={changelogOpen && !onboardingOpen}
          release={changelogRelease}
          previousVersion={previousSeenVersion}
          onAcknowledge={() => {
            persistLastSeenAppVersion(CURRENT_APP_VERSION);
            setPreviousSeenVersion(CURRENT_APP_VERSION);
            setChangelogOpen(false);
          }}
        />

        {liveTripOpen && activeTrip && (
          <LiveTripCompanion
            trip={activeTrip}
            city={city}
            photoReminderMinutes={photoReminderMinutes}
            onClose={() => setLiveTripOpen(false)}
            onCompleteTrip={handleCompleteLiveTrip}
          />
        )}

        <AnimatePresence>
          {postcardOpen && activeTrip && (
            <RoutePostcardModal
              key="route-postcard-modal"
              trip={activeTrip}
              city={city}
              onClose={() => setPostcardOpen(false)}
            />
          )}
        </AnimatePresence>

        <StartupSplash visible={showSplash} onFinish={() => setShowSplash(false)} />
      </div>
    </MotionConfig>
  );
}
