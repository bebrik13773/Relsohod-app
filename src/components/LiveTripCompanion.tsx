import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Bell,
  Bus,
  Camera,
  CheckCircle2,
  ChevronRight,
  ExternalLink,
  Flag,
  Footprints,
  Headphones,
  LocateFixed,
  Pause,
  Play,
  Square,
  TrainFront,
  TramFront,
  Volume2,
  VolumeX,
  X,
} from 'lucide-react';
import { CITIES } from '../data/cities';
import { CityId, GeneratedTrip, RouteSegment, TramStop, TripPhotoMemory } from '../types/tram';
import { matchAudioGuidesForTrip } from '../data/audioGuides';
import {
  buildTripAnnouncements,
  playTramChime,
  speakAnnouncement,
  stopAnnouncement,
} from '../utils/announcer';
import { formatMinutesToClock, getRouteTypeShortLabel } from '../utils/tramRouter';
import {
  LiveGeoCoords,
  computeSegmentLiveProgress,
  pluralizeStopsRu,
} from '../utils/liveTripProgress';
import { triggerHaptic } from '../utils/haptics';

interface LiveTripCompanionProps {
  trip: GeneratedTrip;
  city: CityId;
  photoReminderMinutes: number;
  onClose: () => void;
  onCompleteTrip: (trip: GeneratedTrip, photos: TripPhotoMemory[]) => void;
}

function getSegmentBrandColor(seg: RouteSegment): string {
  if (seg.kind === 'walk') return '#6366f1';
  if (seg.kind === 'fallback_transit') return '#16a34a';
  if (seg.route?.color) return seg.route.color;
  switch (seg.route?.type) {
    case 'bus':
      return '#22c55e';
    case 'trolleybus':
      return '#0ea5e9';
    case 'metro':
      return '#2563eb';
    case 'chizhik':
      return '#10b981';
    default:
      return '#16a34a';
  }
}

function getSegmentBadgeText(seg: RouteSegment): string {
  if (seg.kind === 'walk') return 'Пешком';
  if (seg.kind === 'fallback_transit') return 'Автобус';
  if (!seg.route) return 'Т';
  return seg.route.number.replace(/^А-/, '');
}

function getSegmentModeName(seg: RouteSegment): string {
  if (seg.kind === 'walk') return seg.durationMinutes >= 12 ? 'Пешая прогулка' : 'Пеший переход';
  if (seg.kind === 'fallback_transit') return seg.fallbackModeName ?? 'Автобус';
  switch (seg.route?.type) {
    case 'bus':
      return 'Автобус';
    case 'trolleybus':
      return 'Троллейбус';
    case 'metro':
      return 'Метро';
    case 'chizhik':
      return '«Чижик»';
    case 'retro':
      return 'Ретро-трамвай';
    case 'circular':
      return 'Кольцевой трамвай';
    default:
      return 'Трамвай';
  }
}

function SegmentModeIcon({ seg, className = 'h-4 w-4' }: { seg: RouteSegment; className?: string }) {
  if (seg.kind === 'walk') return <Footprints className={className} />;
  if (seg.kind === 'fallback_transit' || seg.route?.type === 'bus') {
    return <Bus className={className} />;
  }
  if (seg.route?.type === 'metro') return <TrainFront className={className} />;
  return <TramFront className={className} />;
}

async function compressImageFile(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const maxSide = 720;
        const scale = Math.min(1, maxSide / Math.max(img.width, img.height));
        const w = Math.round(img.width * scale);
        const h = Math.round(img.height * scale);
        const canvas = document.createElement('canvas');
        canvas.width = w;
        canvas.height = h;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          resolve(String(reader.result));
          return;
        }
        ctx.drawImage(img, 0, 0, w, h);
        resolve(canvas.toDataURL('image/jpeg', 0.76));
      };
      img.onerror = reject;
      img.src = String(reader.result);
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

