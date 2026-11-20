import { useState } from 'react';
import { Download, Share, X } from 'lucide-react';
import { usePWAInstall } from '../hooks/usePWAInstall';
import { triggerHaptic } from '../utils/haptics';

interface PWAInstallButtonProps {
  compact?: boolean;
}

export function PWAInstallButton({ compact = false }: PWAInstallButtonProps) {
  const { isInstallable, isInstalled, isIOS, install } = usePWAInstall();
  const [showIOSGuide, setShowIOSGuide] = useState(false);

  if (isInstalled) {
    return null;
  }

  if (isInstallable) {
    return (
      <button
        type="button"
        onClick={() => {
          triggerHaptic('medium');
          void install();
        }}
        className="m3-tactile inline-flex min-h-[36px] cursor-pointer items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold whitespace-nowrap shrink-0"
        style={{
          backgroundColor: 'var(--md-primary-container)',
          color: 'var(--md-on-primary-container)',
        }}
      >
        <Download className="h-3.5 w-3.5" />
        <span>{compact ? 'Установить' : 'Установить приложение'}</span>
      </button>
    );
  }

  if (isIOS) {
    return (
      <>
        <button
          type="button"
          onClick={() => {
            triggerHaptic('light');
            setShowIOSGuide(true);
          }}
          className="m3-tactile inline-flex min-h-[36px] cursor-pointer items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold whitespace-nowrap shrink-0"
          style={{
            backgroundColor: 'var(--md-primary-container)',
            color: 'var(--md-on-primary-container)',
          }}
        >
          <Share className="h-3.5 w-3.5" />
          <span>На экран</span>
        </button>

        {showIOSGuide && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
            <div
              className="m3-card w-full max-w-sm rounded-3xl p-6"
              style={{
                backgroundColor: 'var(--bg-surface)',
                color: 'var(--text-primary)',
              }}
            >
              <div className="flex items-start justify-between gap-3">
                <h3 className="font-display text-lg font-bold">Установить «Рельсоход»</h3>
                <button
                  type="button"
                  aria-label="Закрыть инструкцию"
                  onClick={() => setShowIOSGuide(false)}
                  className="m3-tactile flex h-8 w-8 cursor-pointer items-center justify-center rounded-full"
                  style={{ backgroundColor: 'var(--bg-elevated)' }}
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
              <ol
                className="mt-3 flex flex-col gap-2 text-sm leading-relaxed"
                style={{ color: 'var(--text-secondary)' }}
              >
                <li>1. Нажмите кнопку «Поделиться» в нижней панели Safari.</li>
                <li>2. Выберите пункт «На экран Домой» и подтвердите добавление.</li>
              </ol>
              <button
                type="button"
                onClick={() => setShowIOSGuide(false)}
                className="m3-tactile mt-5 min-h-[44px] w-full cursor-pointer rounded-2xl text-sm font-semibold"
                style={{
                  backgroundColor: 'var(--accent-primary)',
                  color: 'var(--accent-on-primary)',
                }}
              >
                Понятно
              </button>
            </div>
          </div>
        )}
      </>
    );
  }

  return null;
}
