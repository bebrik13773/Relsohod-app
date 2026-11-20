import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Bell,
  Camera,
  CheckCircle2,
  ChevronRight,
  Pause,
  Play,
  Square,
  Volume2,
  VolumeX,
  X,
} from 'lucide-react';
import { CityId, GeneratedTrip, TripPhotoMemory } from '../types/tram';
import {
  buildTripAnnouncements,
  playTramChime,
  speakAnnouncement,
  stopAnnouncement,
} from '../utils/announcer';
import { triggerHaptic } from '../utils/haptics';

interface LiveTripCompanionProps {
  trip: GeneratedTrip;
  city: CityId;
  photoReminderMinutes: number;
  onClose: () => void;
  onCompleteTrip: (trip: GeneratedTrip, photos: TripPhotoMemory[]) => void;
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
  const [secondsLeft, setSecondsLeft] = useState(
    () => (trip.segments[0]?.durationMinutes ?? 5) * 60
  );
  const [running, setRunning] = useState(true);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [photos, setPhotos] = useState<TripPhotoMemory[]>([]);
  const [showPhotoPrompt, setShowPhotoPrompt] = useState(false);
  const [elapsedForPhotoSec, setElapsedForPhotoSec] = useState(0);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const announcements = useMemo(() => buildTripAnnouncements(trip, city), [trip, city]);
  const currentSeg = trip.segments[segIndex];
  const isLastSeg = segIndex >= trip.segments.length - 1;

