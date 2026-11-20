import { useState } from 'react';
import { Award, Bookmark, MapPin, Trash2, TramFront } from 'lucide-react';
import { CityId, SavedRoute, TripHistoryEntry, TripPhotoMemory } from '../types/tram';
import { triggerHaptic } from '../utils/haptics';
import { PassportHistoryView } from './PassportHistoryView';

interface FavoritesViewProps {
  city: CityId;
  savedRoutes: SavedRoute[];
  history: TripHistoryEntry[];
  onOpenRoute: (saved: SavedRoute) => void;
  onDeleteRoute: (id: string) => void;
  onOpenHistoryTrip: (entry: TripHistoryEntry) => void;
  onAddPhotoToHistory: (entryId: string, photo: TripPhotoMemory) => void;
  onDeleteHistoryEntry: (entryId: string) => void;
  onGoToPlanner: () => void;
}

export function FavoritesView({
  city,
  savedRoutes,
  history,
  onOpenRoute,
  onDeleteRoute,
  onOpenHistoryTrip,
  onAddPhotoToHistory,
  onDeleteHistoryEntry,
  onGoToPlanner,
}: FavoritesViewProps) {
  const [subTab, setSubTab] = useState<'saved' | 'passport'>('saved');

  return (
    <div className="mx-auto w-full max-w-5xl pb-24 md:pb-10">
      {/* Tonal segmented switcher: Saved vs Passport & Photo History */}
      <div className="px-4 pt-5 lg:px-6">
        <div
          role="tablist"
          aria-label="Разделы избранного и паспорта"
          className="inline-grid w-full max-w-md grid-cols-2 gap-1 rounded-2xl p-1"
          style={{
            backgroundColor: 'var(--bg-elevated)',
            border: '1px solid var(--border-hairline)',
          }}
        >
          <button
            type="button"
            role="tab"
            aria-selected={subTab === 'saved'}
            onClick={() => {
              triggerHaptic('light');
              setSubTab('saved');
            }}
            className="m3-tactile inline-flex min-h-[38px] cursor-pointer items-center justify-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-semibold whitespace-nowrap"
            style={{
              backgroundColor:
                subTab === 'saved' ? 'var(--md-primary-container)' : 'transparent',
              color:
                subTab === 'saved' ? 'var(--md-on-primary-container)' : 'var(--text-muted)',
            }}
          >
            <Bookmark className="h-3.5 w-3.5" />
            Сохранённые ({savedRoutes.length})
          </button>

          <button
            type="button"
            role="tab"
            aria-selected={subTab === 'passport'}
            onClick={() => {
              triggerHaptic('light');
              setSubTab('passport');
            }}
            className="m3-tactile inline-flex min-h-[38px] cursor-pointer items-center justify-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-semibold whitespace-nowrap"
            style={{
              backgroundColor:
                subTab === 'passport' ? 'var(--md-primary-container)' : 'transparent',
              color:
                subTab === 'passport' ? 'var(--md-on-primary-container)' : 'var(--text-muted)',
            }}
          >
            <Award className="h-3.5 w-3.5" />
            Паспорт и фото ({history.length})
          </button>
        </div>
      </div>

      {subTab === 'passport' ? (
        <PassportHistoryView
          city={city}
          history={history}
          savedRoutes={savedRoutes}
          onOpenHistoryTrip={onOpenHistoryTrip}
          onAddPhotoToHistory={onAddPhotoToHistory}
          onDeleteHistoryEntry={onDeleteHistoryEntry}
        />
      ) : savedRoutes.length === 0 ? (
        <div className="mx-auto flex max-w-xl flex-col items-center justify-center px-6 py-16 text-center">
          <Bookmark className="h-10 w-10" style={{ color: 'var(--accent-secondary)' }} />
          <h2 className="font-display mt-4 text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
            В избранном пока пусто
          </h2>
          <p className="mt-2 text-sm leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
            Соберите трамвайное путешествие и нажмите «Сохранить» — маршрут появится здесь и останется в памяти браузера.
          </p>
          <button
            type="button"
            onClick={() => {
              triggerHaptic('light');
              onGoToPlanner();
            }}
            className="m3-tactile m3-fab mt-6 min-h-[44px] cursor-pointer rounded-2xl px-6 py-2.5 text-sm font-semibold"
            style={{
              backgroundColor: 'var(--accent-primary)',
              color: 'var(--accent-on-primary)',
            }}
          >
            Подобрать маршрут
          </button>
        </div>
      ) : (
        <div className="px-4 py-6 lg:px-6">
          <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
            <div>
              <h1 className="font-display text-2xl font-bold sm:text-3xl" style={{ color: 'var(--text-primary)' }}>
                Избранные маршруты
              </h1>
              <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
                Сохранённые цепочки трамваев хранятся локально в вашем браузере.
              </p>
            </div>
            <span className="font-mono-num text-xs font-medium" style={{ color: 'var(--text-muted)' }}>
              Всего маршрутов: {savedRoutes.length}
            </span>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {savedRoutes.map((item) => {
              const { trip } = item;
              return (
                <article
                  key={item.id}
                  className="m3-card flex flex-col justify-between rounded-3xl p-4 sm:p-5"
                  style={{
                    backgroundColor: 'var(--md-surface-container)',
                  }}
                >
                  <div>
                    <div className="flex items-start justify-between gap-3">
                      <h2 className="text-base font-bold leading-snug" style={{ color: 'var(--text-primary)' }}>
                        {item.customTitle}
                      </h2>
                      <button
                        type="button"
                        aria-label={`Удалить маршрут «${item.customTitle}»`}
                        onClick={() => {
                          triggerHaptic('light');
                          onDeleteRoute(item.id);
                        }}
                        className="m3-tactile flex min-h-[36px] min-w-[36px] cursor-pointer items-center justify-center rounded-full transition-colors hover:opacity-75"
                        style={{ color: 'var(--text-muted)' }}
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>

                    <div className="mt-1.5 flex flex-wrap items-center gap-2 text-xs" style={{ color: 'var(--text-muted)' }}>
                      <span className="font-mono-num font-semibold" style={{ color: 'var(--text-primary)' }}>
                        ~{trip.totalDurationMinutes} мин
                      </span>
                      <span aria-hidden="true">·</span>
                      <span>
                        Трамваи: {trip.tramLinesUsed.join(' → ')}
                      </span>
                      <span aria-hidden="true">·</span>
                      <span>
                        {trip.districtsVisited.join(', ')}
                      </span>
                    </div>

                    {item.note && (
                      <p className="mt-2.5 text-xs leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
                        {item.note}
                      </p>
                    )}

                    <div className="mt-3.5 flex flex-wrap gap-1.5">
                      {trip.segments
                        .filter((s) => s.kind === 'tram' && s.route)
                        .map((seg) => (
                          <span
                            key={seg.id}
                            className="font-mono-num inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs font-bold text-white"
                            style={{ backgroundColor: seg.route?.color }}
                          >
                            <TramFront className="h-3 w-3" />
                            {seg.route?.number}
                          </span>
                        ))}
                    </div>
                  </div>

                  <div
                    className="mt-4 flex items-center justify-between border-t pt-3.5"
                    style={{ borderColor: 'var(--border-hairline)' }}
                  >
                    <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
                      Сохранено {new Date(item.savedAt).toLocaleDateString('ru-RU')}
                    </span>
                    <button
                      type="button"
                      onClick={() => {
                        triggerHaptic('medium');
                        onOpenRoute(item);
                      }}
                      className="m3-tactile inline-flex min-h-[38px] cursor-pointer items-center gap-1.5 rounded-full px-4 py-1.5 text-xs font-semibold"
                      style={{
                        backgroundColor: 'var(--md-primary-container)',
                        color: 'var(--md-on-primary-container)',
                      }}
                    >
                      <MapPin className="h-3.5 w-3.5" />
                      Открыть на карте
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
