import { Bookmark, Map as MapIcon, SlidersHorizontal, TramFront } from 'lucide-react';
import { CityId } from '../types/tram';
import { triggerHaptic } from '../utils/haptics';

export type ActiveMobileTab = 'search' | 'route' | 'favorites' | 'directory';
export type MobileNavTab = ActiveMobileTab;

interface MobileBottomNavProps {
  city: CityId;
  activeMobileTab: MobileNavTab;
  onSelectMobileTab: (tab: MobileNavTab) => void;
  favoritesCount: number;
}

export function MobileBottomNav({
  city,
  activeMobileTab,
  onSelectMobileTab,
  favoritesCount,
}: MobileBottomNavProps) {
  const items: Array<{
    id: MobileNavTab;
    label: string;
    icon: typeof SlidersHorizontal;
    badge?: number;
  }> = [
    { id: 'search', label: 'Поиск', icon: SlidersHorizontal },
    { id: 'route', label: 'Маршрут', icon: MapIcon },
    { id: 'favorites', label: 'Избранное', icon: Bookmark, badge: favoritesCount },
    { id: 'directory', label: city === 'omsk' ? 'Линии Омска' : 'Линии СПб', icon: TramFront },
  ];

  return (
    <nav
      aria-label="Нижняя навигация"
      className="fixed right-0 bottom-0 left-0 z-40 border-t backdrop-blur-lg md:hidden"
      style={{
        backgroundColor: 'color-mix(in srgb, var(--bg-surface) 94%, transparent)',
        borderColor: 'var(--border-hairline)',
      }}
    >
      <div className="grid h-[68px] grid-cols-4 items-center px-2">
        {items.map((item) => {
          const Icon = item.icon;
          const active = activeMobileTab === item.id;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => {
                triggerHaptic('light');
                onSelectMobileTab(item.id);
              }}
              aria-current={active ? 'page' : undefined}
              className="m3-tactile relative flex min-h-[52px] cursor-pointer flex-col items-center justify-center gap-1 rounded-2xl px-1 py-1"
              style={{
                color: active ? 'var(--text-primary)' : 'var(--text-muted)',
              }}
            >
              <span
                className="relative flex h-8 w-16 items-center justify-center rounded-full transition-colors duration-200"
                style={{
                  backgroundColor: active ? 'var(--md-primary-container)' : 'transparent',
                  color: active ? 'var(--md-on-primary-container)' : 'var(--text-muted)',
                }}
              >
                <Icon className="h-[19px] w-[19px]" strokeWidth={active ? 2.35 : 1.85} />
                {item.badge !== undefined && item.badge > 0 && (
                  <span
                    className="font-mono-num absolute -top-0.5 right-2 flex h-4 min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-bold"
                    style={{
                      backgroundColor: 'var(--accent-primary)',
                      color: 'var(--accent-on-primary)',
                    }}
                  >
                    {item.badge}
                  </span>
                )}
              </span>
              <span
                className="text-[11px] tracking-tight whitespace-nowrap"
                style={{ fontWeight: active ? 700 : 500 }}
              >
                {item.label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
}