  // Countdown + Photo Reminder Timer
  useEffect(() => {
    if (!running) return;
    const timer = window.setInterval(() => {
      setSecondsLeft((prev) => {
        if (prev <= 1) {
          triggerHaptic('success');
          playTramChime();
          return 0;
        }
        if (prev === 60) {
          triggerHaptic('medium');
        }
        return prev - 1;
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
  }, [running, photoReminderMinutes]);

  useEffect(() => {
    return () => stopAnnouncement();
  }, []);

  const handleNextSegment = () => {
    triggerHaptic('medium');
    stopAnnouncement();
    setIsSpeaking(false);
    if (!isLastSeg) {
      const next = segIndex + 1;
      setSegIndex(next);
      setSecondsLeft((trip.segments[next]?.durationMinutes ?? 5) * 60);
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

  const handleAttachPhoto = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const dataUrl = await compressImageFile(file);
      const next: TripPhotoMemory = {
        id: `photo-${Date.now()}`,
        dataUrl,
        caption: `${currentSeg.fromStop.name.split('(')[0].trim()} — ${
          currentSeg.route ? `Трамвай № ${currentSeg.route.number}` : 'Пересадка'
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

  const minsDisplay = String(Math.floor(secondsLeft / 60)).padStart(2, '0');
  const secsDisplay = String(secondsLeft % 60).padStart(2, '0');

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Живой режим поездки"
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/50 p-0 backdrop-blur-sm sm:items-center sm:p-4"
    >
      <div
        className="m3-card flex max-h-[92dvh] w-full max-w-lg flex-col overflow-hidden rounded-t-3xl sm:rounded-3xl"
        style={{
          backgroundColor: 'var(--bg-surface)',
          color: 'var(--text-primary)',
        }}
      >
        {/* Header */}
        <div
          className="flex items-center justify-between border-b px-5 py-4"
          style={{ borderColor: 'var(--border-hairline)' }}
        >
          <div>
            <div className="text-xs font-bold uppercase tracking-wider" style={{ color: 'var(--accent-primary)' }}>
              В пути · Шаг {segIndex + 1} из {trip.segments.length}
            </div>
            <h2 className="font-display text-base font-extrabold">
              {currentSeg.kind === 'tram'
                ? `Трамвай № ${currentSeg.route?.number}`
                : currentSeg.kind === 'walk'
                  ? 'Пеший переход'
                  : currentSeg.fallbackModeName}
            </h2>
          </div>
          <button
            type="button"
            aria-label="Свернуть режим поездки"
            onClick={() => {
              stopAnnouncement();
              onClose();
            }}
            className="m3-tactile flex h-9 w-9 cursor-pointer items-center justify-center rounded-full"
            style={{ backgroundColor: 'var(--bg-elevated)' }}
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Body */}
        <div className="flex flex-1 flex-col gap-4 overflow-y-auto p-5">
          {/* Timer Card */}
          <div
            className="flex items-center justify-between rounded-3xl p-4"
            style={{
              backgroundColor: 'var(--md-primary-container)',
              color: 'var(--md-on-primary-container)',
            }}
          >
            <div>
              <div className="text-xs font-semibold opacity-80">Ориентировочно до выхода</div>
              <div className="font-mono-num text-3xl font-extrabold">
                {minsDisplay}:{secsDisplay}
              </div>
              <div className="mt-1 text-xs font-medium">
                Выход: <strong>{currentSeg.toStop.name}</strong>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                aria-label={running ? 'Пауза таймера' : 'Продолжить таймер'}
                onClick={() => {
                  triggerHaptic('light');
                  setRunning((r) => !r);
                }}
                className="m3-tactile flex h-11 w-11 cursor-pointer items-center justify-center rounded-2xl"
                style={{
                  backgroundColor: 'var(--bg-surface)',
                  color: 'var(--text-primary)',
                }}
              >
                {running ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
              </button>
            </div>
          </div>

          {/* Photo Reminder Alert */}
          {showPhotoPrompt && (
            <div
              className="flex items-center justify-between gap-3 rounded-2xl border p-3.5"
              style={{
                backgroundColor: 'rgba(217, 119, 6, 0.12)',
                borderColor: 'rgba(217, 119, 6, 0.35)',
              }}
            >
              <div className="flex items-center gap-2.5 text-xs">
                <Bell className="h-4 w-4 shrink-0 text-amber-600" />
                <span>Время фото-чекина! Сделайте снимок из окна трамвая на память.</span>
              </div>
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="m3-tactile rounded-full px-3 py-1.5 text-xs font-bold shrink-0 cursor-pointer"
                style={{
                  backgroundColor: 'var(--accent-primary)',
                  color: 'var(--accent-on-primary)',
                }}
              >
                Снять
              </button>
            </div>
          )}

          {/* Announcer Script Card */}
          <div
            className="rounded-3xl p-4"
            style={{ backgroundColor: 'var(--md-surface-container)' }}
          >
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-bold" style={{ color: 'var(--text-secondary)' }}>
                Автоинформатор вагона
              </span>
              <button
                type="button"
                onClick={handleSpeakCurrent}
                className="m3-tactile inline-flex min-h-[34px] cursor-pointer items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold"
                style={{
                  backgroundColor: 'var(--md-primary-container)',
                  color: 'var(--md-on-primary-container)',
                }}
              >
                {isSpeaking ? <VolumeX className="h-3.5 w-3.5" /> : <Volume2 className="h-3.5 w-3.5" />}
                {isSpeaking ? 'Стоп' : 'Озвучить'}
              </button>
            </div>
            <p className="mt-2 text-xs italic leading-relaxed" style={{ color: 'var(--text-primary)' }}>
              «{announcements.find((a) => a.id.startsWith(currentSeg.id))?.text ?? announcements[0]?.text}»
            </p>
          </div>

          {/* Photo Memories Row */}
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold" style={{ color: 'var(--text-secondary)' }}>
                Фото поездки ({photos.length})
              </span>
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
                onClick={() => {
                  triggerHaptic('light');
                  fileInputRef.current?.click();
                }}
                className="m3-tactile inline-flex min-h-[36px] cursor-pointer items-center gap-1.5 rounded-full px-3.5 py-1 text-xs font-semibold"
                style={{
                  backgroundColor: 'var(--bg-elevated)',
                  color: 'var(--text-primary)',
                }}
              >
                <Camera className="h-3.5 w-3.5" />
                Добавить фото
              </button>
            </div>

            {photos.length > 0 && (
              <div className="grid grid-cols-3 gap-2">
                {photos.map((p) => (
                  <img
                    key={p.id}
                    src={p.dataUrl}
                    alt={p.caption}
                    className="aspect-square w-full rounded-2xl object-cover"
                  />
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Footer Actions */}
        <div
          className="flex items-center justify-between gap-2 border-t px-5 py-4"
          style={{ borderColor: 'var(--border-hairline)' }}
        >
          {!isLastSeg ? (
            <>
              <button
                type="button"
                onClick={() => {
                  stopAnnouncement();
                  triggerHaptic('success');
                  onCompleteTrip(trip, photos);
                }}
                className="m3-tactile inline-flex min-h-[42px] cursor-pointer items-center gap-1.5 rounded-2xl px-3.5 py-2 text-xs font-semibold"
                style={{
                  backgroundColor: 'var(--bg-elevated)',
                  color: 'var(--text-primary)',
                }}
              >
                <Square className="h-3.5 w-3.5" />
                Завершить
              </button>
              <button
                type="button"
                onClick={handleNextSegment}
                className="m3-tactile m3-fab inline-flex min-h-[44px] cursor-pointer items-center gap-1.5 rounded-2xl px-5 py-2 text-sm font-bold"
                style={{
                  backgroundColor: 'var(--accent-primary)',
                  color: 'var(--accent-on-primary)',
                }}
              >
                Следующий шаг
                <ChevronRight className="h-4 w-4" />
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
              className="m3-tactile m3-fab inline-flex min-h-[46px] w-full cursor-pointer items-center justify-center gap-2 rounded-2xl px-5 py-2.5 text-sm font-bold"
              style={{
                backgroundColor: 'var(--accent-primary)',
                color: 'var(--accent-on-primary)',
              }}
            >
              <CheckCircle2 className="h-4 w-4" />
              Завершить поездку и сохранить в Паспорт
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
