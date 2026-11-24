import { GeneratedTrip, MatchedAudioGuide, OfficialAudioGuide } from '../types/tram';

export const OFFICIAL_AUDIO_GUIDES: OfficialAudioGuide[] = [
  {
    id: 'spb-get-route-3',
    city: 'spb',
    title: 'Видовой трамвайный маршрут № 3',
    portalName: 'izi.TRAVEL · СПб ГУП «Горэлектротранс»',
    url: 'https://izi.travel/ru/3f47-vidovoy-tramvaynyy-marshrut-no3/ru',
    durationLabel: '~45 мин',
    description:
      'Официальный аудиогид Горэлектротранса по трамваю № 3: площадь Репина, Семимостье, Сенная, Марсово поле, Троицкий мост и Финляндский вокзал.',
    matchRouteNumbers: ['3'],
    matchDistricts: ['Адмиралтейский', 'Центральный', 'Петроградская сторона', 'Петроградский'],
  },
  {
    id: 'spb-get-museum-channel',
    city: 'spb',
    title: 'Экскурсии «Петербургский Горэлектротранс» и ретро-трамвай Т1',
    portalName: 'izi.TRAVEL · Музей ГЭТ СПб',
    url: 'https://izi.travel/ru/0721-peterburgskiy-gorelektrotrans/ru',
    durationLabel: 'Подборка',
    description:
      'Фирменные аудиоэкскурсии перевозчика об истории петербургского трамвая, маршруте «Первый туристический» (Т1) и трамвайных мостах через Неву.',
    matchRouteNumbers: ['Т1', '6', '40', '23', '1'],
    matchDistricts: ['Васильевский остров', 'Петроградская сторона', 'Центральный'],
    isCityOverview: true,
  },
  {
    id: 'spb-get-museum-site',
    city: 'spb',
    title: 'Музей городского электрического транспорта и ретро-рейс Т1',
    portalName: 'Официальный сайт Музея ГЭТ',
    url: 'https://getmuseum.ru/',
    durationLabel: 'Расписание и гид',
    description:
      'Официальная страница Экспозиционно-выставочного комплекса ГЭТ на Среднем проспекте В.О., 77: трассы туристического ретро-трамвая Т1 и история вагонов.',
    matchRouteNumbers: ['Т1'],
    matchDistricts: ['Васильевский остров'],
  },
  {
    id: 'spb-kgiop-52-60',
    city: 'spb',
    title: 'Краеведческие аудиогиды по трамваям № 52 и № 60',
    portalName: 'izi.TRAVEL · КГИОП СПб',
    url: 'https://izi.travel/ru/6f29-mezhregionalnyy-festival-socialnykh-proektov-kgiop-missiya-sokhranit/ru',
    durationLabel: '~40 мин',
    description:
      'Аудиомаршруты по юго-западу Петербурга (к Северной верфи, Сосновой Поляне и ЛЭМЗ), озвученные актрисой Анастасией Мельниковой в проекте КГИОП.',
    matchRouteNumbers: ['52', '60', '36', '41'],
    matchDistricts: ['Кировский', 'Красносельский', 'Петродворцовый'],
  },
  {
    id: 'spb-visit-kolomna',
    city: 'spb',
    title: 'Очарование Коломны',
    portalName: 'Visit Petersburg · Комитет по туризму СПб',
    url: 'https://visit-petersburg.ru/route/ocharovanie-kolomny/',
    durationLabel: '~50 мин',
    description:
      'Официальный городской аудиомаршрут вдоль Крюкова канала, Никольского собора, Семимостья и площади Тургенева рядом с линией трамваев № 3 и № 16.',
    matchRouteNumbers: ['3', '16', '41'],
    matchDistricts: ['Адмиралтейский'],
  },
  {
    id: 'spb-visit-vasilevsky',
    city: 'spb',
    title: 'Васильевский. Остров памяти и славы',
    portalName: 'Visit Petersburg · Комитет по туризму СПб',
    url: 'https://visit-petersburg.ru/route/vasilevskiy-ostrov-pamyati-i-slavy/',
    durationLabel: '~55 мин',
    description:
      'Официальный аудиомаршрут по линиям Васильевского острова, Стрелке, Среднему проспекту и набережным Невы вдоль трамваев № 1, № 6, № 40 и Т1.',
    matchRouteNumbers: ['1', '6', '40', 'Т1'],
    matchDistricts: ['Васильевский остров'],
  },
  {
    id: 'spb-visit-nevsky',
    city: 'spb',
    title: 'Храмы Невского проспекта и исторический центр',
    portalName: 'Visit Petersburg · Комитет по туризму СПб',
    url: 'https://visit-petersburg.ru/route/khramy-nevskogo-prospekta/',
    durationLabel: '~45 мин',
    description:
      'Официальный аудиопутеводитель Visit Petersburg по парадному центру, Литейному, Садовой и окрестностям центральных трамвайных линий.',
    matchRouteNumbers: ['3', '16', 'А', 'Т1'],
    matchDistricts: ['Центральный', 'Адмиралтейский'],
  },
  {
    id: 'spb-visit-new-geography',
    city: 'spb',
    title: 'Новая туристская география Петербурга XXI века',
    portalName: 'Visit Petersburg · Комитет по туризму СПб',
    url: 'https://visit-petersburg.ru/route/audiomarshrut-novaya-turistskaya-geografiya-10-dostoprimechatelnostey-sankt-peterburga-xxi-veka/',
    durationLabel: '~60 мин',
    description:
      'Официальный аудиомаршрут по современным кварталам, набережным Приморского, Невского и Красногвардейского районов, линиям «Чижик» и «Славянка».',
    matchRouteNumbers: ['48', '19', '7', '8', '63', '64', '23', '2', '22', 'М3', 'М5'],
    matchDistricts: [
      'Приморский',
      'Невский',
      'Красногвардейский',
      'Выборгский',
      'Калининский',
      'Московский',
      'Фрунзенский',
      'Пушкинский',
    ],
  },
  {
    id: 'spb-visit-portal',
    city: 'spb',
    title: 'Официальный туристский портал Санкт-Петербурга',
    portalName: 'Visit Petersburg',
    url: 'https://visit-petersburg.ru/',
    durationLabel: 'Каталог гидов',
    description:
      'Официальная подборка городских аудиоэкскурсий, подкастов и маршрутов по всем районам Петербурга.',
    matchRouteNumbers: ['М1', 'М2', 'М3', 'М4', 'М5', 'М6'],
    matchDistricts: [],
    isCityOverview: true,
  },
  {
    id: 'omsk-tic-welcome',
    city: 'omsk',
    title: 'Добро пожаловать в Омск!',
    portalName: 'izi.TRAVEL · ТИЦ Омской области',
    url: 'https://izi.travel/ru/09f1-dobro-pozhalovat-v-omsk/ru',
    durationLabel: '~50 мин',
    description:
      'Официальный обзорный аудиогид Туристского информационного центра Омской области: Омская крепость, Любинский проспект, площадь Бухгольца и набережная Иртыша.',
    matchRouteNumbers: ['1', '2', '4', '7', '8', '9', 'Тр-4', 'Тр-7', 'Тр-67'],
    matchDistricts: ['Центральный', 'Ленинский', 'Кировский'],
    isCityOverview: true,
  },
  {
    id: 'omsk-next-stop',
    city: 'omsk',
    title: 'Следующая остановка — Омск из окна электротранспорта',
    portalName: 'izi.TRAVEL · Культурный проект Омска',
    url: 'https://izi.travel/ru/c234-sleduyuschaya-ostanovka/ru',
    durationLabel: '~45 мин',
    description:
      'Аудиоэкскурсия о районах и магистралях Омска из окна городского электротранспорта — отличное сопровождение для прогулки на омском трамвае и троллейбусе.',
    matchRouteNumbers: ['4', '7', '8', '2', '9', '2К', '8К', 'Тр-2', 'Тр-3', 'Тр-15', 'Тр-16'],
    matchDistricts: ['Центральный', 'Октябрьский', 'Советский', 'Ленинский', 'Амурский посёлок'],
    isCityOverview: true,
  },
  {
    id: 'omsk-dostoevsky-walk',
    city: 'omsk',
    title: 'Прогулка с Ф. М. Достоевским по Омску',
    portalName: 'izi.TRAVEL · Литературный Омск',
    url: 'https://izi.travel/ru/bd19-progulka-s-f-m-dostoevskim/ru',
    durationLabel: '~40 мин',
    description:
      'Аудиомаршрут по историческому центру, Воскресенскому военному собору, Омской крепости и дому коменданта — вдоль центральных трамвайных узлов.',
    matchRouteNumbers: ['1', '4', '7', '8', 'Тр-4', 'Тр-7'],
    matchDistricts: ['Центральный'],
  },
  {
    id: 'omsk-dostoevsky-fortress',
    city: 'omsk',
    title: 'Достоевский. Я здесь сидел',
    portalName: 'izi.TRAVEL · Омская крепость',
    url: 'https://izi.travel/ru/7fc4-dostoevskiy-ya-zdes-sidel/ru',
    durationLabel: '~35 мин',
    description:
      'Исторический аудиогид о годах писателя в Омске, Тарских и Тобольских воротах и памятниках центра города.',
    matchRouteNumbers: ['4', '7', '8', 'Тр-3', 'Тр-15'],
    matchDistricts: ['Центральный', 'Октябрьский'],
  },
];

