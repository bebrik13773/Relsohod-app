import { WifiOff } from 'lucide-react';
import { useOnlineStatus } from '../hooks/useOnlineStatus';

export function OfflineIndicator() {
  const isOnline = useOnlineStatus();

  if (isOnline) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed bottom-20 left-4 z-50 flex items-center gap-2 rounded-2xl border px-3.5 py-2 text-xs font-semibold shadow-md md:bottom-4"
      style={{
        backgroundColor: 'var(--md-primary-container)',
        color: 'var(--md-on-primary-container)',
        borderColor: 'var(--border-hairline)',
      }}
    >
      <WifiOff className="h-3.5 w-3.5 shrink-0" />
      <span>Офлайн-режим — работают локальные данные</span>
    </div>
  );
}
