import { GeneratedTrip, TramStop } from '../types/tram';

function formatCoordPoint(lat: number, lng: number): string {
  return `${lat.toFixed(6)}%2C${lng.toFixed(6)}`;
}

/**
 * Generates a ready-to-open Yandex Maps public-transit routing link (`rtt=mt`)
 * connecting key stops & transfer nodes of the generated trip.
 * Example format:
 * https://yandex.ru/maps?rtext=55.000375%2C73.358408~54.998903%2C73.364744&rtt=mt
 */
export function buildYandexMapsRouteUrl(trip: GeneratedTrip): string {
  const waypoints: TramStop[] = [];
  const pushUnique = (stop: TramStop | undefined) => {
    if (!stop) return;
    const prev = waypoints[waypoints.length - 1];
    if (
      prev &&
      Math.abs(prev.lat - stop.lat) < 0.00001 &&
      Math.abs(prev.lng - stop.lng) < 0.00001
    ) {
      return;
    }
    waypoints.push(stop);
  };

  pushUnique(trip.startStop);
  for (const seg of trip.segments) {
    pushUnique(seg.fromStop);
    pushUnique(seg.toStop);
  }
  pushUnique(trip.endStop);

  // Keep link compact (Yandex Maps URL performs cleanly with up to ~12 waypoints)
  let sampled = waypoints;
  if (waypoints.length > 10) {
    sampled = [waypoints[0]];
    const step = (waypoints.length - 2) / 8;
    for (let i = 1; i <= 8; i++) {
      sampled.push(waypoints[Math.round(i * step)]);
    }
    sampled.push(waypoints[waypoints.length - 1]);
  }

  const rtext = sampled.map((pt) => formatCoordPoint(pt.lat, pt.lng)).join('~');
  return `https://yandex.ru/maps?rtext=${rtext}&rtt=mt`;
}
