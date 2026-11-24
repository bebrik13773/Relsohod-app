import { useEffect, useMemo, useState } from 'react';
import { CheckCircle2, Clock, Compass, Search, XCircle } from 'lucide-react';
import { CityId, TramRoute, TramStop } from '../types/tram';
import { CITIES } from '../data/cities';
import {
  DAY_NAMES_FULL,
  formatMinutesToClock,
  isRouteActiveAt,
} from '../utils/tramRouter';
import { triggerHaptic } from '../utils/haptics';

interface RoutesDirectoryViewProps {
  city: CityId;
  routes: TramRoute[];
  stops: TramStop[];
  dayOfWeek: number;
  timeMinutes: number;
  onStartFromRoute: (stopId: string) => void;
}

type StatusFilter = 'all' | 'active' | 'planned2027';
type KindFilter = 'all' | 'tram' | 'metro' | 'trolleybus';

export function RoutesDirectoryView({
  city,
  routes,
  stops,
  dayOfWeek,
  timeMinutes,
  onStartFromRoute,
}: RoutesDirectoryViewProps) {
  const [query, setQuery] = useState('');
  const [onlyActiveNow, setOnlyActiveNow] = useState(false);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [kindFilter, setKindFilter] = useState<KindFilter>('all');

  useEffect(() => {
    setKindFilter('all');
    setStatusFilter('all');
  }, [city]);

  const stopNamesById = useMemo(() => {
    const map: Record<string, string> = {};
    for (const s of stops) map[s.id] = s.name;
    return map;
  }, [stops]);

  const hasMetro = useMemo(() => routes.some((r) => r.type === 'metro'), [routes]);
  const hasTrolleybus = useMemo(() => routes.some((r) => r.type === 'trolleybus'), [routes]);

  const effectiveKindFilter: KindFilter =
    (kindFilter === 'metro' && !hasMetro) || (kindFilter === 'trolleybus' && !hasTrolleybus)
      ? 'all'
      : kindFilter;

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return routes.filter((route) => {
      if (statusFilter === 'active' && route.planned2027) return false;
      if (statusFilter === 'planned2027' && !route.planned2027) return false;
      if (effectiveKindFilter === 'metro' && route.type !== 'metro') return false;
      if (effectiveKindFilter === 'trolleybus' && route.type !== 'trolleybus') return false;
      if (
        effectiveKindFilter === 'tram' &&
        (route.type === 'metro' || route.type === 'trolleybus')
      ) {
        return false;
      }

      const active = isRouteActiveAt(route, dayOfWeek, timeMinutes);
      if (onlyActiveNow && !active) return false;
      if (!q) return true;
      const stopsText = route.stopIds.map((id) => stopNamesById[id] || '').join(' ').toLowerCase();
      return (
        route.number.toLowerCase().includes(q) ||
        route.title.toLowerCase().includes(q) ||
        stopsText.includes(q)
      );
    });
  }, [routes, query, onlyActiveNow, statusFilter, effectiveKindFilter, dayOfWeek, timeMinutes, stopNamesById]);

  const kindChips: Array<{ id: KindFilter; label: string }> = [
    { id: 'all', label: 'Все виды' },
    { id: 'tram', label: 'Трамваи' },
    ...(hasMetro ? [{ id: 'metro' as const, label: 'Метро' }] : []),
    ...(hasTrolleybus ? [{ id: 'trolleybus' as const, label: 'Троллейбусы' }] : []),
  ];

  const statusChips: Array<{ id: StatusFilter; label: string }> = [
    { id: 'all', label: 'Все' },
    { id: 'active', label: 'Действующие' },
    { id: 'planned2027', label: 'До лета 2027' },
  ];

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-6 pb-24 md:pb-10 lg:px-6">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-bold sm:text-3xl" style={{ color: 'var(--text-primary)' }}>
            Справочник маршрутов ({CITIES[city].name})
          </h1>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
            Показаны трамваи{hasMetro ? ', линии метро' : ''}
            {hasTrolleybus ? ', троллейбусы' : ''} и проекты до лета 2027 ·{' '}
            {DAY_NAMES_FULL[dayOfWeek].toLowerCase()},{' '}
            <span className="font-mono-num font-semibold">{formatMinutesToClock(timeMinutes)}</span>.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="relative">
            <Search
              className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2"
              style={{ color: 'var(--text-muted)' }}
            />
            <input
              type="search"
              placeholder="Номер или остановка..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              aria-label="Поиск маршрута по номеру или остановке"
              className="min-h-[42px] w-64 rounded-xl border py-2 pr-3 pl-9 text-sm"
              style={{
                backgroundColor: 'var(--bg-surface)',
                color: 'var(--text-primary)',
                borderColor: 'var(--border-hairline)',
              }}
            />
          </div>

          <label className="flex cursor-pointer items-center gap-2 text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
            <input
              type="checkbox"
              checked={onlyActiveNow}
              onChange={(e) => setOnlyActiveNow(e.target.checked)}
              className="h-4 w-4"
              style={{ accentColor: 'var(--accent-primary)' }}
            />
            Только работающие сейчас
          </label>
        </div>
      </div>

      {/* MD3 Filter Chips Bar */}
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="Фильтр по виду транспорта">
          {kindChips.map((chip) => {
            const active = effectiveKindFilter === chip.id;
            return (
              <button
                key={chip.id}
                type="button"
                aria-pressed={active}
                onClick={() => {
                  triggerHaptic('light');
                  setKindFilter(chip.id);
                }}
                className="m3-tactile min-h-[36px] cursor-pointer rounded-full border px-3.5 py-1 text-xs font-semibold"
                style={{
                  backgroundColor: active
                    ? 'var(--md-primary-container)'
                    : 'var(--md-surface-container)',
                  color: active
                    ? 'var(--md-on-primary-container)'
                    : 'var(--text-secondary)',
                  borderColor: active ? 'transparent' : 'var(--md-outline-variant)',
                }}
              >
                {chip.label}
              </button>
            );
          })}
        </div>

        <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="Фильтр по статусу линии">
          {statusChips.map((chip) => {
            const active = statusFilter === chip.id;
            return (
              <button
                key={chip.id}
                type="button"
                aria-pressed={active}
                onClick={() => {
                  triggerHaptic('light');
                  setStatusFilter(chip.id);
                }}
                className="m3-tactile min-h-[36px] cursor-pointer rounded-full border px-3.5 py-1 text-xs font-semibold"
                style={{
                  backgroundColor: active
                    ? 'var(--md-secondary-container)'
                    : 'var(--md-surface-container)',
                  color: active
                    ? 'var(--md-on-secondary-container)'
                    : 'var(--text-secondary)',
                  borderColor: active ? 'transparent' : 'var(--md-outline-variant)',
                }}
              >
                {chip.label}
              </button>
            );
          })}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
        {filtered.map((route) => {
          const active = isRouteActiveAt(route, dayOfWeek, timeMinutes);
          const firstStopId = route.stopIds[0];

          return (
            <article
              key={route.id}
              className="m3-card flex flex-col justify-between rounded-3xl p-4 sm:p-5"
              style={{
                backgroundColor: 'var(--md-surface-container)',
              }}
            >
              <div>
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <span
                      className="font-mono-num inline-flex h-9 min-w-11 items-center justify-center rounded-lg px-2.5 py-1 text-sm font-bold text-white shrink-0"
                      style={{ backgroundColor: route.color }}
                    >
                      {route.number}
                    </span>
                    <div>
                      <div className="flex flex-wrap items-center gap-1.5 text-xs font-medium" style={{ color: 'var(--text-muted)' }}>
                        <span>
                          {route.operator}
                          {route.type === 'circular' ? ' · Кольцевой' : ''}
                          {route.type === 'retro' ? ' · Ретро' : ''}
                          {route.type === 'metro' ? ' · Метро' : ''}
                          {route.type === 'trolleybus' ? ' · Троллейбус' : ''}
                          {route.type === 'chizhik' ? ' · «Чижик»' : ''}
                        </span>
                        {route.planned2027 && (
                          <span
                            className="rounded-full px-2 py-0.5 text-[10px] font-bold"
                            style={{
                              backgroundColor: 'var(--md-secondary-container)',
                              color: 'var(--md-on-secondary-container)',
                            }}
                          >
                            {route.plannedLabel ?? 'До лета 2027'}
                          </span>
                        )}
                      </div>
                      <h2 className="text-sm font-bold leading-snug" style={{ color: 'var(--text-primary)' }}>
                        {route.title}
                      </h2>
                    </div>
                  </div>
                </div>

                <div className="mt-3 flex items-center gap-1.5 text-xs font-medium">
                  {active ? (
                    <>
                      <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600 shrink-0" />
                      <span className="text-emerald-700 dark:text-emerald-400">
                        Работает в выбранное время
                      </span>
                    </>
                  ) : (
                    <>
                      <XCircle className="h-3.5 w-3.5 text-amber-600 shrink-0" />
                      <span className="text-amber-700 dark:text-amber-400">
                        Не работает в выбранное время
                      </span>
                    </>
                  )}
                </div>

                <div
                  className="mt-2 flex items-center gap-1.5 text-xs"
                  style={{ color: 'var(--text-muted)' }}
                >
                  <Clock className="h-3.5 w-3.5 shrink-0" />
                  <span className="font-mono-num">
                    {formatMinutesToClock(route.schedule.startMinutes)}–{formatMinutesToClock(route.schedule.endMinutes)}
                  </span>
                  <span aria-hidden="true">·</span>
                  <span className="font-mono-num">~{route.schedule.intervalMinutes} мин</span>
                </div>

                <p className="mt-2 text-xs leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
                  {route.schedule.note}
                </p>
              </div>

              <div
                className="mt-4 flex items-center justify-between border-t pt-3.5"
                style={{ borderColor: 'var(--border-hairline)' }}
              >
                <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
                  Узлов на трассе: {route.stopIds.length}
                </span>
                <button
                  type="button"
                  onClick={() => {
                    triggerHaptic('medium');
                    onStartFromRoute(firstStopId);
                  }}
                  className="m3-tactile inline-flex min-h-[38px] cursor-pointer items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-semibold"
                  style={{
                    backgroundColor: 'var(--md-primary-container)',
                    color: 'var(--md-on-primary-container)',
                  }}
                >
                  <Compass className="h-3.5 w-3.5" />
                  Стартовать отсюда
                </button>
              </div>
            </article>
          );
        })}
      </div>
    </div>
  );
}
