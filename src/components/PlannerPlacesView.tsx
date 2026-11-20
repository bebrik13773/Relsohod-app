import { useState } from 'react';
import { Image as ImageIcon, MapPin } from 'lucide-react';
import { motion } from 'motion/react';
import { Landmark, LandmarkCategory } from '../types/tram';
import { triggerHaptic } from '../utils/haptics';

interface PlannerPlacesViewProps {
  landmarks: Landmark[];
  selectedLandmarkId: string | null;
  onSelectLandmark: (landmark: Landmark) => void;
  onlyWithPhotos: boolean;
  onChangeOnlyWithPhotos: (only: boolean) => void;
  landmarkCategories: LandmarkCategory[];
  onToggleLandmarkCategory: (category: LandmarkCategory) => void;
}

const LANDMARK_CATEGORY_LABELS: Array<{ id: LandmarkCategory; label: string }> = [
  { id: 'architecture', label: 'Архитектура' },
  { id: 'park', label: 'Парки' },
  { id: 'hidden', label: 'Необычное' },
  { id: 'cafe', label: 'Кофейни' },
  { id: 'grocery', label: 'Пятёрочка / Перекрёсток' },
];

const CATEGORY_RU: Record<LandmarkCategory, string> = {
  architecture: 'Архитектура и музеи',
  park: 'Парк и набережная',
  hidden: 'Скрытая жемчужина',
  cafe: 'Кофейня',
  grocery: 'Пятёрочка / Перекрёсток',
};

export function PlannerPlacesView({
  landmarks,
  selectedLandmarkId,
  onSelectLandmark,
  onlyWithPhotos,
  onChangeOnlyWithPhotos,
  landmarkCategories,
  onToggleLandmarkCategory,
}: PlannerPlacesViewProps) {
  const [brokenImageIds, setBrokenImageIds] = useState<Record<string, boolean>>({});

  return (
    <motion.section
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2, ease: [0.2, 0, 0, 1] }}
      aria-label="Интересное по пути"
      className="flex flex-col gap-4"
    >
      <div className="m3-card flex flex-col gap-3 rounded-3xl p-4 sm:p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-display text-base font-extrabold" style={{ color: 'var(--text-primary)' }}>
            Места вдоль маршрута ({landmarks.length})
          </h2>

          <label
            className="flex cursor-pointer items-center gap-1.5 text-xs font-semibold"
            style={{ color: 'var(--text-secondary)' }}
          >
            <input
              type="checkbox"
              checked={onlyWithPhotos}
              onChange={(e) => {
                triggerHaptic('light');
                onChangeOnlyWithPhotos(e.target.checked);
              }}
              className="h-4 w-4"
              style={{ accentColor: 'var(--accent-primary)' }}
            />
            <ImageIcon className="h-3.5 w-3.5" />
            Только с фото
          </label>
        </div>

        <div className="flex flex-wrap gap-2">
          {LANDMARK_CATEGORY_LABELS.map((cat) => {
            const active = landmarkCategories.includes(cat.id);
            return (
              <button
                key={cat.id}
                type="button"
                onClick={() => {
                  triggerHaptic('light');
                  onToggleLandmarkCategory(cat.id);
                }}
                aria-pressed={active}
                className="m3-tactile min-h-[36px] cursor-pointer rounded-2xl border px-3.5 py-1 text-xs font-semibold whitespace-nowrap shrink-0"
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
                {cat.label}
              </button>
            );
          })}
        </div>
      </div>

      {landmarks.length === 0 ? (
        <div
          className="m3-card rounded-3xl p-6 text-center text-sm"
          style={{ color: 'var(--text-muted)' }}
        >
          По выбранным фильтрам мест на этом маршруте не найдено — снимите ограничения по категориям или фото.
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4">
          {landmarks.map((lm) => {
            const selected = lm.id === selectedLandmarkId;
            const imgFailed = brokenImageIds[lm.id];
            return (
              <button
                key={lm.id}
                type="button"
                onClick={() => {
                  triggerHaptic('light');
                  onSelectLandmark(lm);
                }}
                className="m3-card m3-tactile flex cursor-pointer flex-col overflow-hidden rounded-3xl text-left"
                style={{
                  borderColor: selected ? 'var(--accent-primary)' : 'var(--border-hairline)',
                  boxShadow: selected
                    ? '0 0 0 2px var(--accent-primary)'
                    : 'var(--md-elevation-1)',
                }}
              >
                {lm.photoUrl && !imgFailed ? (
                  <div className="aspect-[4/3] w-full overflow-hidden">
                    <img
                      src={lm.photoUrl}
                      alt={lm.name}
                      referrerPolicy="no-referrer"
                      onError={() => setBrokenImageIds((prev) => ({ ...prev, [lm.id]: true }))}
                      className="h-full w-full object-cover"
                    />
                  </div>
                ) : (
                  <div
                    className="flex aspect-[4/3] w-full flex-col items-center justify-center gap-1.5 p-4 text-center"
                    style={{ backgroundColor: 'var(--bg-elevated)' }}
                  >
                    <MapPin className="h-6 w-6" style={{ color: 'var(--accent-secondary)' }} />
                    <span className="text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
                      {lm.name}
                    </span>
                  </div>
                )}
                <div className="p-4 sm:p-5">
                  <div className="text-sm font-bold leading-snug" style={{ color: 'var(--text-primary)' }}>
                    {lm.name}
                  </div>
                  <div className="mt-1 text-xs" style={{ color: 'var(--text-muted)' }}>
                    {CATEGORY_RU[lm.category]} · {lm.district}
                  </div>
                  <p className="mt-2 text-xs leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
                    {lm.description}
                  </p>
                </div>
              </button>
            );
          })}
        </div>
      )}
    </motion.section>
  );
}
