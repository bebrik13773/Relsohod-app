import {
  CloudRain,
  LocateFixed,
  MapPin,
  Repeat,
  Shuffle,
  Snowflake,
  Sparkles,
  Sun,
} from 'lucide-react';
import { motion } from 'motion/react';
import {
  CityId,
  DurationPreset,
  Landmark,
  ModeBalance,
  RouteThemeFilter,
  TramStop,
  WeatherMode,
} from '../types/tram';
import {
  DAY_NAMES_SHORT,
  formatMinutesToClock,
} from '../utils/tramRouter';
import { triggerHaptic } from '../utils/haptics';
import { AddressDestinationPicker } from './AddressDestinationPicker';
import { ModeTriangleSlider } from './ModeTriangleSlider';

const TIME_SLOTS_MINUTES = [
  15,
  3 * 60,
  6 * 60 + 30,
  8 * 60,
  9 * 60,
  10 * 60,
  11 * 60,
  12 * 60,
  13 * 60,
  14 * 60,
  15 * 60,
  16 * 60,
  17 * 60,
  18 * 60,
  19 * 60,
  20 * 60,
  21 * 60,
  22 * 60,
  23 * 60,
];

interface PlannerParamsViewProps {
  city: CityId;
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
  modeBalance: ModeBalance;
  onChangeModeBalance: (balance: ModeBalance) => void;
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
  onGenerate: () => void;
}

const DURATION_OPTIONS: Array<{ id: DurationPreset; label: string }> = [
  { id: 'any', label: 'Любая' },
  { id: 'short', label: '~30 мин' },
  { id: 'medium', label: '~1 ч' },
  { id: 'long', label: '~2+ ч' },
];

const WEATHER_OPTIONS: Array<{ id: WeatherMode; label: string; icon: typeof Sun }> = [
  { id: 'clear', label: 'Ясно', icon: Sun },
  { id: 'rain', label: 'Дождь', icon: CloudRain },
  { id: 'frost', label: 'Мороз', icon: Snowflake },
];

