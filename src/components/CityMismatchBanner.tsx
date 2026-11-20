import { AlertTriangle, ArrowRightLeft, X } from 'lucide-react';
import { CityId } from '../types/tram';
import { CITIES, CityDetectionResult } from '../data/cities';
import { triggerHaptic } from '../utils/haptics';

interface CityMismatchBannerProps {
  selectedCity: CityId;
  detection: CityDetectionResult | null;
  dismissed: boolean;
  onSwitchCity: (city: CityId) => void;
  onDismiss: () => void;
}

export function CityMismatchBanner({
  selectedCity,
  detection,
  dismissed,
  onSwitchCity,
  onDismiss,
}: CityMismatchBannerProps) {
  if (!detection || dismissed) return null;

  const isOtherCity = detection.detected === 'other';
  const isOppositeSupported =
    detection.detected !== 'other' && detection.detected !== selectedCity;

  if (!isOtherCity && !isOppositeSupported) return null;

  const detectedCityName =
    detection.detected !== 'other' ? CITIES[detection.detected].name : null;

  return (
    <div
      role="status"
      className="border-b px-4 py-2.5 text-xs"
      style={{
        backgroundColor: 'var(--md-primary-container)',
        color: 'var(--md-on-primary-container)',
        borderColor: 'var(--border-hairline)',
      }}
    >
      <div className="mx-auto flex max-w-[1600px] flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <AlertTriangle className="h-4 w-4 shrink-0 text-amber-600" />
          <span>
            {isOppositeSupported && detectedCityName ? (
              <>
                По геолокации вы сейчас в городе <strong>{detectedCityName}</strong>, а в
                приложении выбран город <strong>{CITIES[selectedCity].name}</strong>.
              </>
            ) : (
              <>
                Вы сейчас за пределами Санкт-Петербурга и Омска — показана сеть города{' '}
                <strong>{CITIES[selectedCity].name}</strong> в режиме планирования.
              </>
            )}
          </span>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          {isOppositeSupported && detection.detected !== 'other' && (
            <button
              type="button"
              onClick={() => {
                triggerHaptic('medium');
                onSwitchCity(detection.detected as CityId);
              }}
              className="m3-tactile inline-flex min-h-[32px] cursor-pointer items-center gap-1 rounded-full px-3 py-1 text-xs font-bold"
              style={{
                backgroundColor: 'var(--accent-primary)',
                color: 'var(--accent-on-primary)',
              }}
            >
              <ArrowRightLeft className="h-3.5 w-3.5" />
              Переключить на {CITIES[detection.detected].shortName}
            </button>
          )}
          <button
            type="button"
            aria-label="Скрыть предупреждение о городе"
            onClick={() => {
              triggerHaptic('light');
              onDismiss();
            }}
            className="m3-tactile flex h-7 w-7 cursor-pointer items-center justify-center rounded-full"
            style={{ backgroundColor: 'color-mix(in srgb, var(--bg-surface) 70%, transparent)' }}
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
}