function normalizeRouteToken(raw: string): string {
  return raw.trim().toUpperCase();
}

function formatLineTokenLabel(token: string): string {
  const upper = token.trim().toUpperCase();
  if (upper.startsWith('М')) return `метро ${token}`;
  if (upper.startsWith('ТР-')) return `троллейбус № ${token.slice(3)}`;
  return `трамвай № ${token}`;
}

export function matchAudioGuidesForTrip(
  trip: GeneratedTrip,
  limit = 3,
  preferredRouteNumber?: string
): MatchedAudioGuide[] {
  const city = trip.city ?? 'spb';
  const usedLines = new Set(trip.tramLinesUsed.map(normalizeRouteToken));
  const preferredToken = preferredRouteNumber ? normalizeRouteToken(preferredRouteNumber) : null;
  const visitedDistricts = new Set(trip.districtsVisited.map((d) => d.trim()));
  const tripLandmarkIds = new Set(trip.landmarks.map((l) => l.id));

  const scored = OFFICIAL_AUDIO_GUIDES.filter((g) => g.city === city).map((guide) => {
    const matchedLines = guide.matchRouteNumbers.filter((num) =>
      usedLines.has(normalizeRouteToken(num))
    );
    const matchesPreferredLine =
      preferredToken !== null &&
      guide.matchRouteNumbers.some((num) => normalizeRouteToken(num) === preferredToken);
    const matchedDistricts = guide.matchDistricts.filter((d) =>
      visitedDistricts.has(d.trim())
    );
    const matchedLandmarks = (guide.matchLandmarkIds ?? []).filter((id) =>
      tripLandmarkIds.has(id)
    );

    let score = 0;
    if (matchesPreferredLine) {
      score += 600;
      if (guide.matchRouteNumbers.length === 1) {
        score += 250;
      }
    }
    score += matchedLines.length * 100;
    score += matchedDistricts.length * 35;
    score += matchedLandmarks.length * 20;
    if (guide.isCityOverview) {
      score += 12;
    }

    let matchReason: string;
    if (matchesPreferredLine && preferredRouteNumber) {
      matchReason = `Сейчас по шагу: ${formatLineTokenLabel(preferredRouteNumber)}`;
    } else if (matchedLines.length > 0) {
      matchReason = `По вашему маршруту: ${matchedLines.slice(0, 3).map(formatLineTokenLabel).join(', ')}`;
    } else if (matchedDistricts.length > 0) {
      matchReason = `Район маршрута: ${matchedDistricts.slice(0, 2).join(', ')}`;
    } else {
      matchReason =
        city === 'omsk'
          ? 'Официальный обзорный аудиогид по Омску'
          : 'Официальный городской аудиопортал СПб';
    }

    return {
      ...guide,
      matchReason,
      matchScore: score,
    };
  });

  return scored
    .sort((a, b) => b.matchScore - a.matchScore || a.title.localeCompare(b.title, 'ru'))
    .slice(0, Math.max(1, limit));
}