export function PlannerParamsView({
  city,
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
  modeBalance,
  onChangeModeBalance,
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
}: PlannerParamsViewProps) {
  const tramStops = stops.filter((s) => !s.isolatedFromMainTram);

  const routeThemes: Array<{ id: RouteThemeFilter; label: string }> = [
    { id: 'any', label: 'Полный рандом' },
    { id: 'center', label: city === 'omsk' ? 'Центр и Цирк' : 'Центр и мосты' },
    { id: 'parks', label: 'Парки и набережные' },
    { id: 'suburbs', label: city === 'omsk' ? 'Окраины и Амур' : 'Окраины и Чижики' },
    { id: 'maritime', label: city === 'omsk' ? 'Вдоль Иртыша' : 'К заливу и верфям' },
  ];

  let snappedTimeSlot = TIME_SLOTS_MINUTES[0];
  let bestDiff = Infinity;
  for (const slot of TIME_SLOTS_MINUTES) {
    const diff = Math.abs(slot - timeMinutes);
    if (diff < bestDiff) {
      bestDiff = diff;
      snappedTimeSlot = slot;
    }
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2, ease: [0.2, 0, 0, 1] }}
      className="flex flex-col gap-5"
    >
      <div>
        <h1
          className="font-display text-2xl font-extrabold leading-tight text-balance"
          style={{ color: 'var(--text-primary)' }}
        >
          Куда поехать на трамвае
        </h1>
        <p className="mt-1.5 text-sm leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
          Настройте точку старта, настроение и время — соберём живую цепочку трамваев с пересадками.
        </p>
      </div>

      {/* Block 1: Start & Destination Address Picker (Material You Tonal Surface) */}
      <div className="m3-card flex flex-col gap-4 rounded-3xl p-4 sm:p-5">
        <div className="flex flex-col gap-2">
          <div className="flex items-center justify-between gap-2">
            <label
              htmlFor="start-stop-select"
              className="text-xs font-bold"
              style={{ color: 'var(--text-secondary)' }}
            >
              Отправление
            </label>
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={() => {
                  triggerHaptic('light');
                  onDetectLocation();
                }}
                disabled={geoStatus.state === 'loading'}
                className="m3-tactile inline-flex min-h-[36px] cursor-pointer items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold whitespace-nowrap shrink-0"
                style={{
                  backgroundColor: 'var(--bg-elevated)',
                  color: 'var(--text-primary)',
                }}
              >
                <LocateFixed className="h-3.5 w-3.5" />
                {geoStatus.state === 'loading' ? 'Поиск...' : 'Рядом со мной'}
              </button>
              <button
                type="button"
                onClick={() => {
                  triggerHaptic('light');
                  onToggleMapPickMode(mapPickMode === 'start' ? null : 'start');
                }}
                aria-pressed={mapPickMode === 'start'}
                className="m3-tactile inline-flex min-h-[36px] cursor-pointer items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold whitespace-nowrap shrink-0"
                style={{
                  backgroundColor:
                    mapPickMode === 'start'
                      ? 'var(--md-primary-container)'
                      : 'var(--bg-elevated)',
                  color:
                    mapPickMode === 'start'
                      ? 'var(--md-on-primary-container)'
                      : 'var(--text-primary)',
                }}
              >
                <MapPin className="h-3.5 w-3.5" />
                На карте
              </button>
            </div>
          </div>

          <select
            id="start-stop-select"
            value={startStopId}
            onChange={(e) => {
              triggerHaptic('light');
              onChangeStartStop(e.target.value);
            }}
            className="min-h-[46px] w-full rounded-2xl border px-3.5 py-2 text-sm font-medium focus-visible:outline-2"
            style={{
              backgroundColor: 'var(--bg-input)',
              color: 'var(--text-primary)',
              borderColor: 'var(--border-hairline)',
            }}
          >
            {tramStops.map((stop) => (
              <option key={stop.id} value={stop.id}>
                {stop.name} — {stop.district}
              </option>
            ))}
          </select>

          {geoStatus.message && (
            <p
              className="text-xs"
              style={{
                color: geoStatus.state === 'error' ? '#b91c1c' : 'var(--accent-secondary)',
              }}
            >
              {geoStatus.message}
            </p>
          )}
        </div>

        <AddressDestinationPicker
          city={city}
          stops={stops}
          landmarks={landmarks}
          startStopId={startStopId}
          destinationStopId={destinationStopId}
          destinationAddressLabel={destinationAddressLabel}
          mapPickMode={mapPickMode}
          onChangeDestinationStop={(stopId, label) => {
            if (stopId && loopMode) {
              onChangeLoopMode(false);
            }
            onChangeDestinationStop(stopId, label);
          }}
          onToggleMapPickMode={onToggleMapPickMode}
        />
      </div>

      {/* Block 2: Triangular Mode Balance + Mood, Weather & Duration + Schedule */}
      <div className="m3-card flex flex-col gap-5 rounded-3xl p-4 sm:p-5">
        <ModeTriangleSlider value={modeBalance} onChange={onChangeModeBalance} />

        <div
          className="flex flex-col gap-2.5 border-t pt-4"
          style={{ borderColor: 'var(--border-hairline)' }}
        >
          <span className="text-xs font-bold" style={{ color: 'var(--text-secondary)' }}>
            Настроение маршрута
          </span>
          <div className="flex flex-wrap gap-2">
            {routeThemes.map((item) => {
              const active = themeFilter === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => {
                    triggerHaptic('light');
                    onChangeThemeFilter(item.id);
                  }}
                  aria-pressed={active}
                  className="m3-tactile min-h-[38px] cursor-pointer rounded-2xl border px-3.5 py-1.5 text-xs font-semibold whitespace-nowrap shrink-0"
                  style={{
                    backgroundColor: active
                      ? 'var(--md-primary-container)'
                      : 'var(--bg-input)',
                    color: active
                      ? 'var(--md-on-primary-container)'
                      : 'var(--text-secondary)',
                    borderColor: active ? 'transparent' : 'var(--border-hairline)',
                  }}
                >
                  {item.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* Idea 5: Weather picker */}
        <div
          className="flex flex-col gap-2 border-t pt-4"
          style={{ borderColor: 'var(--border-hairline)' }}
        >
          <span className="text-xs font-bold" style={{ color: 'var(--text-secondary)' }}>
            Погода и пересадки
          </span>
          <div
            className="grid grid-cols-3 gap-1 rounded-2xl p-1"
            style={{ backgroundColor: 'var(--bg-elevated)' }}
          >
            {WEATHER_OPTIONS.map((w) => {
              const Icon = w.icon;
              const active = weatherMode === w.id;
              return (
                <button
                  key={w.id}
                  type="button"
                  onClick={() => {
                    triggerHaptic('light');
                    onChangeWeatherMode(w.id);
                  }}
                  aria-pressed={active}
                  className="m3-tactile inline-flex min-h-[36px] cursor-pointer items-center justify-center gap-1.5 rounded-xl px-2 py-1 text-xs font-semibold whitespace-nowrap"
                  style={{
                    backgroundColor: active ? 'var(--md-primary-container)' : 'transparent',
                    color: active ? 'var(--md-on-primary-container)' : 'var(--text-muted)',
                  }}
                >
                  <Icon className="h-3.5 w-3.5" />
                  {w.label}
                </button>
              );
            })}
          </div>
        </div>

        <div
          className="grid grid-cols-1 gap-4 border-t pt-4"
          style={{ borderColor: 'var(--border-hairline)' }}
        >
          <div className="flex flex-col gap-2">
            <span className="text-xs font-bold" style={{ color: 'var(--text-secondary)' }}>
              Время в пути
            </span>
            <div
              className="grid grid-cols-4 gap-1 rounded-2xl p-1"
              style={{
                backgroundColor: 'var(--bg-elevated)',
              }}
            >
              {DURATION_OPTIONS.map((opt) => {
                const active = durationPreset === opt.id;
                return (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => {
                      triggerHaptic('light');
                      onChangeDurationPreset(opt.id);
                    }}
                    aria-pressed={active}
                    className="m3-tactile min-h-[36px] cursor-pointer rounded-xl px-1.5 py-1 text-xs font-semibold whitespace-nowrap"
                    style={{
                      backgroundColor: active
                        ? 'var(--md-primary-container)'
                        : 'transparent',
                      color: active
                        ? 'var(--md-on-primary-container)'
                        : 'var(--text-muted)',
                    }}
                  >
                    {opt.label}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <label
              htmlFor="trip-time-input"
              className="text-xs font-bold"
              style={{ color: 'var(--text-secondary)' }}
            >
              График (день и время)
            </label>
            <div className="flex items-center gap-2">
              <select
                aria-label="День недели"
                value={dayOfWeek}
                onChange={(e) => {
                  triggerHaptic('light');
                  onChangeDayOfWeek(Number(e.target.value));
                }}
                className="min-h-[44px] rounded-2xl border px-3 py-1.5 text-xs font-semibold"
                style={{
                  backgroundColor: 'var(--bg-input)',
                  color: 'var(--text-primary)',
                  borderColor: 'var(--border-hairline)',
                }}
              >
                {DAY_NAMES_SHORT.map((name, idx) => (
                  <option key={name} value={idx}>
                    {name}
                  </option>
                ))}
              </select>
              <select
                id="trip-time-input"
                value={snappedTimeSlot}
                onChange={(e) => {
                  triggerHaptic('light');
                  onChangeTimeMinutes(Number(e.target.value));
                }}
                className="font-mono-num min-h-[44px] w-full rounded-2xl border px-3 py-1.5 text-xs font-semibold"
                style={{
                  backgroundColor: 'var(--bg-input)',
                  color: 'var(--text-primary)',
                  borderColor: 'var(--border-hairline)',
                }}
              >
                {TIME_SLOTS_MINUTES.map((mins) => (
                  <option key={mins} value={mins}>
                    {formatMinutesToClock(mins)}
                    {mins === 3 * 60 ? ' (ночь)' : ''}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>
      </div>

      {/* Block 3: Options (Loop, Rare Fleet, Fallback, Network, Accent Seed) */}
      <div className="m3-card flex flex-col gap-3.5 rounded-3xl p-4 sm:p-5">
        {/* Idea 4: Loop Route */}
        <label className="flex cursor-pointer items-start gap-3 text-xs">
          <input
            type="checkbox"
            checked={loopMode}
            onChange={(e) => {
              triggerHaptic('light');
              const checked = e.target.checked;
              onChangeLoopMode(checked);
              if (checked) {
                onChangeDestinationStop(null, '');
              }
            }}
            className="mt-0.5 h-4 w-4 shrink-0"
            style={{ accentColor: 'var(--accent-primary)' }}
          />
          <span className="leading-relaxed">
            <span className="flex items-center gap-1.5 font-bold" style={{ color: 'var(--text-primary)' }}>
              <Repeat className="h-3.5 w-3.5" />
              Круговая прогулка (старт = финиш)
            </span>
            <span style={{ color: 'var(--text-muted)' }}>
              Кольцевой маршрут вернёт вас на исходную остановку другим путём.
            </span>
          </span>
        </label>

        {/* Idea 6: Special / Rare Fleet Hunt */}
        <label
          className="flex cursor-pointer items-start gap-3 border-t pt-3.5 text-xs"
          style={{ borderColor: 'var(--border-hairline)' }}
        >
          <input
            type="checkbox"
            checked={preferSpecialFleet}
            onChange={(e) => {
              triggerHaptic('light');
              onChangePreferSpecialFleet(e.target.checked);
            }}
            className="mt-0.5 h-4 w-4 shrink-0"
            style={{ accentColor: 'var(--accent-primary)' }}
          />
          <span className="leading-relaxed">
            <span className="flex items-center gap-1.5 font-bold" style={{ color: 'var(--text-primary)' }}>
              <Sparkles className="h-3.5 w-3.5" />
              Редкий борт
            </span>
            <span style={{ color: 'var(--text-muted)' }}>
              {city === 'omsk'
                ? 'Приоритет историческим центральным и кольцевым линиям Омска.'
                : 'Приоритет ретро-вагонам, кольцевому А и скоростным линиям «Чижик».'}
            </span>
          </span>
        </label>

        <label
          className="flex cursor-pointer items-start gap-3 border-t pt-3.5 text-xs"
          style={{ borderColor: 'var(--border-hairline)' }}
        >
          <input
            type="checkbox"
            checked={allowFallbackTransit}
            onChange={(e) => {
              triggerHaptic('light');
              onChangeAllowFallbackTransit(e.target.checked);
            }}
            className="mt-0.5 h-4 w-4 shrink-0"
            style={{ accentColor: 'var(--accent-primary)' }}
          />
          <span className="leading-relaxed">
            <span className="block font-bold" style={{ color: 'var(--text-primary)' }}>
              Резервный транспорт при необходимости
            </span>
            <span style={{ color: 'var(--text-muted)' }}>
              Подключит автобус или метро только на участке, где трамвай физически не проходит и пешком далеко.
            </span>
          </span>
        </label>

        <label
          className="flex cursor-pointer items-center gap-3 border-t pt-3.5 text-xs"
          style={{ borderColor: 'var(--border-hairline)' }}
        >
          <input
            type="checkbox"
            checked={showAllNetwork}
            onChange={(e) => {
              triggerHaptic('light');
              onChangeShowAllNetwork(e.target.checked);
            }}
            className="h-4 w-4 shrink-0"
            style={{ accentColor: 'var(--accent-primary)' }}
          />
          <span className="font-semibold" style={{ color: 'var(--text-secondary)' }}>
            Показывать всю сеть трамваев {city === 'omsk' ? 'Омска' : 'СПб'} на карте
          </span>
        </label>

      </div>

      {/* Primary Material You Extended FAB CTA */}
      <button
        type="button"
        onClick={() => {
          triggerHaptic('medium');
          onGenerate();
        }}
        className="m3-fab flex min-h-[54px] w-full cursor-pointer items-center justify-center gap-2.5 rounded-2xl px-5 py-3.5 text-sm font-bold"
        style={{
          backgroundColor: 'var(--accent-primary)',
          color: 'var(--accent-on-primary)',
        }}
      >
        <Shuffle className="h-4 w-4" />
        {loopMode
          ? 'Построить круговой маршрут'
          : destinationStopId
            ? 'Построить трамвайный маршрут'
            : 'Подобрать случайный маршрут'}
      </button>
    </motion.div>
  );
}
