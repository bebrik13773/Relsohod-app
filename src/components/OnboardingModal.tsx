import { useState } from 'react';
import {
  CheckCircle2,
  Compass,
  LocateFixed,
  MapPin,
  Navigation2,
  Sparkles,
  TramFront,
  AlertTriangle,
} from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { CityId } from '../types/tram';
import { CITIES, CityDetectionResult } from '../data/cities';
import { triggerHaptic } from '../utils/haptics';
import { BrandLogo } from './BrandLogo';

interface OnboardingModalProps {
  isOpen: boolean;
  selectedCity: CityId;
  onSelectCity: (city: CityId) => void;
  geoStatus: { state: 'idle' | 'loading' | 'error' | 'ok'; message?: string };
  cityDetection: CityDetectionResult | null;
  onRequestLocation: () => void;
  onComplete: () => void;
}

export function OnboardingModal({
  isOpen,
  selectedCity,
  onSelectCity,
  geoStatus,
  cityDetection,
  onRequestLocation,
  onComplete,
}: OnboardingModalProps) {
  const [step, setStep] = useState<0 | 1 | 2>(0);

  if (!isOpen) return null;

  const isMismatch =
    cityDetection !== null &&
    (cityDetection.detected === 'other' || cityDetection.detected !== selectedCity);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Знакомство с Рельсоходом"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4 backdrop-blur-sm"
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.96, y: 12 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        transition={{ duration: 0.24, ease: [0.2, 0, 0, 1] }}
        className="m3-card flex max-h-[90dvh] w-full max-w-lg flex-col overflow-hidden rounded-3xl"
        style={{
          backgroundColor: 'var(--bg-surface)',
          color: 'var(--text-primary)',
        }}
      >
        {/* Top step progress pills */}
        <div
          className="flex items-center justify-between border-b px-5 py-4"
          style={{ borderColor: 'var(--border-hairline)' }}
        >
          <div className="flex items-center gap-2.5">
            <BrandLogo size="sm" />
            <span className="font-display text-base font-extrabold tracking-tight">
              Рельсоход
            </span>
          </div>
          <div className="flex items-center gap-1.5" aria-label={`Шаг ${step + 1} из 3`}>
            {[0, 1, 2].map((idx) => (
              <span
                key={idx}
                className="h-2 rounded-full transition-all"
                style={{
                  width: step === idx ? '24px' : '8px',
                  backgroundColor:
                    step === idx ? 'var(--accent-primary)' : 'var(--md-surface-container-high)',
                }}
              />
            ))}
          </div>
        </div>

        {/* Scrollable body */}
        <div className="flex-1 overflow-y-auto p-5 sm:p-6">
          <AnimatePresence mode="wait">
            {step === 0 && (
              <motion.div
                key="step-0"
                initial={{ opacity: 0, x: 12 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -12 }}
                transition={{ duration: 0.18 }}
                className="flex flex-col gap-4"
              >
                <div className="flex items-center gap-4">
                  <BrandLogo size="lg" />
                  <div>
                    <h2 className="font-display text-xl font-extrabold sm:text-2xl">
                      Трамвайное путешествие в один клик
                    </h2>
                    <p className="mt-1 text-xs sm:text-sm" style={{ color: 'var(--text-secondary)' }}>
                      Собирайте случайные или точные цепочки с пересадками по реальному графику.
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-1 gap-2.5">
                  <div
                    className="flex items-start gap-3 rounded-2xl p-3.5"
                    style={{ backgroundColor: 'var(--md-surface-container)' }}
                  >
                    <TramFront className="mt-0.5 h-5 w-5 shrink-0" style={{ color: 'var(--accent-primary)' }} />
                    <div>
                      <div className="text-xs font-bold sm:text-sm">Актуальная сеть и пересадки</div>
                      <p className="mt-0.5 text-xs leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
                        Реальные маршруты, интервалы, круговые линии, «Чижики» и пересадочные узлы без устаревших схем.
                      </p>
                    </div>
                  </div>

                  <div
                    className="flex items-start gap-3 rounded-2xl p-3.5"
                    style={{ backgroundColor: 'var(--md-surface-container)' }}
                  >
                    <Navigation2 className="mt-0.5 h-5 w-5 shrink-0" style={{ color: 'var(--accent-primary)' }} />
                    <div>
                      <div className="text-xs font-bold sm:text-sm">Ведение в Яндекс Картах и автоинформатор</div>
                      <p className="mt-0.5 text-xs leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
                        Готовая ссылка маршрута для Яндекс Карт, диктовки остановок и живой таймер в пути.
                      </p>
                    </div>
                  </div>

                  <div
                    className="flex items-start gap-3 rounded-2xl p-3.5"
                    style={{ backgroundColor: 'var(--md-surface-container)' }}
                  >
                    <Sparkles className="mt-0.5 h-5 w-5 shrink-0" style={{ color: 'var(--accent-primary)' }} />
                    <div>
                      <div className="text-xs font-bold sm:text-sm">Паспорт, фото-история и точки перекуса</div>
                      <p className="mt-0.5 text-xs leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
                        Собирайте бейджи, сохраняйте фото поездки и находите кофейни, «Пятёрочки» и «Перекрёстки» у пересадок.
                      </p>
                    </div>
                  </div>
                </div>
              </motion.div>
            )}

            {step === 1 && (
              <motion.div
                key="step-1"
                initial={{ opacity: 0, x: 12 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -12 }}
                transition={{ duration: 0.18 }}
                className="flex flex-col gap-4"
              >
                <div>
                  <h2 className="font-display text-xl font-extrabold">Выберите ваш город</h2>
                  <p className="mt-1 text-xs sm:text-sm" style={{ color: 'var(--text-secondary)' }}>
                    Сейчас доступны два города — Санкт-Петербург и Омск. Поменять город позже можно в Настройках.
                  </p>
                </div>

                <div className="grid grid-cols-1 gap-3">
                  {(['spb', 'omsk'] as CityId[]).map((cityKey) => {
                    const cfg = CITIES[cityKey];
                    const active = selectedCity === cityKey;
                    return (
                      <button
                        key={cityKey}
                        type="button"
                        onClick={() => {
                          triggerHaptic('medium');
                          onSelectCity(cityKey);
                        }}
                        aria-pressed={active}
                        className="m3-tactile flex cursor-pointer items-start justify-between gap-3 rounded-3xl border-2 p-4 text-left"
                        style={{
                          backgroundColor: active
                            ? 'var(--md-primary-container)'
                            : 'var(--md-surface-container)',
                          color: active
                            ? 'var(--md-on-primary-container)'
                            : 'var(--text-primary)',
                          borderColor: active ? 'var(--accent-primary)' : 'transparent',
                        }}
                      >
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-2">
                            <MapPin className="h-4 w-4 shrink-0" />
                            <span className="font-display text-base font-extrabold">
                              {cfg.name}
                            </span>
                          </div>
                          <p className="mt-1 text-xs opacity-85">{cfg.tagline}</p>
                          <div className="font-mono-num mt-2 text-[11px] font-semibold opacity-80">
                            {cfg.routes.length} трамвайных линий · {cfg.stops.length} узлов и остановок
                          </div>
                        </div>
                        {active && <CheckCircle2 className="mt-1 h-5 w-5 shrink-0" />}
                      </button>
                    );
                  })}
                </div>
              </motion.div>
            )}

            {step === 2 && (
              <motion.div
                key="step-2"
                initial={{ opacity: 0, x: 12 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -12 }}
                transition={{ duration: 0.18 }}
                className="flex flex-col gap-4"
              >
                <div>
                  <h2 className="font-display text-xl font-extrabold">
                    Разрешить доступ к местоположению?
                  </h2>
                  <p className="mt-1 text-xs leading-relaxed sm:text-sm" style={{ color: 'var(--text-secondary)' }}>
                    Геолокация поможет автоматически находить ближайшую трамвайную остановку в{' '}
                    <strong>{CITIES[selectedCity].genitiveName}</strong> и предупредит, если вы сейчас в другом городе.
                  </p>
                </div>

                <button
                  type="button"
                  onClick={() => {
                    triggerHaptic('medium');
                    onRequestLocation();
                  }}
                  className="m3-tactile flex min-h-[48px] w-full cursor-pointer items-center justify-center gap-2 rounded-2xl px-4 py-3 text-sm font-bold"
                  style={{
                    backgroundColor: 'var(--md-primary-container)',
                    color: 'var(--md-on-primary-container)',
                  }}
                >
                  <LocateFixed className="h-4 w-4" />
                  {geoStatus.state === 'loading'
                    ? 'Определяем координаты...'
                    : 'Разрешить и определить город'}
                </button>

                {geoStatus.state !== 'idle' && geoStatus.message && (
                  <div
                    className="rounded-2xl p-3.5 text-xs leading-relaxed"
                    style={{
                      backgroundColor:
                        geoStatus.state === 'error'
                          ? 'rgba(185, 28, 28, 0.1)'
                          : 'var(--md-surface-container)',
                      color:
                        geoStatus.state === 'error' ? '#b91c1c' : 'var(--text-primary)',
                    }}
                  >
                    {geoStatus.message}
                  </div>
                )}

                {isMismatch && cityDetection && (
                  <div
                    className="flex items-start gap-2.5 rounded-2xl border p-3.5 text-xs"
                    style={{
                      backgroundColor: 'rgba(217, 119, 6, 0.12)',
                      borderColor: 'rgba(217, 119, 6, 0.35)',
                      color: 'var(--text-primary)',
                    }}
                  >
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
                    <div className="flex-1">
                      <div className="font-bold">Обнаружен другой город</div>
                      <p className="mt-0.5 leading-relaxed opacity-90">
                        {cityDetection.detected === 'other'
                          ? `Вы находитесь за пределами СПб и Омска — приложение продолжит работать для выбранного города (${CITIES[selectedCity].name}).`
                          : `По координатам вы находитесь в городе ${CITIES[cityDetection.detected].name}, а выбран ${CITIES[selectedCity].name}.`}
                      </p>
                      {cityDetection.detected !== 'other' && (
                        <button
                          type="button"
                          onClick={() => {
                            triggerHaptic('medium');
                            onSelectCity(cityDetection.detected as CityId);
                          }}
                          className="m3-tactile mt-2 inline-flex cursor-pointer items-center gap-1 rounded-full px-3 py-1 text-xs font-bold"
                          style={{
                            backgroundColor: 'var(--accent-primary)',
                            color: 'var(--accent-on-primary)',
                          }}
                        >
                          Переключиться на {CITIES[cityDetection.detected].name}
                        </button>
                      )}
                    </div>
                  </div>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Footer controls */}
        <div
          className="flex items-center justify-between gap-3 border-t px-5 py-4"
          style={{ borderColor: 'var(--border-hairline)' }}
        >
          {step > 0 ? (
            <button
              type="button"
              onClick={() => {
                triggerHaptic('light');
                setStep((s) => (s - 1) as 0 | 1 | 2);
              }}
              className="m3-tactile min-h-[42px] cursor-pointer rounded-2xl px-4 py-2 text-xs font-semibold"
              style={{
                backgroundColor: 'var(--bg-elevated)',
                color: 'var(--text-primary)',
              }}
            >
              Назад
            </button>
          ) : (
            <button
              type="button"
              onClick={() => {
                triggerHaptic('light');
                onComplete();
              }}
              className="m3-tactile min-h-[42px] cursor-pointer rounded-2xl px-3 py-2 text-xs font-semibold"
              style={{ color: 'var(--text-muted)' }}
            >
              Пропустить
            </button>
          )}

          {step < 2 ? (
            <button
              type="button"
              onClick={() => {
                triggerHaptic('medium');
                setStep((s) => (s + 1) as 0 | 1 | 2);
              }}
              className="m3-tactile m3-fab inline-flex min-h-[44px] cursor-pointer items-center gap-2 rounded-2xl px-5 py-2.5 text-sm font-bold"
              style={{
                backgroundColor: 'var(--accent-primary)',
                color: 'var(--accent-on-primary)',
              }}
            >
              Далее
            </button>
          ) : (
            <button
              type="button"
              onClick={() => {
                triggerHaptic('success');
                onComplete();
              }}
              className="m3-tactile m3-fab inline-flex min-h-[44px] cursor-pointer items-center gap-2 rounded-2xl px-5 py-2.5 text-sm font-bold"
              style={{
                backgroundColor: 'var(--accent-primary)',
                color: 'var(--accent-on-primary)',
              }}
            >
              <Compass className="h-4 w-4" />
              Начать ({CITIES[selectedCity].shortName})
            </button>
          )}
        </div>
      </motion.div>
    </div>
  );
}
