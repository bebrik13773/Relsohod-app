import { CityId, Landmark, TramRoute, TramStop } from '../types/tram';
import {
  SPB_CENTER,
  SPB_DEFAULT_START_ID,
  SPB_LANDMARKS,
  SPB_TRAM_ROUTES,
  SPB_STOPS,
} from './spbTramNetwork';
import {
  OMSK_CENTER,
  OMSK_DEFAULT_START_ID,
  OMSK_LANDMARKS,
  OMSK_STOPS,
  OMSK_TRAM_ROUTES,
} from './omskTramNetwork';
import { haversineKm } from '../utils/tramRouter';

export interface CityConfig {
  id: CityId;
  name: string;
  shortName: string;
  genitiveName: string;
  tagline: string;
  center: { lat: number; lng: number };
  defaultStartStopId: string;
  radiusKm: number;
  stops: TramStop[];
  routes: TramRoute[];
  landmarks: Landmark[];
  announcerIntro: string;
}

export const CITIES: Record<CityId, CityConfig> = {
  spb: {
    id: 'spb',
    name: 'Санкт-Петербург',
    shortName: 'СПб',
    genitiveName: 'Санкт-Петербурга',
    tagline: 'Нева, метро М1–М6, все трамваи СПб, «Славянка» и пешие прогулки',
    center: SPB_CENTER,
    defaultStartStopId: SPB_DEFAULT_START_ID,
    radiusKm: 42,
    stops: SPB_STOPS,
    routes: SPB_TRAM_ROUTES,
    landmarks: SPB_LANDMARKS,
    announcerIntro: 'СПб ГУП «Горэлектротранс» и Петербургский метрополитен желают приятного пути.',
  },
  omsk: {
    id: 'omsk',
    name: 'Омск',
    shortName: 'Омск',
    genitiveName: 'Омска',
    tagline: 'Иртыш, трамваи и магистральные троллейбусы по всему Омску',
    center: OMSK_CENTER,
    defaultStartStopId: OMSK_DEFAULT_START_ID,
    radiusKm: 32,
    stops: OMSK_STOPS,
    routes: OMSK_TRAM_ROUTES,
    landmarks: OMSK_LANDMARKS,
    announcerIntro: 'МП города Омска «Электрический транспорт» желает вам приятной поездки.',
  },
};

export interface CityDetectionResult {
  detected: CityId | 'other';
  nearestSupportedCity: CityId;
  distanceToSpbKm: number;
  distanceToOmskKm: number;
}

export function detectCityFromCoords(lat: number, lng: number): CityDetectionResult {
  const distanceToSpbKm = haversineKm({ lat, lng }, CITIES.spb.center);
  const distanceToOmskKm = haversineKm({ lat, lng }, CITIES.omsk.center);

  if (distanceToSpbKm <= CITIES.spb.radiusKm) {
    return {
      detected: 'spb',
      nearestSupportedCity: 'spb',
      distanceToSpbKm,
      distanceToOmskKm,
    };
  }
  if (distanceToOmskKm <= CITIES.omsk.radiusKm) {
    return {
      detected: 'omsk',
      nearestSupportedCity: 'omsk',
      distanceToSpbKm,
      distanceToOmskKm,
    };
  }
  return {
    detected: 'other',
    nearestSupportedCity: distanceToSpbKm <= distanceToOmskKm ? 'spb' : 'omsk',
    distanceToSpbKm,
    distanceToOmskKm,
  };
}