export function LiveTripCompanion({
  trip,
  city,
  photoReminderMinutes,
  onClose,
  onCompleteTrip,
}: LiveTripCompanionProps) {
  const [segIndex, setSegIndex] = useState(0);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [running, setRunning] = useState(true);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [showStepPicker, setShowStepPicker] = useState(false);
  const [photos, setPhotos] = useState<TripPhotoMemory[]>([]);
  const [showPhotoPrompt, setShowPhotoPrompt] = useState(false);
  const [elapsedForPhotoSec, setElapsedForPhotoSec] = useState(0);

  const [userCoords, setUserCoords] = useState<LiveGeoCoords | null>(null);
  const [gpsState, setGpsState] = useState<'idle' | 'watching' | 'unavailable'>('idle');
  const [focusPulse, setFocusPulse] = useState(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const riderMarkerRef = useRef<HTMLDivElement | null>(null);
  const watchIdRef = useRef<number | null>(null);
  const arrivedBellFiredRef = useRef(false);

  const stopsById = useMemo<Record<string, TramStop>>(() => {
    const map: Record<string, TramStop> = {};
    for (const s of CITIES[city].stops) {
      map[s.id] = s;
    }
    return map;
  }, [city]);

  const currentSeg = trip.segments[segIndex] ?? trip.segments[0];
  const nextSeg = trip.segments[segIndex + 1] ?? null;
  const isLastSeg = segIndex >= trip.segments.length - 1;

  const primaryGuide = useMemo(
    () => matchAudioGuidesForTrip(trip, 1, currentSeg?.route?.number)[0] ?? null,
    [trip, currentSeg?.route?.number]
  );
  const announcements = useMemo(() => buildTripAnnouncements(trip, city), [trip, city]);

  const liveProgress = useMemo(
    () =>
      computeSegmentLiveProgress({
        segment: currentSeg,
        stopsById,
        elapsedSeconds,
        userCoords,
      }),
    [currentSeg, stopsById, elapsedSeconds, userCoords]
  );

  const totalRemainingMinutes = useMemo(() => {
    const afterCurrent = trip.segments
      .slice(segIndex + 1)
      .reduce((acc, s) => acc + s.durationMinutes, 0);
    return Math.max(liveProgress.minutesRemaining, liveProgress.minutesRemaining + afterCurrent);
  }, [trip.segments, segIndex, liveProgress.minutesRemaining]);

  const arrivalClock = useMemo(() => {
    const now = new Date();
    const nowMins = now.getHours() * 60 + now.getMinutes();
    return formatMinutesToClock(nowMins + totalRemainingMinutes);
  }, [totalRemainingMinutes]);

  const startGpsWatch = useCallback(() => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      setGpsState('unavailable');
      return;
    }
    if (watchIdRef.current !== null) {
      navigator.geolocation.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
    }
    setGpsState('watching');
    watchIdRef.current = navigator.geolocation.watchPosition(
      (pos) => {
        setUserCoords({
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
          accuracyMeters: pos.coords.accuracy,
        });
      },
      () => {
        setGpsState('unavailable');
      },
      {
        enableHighAccuracy: true,
        maximumAge: 4000,
        timeout: 12000,
      }
    );
  }, []);

  useEffect(() => {
    startGpsWatch();
    return () => {
      if (
        watchIdRef.current !== null &&
        typeof navigator !== 'undefined' &&
        navigator.geolocation
      ) {
        navigator.geolocation.clearWatch(watchIdRef.current);
      }
    };
  }, [startGpsWatch]);

  // Smooth 1-second progression + photo reminder timer
  useEffect(() => {
    arrivedBellFiredRef.current = false;
  }, [segIndex]);

  useEffect(() => {
    if (!running) return;
    const totalSec = Math.max(60, (currentSeg?.durationMinutes ?? 5) * 60);
    const timer = window.setInterval(() => {
      setElapsedSeconds((prev) => {
        const next = Math.min(totalSec, prev + 1);
        if (next >= totalSec && !arrivedBellFiredRef.current) {
          arrivedBellFiredRef.current = true;
          triggerHaptic('success');
          playTramChime();
        } else if (totalSec - next === 60) {
          triggerHaptic('medium');
        }
        return next;
      });

      if (photoReminderMinutes > 0) {
        setElapsedForPhotoSec((prev) => {
          const threshold = photoReminderMinutes * 60;
          if (prev + 1 >= threshold) {
            setShowPhotoPrompt(true);
            triggerHaptic('medium');
            return 0;
          }
          return prev + 1;
        });
      }
    }, 1000);
    return () => window.clearInterval(timer);
  }, [running, photoReminderMinutes, currentSeg?.durationMinutes]);

  useEffect(() => {
    return () => stopAnnouncement();
  }, []);

  const handleSelectSegment = (idx: number) => {
    triggerHaptic('medium');
    stopAnnouncement();
    setIsSpeaking(false);
    setSegIndex(idx);
    setElapsedSeconds(0);
    setShowStepPicker(false);
  };

  const handleNextSegment = () => {
    if (!isLastSeg) {
      handleSelectSegment(segIndex + 1);
    }
  };

  const handleSpeakCurrent = () => {
    if (isSpeaking) {
      stopAnnouncement();
      setIsSpeaking(false);
      return;
    }
    const line =
      announcements.find((a) => a.id.startsWith(currentSeg.id)) ?? announcements[0];
    if (!line) return;
    triggerHaptic('light');
    setIsSpeaking(true);
    const ok = speakAnnouncement(line.text, () => setIsSpeaking(false));
    if (!ok) {
      setIsSpeaking(false);
    }
  };

  const handleFocusOnPosition = () => {
    triggerHaptic('light');
    startGpsWatch();
    setFocusPulse(true);
    window.setTimeout(() => setFocusPulse(false), 1200);
    riderMarkerRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  };

  const handleAttachPhoto = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const dataUrl = await compressImageFile(file);
      const next: TripPhotoMemory = {
        id: `photo-${Date.now()}`,
        dataUrl,
        caption: `${currentSeg.fromStop.name.split('(')[0].trim()} — ${
          currentSeg.route ? getRouteTypeShortLabel(currentSeg.route) : 'Прогулка'
        }`,
        takenAt: new Date().toISOString(),
      };
      setPhotos((prev) => [next, ...prev]);
      setShowPhotoPrompt(false);
      triggerHaptic('success');
    } catch {
      // ignore invalid image
    } finally {
      e.target.value = '';
    }
  };

  const accentColor = getSegmentBrandColor(currentSeg);
  const progressPercent = Math.round(liveProgress.progressFraction * 1000) / 10;
  const stopsCount = liveProgress.stops.length;

  const destinationHint = useMemo(() => {
    if (currentSeg.route?.title) {
      const parts = currentSeg.route.title.split('—');
      return (parts[parts.length - 1] ?? currentSeg.toStop.name).trim();
    }
    return currentSeg.toStop.name.split('(')[0].trim();
  }, [currentSeg]);

  const headerPrimaryText =
    currentSeg.kind === 'walk'
      ? `${liveProgress.minutesRemaining} мин · пешком`
      : `${liveProgress.minutesRemaining} мин · ${liveProgress.stopsRemaining} ${pluralizeStopsRu(
          liveProgress.stopsRemaining
        )}`;

  const gpsBottomLabel =
    liveProgress.trackingSource === 'gps'
      ? `GPS на линии · у ост. «${liveProgress.stops[liveProgress.currentStopIndex]?.name.split('(')[0].trim()}»`
      : gpsState === 'watching' && liveProgress.distanceToRouteMeters !== null
        ? `Где я на линии · до маршрута ${(liveProgress.distanceToRouteMeters / 1000).toFixed(1)} км`
        : 'Где я на линии · показать позицию';

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Живой режим поездки"
      data-testid="live-trip-sheet"
      className="fixed inset-0 z-[2000] flex items-end justify-center bg-black/60 p-0 sm:items-center sm:p-4"
    >
      <div
        className="m3-card flex max-h-[94dvh] w-full max-w-lg flex-col overflow-hidden rounded-t-[28px] sm:rounded-[28px]"
        style={{
          backgroundColor: 'var(--bg-surface)',
          color: 'var(--text-primary)',
        }}
      >
        {/* Drag Handle */}
        <div className="flex justify-center pt-2.5">
          <div
            className="h-1 w-9 rounded-full opacity-25"
            style={{ backgroundColor: 'var(--text-primary)' }}
          />
        </div>

        {/* Top Summary Header (matches Yandex Maps style from reference screenshot) */}
        <div className="flex items-center justify-between gap-2 px-4 pt-2 pb-2.5">
          <button
            type="button"
            aria-label={isSpeaking ? 'Остановить озвучку' : 'Озвучить следующую остановку'}
            onClick={handleSpeakCurrent}
            className="m3-tactile flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full"
            style={{
              backgroundColor: 'var(--bg-elevated)',
              color: isSpeaking ? accentColor : 'var(--text-primary)',
            }}
          >
            {isSpeaking ? <VolumeX className="h-4 w-4" /> : <Volume2 className="h-4 w-4" />}
          </button>

          <div className="min-w-0 flex-1 text-center">
            <h2
              data-testid="live-trip-summary"
              className="font-display font-mono-num truncate text-lg font-extrabold tracking-tight"
            >
              {headerPrimaryText}
            </h2>
            <div
              className="font-mono-num truncate text-xs font-medium"
              style={{ color: 'var(--text-secondary)' }}
            >
              Весь маршрут: {trip.totalDurationMinutes} мин · {arrivalClock}
            </div>
          </div>

          <button
            type="button"
            aria-label="Свернуть режим поездки"
            onClick={() => {
              stopAnnouncement();
              onClose();
            }}
            className="m3-tactile flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full"
            style={{
              backgroundColor: 'var(--bg-elevated)',
              color: 'var(--text-primary)',
            }}
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Horizontal Progress Timeline Bar */}
        <div className="flex items-center gap-2 px-4 pb-3">
          <span
            className="inline-flex shrink-0 items-center gap-1 rounded-lg px-2 py-1 text-xs font-extrabold text-white shadow-xs"
            style={{ backgroundColor: accentColor }}
          >
            <SegmentModeIcon seg={currentSeg} className="h-3.5 w-3.5" />
            <span>{getSegmentBadgeText(currentSeg)}</span>
          </span>

          <div className="relative flex flex-1 items-center">
            {/* Base track */}
            <div
              className="h-1.5 w-full rounded-full"
              style={{ backgroundColor: 'rgba(100, 116, 139, 0.28)' }}
            />
            {/* Smooth colored progress fill */}
            <div
              className="absolute left-0 h-1.5 rounded-full transition-[width] duration-700 ease-out"
              style={{
                width: `${progressPercent}%`,
                backgroundColor: accentColor,
              }}
            />
            {/* Evenly distributed stop dots along horizontal track */}
            <div className="pointer-events-none absolute inset-0 flex items-center justify-between px-0.5">
              {liveProgress.stops.map((stop, idx) => {
                const passed = idx <= liveProgress.currentStopIndex;
                return (
                  <span
                    key={`${stop.id}-h-${idx}`}
                    className="h-2.5 w-2.5 rounded-full border-2 transition-colors duration-300"
                    style={{
                      backgroundColor: passed ? accentColor : 'var(--bg-surface)',
                      borderColor: accentColor,
                    }}
                  />
                );
              })}
            </div>
            {/* Moving live rider dot on horizontal bar */}
            <span
              className="pointer-events-none absolute top-1/2 h-3.5 w-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white shadow transition-[left] duration-700 ease-out"
              style={{
                left: `${progressPercent}%`,
                backgroundColor: accentColor,
              }}
            />
          </div>

          <span
            className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-lg"
            title={nextSeg ? 'Следующий шаг' : 'Финиш'}
            style={{
              backgroundColor: nextSeg ? getSegmentBrandColor(nextSeg) : 'var(--md-primary-container)',
              color: nextSeg ? '#ffffff' : 'var(--md-on-primary-container)',
            }}
          >
            {nextSeg ? <SegmentModeIcon seg={nextSeg} className="h-3.5 w-3.5" /> : <Flag className="h-3.5 w-3.5" />}
          </span>
        </div>

        {/* Exit Prompt Banner Card */}
        <div
          className="mx-4 mb-3 flex items-center gap-3 rounded-2xl p-3"
          style={{ backgroundColor: 'var(--md-surface-container)' }}
        >
          <div
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-white"
            style={{ backgroundColor: '#2563eb' }}
          >
            <SegmentModeIcon seg={currentSeg} className="h-5 w-5" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
              {currentSeg.kind === 'walk' ? 'Направляйтесь к остановке' : 'Выходите на остановке'}
            </div>
            <div className="truncate text-base font-extrabold leading-tight">
              {currentSeg.toStop.name}
            </div>
          </div>
        </div>

        {/* Expanded Vertical Linear Route Card + Sticky Bottom Location Button */}
        <div
          className="mx-4 mb-2 flex min-h-0 flex-1 flex-col overflow-hidden rounded-3xl border"
          style={{
            backgroundColor: 'var(--md-surface-container)',
            borderColor: 'var(--border-hairline)',
          }}
        >
          {/* Active Segment Header */}
          <div
            className="flex items-start gap-3 border-b px-4 py-3"
            style={{ borderColor: 'var(--border-hairline)' }}
          >
            <div
              className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-white"
              style={{ backgroundColor: accentColor }}
            >
              <SegmentModeIcon seg={currentSeg} className="h-5 w-5" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between gap-2">
                <span className="truncate text-base font-extrabold">
                  {getSegmentBadgeText(currentSeg)}
                </span>
                <span
                  className="font-mono-num shrink-0 rounded-full px-2 py-0.5 text-[11px] font-bold"
                  style={{
                    backgroundColor:
                      liveProgress.trackingSource === 'gps'
                        ? 'rgba(22, 163, 74, 0.16)'
                        : 'var(--bg-elevated)',
                    color:
                      liveProgress.trackingSource === 'gps'
                        ? '#15803d'
                        : 'var(--text-secondary)',
                  }}
                >
                  {liveProgress.trackingSource === 'gps' ? 'GPS · LIVE' : 'Авто-ход'}
                </span>
              </div>
              <div className="text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
                {getSegmentModeName(currentSeg)} · В пути {currentSeg.durationMinutes} мин
              </div>
              <div className="truncate text-xs" style={{ color: 'var(--text-secondary)' }}>
                В направлении: «{destinationHint}»
              </div>
              {trip.segments.length > 1 && (
                <button
                  type="button"
                  onClick={() => setShowStepPicker((v) => !v)}
                  className="mt-1 cursor-pointer text-xs font-bold"
                  style={{ color: 'var(--accent-primary)' }}
                >
                  {showStepPicker
                    ? 'Скрыть шаги маршрута'
                    : `Выбрать другой шаг (${segIndex + 1} из ${trip.segments.length})`}
                </button>
              )}
            </div>
          </div>

          {showStepPicker && trip.segments.length > 1 && (
            <div
              className="flex gap-1.5 overflow-x-auto border-b px-4 py-2"
              style={{ borderColor: 'var(--border-hairline)', backgroundColor: 'var(--bg-surface)' }}
            >
              {trip.segments.map((seg, idx) => (
                <button
                  key={seg.id}
                  type="button"
                  onClick={() => handleSelectSegment(idx)}
                  className="m3-tactile shrink-0 cursor-pointer rounded-xl px-2.5 py-1 text-xs font-bold"
                  style={{
                    backgroundColor:
                      idx === segIndex ? getSegmentBrandColor(seg) : 'var(--bg-elevated)',
                    color: idx === segIndex ? '#ffffff' : 'var(--text-primary)',
                  }}
                >
                  {idx + 1}. {getSegmentBadgeText(seg)} ({seg.durationMinutes}м)
                </button>
              ))}
            </div>
          )}

          {/* Scrollable Vertical Linear Stops List with Smooth Live Progress Line */}
          <div className="flex-1 overflow-y-auto px-4 py-3">
            <div data-testid="vertical-linear-route" className="relative pl-8">
              {/* Vertical Rail spanning from 1st dot center to last dot center */}
              {stopsCount > 1 && (
                <div
                  className="pointer-events-none absolute top-[20px] bottom-[20px] left-[11px] w-1.5 -translate-x-1/2 rounded-full"
                  style={{ backgroundColor: 'rgba(100, 116, 139, 0.28)' }}
                >
                  {/* Active filled progress line ("где ты едешь в виде линии") */}
                  <div
                    data-testid="vertical-progress-fill"
                    className="w-full rounded-full transition-[height] duration-700 ease-out"
                    style={{
                      height: `${progressPercent}%`,
                      backgroundColor: accentColor,
                    }}
                  />

                  {/* Smooth live rider indicator dot along the vertical line */}
                  <div
                    ref={riderMarkerRef}
                    data-testid="vertical-gps-marker"
                    className="absolute left-1/2 -translate-x-1/2 -translate-y-1/2 transition-[top] duration-700 ease-out"
                    style={{ top: `${progressPercent}%` }}
                  >
                    <span
                      className={`absolute inset-0 -m-1.5 rounded-full opacity-40 ${
                        focusPulse ? 'animate-ping' : 'animate-pulse'
                      }`}
                      style={{ backgroundColor: accentColor }}
                    />
                    <span
                      className="relative block h-4 w-4 rounded-full border-2 border-white shadow-md"
                      style={{ backgroundColor: accentColor }}
                    />
                  </div>
                </div>
              )}

              {/* Stop Rows along vertical line */}
              <div className="flex flex-col">
                {liveProgress.stops.map((stop, idx) => {
                  const isTerminal = idx === 0 || idx === stopsCount - 1;
                  const isPassed = idx < liveProgress.currentStopIndex;
                  const isCurrent = idx === liveProgress.currentStopIndex;
                  return (
                    <div
                      key={`${stop.id}-v-${idx}`}
                      className="relative flex h-10 items-center justify-between gap-2"
                    >
                      {/* Stop Node centered on rail */}
                      <span
                        className="absolute -left-[21px] top-1/2 h-2.5 w-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 transition-colors duration-300"
                        style={{
                          backgroundColor:
                            isPassed || isTerminal ? accentColor : 'var(--bg-surface)',
                          borderColor: accentColor,
                        }}
                      />
                      <span
                        className={`min-w-0 truncate leading-snug transition-opacity ${
                          isTerminal
                            ? 'text-[15px] font-extrabold'
                            : isCurrent
                              ? 'text-sm font-bold'
                              : 'text-sm font-medium'
                        }`}
                        style={{
                          color: 'var(--text-primary)',
                          opacity: isPassed && idx > 0 ? 0.55 : 1,
                        }}
                      >
                        {stop.name}
                      </span>
                      {isCurrent && idx < stopsCount - 1 && (
                        <span
                          className="shrink-0 rounded-md px-1.5 py-0.5 text-[10px] font-bold"
                          style={{
                            backgroundColor: `${accentColor}22`,
                            color: accentColor,
                          }}
                        >
                          {liveProgress.trackingSource === 'gps' ? 'Вы здесь' : 'Сейчас'}
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Compact Photo / Audio Guide Strip tucked cleanly below linear stops */}
            {(showPhotoPrompt || primaryGuide || photos.length > 0) && (
              <div className="mt-4 flex flex-col gap-2.5 border-t pt-3" style={{ borderColor: 'var(--border-hairline)' }}>
                {showPhotoPrompt && (
                  <div
                    className="flex items-center justify-between gap-2 rounded-2xl border p-2.5"
                    style={{
                      backgroundColor: 'rgba(217, 119, 6, 0.12)',
                      borderColor: 'rgba(217, 119, 6, 0.35)',
                    }}
                  >
                    <div className="flex items-center gap-2 text-xs">
                      <Bell className="h-3.5 w-3.5 shrink-0 text-amber-600" />
                      <span>Время фото-чекина на память!</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => fileInputRef.current?.click()}
                      className="m3-tactile shrink-0 cursor-pointer rounded-full px-2.5 py-1 text-xs font-bold"
                      style={{
                        backgroundColor: 'var(--accent-primary)',
                        color: 'var(--accent-on-primary)',
                      }}
                    >
                      Снять
                    </button>
                  </div>
                )}

                {primaryGuide && (
                  <a
                    href={primaryGuide.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    onClick={() => triggerHaptic('light')}
                    className="m3-tactile flex items-center justify-between gap-2 rounded-2xl px-3 py-2 text-xs font-semibold"
                    style={{
                      backgroundColor: 'var(--bg-surface)',
                      color: 'var(--text-primary)',
                    }}
                  >
                    <span className="flex min-w-0 items-center gap-2">
                      <Headphones className="h-3.5 w-3.5 shrink-0" style={{ color: accentColor }} />
                      <span className="truncate">{primaryGuide.title}</span>
                    </span>
                    <ExternalLink className="h-3.5 w-3.5 shrink-0 opacity-60" />
                  </a>
                )}

                {photos.length > 0 && (
                  <div className="grid grid-cols-4 gap-1.5">
                    {photos.map((p) => (
                      <img
                        key={p.id}
                        src={p.dataUrl}
                        alt={p.caption}
                        className="aspect-square w-full rounded-xl object-cover"
                      />
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Sticky Bottom Location Button on Expanded Vertical Linear Route */}
          <div
            className="flex items-center gap-2 border-t px-3.5 py-2.5"
            style={{
              borderColor: 'var(--border-hairline)',
              backgroundColor: 'var(--bg-surface)',
            }}
          >
            <button
              type="button"
              data-testid="live-trip-locate-btn"
              onClick={handleFocusOnPosition}
              className="m3-tactile flex min-h-[40px] flex-1 cursor-pointer items-center justify-center gap-2 rounded-2xl px-3.5 py-2 text-xs font-bold"
              style={{
                backgroundColor:
                  liveProgress.trackingSource === 'gps'
                    ? 'rgba(22, 163, 74, 0.16)'
                    : 'var(--md-primary-container)',
                color:
                  liveProgress.trackingSource === 'gps'
                    ? '#15803d'
                    : 'var(--md-on-primary-container)',
              }}
            >
              <LocateFixed className="h-4 w-4 shrink-0" />
              <span className="truncate">{gpsBottomLabel}</span>
            </button>

            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              capture="environment"
              onChange={handleAttachPhoto}
              className="hidden"
            />
            <button
              type="button"
              aria-label="Добавить фото поездки"
              onClick={() => {
                triggerHaptic('light');
                fileInputRef.current?.click();
              }}
              className="m3-tactile flex h-10 shrink-0 cursor-pointer items-center gap-1 rounded-2xl px-3 text-xs font-semibold"
              style={{
                backgroundColor: 'var(--bg-elevated)',
                color: 'var(--text-primary)',
              }}
            >
              <Camera className="h-4 w-4" />
              {photos.length > 0 && <span>{photos.length}</span>}
            </button>
          </div>
        </div>

        {/* Bottom Trip Action Bar */}
        <div
          className="flex items-center justify-between gap-2 px-4 py-3"
          style={{ borderTop: '1px solid var(--border-hairline)' }}
        >
          <button
            type="button"
            aria-label={running ? 'Пауза таймера' : 'Продолжить таймер'}
            onClick={() => {
              triggerHaptic('light');
              setRunning((r) => !r);
            }}
            className="m3-tactile flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-2xl"
            style={{
              backgroundColor: 'var(--bg-elevated)',
              color: 'var(--text-primary)',
            }}
          >
            {running ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
          </button>

          {!isLastSeg ? (
            <>
              <button
                type="button"
                onClick={() => {
                  stopAnnouncement();
                  triggerHaptic('success');
                  onCompleteTrip(trip, photos);
                }}
                className="m3-tactile inline-flex min-h-[40px] shrink-0 cursor-pointer items-center gap-1.5 rounded-2xl px-3 py-2 text-xs font-semibold whitespace-nowrap"
                style={{
                  backgroundColor: 'var(--bg-elevated)',
                  color: 'var(--text-primary)',
                }}
              >
                <Square className="h-3.5 w-3.5 shrink-0" />
                Завершить
              </button>
              <button
                type="button"
                onClick={handleNextSegment}
                className="m3-tactile m3-fab inline-flex min-h-[42px] flex-1 cursor-pointer items-center justify-center gap-1 rounded-2xl px-3 py-2 text-xs font-bold whitespace-nowrap sm:px-4 sm:text-sm"
                style={{
                  backgroundColor: 'var(--accent-primary)',
                  color: 'var(--accent-on-primary)',
                }}
              >
                Следующий шаг
                <ChevronRight className="h-4 w-4 shrink-0" />
              </button>
            </>
          ) : (
            <button
              type="button"
              onClick={() => {
                stopAnnouncement();
                triggerHaptic('success');
                onCompleteTrip(trip, photos);
              }}
              className="m3-tactile m3-fab inline-flex min-h-[42px] flex-1 cursor-pointer items-center justify-center gap-2 rounded-2xl px-4 py-2 text-sm font-bold"
              style={{
                backgroundColor: 'var(--accent-primary)',
                color: 'var(--accent-on-primary)',
              }}
            >
              <CheckCircle2 className="h-4 w-4" />
              Завершить поездку
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
