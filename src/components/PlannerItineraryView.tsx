import { useEffect, useMemo, useState } from 'react';
import {
  Bookmark,
  BookmarkCheck,
  Bus,
  Check,
  Clock,
  Copy,
  Dices,
  ExternalLink,
  Footprints,
  Navigation2,
  Play,
  RefreshCw,
  Share2,
  SlidersHorizontal,
  TramFront,
  Volume2,
  VolumeX,
} from 'lucide-react';
import { motion } from 'motion/react';
import { CityId, GeneratedTrip, GenerationOutcome } from '../types/tram';
import {
  buildTripAnnouncements,
  speakAnnouncement,
  stopAnnouncement,
} from '../utils/announcer';
import { DAY_NAMES_SHORT, formatMinutesToClock } from '../utils/tramRouter';
import { buildYandexMapsRouteUrl } from '../utils/yandexMaps';
import { triggerHaptic } from '../utils/haptics';

interface PlannerItineraryViewProps {
  city: CityId;
  outcome: GenerationOutcome | null;
  onGenerate: (overrideFallback?: boolean, overrideDay?: number, overrideTime?: number) => void;
  onExtendLastSegment: () => void;
  onStartLiveTrip: () => void;
  onOpenPostcard: () => void;
  onChangeAllowFallbackTransit: (allow: boolean) => void;
  onChangeDayOfWeek: (day: number) => void;
  onChangeTimeMinutes: (minutes: number) => void;
  onSaveTrip: (trip: GeneratedTrip, customTitle: string, note: string) => void;
  isCurrentTripSaved: boolean;
  onEditParams: () => void;
}

function pluralizeRu(count: number, forms: [string, string, string]): string {
  const abs = Math.abs(count) % 100;
  const mod10 = abs % 10;
  if (abs > 10 && abs < 20) return forms[2];
  if (mod10 > 1 && mod10 < 5) return forms[1];
  if (mod10 === 1) return forms[0];
  return forms[2];
}

