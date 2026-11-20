import { CityId, SavedRoute, TripHistoryEntry } from '../types/tram';

export interface PassportBadge {
  id: string;
  title: string;
  description: string;
  icon: string;
  unlocked: boolean;
  progressText: string;
}

export function evaluatePassportBadges(
  history: TripHistoryEntry[],
  savedRoutes: SavedRoute[],
  activeCity: CityId
): {
  badges: PassportBadge[];
  totalTrips: number;
  totalPhotos: number;
  uniqueLinesCount: number;
  uniqueDistrictsCount: number;
} {
  const allTrips = [
    ...history.map((h) => h.trip),
    ...savedRoutes.map((s) => s.trip),
  ];

  const uniqueLines = new Set<string>();
  const uniqueDistricts = new Set<string>();
  let hasLoop = false;
  let hasChizhik = false;
  let hasNightOrEarly = false;
  let hasSpb = false;
  let hasOmsk = false;
  let maxTransfers = 0;

  for (const t of allTrips) {
    for (const line of t.tramLinesUsed) uniqueLines.add(line);
    for (const d of t.districtsVisited) uniqueDistricts.add(d);
    if (t.isLoop || (t.startStop.id === t.endStop.id && t.segments.length >= 2)) {
      hasLoop = true;
    }
    if (t.segments.some((s) => s.route?.type === 'chizhik')) {
      hasChizhik = true;
    }
    if (t.timeMinutes < 7 * 60 || t.timeMinutes >= 22 * 60) {
      hasNightOrEarly = true;
    }
    if (t.city === 'omsk' || t.startStop.id.startsWith('omsk_')) {
      hasOmsk = true;
    } else {
      hasSpb = true;
    }
    if (t.transferCount > maxTransfers) {
      maxTransfers = t.transferCount;
    }
  }

  const totalPhotos = history.reduce((sum, h) => sum + h.photos.length, 0);
  const totalTrips = history.length;

  const badges: PassportBadge[] = [
    {
      id: 'first_ride',
      title: 'Первый звонок кондуктора',
      description: 'Завершите или сохраните первый трамвайный маршрут.',
      icon: '🚋',
      unlocked: allTrips.length >= 1,
      progressText: `${Math.min(allTrips.length, 1)} / 1`,
    },
    {
      id: 'transfer_master',
      title: 'Мастер пересадок',
      description: 'Соберите цепочку минимум с 2 пересадками за одну прогулку.',
      icon: '🔀',
      unlocked: maxTransfers >= 2,
      progressText: `${Math.min(maxTransfers, 2)} / 2 пересадки`,
    },
    {
      id: 'district_explorer',
      title: 'Знаток районов',
      description: 'Посетите 5 разных районов по маршрутам.',
      icon: '🗺️',
      unlocked: uniqueDistricts.size >= 5,
      progressText: `${Math.min(uniqueDistricts.size, 5)} / 5`,
    },
    {
      id: 'loop_traveler',
      title: 'Трамвайное кольцо',
      description: 'Прокатитесь по круговому маршруту со стартом и финишем в одной точке.',
      icon: '⭕',
      unlocked: hasLoop,
      progressText: hasLoop ? 'Открыто' : '0 / 1',
    },
    {
      id: 'photo_chronicler',
      title: 'Фотохроникёр рельсов',
      description: 'Прикрепите хотя бы 1 фотовоспоминание в историю поездок.',
      icon: '📸',
      unlocked: totalPhotos >= 1,
      progressText: `${Math.min(totalPhotos, 1)} / 1 фото`,
    },
    {
      id: 'special_fleet',
      title: 'Ловец редкого борта',
      description: 'Прокатитесь на скоростном «Чижике» или исторических линиях.',
      icon: '✨',
      unlocked: hasChizhik || uniqueLines.has('Т1') || uniqueLines.has('А'),
      progressText: hasChizhik || uniqueLines.has('Т1') || uniqueLines.has('А') ? 'Открыто' : '0 / 1',
    },
    {
      id: 'two_capitals',
      title: 'Нева и Иртыш',
      description:
        activeCity === 'omsk'
          ? 'Спланируйте маршруты и по Омску, и по Санкт-Петербургу.'
          : 'Откройте трамвайные маршруты в обоих городах (СПб и Омск).',
      icon: '🏙️',
      unlocked: hasSpb && hasOmsk,
      progressText: `${(hasSpb ? 1 : 0) + (hasOmsk ? 1 : 0)} / 2 города`,
    },
    {
      id: 'evening_tram',
      title: 'Вечерний фонарь',
      description: 'Соберите ранний утренний или поздний вечерний маршрут.',
      icon: '🌙',
      unlocked: hasNightOrEarly,
      progressText: hasNightOrEarly ? 'Открыто' : '0 / 1',
    },
  ];

  return {
    badges,
    totalTrips,
    totalPhotos,
    uniqueLinesCount: uniqueLines.size,
    uniqueDistrictsCount: uniqueDistricts.size,
  };
}
