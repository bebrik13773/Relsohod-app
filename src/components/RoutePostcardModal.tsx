import { useEffect, useRef, useState } from 'react';
import { Check, Copy, Download, Share2, X } from 'lucide-react';
import { CityId, GeneratedTrip } from '../types/tram';
import { CITIES } from '../data/cities';
import { buildYandexMapsRouteUrl } from '../utils/yandexMaps';
import { triggerHaptic } from '../utils/haptics';

interface RoutePostcardModalProps {
  trip: GeneratedTrip;
  city: CityId;
  onClose: () => void;
}

export function RoutePostcardModal({ trip, city, onClose }: RoutePostcardModalProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [copied, setCopied] = useState(false);

  const yandexUrl = buildYandexMapsRouteUrl(trip);
  const cityName = CITIES[city].name;
  const startName = trip.startStop.name.split('(')[0].trim();
  const endName = trip.endStop.name.split('(')[0].trim();

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const w = 960;
    const h = 540;
    canvas.width = w;
    canvas.height = h;

    // Warm cream postcard background
    ctx.fillStyle = '#f5f7fa';
    ctx.fillRect(0, 0, w, h);

    // Top accent stripe
    ctx.fillStyle = '#0f5e8c';
    ctx.fillRect(0, 0, w, 14);

    // Outer card border
    ctx.strokeStyle = '#d4dce6';
    ctx.lineWidth = 2;
    ctx.strokeRect(24, 34, w - 48, h - 58);

    // Brand label
    ctx.fillStyle = '#0f5e8c';
    ctx.font = 'bold 26px sans-serif';
    ctx.fillText(`РЕЛЬСОХОД · ${cityName.toUpperCase()}`, 56, 86);

    // Route title
    ctx.fillStyle = '#0f172a';
    ctx.font = 'bold 34px sans-serif';
    ctx.fillText(`${startName} →`, 56, 152);
    ctx.fillText(`${endName}`, 56, 196);

    // Stats line
    ctx.fillStyle = '#475569';
    ctx.font = '600 22px sans-serif';
    ctx.fillText(
      `~${trip.totalDurationMinutes} мин  ·  Пересадок: ${trip.transferCount}  ·  Районов: ${trip.districtsVisited.length}`,
      56,
      248
    );

    // Draw tram badges
    let x = 56;
    const y = 286;
    trip.segments.forEach((seg, idx) => {
      if (idx > 0) {
        ctx.fillStyle = '#64748b';
        ctx.font = 'bold 22px sans-serif';
        ctx.fillText('→', x, y + 32);
        x += 34;
      }
      const label =
        seg.kind === 'tram'
          ? `№${seg.route?.number}`
          : seg.kind === 'walk'
            ? 'Пешком'
            : 'Стыковка';
      const boxW = Math.max(82, label.length * 16 + 24);
      if (x + boxW > w - 60) return;
      ctx.fillStyle =
        seg.kind === 'tram'
          ? seg.route?.color ?? '#0f5e8c'
          : seg.kind === 'walk'
            ? '#64748b'
            : '#d97706';
      ctx.beginPath();
      ctx.roundRect(x, y, boxW, 46, 14);
      ctx.fill();
      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 20px sans-serif';
      ctx.fillText(label, x + 14, y + 30);
      x += boxW + 12;
    });

    // Districts & footer
    ctx.fillStyle = '#334155';
    ctx.font = '500 20px sans-serif';
    ctx.fillText(`Районы: ${trip.districtsVisited.slice(0, 4).join(', ')}`, 56, 396);

    ctx.fillStyle = '#64748b';
    ctx.font = '500 17px sans-serif';
    ctx.fillText('Ведение по маршруту доступно по ссылке в Яндекс Картах', 56, 468);
  }, [trip, cityName, startName, endName]);

  const handleDownloadPng = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    triggerHaptic('success');
    const a = document.createElement('a');
    a.href = canvas.toDataURL('image/png');
    a.download = `relsokhod-${city}-route.png`;
    a.click();
  };

  const shareText = `🚋 Маршрут в «Рельсоходе» (${cityName}):\n${startName} → ${endName}\nТрамваи: ${trip.tramLinesUsed.join(' → ')} (~${trip.totalDurationMinutes} мин)\nЯндекс Карты: ${yandexUrl}`;

  const handleCopyShare = async () => {
    triggerHaptic('light');
    try {
      await navigator.clipboard.writeText(shareText);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // ignore
    }
  };

  const handleNativeShare = async () => {
    triggerHaptic('medium');
    if (navigator.share) {
      try {
        await navigator.share({
          title: `Рельсоход: ${startName} → ${endName}`,
          text: shareText,
          url: yandexUrl,
        });
        return;
      } catch {
        // user cancelled or unavailable
      }
    }
    await handleCopyShare();
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Открытка маршрута"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4 backdrop-blur-sm"
    >
      <div
        className="m3-card flex w-full max-w-lg flex-col gap-4 rounded-3xl p-5"
        style={{
          backgroundColor: 'var(--bg-surface)',
          color: 'var(--text-primary)',
        }}
      >
        <div className="flex items-center justify-between">
          <h2 className="font-display text-lg font-extrabold">Открытка маршрута</h2>
          <button
            type="button"
            aria-label="Закрыть открытку"
            onClick={onClose}
            className="m3-tactile flex h-9 w-9 cursor-pointer items-center justify-center rounded-full"
            style={{ backgroundColor: 'var(--bg-elevated)' }}
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <canvas
          ref={canvasRef}
          className="aspect-[16/9] w-full rounded-2xl border"
          style={{ borderColor: 'var(--border-hairline)' }}
        />

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={handleDownloadPng}
            className="m3-tactile inline-flex min-h-[42px] flex-1 cursor-pointer items-center justify-center gap-1.5 rounded-2xl px-4 py-2 text-xs font-bold"
            style={{
              backgroundColor: 'var(--accent-primary)',
              color: 'var(--accent-on-primary)',
            }}
          >
            <Download className="h-4 w-4" />
            Скачать PNG
          </button>
          <button
            type="button"
            onClick={handleCopyShare}
            className="m3-tactile inline-flex min-h-[42px] cursor-pointer items-center justify-center gap-1.5 rounded-2xl px-4 py-2 text-xs font-semibold"
            style={{
              backgroundColor: 'var(--md-primary-container)',
              color: 'var(--md-on-primary-container)',
            }}
          >
            {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
            {copied ? 'Скопировано' : 'Копировать'}
          </button>
          <button
            type="button"
            onClick={handleNativeShare}
            className="m3-tactile inline-flex min-h-[42px] cursor-pointer items-center justify-center gap-1.5 rounded-2xl px-4 py-2 text-xs font-semibold"
            style={{
              backgroundColor: 'var(--bg-elevated)',
              color: 'var(--text-primary)',
            }}
          >
            <Share2 className="h-4 w-4" />
            Поделиться
          </button>
        </div>
      </div>
    </div>
  );
}
