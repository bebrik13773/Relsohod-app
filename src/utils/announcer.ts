import { CityId, GeneratedTrip } from '../types/tram';
import { CITIES } from '../data/cities';

export interface AnnouncerLine {
  id: string;
  label: string;
  text: string;
  stopName: string;
}

/**
 * Generates authentic Russian tram auto-informer announcements styled after
 * СПб ГУП «Горэлектротранс» and МП г. Омска «Электрический транспорт».
 */
export function buildTripAnnouncements(
  trip: GeneratedTrip,
  city: CityId = trip.city ?? 'spb'
): AnnouncerLine[] {
  const lines: AnnouncerLine[] = [];
  const intro = CITIES[city].announcerIntro;

  lines.push({
    id: 'intro',
    label: 'Отправление',
    stopName: trip.startStop.name,
    text: `${intro} Начальная остановка — «${trip.startStop.name.split('(')[0].trim()}». Будьте внимательны, оплачивайте проезд при входе.`,
  });

  trip.segments.forEach((seg, idx) => {
    const fromClean = seg.fromStop.name.split('(')[0].trim();
    const toClean = seg.toStop.name.split('(')[0].trim();

    if (seg.kind === 'tram' && seg.route) {
      const isMetro = seg.route.type === 'metro';
      const isTrol = seg.route.type === 'trolleybus';
      const numClean = isTrol ? seg.route.number.replace(/^Тр-/, '') : seg.route.number;
      const vehLabel = isMetro
        ? `Метро ${seg.route.number}`
        : isTrol
          ? `Троллейбус № ${numClean}`
          : `Трамвай № ${seg.route.number}`;
      const departText = isMetro
        ? `Линия метро ${seg.route.number}, следуйте до станции «${toClean}».`
        : isTrol
          ? `Троллейбус маршрута номер ${numClean}, следует до остановки «${toClean}». Осторожно, двери закрываются.`
          : `Трамвай маршрута номер ${seg.route.number}, следует до остановки «${toClean}». Осторожно, двери закрываются. Следующая остановка — по маршруту.`;
      const arriveText =
        idx === trip.segments.length - 1
          ? `${isMetro ? 'Станция' : 'Остановка'} «${toClean}», конечная. Не забывайте свои вещи.`
          : `${isMetro ? 'Станция' : 'Остановка'} «${toClean}». Пересадка на следующий участок маршрута.`;

      lines.push({
        id: `${seg.id}-depart`,
        label: vehLabel,
        stopName: fromClean,
        text: departText,
      });
      lines.push({
        id: `${seg.id}-arrive`,
        label: idx === trip.segments.length - 1 ? 'Конечная' : 'Прибытие / Пересадка',
        stopName: toClean,
        text: arriveText,
      });
    } else if (seg.kind === 'walk') {
      lines.push({
        id: `${seg.id}-walk`,
        label: seg.durationMinutes >= 12 ? 'Пешая прогулка' : 'Пересадка пешком',
        stopName: toClean,
        text: `Пеший переход от остановки «${fromClean}» к остановке «${toClean}». Ориентировочно ${seg.durationMinutes} минут.`,
      });
    } else {
      lines.push({
        id: `${seg.id}-fallback`,
        label: 'Стыковка',
        stopName: toClean,
        text: `На этом участке пересядьте на ${seg.fallbackModeName ?? 'городской транспорт'} до остановки «${toClean}».`,
      });
    }
  });

  return lines;
}

/**
 * Plays an authentic 2-tone tram announcer chime using Web Audio API.
 */
export function playTramChime(): void {
  if (typeof window === 'undefined') return;
  try {
    const AudioCtx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx();
    const now = ctx.currentTime;

    const playTone = (freq: number, start: number, duration: number) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, start);
      gain.gain.setValueAtTime(0.001, start);
      gain.gain.exponentialRampToValueAtTime(0.12, start + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.001, start + duration);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(start);
      osc.stop(start + duration);
    };

    playTone(587.33, now, 0.22); // D5
    playTone(880.0, now + 0.19, 0.35); // A5
  } catch {
    // Ignore audio context blocks silently
  }
}

export function speakAnnouncement(text: string, onEnd?: () => void): boolean {
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
    return false;
  }
  try {
    window.speechSynthesis.cancel();
    playTramChime();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'ru-RU';
    utterance.rate = 0.96;
    utterance.pitch = 1.0;
    const voices = window.speechSynthesis.getVoices();
    const ruVoice = voices.find((v) => v.lang.toLowerCase().startsWith('ru'));
    if (ruVoice) {
      utterance.voice = ruVoice;
    }
    if (onEnd) {
      utterance.onend = onEnd;
      utterance.onerror = onEnd;
    }
    setTimeout(() => {
      window.speechSynthesis.speak(utterance);
    }, 320);
    return true;
  } catch {
    return false;
  }
}

export function stopAnnouncement(): void {
  if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
    try {
      window.speechSynthesis.cancel();
    } catch {
      // ignore
    }
  }
}
