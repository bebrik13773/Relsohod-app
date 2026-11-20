import { useRef } from 'react';
import { Award, Camera, History, MapPin, Trash2 } from 'lucide-react';
import { CityId, SavedRoute, TripHistoryEntry, TripPhotoMemory } from '../types/tram';
import { evaluatePassportBadges } from '../utils/passport';
import { triggerHaptic } from '../utils/haptics';

interface PassportHistoryViewProps {
  city: CityId;
  history: TripHistoryEntry[];
  savedRoutes: SavedRoute[];
  onOpenHistoryTrip: (entry: TripHistoryEntry) => void;
  onAddPhotoToHistory: (entryId: string, photo: TripPhotoMemory) => void;
  onDeleteHistoryEntry: (entryId: string) => void;
}

export function PassportHistoryView({
  city,
  history,
  savedRoutes,
  onOpenHistoryTrip,
  onAddPhotoToHistory,
  onDeleteHistoryEntry,
}: PassportHistoryViewProps) {
  const fileInputsRef = useRef<Record<string, HTMLInputElement | null>>({});
  const stats = evaluatePassportBadges(history, savedRoutes, city);

  const handleFileChange = (entryId: string, e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const scale = Math.min(1, 640 / Math.max(img.width, img.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.round(img.width * scale);
        canvas.height = Math.round(img.height * scale);
        const ctx = canvas.getContext('2d');
        ctx?.drawImage(img, 0, 0, canvas.width, canvas.height);
        onAddPhotoToHistory(entryId, {
          id: `hist-photo-${Date.now()}`,
          dataUrl: canvas.toDataURL('image/jpeg', 0.74),
          caption: 'Фото из поездки',
          takenAt: new Date().toISOString(),
        });
      };
      img.src = String(reader.result);
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-6 pb-24 md:pb-10 lg:px-6">
      {/* Passport Summary & Badges */}
      <section aria-label="Паспорт рельсохода" className="flex flex-col gap-4">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 className="font-display flex items-center gap-2 text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
              <Award className="h-6 w-6" style={{ color: 'var(--accent-primary)' }} />
              Паспорт рельсохода
            </h2>
            <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
              Штампы открываются за завершённые, сохранённые и круговые трамвайные маршруты.
            </p>
          </div>
          <div className="flex flex-wrap gap-2 text-xs">
            <span
              className="font-mono-num rounded-full px-3 py-1 font-bold"
              style={{
                backgroundColor: 'var(--md-primary-container)',
                color: 'var(--md-on-primary-container)',
              }}
            >
              Поездок: {stats.totalTrips}
            </span>
            <span
              className="font-mono-num rounded-full px-3 py-1 font-bold"
              style={{
                backgroundColor: 'var(--md-surface-container-high)',
                color: 'var(--text-primary)',
              }}
            >
              Линий: {stats.uniqueLinesCount}
            </span>
            <span
              className="font-mono-num rounded-full px-3 py-1 font-bold"
              style={{
                backgroundColor: 'var(--md-surface-container-high)',
                color: 'var(--text-primary)',
              }}
            >
              Фото: {stats.totalPhotos}
            </span>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {stats.badges.map((badge) => (
            <div
              key={badge.id}
              className="m3-card flex flex-col justify-between rounded-3xl p-4"
              style={{
                backgroundColor: badge.unlocked
                  ? 'var(--md-primary-container)'
                  : 'var(--md-surface-container)',
                color: badge.unlocked
                  ? 'var(--md-on-primary-container)'
                  : 'var(--text-secondary)',
                opacity: badge.unlocked ? 1 : 0.72,
              }}
            >
              <div>
                <div className="flex items-center justify-between">
                  <span className="text-2xl" aria-hidden="true">
                    {badge.icon}
                  </span>
                  <span className="font-mono-num text-[11px] font-bold">
                    {badge.progressText}
                  </span>
                </div>
                <div className="mt-2 text-sm font-bold">{badge.title}</div>
                <p className="mt-1 text-xs leading-snug opacity-85">{badge.description}</p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Trip Photo History */}
      <section aria-label="История поездок с фото" className="flex flex-col gap-4">
        <h2 className="font-display flex items-center gap-2 text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
          <History className="h-5 w-5" style={{ color: 'var(--accent-primary)' }} />
          История поездок с фото ({history.length})
        </h2>

        {history.length === 0 ? (
          <div
            className="m3-card rounded-3xl p-6 text-center text-sm"
            style={{ color: 'var(--text-secondary)' }}
          >
            Запустите живой режим <strong>«В пути»</strong> на любом маршруте и нажмите «Завершить» — поездка и сделанные фото появятся здесь.
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {history.map((entry) => (
              <article
                key={entry.id}
                className="m3-card flex flex-col justify-between gap-3 rounded-3xl p-4 sm:p-5"
                style={{ backgroundColor: 'var(--md-surface-container)' }}
              >
                <div>
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <div className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
                        {entry.trip.startStop.name.split('(')[0].trim()} →{' '}
                        {entry.trip.endStop.name.split('(')[0].trim()}
                      </div>
                      <div className="font-mono-num mt-0.5 text-xs" style={{ color: 'var(--text-muted)' }}>
                        {new Date(entry.completedAt).toLocaleDateString('ru-RU')} ·{' '}
                        {entry.trip.tramLinesUsed.join(' → ')} · ~{entry.trip.totalDurationMinutes} мин
                      </div>
                    </div>
                    <button
                      type="button"
                      aria-label="Удалить запись из истории"
                      onClick={() => {
                        triggerHaptic('light');
                        onDeleteHistoryEntry(entry.id);
                      }}
                      className="m3-tactile flex h-8 w-8 cursor-pointer items-center justify-center rounded-full"
                      style={{ color: 'var(--text-muted)' }}
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>

                  {entry.photos.length > 0 && (
                    <div className="mt-3 grid grid-cols-3 gap-2">
                      {entry.photos.map((photo) => (
                        <img
                          key={photo.id}
                          src={photo.dataUrl}
                          alt={photo.caption}
                          className="aspect-square w-full rounded-2xl object-cover"
                        />
                      ))}
                    </div>
                  )}
                </div>

                <div
                  className="flex flex-wrap items-center justify-between gap-2 border-t pt-3"
                  style={{ borderColor: 'var(--border-hairline)' }}
                >
                  <input
                    ref={(el) => {
                      fileInputsRef.current[entry.id] = el;
                    }}
                    type="file"
                    accept="image/*"
                    onChange={(e) => handleFileChange(entry.id, e)}
                    className="hidden"
                  />
                  <button
                    type="button"
                    onClick={() => {
                      triggerHaptic('light');
                      fileInputsRef.current[entry.id]?.click();
                    }}
                    className="m3-tactile inline-flex min-h-[36px] cursor-pointer items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold"
                    style={{
                      backgroundColor: 'var(--bg-elevated)',
                      color: 'var(--text-primary)',
                    }}
                  >
                    <Camera className="h-3.5 w-3.5" />
                    Добавить фото
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      triggerHaptic('medium');
                      onOpenHistoryTrip(entry);
                    }}
                    className="m3-tactile inline-flex min-h-[36px] cursor-pointer items-center gap-1.5 rounded-full px-3.5 py-1 text-xs font-semibold"
                    style={{
                      backgroundColor: 'var(--md-primary-container)',
                      color: 'var(--md-on-primary-container)',
                    }}
                  >
                    <MapPin className="h-3.5 w-3.5" />
                    На карту
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