export function PlannerItineraryView({
  city,
  outcome,
  onGenerate,
  onExtendLastSegment,
  onStartLiveTrip,
  onOpenPostcard,
  onChangeAllowFallbackTransit,
  onChangeDayOfWeek,
  onChangeTimeMinutes,
  onSaveTrip,
  isCurrentTripSaved,
  onEditParams,
}: PlannerItineraryViewProps) {
  const [showSaveForm, setShowSaveForm] = useState(false);
  const [customTitle, setCustomTitle] = useState('');
  const [customNote, setCustomNote] = useState('');
  const [copiedYandex, setCopiedYandex] = useState(false);
  const [playingAnnId, setPlayingAnnId] = useState<string | null>(null);

  useEffect(() => {
    return () => stopAnnouncement();
  }, []);

  const tripForMemo = outcome?.status === 'ok' ? outcome.trip : null;
  const announcements = useMemo(
    () => (tripForMemo ? buildTripAnnouncements(tripForMemo, city) : []),
    [tripForMemo, city]
  );

  const handleOpenSaveForm = () => {
    if (outcome?.status !== 'ok') return;
    triggerHaptic('light');
    setCustomTitle(
      `${outcome.trip.startStop.name.split('(')[0].trim()} → ${outcome.trip.endStop.name.split('(')[0].trim()}`
    );
    setCustomNote('');
    setShowSaveForm(true);
  };

  const handleConfirmSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (outcome?.status !== 'ok') return;
    triggerHaptic('success');
    onSaveTrip(outcome.trip, customTitle.trim() || 'Трамвайная прогулка', customNote.trim());
    setShowSaveForm(false);
  };

  if (outcome?.status === 'needs_fallback') {
    return (
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        className="m3-card rounded-3xl p-5"
      >
        <h2 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
          Сюда нет сквозных трамвайных рельсов
        </h2>
        <p className="mt-2 text-sm leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
          {outcome.reason}
        </p>
        {outcome.nearestTramStop && (
          <p className="mt-2 text-xs" style={{ color: 'var(--text-muted)' }}>
            Ближайшая трамвайная остановка: {outcome.nearestTramStop.name}
          </p>
        )}
        <div className="mt-4 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => {
              triggerHaptic('medium');
              onChangeAllowFallbackTransit(true);
              onGenerate(true);
            }}
            className="m3-tactile inline-flex min-h-[44px] cursor-pointer items-center gap-2 rounded-2xl px-4 py-2 text-xs font-bold"
            style={{
              backgroundColor: 'var(--accent-primary)',
              color: 'var(--accent-on-primary)',
            }}
          >
            <Bus className="h-4 w-4" />
            Включить резервный транспорт и построить
          </button>
          <button
            type="button"
            onClick={() => {
              triggerHaptic('light');
              onEditParams();
            }}
            className="m3-tactile inline-flex min-h-[44px] cursor-pointer items-center gap-1.5 rounded-2xl px-4 py-2 text-xs font-semibold"
            style={{
              backgroundColor: 'var(--bg-elevated)',
              color: 'var(--text-primary)',
            }}
          >
            Изменить точку
          </button>
        </div>
      </motion.div>
    );
  }

  if (outcome?.status === 'no_service') {
    return (
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        className="m3-card rounded-3xl p-5"
      >
        <h2 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
          Сейчас трамваи здесь не ходят
        </h2>
        <p className="mt-2 text-sm leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
          {outcome.reason}
        </p>
        <div className="mt-4 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => {
              triggerHaptic('medium');
              onChangeDayOfWeek(3);
              onChangeTimeMinutes(13 * 60);
              onGenerate(undefined, 3, 13 * 60);
            }}
            className="m3-tactile inline-flex min-h-[44px] cursor-pointer items-center gap-2 rounded-2xl px-4 py-2 text-xs font-bold"
            style={{
              backgroundColor: 'var(--accent-primary)',
              color: 'var(--accent-on-primary)',
            }}
          >
            <Clock className="h-4 w-4" />
            Переключить на будний день (13:00)
          </button>
          <button
            type="button"
            onClick={() => {
              triggerHaptic('light');
              onEditParams();
            }}
            className="m3-tactile inline-flex min-h-[44px] cursor-pointer items-center gap-1.5 rounded-2xl px-4 py-2 text-xs font-semibold"
            style={{
              backgroundColor: 'var(--bg-elevated)',
              color: 'var(--text-primary)',
            }}
          >
            Параметры
          </button>
        </div>
      </motion.div>
    );
  }

  if (outcome?.status !== 'ok') {
    return (
      <div className="m3-card rounded-3xl p-6 text-center">
        <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
          Маршрут ещё не собран. Настройте параметры и запустите подбор.
        </p>
        <button
          type="button"
          onClick={() => {
            triggerHaptic('light');
            onEditParams();
          }}
          className="m3-tactile mt-4 inline-flex min-h-[44px] cursor-pointer items-center gap-2 rounded-2xl px-5 py-2 text-xs font-bold"
          style={{
            backgroundColor: 'var(--accent-primary)',
            color: 'var(--accent-on-primary)',
          }}
        >
          К параметрам поиска
        </button>
      </div>
    );
  }

  const { trip } = outcome;
  const yandexUrl = buildYandexMapsRouteUrl(trip);

  const handleCopyYandex = async () => {
    triggerHaptic('light');
    try {
      await navigator.clipboard.writeText(yandexUrl);
      setCopiedYandex(true);
      setTimeout(() => setCopiedYandex(false), 2000);
    } catch {
      // ignore clipboard errors
    }
  };

  const handleToggleSpeech = (annId: string, text: string) => {
    if (playingAnnId === annId) {
      stopAnnouncement();
      setPlayingAnnId(null);
      return;
    }
    triggerHaptic('light');
    setPlayingAnnId(annId);
    const ok = speakAnnouncement(text, () => setPlayingAnnId(null));
    if (!ok) {
      setPlayingAnnId(null);
    }
  };

  return (
    <motion.section
      key={trip.id}
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.22, ease: [0.2, 0, 0, 1] }}
      aria-label="Сгенерированный маршрут"
      className="flex flex-col gap-4"
    >
      {/* Material You Tonal Trip Summary Card */}
      <div className="m3-card rounded-3xl p-4 sm:p-5">
        <div className="flex flex-wrap items-center gap-2">
          {trip.isLoop && (
            <span
              className="rounded-full px-2.5 py-0.5 text-[11px] font-bold"
              style={{
                backgroundColor: 'var(--md-primary-container)',
                color: 'var(--md-on-primary-container)',
              }}
            >
              Круговой маршрут
            </span>
          )}
          <h2
            className="font-display w-full text-lg font-extrabold leading-snug text-balance sm:text-xl"
            style={{ color: 'var(--text-primary)' }}
          >
            {trip.startStop.name.split('(')[0].trim()} → {trip.endStop.name.split('(')[0].trim()}
          </h2>
        </div>

        <div
          className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs"
          style={{ color: 'var(--text-muted)' }}
        >
          <span className="font-mono-num font-bold" style={{ color: 'var(--text-primary)' }}>
            ~{trip.totalDurationMinutes} мин
          </span>
          <span aria-hidden="true">·</span>
          <span className="font-mono-num">
            {trip.transferCount === 0
              ? 'Без пересадок'
              : `${trip.transferCount} ${pluralizeRu(trip.transferCount, ['пересадка', 'пересадки', 'пересадок'])}`}
          </span>
          <span aria-hidden="true">·</span>
          <span>
            {trip.districtsVisited.length}{' '}
            {pluralizeRu(trip.districtsVisited.length, ['район', 'района', 'районов'])}
          </span>
          <span aria-hidden="true">·</span>
          <span>
            {DAY_NAMES_SHORT[trip.dayOfWeek]}, {formatMinutesToClock(trip.timeMinutes)}
          </span>
        </div>

        {/* Yandex Maps Navigation Strip */}
        <div
          className="mt-4 flex flex-wrap items-center gap-2 rounded-2xl border p-2.5"
          style={{
            backgroundColor: 'var(--md-surface-container)',
            borderColor: 'var(--border-hairline)',
          }}
        >
          <a
            href={yandexUrl}
            target="_blank"
            rel="noopener noreferrer"
            onClick={() => triggerHaptic('light')}
            className="m3-tactile inline-flex min-h-[38px] flex-1 cursor-pointer items-center justify-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-bold whitespace-nowrap"
            style={{
              backgroundColor: 'var(--md-primary-container)',
              color: 'var(--md-on-primary-container)',
            }}
          >
            <Navigation2 className="h-3.5 w-3.5" />
            Открыть в Яндекс Картах
            <ExternalLink className="h-3 w-3 opacity-75" />
          </a>
          <button
            type="button"
            onClick={handleCopyYandex}
            aria-label="Скопировать ссылку на маршрут в Яндекс Картах"
            className="m3-tactile inline-flex min-h-[38px] cursor-pointer items-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-semibold whitespace-nowrap"
            style={{
              backgroundColor: 'var(--bg-surface)',
              color: 'var(--text-primary)',
            }}
          >
            {copiedYandex ? <Check className="h-3.5 w-3.5 text-emerald-600" /> : <Copy className="h-3.5 w-3.5" />}
            {copiedYandex ? 'Скопировано' : 'Ссылка'}
          </button>
        </div>

        {/* Interactive Actions Row */}
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => {
              triggerHaptic('medium');
              onStartLiveTrip();
            }}
            className="m3-tactile inline-flex min-h-[40px] cursor-pointer items-center gap-1.5 rounded-2xl px-3.5 py-1.5 text-xs font-bold"
            style={{
              backgroundColor: 'var(--accent-primary)',
              color: 'var(--accent-on-primary)',
            }}
          >
            <Play className="h-3.5 w-3.5" />
            В пути
          </button>

          <button
            type="button"
            onClick={() => {
              triggerHaptic('medium');
              onExtendLastSegment();
            }}
            title="Докрутить ещё 1 трамвайную пересадку к концу маршрута"
            className="m3-tactile inline-flex min-h-[40px] cursor-pointer items-center gap-1.5 rounded-2xl px-3 py-1.5 text-xs font-semibold"
            style={{
              backgroundColor: 'var(--bg-elevated)',
              color: 'var(--text-primary)',
            }}
          >
            <Dices className="h-3.5 w-3.5" />
            Докрутить +1
          </button>

          <button
            type="button"
            onClick={() => {
              triggerHaptic('light');
              onOpenPostcard();
            }}
            className="m3-tactile inline-flex min-h-[40px] cursor-pointer items-center gap-1.5 rounded-2xl px-3 py-1.5 text-xs font-semibold"
            style={{
              backgroundColor: 'var(--bg-elevated)',
              color: 'var(--text-primary)',
            }}
          >
            <Share2 className="h-3.5 w-3.5" />
            Открытка
          </button>

          <button
            type="button"
            onClick={() => {
              triggerHaptic('light');
              onEditParams();
            }}
            className="m3-tactile inline-flex min-h-[40px] cursor-pointer items-center gap-1.5 rounded-2xl px-3 py-1.5 text-xs font-semibold"
            style={{
              backgroundColor: 'var(--bg-elevated)',
              color: 'var(--text-primary)',
            }}
          >
            <SlidersHorizontal className="h-3.5 w-3.5" />
            Параметры
          </button>

          <button
            type="button"
            aria-label="Перегенерировать маршрут"
            onClick={() => {
              triggerHaptic('medium');
              onGenerate();
            }}
            className="m3-tactile inline-flex min-h-[40px] cursor-pointer items-center gap-1.5 rounded-2xl px-3 py-1.5 text-xs font-semibold"
            style={{
              backgroundColor: 'var(--bg-elevated)',
              color: 'var(--text-primary)',
            }}
          >
            <RefreshCw className="h-3.5 w-3.5" />
            Другой
          </button>

          <button
            type="button"
            onClick={handleOpenSaveForm}
            disabled={isCurrentTripSaved}
            className="m3-tactile ml-auto inline-flex min-h-[40px] cursor-pointer items-center gap-1.5 rounded-2xl px-4 py-1.5 text-xs font-bold whitespace-nowrap shrink-0 disabled:opacity-65"
            style={{
              backgroundColor: isCurrentTripSaved
                ? 'var(--md-primary-container)'
                : 'var(--bg-elevated)',
              color: isCurrentTripSaved
                ? 'var(--md-on-primary-container)'
                : 'var(--text-primary)',
            }}
          >
            {isCurrentTripSaved ? (
              <>
                <BookmarkCheck className="h-3.5 w-3.5" />
                В избранном
              </>
            ) : (
              <>
                <Bookmark className="h-3.5 w-3.5" />
                Сохранить
              </>
            )}
          </button>
        </div>

        {showSaveForm && (
          <form
            onSubmit={handleConfirmSave}
            className="mt-4 flex flex-col gap-2.5 border-t pt-4"
            style={{ borderColor: 'var(--border-hairline)' }}
          >
            <label
              className="flex flex-col gap-1 text-xs font-semibold"
              style={{ color: 'var(--text-secondary)' }}
            >
              Название в избранном
              <input
                type="text"
                required
                value={customTitle}
                onChange={(e) => setCustomTitle(e.target.value)}
                className="min-h-[42px] rounded-2xl border px-3 py-1.5 text-sm"
                style={{
                  backgroundColor: 'var(--bg-input)',
                  color: 'var(--text-primary)',
                  borderColor: 'var(--border-hairline)',
                }}
              />
            </label>
            <label
              className="flex flex-col gap-1 text-xs font-semibold"
              style={{ color: 'var(--text-secondary)' }}
            >
              Заметка (по желанию)
              <input
                type="text"
                placeholder="Например: зайти в кофейню у остановки"
                value={customNote}
                onChange={(e) => setCustomNote(e.target.value)}
                className="min-h-[42px] rounded-2xl border px-3 py-1.5 text-xs"
                style={{
                  backgroundColor: 'var(--bg-input)',
                  color: 'var(--text-primary)',
                  borderColor: 'var(--border-hairline)',
                }}
              />
            </label>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowSaveForm(false)}
                className="m3-tactile min-h-[38px] cursor-pointer rounded-2xl px-3.5 py-1.5 text-xs font-semibold"
                style={{ color: 'var(--text-secondary)' }}
              >
                Отмена
              </button>
              <button
                type="submit"
                className="m3-tactile min-h-[38px] cursor-pointer rounded-2xl px-4 py-1.5 text-xs font-bold"
                style={{
                  backgroundColor: 'var(--accent-primary)',
                  color: 'var(--accent-on-primary)',
                }}
              >
                Сохранить маршрут
              </button>
            </div>
          </form>
        )}
      </div>

      {/* Idea 2: Authentic City Auto-Informer Preview */}
      {announcements.length > 0 && (
        <div className="m3-card rounded-3xl p-4 sm:p-5">
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-bold" style={{ color: 'var(--text-secondary)' }}>
              Автоинформатор ({city === 'omsk' ? 'Электрический транспорт' : 'Горэлектротранс'})
            </span>
            <button
              type="button"
              onClick={() =>
                handleToggleSpeech(announcements[0].id, announcements[0].text)
              }
              className="m3-tactile inline-flex min-h-[34px] cursor-pointer items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold"
              style={{
                backgroundColor: 'var(--md-primary-container)',
                color: 'var(--md-on-primary-container)',
              }}
            >
              {playingAnnId === announcements[0].id ? (
                <>
                  <VolumeX className="h-3.5 w-3.5" />
                  Стоп
                </>
              ) : (
                <>
                  <Volume2 className="h-3.5 w-3.5" />
                  Диктовка
                </>
              )}
            </button>
          </div>
          <p className="mt-2 text-xs italic leading-relaxed" style={{ color: 'var(--text-primary)' }}>
            «{announcements[0].text}»
          </p>
        </div>
      )}

      {/* Spacious Step-by-Step Transit Timeline */}
      <div className="m3-card rounded-3xl p-4 sm:p-5">
        <div className="flex flex-col">
          {trip.segments.map((seg, idx) => {
            const isTram = seg.kind === 'tram';
            const badgeBg = isTram
              ? seg.route?.color ?? '#0f5e8c'
              : seg.kind === 'walk'
                ? '#059669'
                : '#b45309';

            return (
              <div key={seg.id} className="flex flex-col">
                {idx > 0 && (
                  <div
                    className="my-3.5 flex items-center gap-2 rounded-xl px-3 py-2 text-xs font-semibold"
                    style={{
                      backgroundColor: 'var(--bg-elevated)',
                      color: 'var(--text-secondary)',
                    }}
                  >
                    <span>Пересадка · {seg.fromStop.name}</span>
                  </div>
                )}

                <div className="flex items-start gap-3">
                  <span
                    className="font-mono-num mt-0.5 inline-flex h-9 min-w-11 items-center justify-center gap-1 rounded-2xl px-2.5 text-xs font-bold text-white shadow-xs shrink-0"
                    style={{ backgroundColor: badgeBg }}
                  >
                    {isTram ? (
                      <>
                        <TramFront className="h-3.5 w-3.5" />
                        {seg.route?.number}
                      </>
                    ) : seg.kind === 'walk' ? (
                      <Footprints className="h-3.5 w-3.5" />
                    ) : (
                      <Bus className="h-3.5 w-3.5" />
                    )}
                  </span>

                  <div className="min-w-0 flex-1">
                    <div className="flex items-baseline justify-between gap-2">
                      <div className="text-sm font-bold leading-snug" style={{ color: 'var(--text-primary)' }}>
                        {isTram
                          ? `Трамвай № ${seg.route?.number}`
                          : seg.kind === 'walk'
                            ? seg.durationMinutes >= 12
                              ? 'Пешая прогулка'
                              : 'Пеший переход'
                            : seg.fallbackModeName}
                      </div>
                      <span
                        className="font-mono-num text-xs font-bold whitespace-nowrap shrink-0"
                        style={{ color: 'var(--text-primary)' }}
                      >
                        {seg.durationMinutes} мин
                      </span>
                    </div>

                    <div className="mt-0.5 text-xs" style={{ color: 'var(--text-muted)' }}>
                      {seg.directionNote}
                    </div>

                    <div className="mt-2.5 flex flex-col gap-1 text-xs">
                      <div>
                        <span style={{ color: 'var(--text-muted)' }}>Посадка: </span>
                        <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                          {seg.fromStop.name}
                        </span>
                      </div>
                      <div>
                        <span style={{ color: 'var(--text-muted)' }}>Выход: </span>
                        <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                          {seg.toStop.name}
                        </span>
                      </div>
                    </div>

                    {isTram && seg.route && (
                      <div className="mt-1.5 text-[11px] leading-relaxed" style={{ color: 'var(--text-muted)' }}>
                        Остановок: {seg.stopsCount} · Интервал ~{seg.route.schedule.intervalMinutes} мин ·{' '}
                        {seg.route.schedule.note}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </motion.section>
  );
}
