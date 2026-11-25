import { useEffect, useState } from 'react';
import { Navigation, RotateCcw, Search } from 'lucide-react';
import { CityId, Landmark, TramStop } from '../types/tram';
import { CITIES } from '../data/cities';
import { findNearestStops } from '../utils/tramRouter';
import { triggerHaptic } from '../utils/haptics';

interface AddressDestinationPickerProps {
  city: CityId;
  stops: TramStop[];
  landmarks: Landmark[];
  startStopId: string;
  destinationStopId: string | null;
  destinationAddressLabel: string;
  mapPickMode: 'start' | 'destination' | null;
  onChangeDestinationStop: (stopId: string | null, addressLabel?: string) => void;
  onToggleMapPickMode: (mode: 'start' | 'destination' | null) => void;
}

export function AddressDestinationPicker({
  city,
  stops,
  landmarks,
  startStopId,
  destinationStopId,
  destinationAddressLabel,
  mapPickMode,
  onChangeDestinationStop,
  onToggleMapPickMode,
}: AddressDestinationPickerProps) {
  const [addressQuery, setAddressQuery] = useState(destinationAddressLabel);
  const [searching, setSearching] = useState(false);
  const [lookupNote, setLookupNote] = useState<string | null>(null);

  useEffect(() => {
    setAddressQuery(destinationAddressLabel);
    if (!destinationAddressLabel) {
      setLookupNote(null);
    }
  }, [destinationAddressLabel]);

  const cityCfg = CITIES[city];

  const handleResolveAddress = async (e: React.FormEvent) => {
    e.preventDefault();
    const q = addressQuery.trim();
    if (!q) {
      onChangeDestinationStop(null, '');
      setLookupNote(null);
      return;
    }

    triggerHaptic('light');
    setSearching(true);
    setLookupNote(null);

    const qLower = q.toLowerCase();

    // 1. Instant local match across city stops & verified landmarks/groceries
    const stopMatch = stops.find(
      (s) =>
        s.id !== startStopId &&
        (s.name.toLowerCase().includes(qLower) || s.district.toLowerCase().includes(qLower))
    );
    if (stopMatch) {
      onChangeDestinationStop(stopMatch.id, q);
      setLookupNote(`Подобрана остановка: ${stopMatch.name}`);
      setSearching(false);
      return;
    }

    const landmarkMatch = landmarks.find(
      (l) =>
        l.name.toLowerCase().includes(qLower) || l.description.toLowerCase().includes(qLower)
    );
    if (landmarkMatch && landmarkMatch.nearbyStopIds.length > 0) {
      const targetStop =
        stops.find((s) => landmarkMatch.nearbyStopIds.includes(s.id) && s.id !== startStopId) ??
        findNearestStops(landmarkMatch.lat, landmarkMatch.lng, stops, 1, true)[0]?.stop;
      if (targetStop) {
        onChangeDestinationStop(targetStop.id, `${landmarkMatch.name}`);
        setLookupNote(`Привязано к остановке «${targetStop.name}»`);
        setSearching(false);
        return;
      }
    }

    // 2. Geocoding fallback via OpenStreetMap Nominatim (address-to-coordinates only)
    try {
      const osmQuery = encodeURIComponent(`${q}, ${cityCfg.name}, Россия`);
      const controller = new AbortController();
      const timeoutId = window.setTimeout(() => controller.abort(), 5000);
      const res = await fetch(
        `https://nominatim.openstreetmap.org/search?format=json&limit=1&accept-language=ru&q=${osmQuery}`,
        { signal: controller.signal }
      );
      window.clearTimeout(timeoutId);
      if (res.ok) {
        const items = (await res.json()) as Array<{ lat?: string; lon?: string }>;
        const first = items[0];
        const lat = first?.lat ? Number(first.lat) : NaN;
        const lng = first?.lon ? Number(first.lon) : NaN;
        if (Number.isFinite(lat) && Number.isFinite(lng)) {
          const nearest = findNearestStops(lat, lng, stops, 1, true)[0];
          if (nearest && nearest.distanceKm <= 35) {
            onChangeDestinationStop(nearest.stop.id, q);
            setLookupNote(
              `Адрес найден (OSM) → ближайшая остановка «${nearest.stop.name}» (${nearest.distanceKm.toFixed(1)} км)`
            );
            setSearching(false);
            return;
          }
        }
      }
    } catch {
      // Fall through to helpful hint cleanly
    }

    setLookupNote('Точный адрес не найден — выберите ближайшую остановку из списка ниже или укажите на карте.');
    setSearching(false);
  };

  const handleResetToRandom = () => {
    triggerHaptic('light');
    setAddressQuery('');
    setLookupNote(null);
    onChangeDestinationStop(null, '');
  };

  return (
    <div
      className="flex flex-col gap-2.5 border-t pt-4"
      style={{ borderColor: 'var(--border-hairline)' }}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <label
          htmlFor="dest-stop-select"
          className="text-xs font-bold"
          style={{ color: 'var(--text-secondary)' }}
        >
          Конечный адрес или остановка
        </label>
        <div className="flex items-center gap-1.5">
          {(destinationStopId || addressQuery) && (
            <button
              type="button"
              onClick={handleResetToRandom}
              className="m3-tactile inline-flex min-h-[32px] cursor-pointer items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-semibold"
              style={{
                backgroundColor: 'var(--bg-elevated)',
                color: 'var(--text-secondary)',
              }}
            >
              <RotateCcw className="h-3 w-3" />
              Случайный
            </button>
          )}
          <button
            type="button"
            onClick={() => {
              triggerHaptic('light');
              onToggleMapPickMode(mapPickMode === 'destination' ? null : 'destination');
            }}
            aria-pressed={mapPickMode === 'destination'}
            className="m3-tactile inline-flex min-h-[34px] cursor-pointer items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold whitespace-nowrap shrink-0"
            style={{
              backgroundColor:
                mapPickMode === 'destination'
                  ? 'var(--md-primary-container)'
                  : 'var(--bg-elevated)',
              color:
                mapPickMode === 'destination'
                  ? 'var(--md-on-primary-container)'
                  : 'var(--text-primary)',
            }}
          >
            <Navigation className="h-3.5 w-3.5" />
            Финиш на карте
          </button>
        </div>
      </div>

      {/* Address Search Input */}
      <form onSubmit={handleResolveAddress} className="flex items-center gap-1.5">
        <div className="relative flex-1">
          <Search
            className="pointer-events-none absolute top-1/2 left-3 h-3.5 w-3.5 -translate-y-1/2"
            style={{ color: 'var(--text-muted)' }}
          />
          <input
            type="text"
            value={addressQuery}
            onChange={(e) => setAddressQuery(e.target.value)}
            placeholder={`Адрес или место в ${city === 'omsk' ? 'Омске' : 'СПб'}...`}
            aria-label="Поиск финального адреса или места"
            className="min-h-[42px] w-full rounded-2xl border py-1.5 pr-3 pl-8 text-xs font-medium focus-visible:outline-2"
            style={{
              backgroundColor: 'var(--bg-input)',
              color: 'var(--text-primary)',
              borderColor: 'var(--border-hairline)',
            }}
          />
        </div>
        <button
          type="submit"
          disabled={searching}
          className="m3-tactile min-h-[42px] cursor-pointer rounded-2xl px-3.5 py-1.5 text-xs font-bold shrink-0"
          style={{
            backgroundColor: 'var(--md-primary-container)',
            color: 'var(--md-on-primary-container)',
          }}
        >
          {searching ? '...' : 'Найти'}
        </button>
      </form>

      {lookupNote && (
        <p className="text-[11px] leading-snug" style={{ color: 'var(--accent-secondary)' }}>
          {lookupNote}
        </p>
      )}

      {/* Full Stops Dropdown */}
      <select
        id="dest-stop-select"
        value={destinationStopId ?? ''}
        onChange={(e) => {
          triggerHaptic('light');
          const nextId = e.target.value ? e.target.value : null;
          setLookupNote(null);
          if (!nextId) setAddressQuery('');
          onChangeDestinationStop(nextId, '');
        }}
        className="min-h-[46px] w-full rounded-2xl border px-3.5 py-2 text-sm font-medium focus-visible:outline-2"
        style={{
          backgroundColor: 'var(--bg-input)',
          color: 'var(--text-primary)',
          borderColor: 'var(--border-hairline)',
        }}
      >
        <option value="">Случайная точка в городе</option>
        {stops
          .filter((s) => s.id !== startStopId)
          .map((stop) => (
            <option key={stop.id} value={stop.id}>
              {stop.name} — {stop.district}
              {stop.isolatedFromMainTram ? ' (нет прямого трамвая)' : ''}
            </option>
          ))}
      </select>
    </div>
  );
}
