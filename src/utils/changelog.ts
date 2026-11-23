export interface ChangelogSection {
  title: string;
  items: string[];
}

export interface ChangelogRelease {
  version: string;
  subtitle: string;
  sections: ChangelogSection[];
}

export const CURRENT_APP_VERSION = '2.3.0';

export const LATEST_CHANGELOG: ChangelogRelease = {
  version: CURRENT_APP_VERSION,
  subtitle: 'Ноябрь 2026',
  sections: [
    {
      title: 'Новое',
      items: [
        'Единый экспорт и полное восстановление данных (маршруты, паспорт с фото, настройки) в файл export-ГГГГ-ММ-ДД.relsohod между браузером и Android.',
        'Официальные аудиогиды по маршруту и остановкам (Visit Petersburg, Горэлектротранс, КГИОП, ТИЦ Омска).',
        'Фирменная иконка «Нева» и анимированный стартовый экран.',
        'В Android-клиенте добавлена проверка, скачивание и установка обновлений прямо из приложения.',
      ],
    },
    {
      title: 'Улучшения и исправления',
      items: [
        'В обучении запрос геолокации перенесён до выбора города, чтобы город определялся автоматически.',
        'Добавлен шаг импорта резервной копии .relsohod прямо при первом запуске.',
        'Исправлен подбор аудиогида в режиме «В пути» и улучшена вёрстка на узких экранах.',
      ],
    },
  ],
};

export function parseChangelogSections(rawMarkdown: string): ChangelogRelease | null {
  const lines = rawMarkdown.split(/\r?\n/);
  let version: string | null = null;
  const sections: ChangelogSection[] = [];
  let currentSection: ChangelogSection | null = null;

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line) continue;

    if (line.startsWith('## ')) {
      if (version !== null) {
        break;
      }
      version = line.slice(3).trim();
      continue;
    }

    if (version === null) continue;

    if (line.startsWith('### ')) {
      currentSection = {
        title: line.slice(4).trim(),
        items: [],
      };
      sections.push(currentSection);
      continue;
    }

    if (line.startsWith('- ') || line.startsWith('* ')) {
      const text = line.slice(2).trim();
      if (!text) continue;
      if (!currentSection) {
        currentSection = { title: 'Изменения', items: [] };
        sections.push(currentSection);
      }
      currentSection.items.push(text);
    }
  }

  if (!version || sections.length === 0) return null;
  return {
    version,
    subtitle: 'Актуальное обновление',
    sections: sections.filter((s) => s.items.length > 0),
  };
}

export function shouldShowChangelogOnLaunch(params: {
  storedVersion: string | null;
  currentVersion: string;
  onboardingDone: boolean;
  isAutostart: boolean;
}): boolean {
  const { storedVersion, currentVersion, onboardingDone, isAutostart } = params;
  if (storedVersion === currentVersion) return false;
  if (storedVersion !== null && storedVersion !== currentVersion) {
    return true;
  }
  if (isAutostart) return false;
  return onboardingDone;
}
