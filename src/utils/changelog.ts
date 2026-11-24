export interface ChangelogSection {
  title: string;
  items: string[];
}

export interface ChangelogRelease {
  version: string;
  subtitle: string;
  sections: ChangelogSection[];
}

export const CURRENT_APP_VERSION = '2.4.0';
export const APP_VERSION_STORAGE_KEY = 'relsokhod_app_version_v1';

export interface StorageLike {
  getItem(key: string): string | null;
}

export interface AppVersionCheckResult {
  changed: boolean;
  shouldShowChangelog: boolean;
  currentVersion: string;
  storedVersion: string | null;
  release: ChangelogRelease;
}

export const LATEST_CHANGELOG: ChangelogRelease = {
  version: CURRENT_APP_VERSION,
  subtitle: 'Последнее обновление',
  sections: [
    {
      title: 'Новое',
      items: [
        '«Рельсоход» стал универсальным транспортно-пешеходным рандомайзером прогулок по Санкт-Петербургу и Омску.',
        'Добавлены все 6 линий метро СПб (М1–М6), вся сеть действующих трамваев СПб (включая этап 1 «Славянки») и проект до лета 2027 (№ 22), а также полная сеть Омска: 6 трамваев, 7 магистральных троллейбусов и встречные полукольца 2К/8К до лета 2027.',
        'Новый режим «Прибыть к часу»: задайте время встречи (например, 18:00), и приложение заполнит свободное окно разнообразной поездкой и прогулкой с запасом до цели.',
        'Всегда до 3 вариантов маршрута на выбор: включая выход на соседней остановке с долгой пешей прогулкой и обход привычных прямых линий.',
      ],
    },
    {
      title: 'Улучшения и фильтры',
      items: [
        'В Планировщике появился тумблер «Линии и продления до лета 2027», а в Справочнике — фильтры по виду транспорта и статусу линии.',
        'Расширены границы схемы Санкт-Петербурга с отображением станций метро, пунктирных строящихся линий и полных номеров маршрутов.',
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

export function checkAppVersionAgainstStorage(options?: {
  storage?: StorageLike | null;
  storageKey?: string;
  currentVersion?: string;
  onboardingDone?: boolean;
  isAutostart?: boolean;
}): AppVersionCheckResult {
  const currentVersion = options?.currentVersion ?? CURRENT_APP_VERSION;
  const storageKey = options?.storageKey ?? APP_VERSION_STORAGE_KEY;
  const onboardingDone = options?.onboardingDone ?? true;
  const isAutostart = options?.isAutostart ?? false;

  let storage: StorageLike | null = options?.storage ?? null;
  if (storage === null && options?.storage === undefined) {
    try {
      storage = typeof window !== 'undefined' ? window.localStorage : null;
    } catch {
      storage = null;
    }
  }

  if (!storage) {
    return {
      changed: false,
      shouldShowChangelog: false,
      currentVersion,
      storedVersion: null,
      release: LATEST_CHANGELOG,
    };
  }

  let storedVersion: string | null = null;
  try {
    storedVersion = storage.getItem(storageKey);
  } catch {
    storedVersion = null;
  }

  const changed = storedVersion !== currentVersion;
  const shouldShowChangelog = shouldShowChangelogOnLaunch({
    storedVersion,
    currentVersion,
    onboardingDone,
    isAutostart,
  });

  return {
    changed,
    shouldShowChangelog,
    currentVersion,
    storedVersion,
    release: LATEST_CHANGELOG,
  };
}
