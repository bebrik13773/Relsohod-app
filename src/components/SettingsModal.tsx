import { ChangeEvent, useEffect, useRef, useState } from 'react';
import {
  AlertTriangle,
  Camera,
  CheckCircle2,
  Download,
  FolderOpen,
  GraduationCap,
  HelpCircle,
  MapPin,
  Sparkles,
  Upload,
  X,
} from 'lucide-react';
import { AccentSeed, AppTheme, CityId } from '../types/tram';
import { CITIES } from '../data/cities';
import { triggerHaptic } from '../utils/haptics';
import { buildBackupFilename } from '../utils/backup';
import { MaterialYouAccentPicker } from './MaterialYouAccentPicker';

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  city: CityId;
  onChangeCity: (city: CityId) => void;
  theme: AppTheme;
  onChangeTheme: (theme: AppTheme) => void;
  accentSeed: AccentSeed;
  onChangeAccentSeed: (accent: AccentSeed) => void;
  photoReminderMinutes: number;
  onChangePhotoReminderMinutes: (mins: number) => void;
  onExportBackup: () => string;
  onImportBackupFile: (
    file: File
  ) => Promise<{ ok: true; message: string } | { ok: false; error: string }>;
  onOpenChangelog: () => void;
  onOpenHelp: () => void;
  onRestartOnboarding: () => void;
}

const REMINDER_OPTIONS = [
  { mins: 0, label: 'Выкл' },
  { mins: 15, label: '15 мин' },
  { mins: 30, label: '30 мин' },
  { mins: 45, label: '45 мин' },
  { mins: 60, label: '1 час' },
];

const THEME_ITEMS: Array<{ id: AppTheme; label: string }> = [
  { id: 'light', label: 'Светлая' },
  { id: 'dark', label: 'Тёмная' },
  { id: 'tram', label: 'Трамвай' },
];

