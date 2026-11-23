import { Moon, Settings, Sun, TramFront } from 'lucide-react';
import { AppTheme, CityId } from '../types/tram';
import { triggerHaptic } from '../utils/haptics';
import { BrandLogo } from './BrandLogo';
import { PWAInstallButton } from './PWAInstallButton';

export type ActiveTab = 'planner' | 'favorites' | 'directory';

interface TopBarProps {
  city: CityId;
  activeTab: ActiveTab;
  onSelectTab: (tab: ActiveTab) => void;
  favoritesCount: number;
  theme: AppTheme;
  onChangeTheme: (theme: AppTheme) => void;
  onOpenSettings: () => void;
}

const THEME_OPTIONS: Array<{ id: AppTheme; label: string }> = [
  { id: 'light', label: 'Светлая' },
  { id: 'dark', label: 'Тёмная' },
  { id: 'tram', label: 'Трамвай' },
];

export function TopBar({
  city,
  activeTab,
  onSelectTab,
  favoritesCount,
  theme,
  onChangeTheme,
  onOpenSettings,
}: TopBarProps) {
  const handleCycleTheme = () => {
    triggerHaptic('light');
    const idx = THEME_OPTIONS.findIndex((t) => t.id === theme);
    const next = THEME_OPTIONS[(idx + 1) % THEME_OPTIONS.length];
    onChangeTheme(next.id);
  };

  const currentThemeLabel = THEME_OPTIONS.find((t) => t.id === theme)?.label ?? 'Светлая';
  const linesLabel = city === 'omsk' ? 'Линии Омска' : 'Линии СПб';

  return (
    <header
      className="sticky top-0 z-30 border-b backdrop-blur-md transition-colors"
      style={{
        backgroundColor: 'color-mix(in srgb, var(--md-surface-container-low) 92%, transparent)',
        borderColor: 'var(--md-outline-variant)',
      }}
    >
      <div className="mx-auto flex h-14 max-w-[1600px] items-center justify-between gap-3 px-4 lg:gap-8 lg:px-6">
        {/* Zone 1: Brand icon + single-line brand wordmark */}
        <button
          type="button"
          onClick={() => {
            triggerHaptic('light');
            onSelectTab('planner');
          }}
          className="font-display m3-tactile flex cursor-pointer items-center gap-2.5 text-left text-lg font-extrabold tracking-tight whitespace-nowrap shrink-0 focus-visible:outline-2 focus-visible:outline-offset-2 sm:text-xl"
          style={{ color: 'var(--text-primary)' }}
        >
          <BrandLogo size="sm" />
          <span>Рельсоход</span>
        </button>

        {/* Zone 2: Clean typographic desktop navigation (hidden on mobile; bottom nav takes over on <768px) */}
        <nav aria-label="Основные разделы" className="hidden items-center gap-7 md:flex">
          <button
            type="button"
            onClick={() => {
              triggerHaptic('light');
              onSelectTab('planner');
            }}
            aria-current={activeTab === 'planner' ? 'page' : undefined}
            className="m3-tactile cursor-pointer border-b-2 py-4 text-sm font-semibold whitespace-nowrap shrink-0 transition-colors"
            style={{
              color: activeTab === 'planner' ? 'var(--accent-primary)' : 'var(--text-secondary)',
              borderColor: activeTab === 'planner' ? 'var(--accent-primary)' : 'transparent',
            }}
          >
            Планировщик
          </button>
          <button
            type="button"
            onClick={() => {
              triggerHaptic('light');
              onSelectTab('favorites');
            }}
            aria-current={activeTab === 'favorites' ? 'page' : undefined}
            className="m3-tactile cursor-pointer border-b-2 py-4 text-sm font-semibold whitespace-nowrap shrink-0 transition-colors"
            style={{
              color: activeTab === 'favorites' ? 'var(--accent-primary)' : 'var(--text-secondary)',
              borderColor: activeTab === 'favorites' ? 'var(--accent-primary)' : 'transparent',
            }}
          >
            Избранное{favoritesCount > 0 ? ` (${favoritesCount})` : ''}
          </button>
          <button
            type="button"
            onClick={() => {
              triggerHaptic('light');
              onSelectTab('directory');
            }}
            aria-current={activeTab === 'directory' ? 'page' : undefined}
            className="m3-tactile cursor-pointer border-b-2 py-4 text-sm font-semibold whitespace-nowrap shrink-0 transition-colors"
            style={{
              color: activeTab === 'directory' ? 'var(--accent-primary)' : 'var(--text-secondary)',
              borderColor: activeTab === 'directory' ? 'var(--accent-primary)' : 'transparent',
            }}
          >
            {linesLabel}
          </button>
        </nav>

        {/* Zone 3: Install button, Theme switcher & Settings */}
        <div className="flex items-center gap-2 shrink-0">
          <PWAInstallButton compact />

          <div
            role="group"
            aria-label="Тема оформления"
            className="hidden items-center gap-1 rounded-full p-1 shrink-0 md:flex"
            style={{
              backgroundColor: 'var(--md-surface-container-high)',
              border: '1px solid var(--md-outline-variant)',
            }}
          >
            {THEME_OPTIONS.map((opt) => {
              const active = theme === opt.id;
              return (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => {
                    triggerHaptic('light');
                    onChangeTheme(opt.id);
                  }}
                  aria-pressed={active}
                  className="m3-tactile cursor-pointer rounded-full px-3 py-1 text-xs font-semibold whitespace-nowrap shrink-0"
                  style={{
                    backgroundColor: active ? 'var(--md-primary-container)' : 'transparent',
                    color: active ? 'var(--md-on-primary-container)' : 'var(--text-muted)',
                  }}
                >
                  {opt.label}
                </button>
              );
            })}
          </div>

          <button
            type="button"
            onClick={handleCycleTheme}
            aria-label={`Сменить тему оформления (сейчас: ${currentThemeLabel})`}
            className="m3-tactile inline-flex min-h-[38px] cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold whitespace-nowrap shrink-0 md:hidden"
            style={{
              backgroundColor: 'var(--md-primary-container)',
              color: 'var(--md-on-primary-container)',
              borderColor: 'var(--md-outline-variant)',
            }}
          >
            {theme === 'dark' ? (
              <Moon className="h-3.5 w-3.5" />
            ) : theme === 'tram' ? (
              <TramFront className="h-3.5 w-3.5" />
            ) : (
              <Sun className="h-3.5 w-3.5" />
            )}
            <span>{currentThemeLabel}</span>
          </button>

          <button
            type="button"
            onClick={() => {
              triggerHaptic('light');
              onOpenSettings();
            }}
            aria-label="Настройки приложения"
            className="m3-tactile flex h-[40px] w-[40px] cursor-pointer items-center justify-center rounded-full border shrink-0"
            style={{
              backgroundColor: 'var(--md-surface-container-high)',
              color: 'var(--text-primary)',
              borderColor: 'var(--md-outline-variant)',
            }}
          >
            <Settings className="h-4 w-4" />
          </button>
        </div>
      </div>
    </header>
  );
}
