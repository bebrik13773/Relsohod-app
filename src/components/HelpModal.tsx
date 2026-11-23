import { useEffect, useState } from 'react';
import { ChevronDown, HelpCircle, X } from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';
import { FAQ_ITEMS } from '../data/faq';
import { triggerHaptic } from '../utils/haptics';

interface HelpModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function HelpModal({ isOpen, onClose }: HelpModalProps) {
  const [expandedIds, setExpandedIds] = useState<Record<string, boolean>>({
    transfers: true,
    'city-detection': true,
  });

  useEffect(() => {
    if (!isOpen) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [isOpen, onClose]);

  const toggleItem = (id: string) => {
    triggerHaptic('light');
    setExpandedIds((prev) => ({
      ...prev,
      [id]: !prev[id],
    }));
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          key="help-modal-backdrop"
          role="dialog"
          aria-modal="true"
          aria-labelledby="help-modal-title"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2, ease: 'easeOut' }}
          onClick={(e) => {
            if (e.target === e.currentTarget) {
              onClose();
            }
          }}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4 backdrop-blur-sm"
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.94, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 8 }}
            transition={{ type: 'spring', stiffness: 360, damping: 28, mass: 0.85 }}
            className="m3-card flex max-h-[88dvh] w-full max-w-lg flex-col overflow-hidden rounded-3xl"
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
                  className="flex items-center gap-1.5 text-xs font-semibold"
                  style={{ color: 'var(--accent-primary)' }}
                >
                  <HelpCircle className="h-3.5 w-3.5 shrink-0" />
                  <span>Справка · Частые вопросы</span>
                </div>
                <h2
                  id="help-modal-title"
                  className="font-display mt-1 text-lg font-extrabold tracking-tight text-balance"
                >
                  Как работает Рельсоход
                </h2>
              </div>

              <button
                type="button"
                aria-label="Закрыть справку"
                onClick={() => {
                  triggerHaptic('light');
                  onClose();
                }}
                className="m3-tactile flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full"
                style={{
                  backgroundColor: 'var(--bg-elevated)',
                  color: 'var(--text-primary)',
                }}
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            {/* Scrollable FAQ list */}
            <div className="min-h-0 flex-1 space-y-2.5 overflow-y-auto px-5 py-4">
              {FAQ_ITEMS.map((item) => {
                const expanded = Boolean(expandedIds[item.id]);
                const panelId = `faq-answer-${item.id}`;
                return (
                  <div
                    key={item.id}
                    className="shrink-0 overflow-hidden rounded-2xl border transition-colors"
                    style={{
                      backgroundColor: 'var(--md-surface-container)',
                      borderColor: expanded
                        ? 'var(--accent-primary)'
                        : 'var(--border-hairline)',
                    }}
                  >
                    <button
                      type="button"
                      aria-expanded={expanded}
                      aria-controls={panelId}
                      onClick={() => toggleItem(item.id)}
                      className="flex min-h-[48px] w-full cursor-pointer items-center justify-between gap-3 px-4 py-3 text-left"
                    >
                      <span className="text-[13px] font-bold leading-snug">
                        {item.question}
                      </span>
                      <ChevronDown
                        className={`h-4 w-4 shrink-0 transition-transform duration-200 ${
                          expanded ? 'rotate-180' : ''
                        }`}
                        style={{ color: 'var(--text-secondary)' }}
                      />
                    </button>

                    {expanded && (
                      <div
                        id={panelId}
                        className="border-t px-4 py-3 text-[13px] leading-relaxed"
                        style={{
                          borderColor: 'var(--border-hairline)',
                          color: 'var(--text-secondary)',
                        }}
                      >
                        {item.answer}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

            {/* Footer */}
            <div
              className="border-t px-5 py-4"
              style={{ borderColor: 'var(--border-hairline)' }}
            >
              <button
                type="button"
                onClick={() => {
                  triggerHaptic('light');
                  onClose();
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
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
