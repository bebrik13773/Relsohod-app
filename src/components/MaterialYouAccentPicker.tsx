import { Check } from 'lucide-react';
import { AccentSeed } from '../types/tram';
import { triggerHaptic } from '../utils/haptics';

interface MaterialYouAccentPickerProps {
  accent: AccentSeed;
  onChangeAccent: (accent: AccentSeed) => void;
  disabled?: boolean;
}

const ACCENT_SEEDS: Array<{ id: AccentSeed; label: string; swatch: string }> = [
  { id: 'neva', label: 'Нева (по умолчанию)', swatch: '#0f5e8c' },
  { id: 'sage', label: 'Шалфей', swatch: '#1b6d4f' },
  { id: 'terracotta', label: 'Терракота', swatch: '#a84a2a' },
  { id: 'indigo', label: 'Индиго', swatch: '#4b48a8' },
  { id: 'amber', label: 'Янтарь', swatch: '#9a5b13' },
];

export function MaterialYouAccentPicker({
  accent,
  onChangeAccent,
  disabled = false,
}: MaterialYouAccentPickerProps) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-semibold" style={{ color: 'var(--text-secondary)' }}>
          Акцент Material You
        </span>
        <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
          {ACCENT_SEEDS.find((s) => s.id === accent)?.label}
        </span>
      </div>

      <div className="flex items-center gap-2.5">
        {ACCENT_SEEDS.map((seed) => {
          const active = accent === seed.id;
          return (
            <button
              key={seed.id}
              type="button"
              disabled={disabled}
              aria-label={`Акцент ${seed.label}`}
              aria-pressed={active}
              onClick={() => {
                triggerHaptic('light');
                onChangeAccent(seed.id);
              }}
              className="m3-tactile relative flex h-9 w-9 cursor-pointer items-center justify-center rounded-full border-2 disabled:cursor-not-allowed disabled:opacity-45"
              style={{
                backgroundColor: seed.swatch,
                borderColor: active ? 'var(--text-primary)' : 'transparent',
                boxShadow: active ? '0 0 0 2px var(--bg-surface) inset' : 'none',
              }}
            >
              {active && <Check className="h-4 w-4 text-white" strokeWidth={2.75} />}
            </button>
          );
        })}
      </div>
    </div>
  );
}
