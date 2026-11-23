import { ChangeEvent, useEffect, useRef, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  Compass,
  Download,
  FolderOpen,
  LocateFixed,
  MapPin,
  Navigation2,
  Smartphone,
  Sparkles,
  TramFront,
  Upload,
} from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { CityId } from '../types/tram';
import { CITIES, CityDetectionResult } from '../data/cities';
import { ANDROID_RELEASES_PAGE_URL, ANDROID_RELEASE_APK_URL } from '../utils/feedback';
import { triggerHaptic } from '../utils/haptics';
import { BrandLogo } from './BrandLogo';

export type BackupImportFeedback =
  | { state: 'idle' }
  | { state: 'ok'; message: string }
  | { state: 'error'; message: string };

interface OnboardingModalProps {
  isOpen: boolean;
  selectedCity: CityId;
  onSelectCity: (city: CityId) => void;
  geoStatus: { state: 'idle' | 'loading' | 'error' | 'ok'; message?: string };
  cityDetection: CityDetectionResult | null;
  onRequestLocation: () => void;
  onImportBackupFile: (file: File) => Promise<{ ok: true; message: string } | { ok: false; error: string }>;
  onComplete: () => void;
}

const TOTAL_STEPS = 4;

export function OnboardingModal({
  isOpen,
  selectedCity,
  onSelectCity,
  geoStatus,
  cityDetection,
  onRequestLocation,
  onImportBackupFile,
  onComplete,
}: OnboardingModalProps) {
  const [step, setStep] = useState<0 | 1 | 2 | 3>(0);
  const [importStatus, setImportStatus] = useState<BackupImportFeedback>({ state: 'idle' });
  const [isImporting, setIsImporting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (isOpen) {
      setStep(0);
      setImportStatus({ state: 'idle' });
      setIsImporting(false);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const isGeoOutsideCities = cityDetection !== null && cityDetection.detected === 'other';
  const detectedCity =
    cityDetection !== null && cityDetection.detected !== 'other' ? cityDetection.detected : null;

  const handleFileSelected = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setIsImporting(true);
    const res = await onImportBackupFile(file);
    setIsImporting(false);
    if (res.ok) {
      triggerHaptic('success');
      setImportStatus({ state: 'ok', message: res.message });
    } else {
      triggerHaptic('light');
      setImportStatus({ state: 'error', message: res.error });
    }
  };

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
        className="m3-card flex max-h-[90dvh] w-full max-w-lg flex-col overflow-hidden rounded-[28px]"
        style={{
          backgroundColor: 'var(--md-surface-container-low)',
          color: 'var(--text-primary)',
        }}
      >
        {/* Top step progress */}
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
          <div className="flex items-center gap-1.5" aria-label={`Шаг ${step + 1} из ${TOTAL_STEPS}`}>
            {[0, 1, 2, 3].map((idx) => (
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
                        Собирайте штампы, сохраняйте фото поездки и находите кофейни, «Пятёрочки» и «Перекрёстки» у пересадок.
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
                  <h2 className="font-display text-xl font-extrabold">
                    Восстановить из файла .relsohod?
                  </h2>
                  <p className="mt-1 text-xs leading-relaxed sm:text-sm" style={{ color: 'var(--text-secondary)' }}>
                    Если у вас уже есть резервная копия (<span className="font-mono">export-....relsohod</span>) из браузера или Android-версии, загрузите её сейчас — или пропустите этот шаг.
                  </p>
                </div>

                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".relsohod,.relsokhod,.json,application/json,application/octet-stream"
                  onChange={handleFileSelected}
                  className="hidden"
                  aria-label="Выбрать файл резервной копии .relsohod"
                />

                <button
                  type="button"
                  disabled={isImporting}
                  onClick={() => {
                    triggerHaptic('medium');
                    fileInputRef.current?.click();
                  }}
                  className="m3-tactile flex min-h-[52px] w-full cursor-pointer items-center justify-center gap-2 rounded-2xl px-4 py-3 text-sm font-bold disabled:opacity-60"
                  style={{
                    backgroundColor: 'var(--md-primary-container)',
                    color: 'var(--md-on-primary-container)',
                  }}
                >
                  {isImporting ? <Upload className="h-4 w-4 animate-pulse" /> : <FolderOpen className="h-4 w-4" />}
                  {isImporting ? 'Читаем файл...' : 'Выбрать файл .relsohod'}
                </button>

                {importStatus.state !== 'idle' && (
                  <div
                    className="flex flex-col gap-2.5 rounded-2xl p-3.5 text-xs leading-relaxed"
                    style={{
                      backgroundColor:
                        importStatus.state === 'error'
                          ? 'rgba(185, 28, 28, 0.1)'
                          : 'var(--md-surface-container)',
                      color:
                        importStatus.state === 'error' ? '#b91c1c' : 'var(--text-primary)',
                    }}
                  >
                    <div className="flex items-start gap-2">
                      {importStatus.state === 'ok' ? (
                        <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
                      ) : (
                        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-red-600" />
                      )}
                      <span>{importStatus.message}</span>
                    </div>
                    {importStatus.state === 'ok' && (
                      <button
                        type="button"
                        onClick={() => {
                          triggerHaptic('success');
                          onComplete();
                        }}
                        className="m3-tactile inline-flex min-h-[40px] w-full cursor-pointer items-center justify-center gap-1.5 rounded-xl px-4 py-2 text-xs font-bold"
                        style={{
                          backgroundColor: 'var(--accent-primary)',
                          color: 'var(--accent-on-primary)',
                        }}
                      >
                        <Compass className="h-4 w-4" />
                        Перейти в приложение с восстановленными данными
                      </button>
                    )}
                  </div>
                )}
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
                    По геопозиции приложение само определит ваш город (Санкт-Петербург или Омск) и подберёт ближайшую трамвайную остановку.
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

                {detectedCity && (
                  <div
                    className="flex items-start gap-2.5 rounded-2xl p-3.5 text-xs"
                    style={{ backgroundColor: 'var(--md-surface-container)' }}
                  >
                    <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
                    <div>
                      <div className="font-bold">
                        Автоматически выбран город: {CITIES[detectedCity].name}
                      </div>
                      <p className="mt-0.5 opacity-80">
                        На следующем шаге вы сможете подтвердить выбор или переключиться на другой город.
                      </p>
                    </div>
                  </div>
                )}

                {isGeoOutsideCities && cityDetection && (
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
                      <div className="font-bold">Вы находитесь за пределами СПб и Омска</div>
                      <p className="mt-0.5 leading-relaxed opacity-90">
                        На следующем шаге выберите сеть Санкт-Петербурга или Омска вручную.
                      </p>
                    </div>
                  </div>
                )}
              </motion.div>
            )}

            {step === 3 && (
              <motion.div
                key="step-3"
                initial={{ opacity: 0, x: 12 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -12 }}
                transition={{ duration: 0.18 }}
                className="flex flex-col gap-4"
              >
                <div>
                  <h2 className="font-display text-xl font-extrabold">
                    {detectedCity ? 'Подтвердите или выберите город' : 'Выберите ваш город'}
                  </h2>
                  <p className="mt-1 text-xs sm:text-sm" style={{ color: 'var(--text-secondary)' }}>
                    {detectedCity
                      ? `По геопозиции выбран город ${CITIES[detectedCity].name}. При желании выберите другой город — поменять его можно и позже в Настройках.`
                      : 'Сейчас доступны два города — Санкт-Петербург и Омск. Поменять город позже можно в Настройках.'}
                  </p>
                </div>

                <div className="grid grid-cols-1 gap-3">
                  {(['spb', 'omsk'] as CityId[]).map((cityKey) => {
                    const cfg = CITIES[cityKey];
                    const active = selectedCity === cityKey;
                    const matchedGeo = detectedCity === cityKey;
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
                          <div className="flex flex-wrap items-center gap-2">
                            <MapPin className="h-4 w-4 shrink-0" />
                            <span className="font-display text-base font-extrabold">
                              {cfg.name}
                            </span>
                            {matchedGeo && (
                              <span className="text-[11px] font-semibold opacity-80">
                                · определён по геопозиции
                              </span>
                            )}
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

                {/* Native Android App recommendation on final onboarding step */}
                <div
                  className="flex flex-col gap-3 rounded-[24px] border p-4"
                  style={{
                    backgroundColor: 'var(--md-secondary-container)',
                    color: 'var(--md-on-secondary-container)',
                    borderColor: 'var(--md-outline-variant)',
                  }}
                >
                  <div className="flex items-start gap-3">
                    <div
                      className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl"
                      style={{
                        backgroundColor: 'var(--md-primary-container)',
                        color: 'var(--md-on-primary-container)',
                      }}
                    >
                      <Smartphone className="h-5 w-5" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="font-display text-sm font-extrabold">
                        На телефоне удобнее в Android-приложении
                      </div>
                      <p className="mt-0.5 text-xs leading-relaxed opacity-90">
                        Веб-версия отлично подходит для быстрого знакомства и ПК. А если вы планируете гулять со смартфона — скачайте нативный клиент с быстрой картой, офлайн-работой и автообновлением.
                      </p>
                    </div>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <a
                      href={ANDROID_RELEASE_APK_URL}
                      target="_blank"
                      rel="noopener noreferrer"
                      onClick={() => triggerHaptic('medium')}
                      className="m3-tactile inline-flex min-h-[40px] flex-1 items-center justify-center gap-2 rounded-full px-4 py-2 text-xs font-bold whitespace-nowrap"
                      style={{
                        backgroundColor: 'var(--accent-primary)',
                        color: 'var(--accent-on-primary)',
                      }}
                    >
                      <Download className="h-4 w-4 shrink-0" />
                      Скачать Android APK
                    </a>
                    <a
                      href={ANDROID_RELEASES_PAGE_URL}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="m3-tactile inline-flex min-h-[40px] items-center justify-center rounded-full border px-3.5 py-2 text-xs font-semibold whitespace-nowrap"
                      style={{
                        backgroundColor: 'var(--md-surface-container-low)',
                        color: 'var(--text-primary)',
                        borderColor: 'var(--md-outline-variant)',
                      }}
                    >
                      Страница релиза
                    </a>
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Footer controls */}
        <div
          className="flex items-center justify-between gap-3 border-t px-5 py-4"
          style={{ borderColor: 'var(--border-hairline)' }}
        >
          {step === 0 ? (
            <button
              type="button"
              onClick={() => {
                triggerHaptic('light');
                onComplete();
              }}
              className="m3-tactile min-h-[42px] cursor-pointer rounded-full px-4 py-2 text-xs font-semibold"
              style={{ color: 'var(--text-muted)' }}
            >
              Пропустить
            </button>
          ) : step === 1 ? (
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => {
                  triggerHaptic('light');
                  setStep(0);
                }}
                className="m3-tactile min-h-[42px] cursor-pointer rounded-full px-4 py-2 text-xs font-semibold"
                style={{
                  backgroundColor: 'var(--md-surface-container-high)',
                  color: 'var(--text-primary)',
                }}
              >
                Назад
              </button>
              <button
                type="button"
                onClick={() => {
                  triggerHaptic('light');
                  setStep(2);
                }}
                className="m3-tactile min-h-[42px] cursor-pointer rounded-full px-3 py-2 text-xs font-semibold"
                style={{ color: 'var(--text-muted)' }}
              >
                Пропустить
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => {
                triggerHaptic('light');
                setStep((s) => (s - 1) as 0 | 1 | 2 | 3);
              }}
              className="m3-tactile min-h-[42px] cursor-pointer rounded-full px-4 py-2 text-xs font-semibold"
              style={{
                backgroundColor: 'var(--md-surface-container-high)',
                color: 'var(--text-primary)',
              }}
            >
              Назад
            </button>
          )}

          {step < 3 ? (
            <button
              type="button"
              onClick={() => {
                triggerHaptic('medium');
                setStep((s) => (s + 1) as 0 | 1 | 2 | 3);
              }}
              className="m3-tactile m3-fab inline-flex min-h-[44px] cursor-pointer items-center gap-2 rounded-full px-6 py-2.5 text-sm font-bold"
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
              className="m3-tactile m3-fab inline-flex min-h-[44px] cursor-pointer items-center gap-2 rounded-full px-6 py-2.5 text-sm font-bold"
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