export function SettingsModal({
  isOpen,
  onClose,
  city,
  onChangeCity,
  theme,
  onChangeTheme,
  accentSeed,
  onChangeAccentSeed,
  photoReminderMinutes,
  onChangePhotoReminderMinutes,
  onExportBackup,
  onImportBackupFile,
  onOpenChangelog,
  onOpenHelp,
  onRestartOnboarding,
}: SettingsModalProps) {
  const [backupFeedback, setBackupFeedback] = useState<{
    kind: 'idle' | 'ok' | 'error';
    message: string;
  }>({ kind: 'idle', message: '' });
  const [isImporting, setIsImporting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (isOpen) {
      setBackupFeedback({ kind: 'idle', message: '' });
      setIsImporting(false);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const expectedFilename = buildBackupFilename();

  const handleImportFileChange = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setIsImporting(true);
    const res = await onImportBackupFile(file);
    setIsImporting(false);
    if (res.ok) {
      triggerHaptic('success');
      setBackupFeedback({ kind: 'ok', message: res.message });
    } else {
      triggerHaptic('light');
      setBackupFeedback({ kind: 'error', message: res.error });
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Настройки приложения"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm"
    >
      <div
        className="m3-card flex max-h-[88dvh] w-full max-w-md flex-col overflow-hidden rounded-3xl"
        style={{
          backgroundColor: 'var(--bg-surface)',
          color: 'var(--text-primary)',
        }}
      >
        <div
          className="flex items-center justify-between border-b px-5 py-4"
          style={{ borderColor: 'var(--border-hairline)' }}
        >
          <h2 className="font-display text-lg font-extrabold">Настройки</h2>
          <button
            type="button"
            aria-label="Закрыть настройки"
            onClick={() => {
              triggerHaptic('light');
              onClose();
            }}
            className="m3-tactile flex h-9 w-9 cursor-pointer items-center justify-center rounded-full"
            style={{ backgroundColor: 'var(--bg-elevated)' }}
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="flex flex-1 flex-col gap-5 overflow-y-auto p-5">
          {/* City Switcher */}
          <div className="flex flex-col gap-2">
            <span
              className="flex items-center gap-1.5 text-xs font-bold"
              style={{ color: 'var(--text-secondary)' }}
            >
              <MapPin className="h-3.5 w-3.5" />
              Город трамвайной сети
            </span>
            <div className="grid grid-cols-2 gap-2">
              {(['spb', 'omsk'] as CityId[]).map((c) => {
                const active = city === c;
                return (
                  <button
                    key={c}
                    type="button"
                    aria-pressed={active}
                    onClick={() => {
                      triggerHaptic('medium');
                      onChangeCity(c);
                    }}
                    className="m3-tactile flex min-h-[46px] cursor-pointer flex-col items-start rounded-2xl border px-3.5 py-2 text-left"
                    style={{
                      backgroundColor: active
                        ? 'var(--md-primary-container)'
                        : 'var(--md-surface-container)',
                      color: active
                        ? 'var(--md-on-primary-container)'
                        : 'var(--text-primary)',
                      borderColor: active ? 'var(--accent-primary)' : 'var(--border-hairline)',
                    }}
                  >
                    <span className="text-xs font-bold">{CITIES[c].name}</span>
                    <span className="font-mono-num text-[10px] opacity-80">
                      {CITIES[c].routes.length} линий
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Photo Check-in Reminder Interval */}
          <div
            className="flex flex-col gap-2 border-t pt-4"
            style={{ borderColor: 'var(--border-hairline)' }}
          >
            <span
              className="flex items-center gap-1.5 text-xs font-bold"
              style={{ color: 'var(--text-secondary)' }}
            >
              <Camera className="h-3.5 w-3.5" />
              Напоминание сделать фото в поездке
            </span>
            <p className="text-[11px] leading-relaxed" style={{ color: 'var(--text-muted)' }}>
              Во время активного режима «В пути» приложение мягко предложит сохранить кадр в историю.
            </p>
            <div className="flex flex-wrap gap-1.5">
              {REMINDER_OPTIONS.map((opt) => {
                const active = photoReminderMinutes === opt.mins;
                return (
                  <button
                    key={opt.mins}
                    type="button"
                    aria-pressed={active}
                    onClick={() => {
                      triggerHaptic('light');
                      onChangePhotoReminderMinutes(opt.mins);
                    }}
                    className="m3-tactile min-h-[36px] cursor-pointer rounded-2xl border px-3 py-1 text-xs font-semibold"
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
                    {opt.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Theme + Material You Accent */}
          <div
            className="flex flex-col gap-3 border-t pt-4"
            style={{ borderColor: 'var(--border-hairline)' }}
          >
            <span className="text-xs font-bold" style={{ color: 'var(--text-secondary)' }}>
              Тема и акцент Material You
            </span>
            <div className="grid grid-cols-3 gap-1.5">
              {THEME_ITEMS.map((item) => {
                const active = theme === item.id;
                return (
                  <button
                    key={item.id}
                    type="button"
                    aria-pressed={active}
                    onClick={() => {
                      triggerHaptic('light');
                      onChangeTheme(item.id);
                    }}
                    className="m3-tactile min-h-[38px] cursor-pointer rounded-2xl border px-2 py-1 text-xs font-semibold"
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

            <MaterialYouAccentPicker
              accent={accentSeed}
              onChangeAccent={onChangeAccentSeed}
              disabled={theme === 'tram'}
            />
          </div>

          {/* Backup Export / Import (.relsohod) */}
          <div
            className="flex flex-col gap-2.5 border-t pt-4"
            style={{ borderColor: 'var(--border-hairline)' }}
          >
            <span className="text-xs font-bold" style={{ color: 'var(--text-secondary)' }}>
              Резервная копия и перенос (.relsohod)
            </span>
            <p className="text-[11px] leading-relaxed" style={{ color: 'var(--text-muted)' }}>
              Единый формат для браузера и Android: сохраняет избранные маршруты, паспорт с фото и все настройки в файл <span className="font-mono">{expectedFilename}</span>.
            </p>

            <input
              ref={fileInputRef}
              type="file"
              accept=".relsohod,.relsokhod,.json,application/json,application/octet-stream"
              onChange={handleImportFileChange}
              className="hidden"
              aria-label="Выбрать файл резервной копии .relsohod"
            />

            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              <button
                type="button"
                onClick={() => {
                  triggerHaptic('medium');
                  const filename = onExportBackup();
                  setBackupFeedback({
                    kind: 'ok',
                    message: `Файл ${filename} сформирован и скачан.`,
                  });
                }}
                className="m3-tactile flex min-h-[44px] cursor-pointer items-center justify-center gap-2 rounded-2xl px-3.5 py-2 text-xs font-bold"
                style={{
                  backgroundColor: 'var(--md-primary-container)',
                  color: 'var(--md-on-primary-container)',
                }}
              >
                <Download className="h-4 w-4 shrink-0" />
                <span>Экспорт .relsohod</span>
              </button>

              <button
                type="button"
                disabled={isImporting}
                onClick={() => {
                  triggerHaptic('light');
                  fileInputRef.current?.click();
                }}
                className="m3-tactile flex min-h-[44px] cursor-pointer items-center justify-center gap-2 rounded-2xl border px-3.5 py-2 text-xs font-semibold disabled:opacity-60"
                style={{
                  backgroundColor: 'var(--bg-elevated)',
                  color: 'var(--text-primary)',
                  borderColor: 'var(--border-hairline)',
                }}
              >
                {isImporting ? (
                  <Upload className="h-4 w-4 shrink-0 animate-pulse" />
                ) : (
                  <FolderOpen className="h-4 w-4 shrink-0" />
                )}
                <span>{isImporting ? 'Импорт...' : 'Восстановить файл'}</span>
              </button>
            </div>

            {backupFeedback.kind !== 'idle' && (
              <div
                className="flex items-start gap-2 rounded-2xl p-3 text-xs leading-relaxed"
                style={{
                  backgroundColor:
                    backupFeedback.kind === 'error'
                      ? 'rgba(185, 28, 28, 0.1)'
                      : 'var(--md-surface-container)',
                  color: backupFeedback.kind === 'error' ? '#b91c1c' : 'var(--text-primary)',
                }}
              >
                {backupFeedback.kind === 'ok' ? (
                  <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
                ) : (
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-red-600" />
                )}
                <span>{backupFeedback.message}</span>
              </div>
            )}
          </div>

          {/* Changelog & Replay Onboarding */}
          <div
            className="flex flex-col gap-2 border-t pt-4"
            style={{ borderColor: 'var(--border-hairline)' }}
          >
            <button
              type="button"
              onClick={() => {
                triggerHaptic('light');
                onClose();
                onOpenHelp();
              }}
              className="m3-tactile flex min-h-[42px] w-full cursor-pointer items-center justify-center gap-2 rounded-2xl border px-4 py-2 text-xs font-semibold"
              style={{
                backgroundColor: 'var(--bg-elevated)',
                color: 'var(--text-primary)',
                borderColor: 'var(--border-hairline)',
              }}
            >
              <HelpCircle className="h-4 w-4" />
              Справка и частые вопросы (FAQ)
            </button>

            <button
              type="button"
              onClick={() => {
                triggerHaptic('light');
                onClose();
                onOpenChangelog();
              }}
              className="m3-tactile flex min-h-[42px] w-full cursor-pointer items-center justify-center gap-2 rounded-2xl border px-4 py-2 text-xs font-semibold"
              style={{
                backgroundColor: 'var(--bg-elevated)',
                color: 'var(--text-primary)',
                borderColor: 'var(--border-hairline)',
              }}
            >
              <Sparkles className="h-4 w-4" />
              Что нового в обновлении
            </button>

            <button
              type="button"
              onClick={() => {
                triggerHaptic('light');
                onClose();
                onRestartOnboarding();
              }}
              className="m3-tactile flex min-h-[42px] w-full cursor-pointer items-center justify-center gap-2 rounded-2xl border px-4 py-2 text-xs font-semibold"
              style={{
                backgroundColor: 'var(--bg-elevated)',
                color: 'var(--text-primary)',
                borderColor: 'var(--border-hairline)',
              }}
            >
              <GraduationCap className="h-4 w-4" />
              Пройти презентацию (обучение) заново
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
