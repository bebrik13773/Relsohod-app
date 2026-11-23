import { useEffect } from 'react';
import { Sparkles, X } from 'lucide-react';
import { ChangelogRelease } from '../utils/changelog';
import { triggerHaptic } from '../utils/haptics';

interface ChangelogModalProps {
  isOpen: boolean;
  release: ChangelogRelease;
  previousVersion: string | null;
  onAcknowledge: () => void;
}

export function ChangelogModal({
  isOpen,
  release,
  previousVersion,
  onAcknowledge,
}: ChangelogModalProps) {
  useEffect(() => {
    if (!isOpen) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onAcknowledge();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [isOpen, onAcknowledge]);

  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="changelog-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4 backdrop-blur-sm"
    >
      <div
        className="m3-card flex max-h-[88dvh] w-full max-w-md flex-col overflow-hidden rounded-3xl"
        style={{
          backgroundColor: 'var(--bg-surface)',
          color: 'var(--text-primary)',
        }}
      >
        {/* Header */}
        <div
          className="flex items-start justify-between gap-3 border-b px-5 py-4"
          style={{ borderColor: 'var(--border-hairline)' }}
        >
          <div className="min-w-0">
            <div
              className="flex items-center gap-1.5 text-xs font-semibold tabular-nums"
              style={{ color: 'var(--accent-primary)' }}
            >
              <Sparkles className="h-3.5 w-3.5 shrink-0" />
              <span>Версия {release.version}</span>
              <span aria-hidden="true">·</span>
              <span>
                {previousVersion && previousVersion !== release.version
                  ? `с ${previousVersion}`
                  : release.subtitle}
              </span>
            </div>
            <h2
              id="changelog-modal-title"
              className="font-display mt-1 text-lg font-extrabold tracking-tight text-balance"
            >
              Что нового в Рельсоходе
            </h2>
          </div>

          <button
            type="button"
            aria-label="Закрыть список изменений"
            onClick={() => {
              triggerHaptic('light');
              onAcknowledge();
            }}
            className="m3-tactile flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full"
            style={{ backgroundColor: 'var(--bg-elevated)', color: 'var(--text-primary)' }}
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Body */}
        <div className="flex flex-1 flex-col gap-4 overflow-y-auto px-5 py-4">
          {release.sections.map((section) => (
            <section key={section.title} className="flex flex-col gap-2">
              <h3
                className="text-xs font-bold tracking-wide"
                style={{ color: 'var(--text-secondary)' }}
              >
                {section.title}
              </h3>
              <ul className="flex flex-col gap-2">
                {section.items.map((item) => (
                  <li
                    key={item}
                    className="flex items-start gap-2.5 text-[13px] leading-relaxed"
                    style={{ color: 'var(--text-primary)' }}
                  >
                    <span
                      aria-hidden="true"
                      className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full"
                      style={{ backgroundColor: 'var(--accent-primary)' }}
                    />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>

        {/* Footer */}
        <div
          className="border-t px-5 py-4"
          style={{ borderColor: 'var(--border-hairline)' }}
        >
          <button
            type="button"
            onClick={() => {
              triggerHaptic('success');
              onAcknowledge();
            }}
            className="m3-tactile min-h-[44px] w-full cursor-pointer rounded-2xl px-4 py-2.5 text-sm font-bold"
            style={{
              backgroundColor: 'var(--accent-primary)',
              color: 'var(--accent-on-primary)',
            }}
          >
            Понятно
          </button>
        </div>
      </div>
    </div>
  );
}
