import React, { useRef, useState } from 'react';
import { Bus, Footprints, TramFront } from 'lucide-react';
import { ModeBalance } from '../types/tram';
import {
  balanceToTrianglePoint,
  TRIANGLE_VERTICES,
  trianglePointToBalance,
} from '../utils/mapGeometry';
import { triggerHaptic } from '../utils/haptics';

interface ModeTriangleSliderProps {
  value: ModeBalance;
  onChange: (next: ModeBalance) => void;
}

const PRESETS: Array<{ label: string; value: ModeBalance }> = [
  { label: 'Больше трамвая', value: { tram: 0.86, transit: 0.04, walk: 0.10 } },
  { label: 'Трамвай + прогулка', value: { tram: 0.50, transit: 0.05, walk: 0.45 } },
  { label: 'Баланс 3 в 1', value: { tram: 0.34, transit: 0.33, walk: 0.33 } },
  { label: 'Макс. пешком', value: { tram: 0.22, transit: 0.06, walk: 0.72 } },
];

const PAD_W = 280;
const PAD_H = 228;

export function ModeTriangleSlider({ value, onChange }: ModeTriangleSliderProps) {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const [dragging, setDragging] = useState(false);

  const thumbNorm = balanceToTrianglePoint(value);
  const thumbX = thumbNorm.x * PAD_W;
  const thumbY = thumbNorm.y * PAD_H;

  const vTram = { x: TRIANGLE_VERTICES.tram.x * PAD_W, y: TRIANGLE_VERTICES.tram.y * PAD_H };
  const vTransit = { x: TRIANGLE_VERTICES.transit.x * PAD_W, y: TRIANGLE_VERTICES.transit.y * PAD_H };
  const vWalk = { x: TRIANGLE_VERTICES.walk.x * PAD_W, y: TRIANGLE_VERTICES.walk.y * PAD_H };
  const center = {
    x: (vTram.x + vTransit.x + vWalk.x) / 3,
    y: (vTram.y + vTransit.y + vWalk.y) / 3,
  };

  const updateFromPointer = (clientX: number, clientY: number) => {
    const svg = svgRef.current;
    if (!svg) return;
    const rect = svg.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return;
    const nx = (clientX - rect.left) / rect.width;
    const ny = (clientY - rect.top) / rect.height;
    onChange(trianglePointToBalance(nx, ny));
  };

  const handlePointerDown = (e: React.PointerEvent<SVGSVGElement>) => {
    e.preventDefault();
    e.currentTarget.setPointerCapture(e.pointerId);
    setDragging(true);
    triggerHaptic('light');
    updateFromPointer(e.clientX, e.clientY);
  };

  const handlePointerMove = (e: React.PointerEvent<SVGSVGElement>) => {
    if (!dragging) return;
    updateFromPointer(e.clientX, e.clientY);
  };

  const handlePointerUp = () => {
    if (dragging) {
      setDragging(false);
    }
  };

  const pctTram = Math.round(value.tram * 100);
  const pctTransit = Math.round(value.transit * 100);
  const pctWalk = Math.max(0, 100 - pctTram - pctTransit);

  return (
    <div className="flex flex-col gap-3" data-testid="mode-triangle-slider">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-bold" style={{ color: 'var(--text-secondary)' }}>
          Баланс маршрута
        </span>
        <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
          Двигайте точку внутри треугольника
        </span>
      </div>

      {/* Live 3-mode percentage badges */}
      <div className="grid grid-cols-3 gap-1.5 text-center">
        <div
          className="flex flex-col items-center rounded-2xl px-2 py-1.5"
          style={{ backgroundColor: 'var(--md-primary-container)', color: 'var(--md-on-primary-container)' }}
        >
          <span className="inline-flex items-center gap-1 text-[11px] font-semibold">
            <TramFront className="h-3.5 w-3.5" />
            Трамвай
          </span>
          <span className="font-mono-num text-sm font-extrabold" data-testid="pct-tram">
            {pctTram}%
          </span>
        </div>

        <div
          className="flex flex-col items-center rounded-2xl px-2 py-1.5"
          style={{ backgroundColor: 'var(--bg-elevated)', color: 'var(--text-primary)' }}
        >
          <span className="inline-flex items-center gap-1 text-[11px] font-semibold">
            <Bus className="h-3.5 w-3.5 text-amber-600" />
            Транспорт
          </span>
          <span className="font-mono-num text-sm font-extrabold" data-testid="pct-transit">
            {pctTransit}%
          </span>
        </div>

        <div
          className="flex flex-col items-center rounded-2xl px-2 py-1.5"
          style={{ backgroundColor: 'var(--bg-elevated)', color: 'var(--text-primary)' }}
        >
          <span className="inline-flex items-center gap-1 text-[11px] font-semibold">
            <Footprints className="h-3.5 w-3.5 text-emerald-600" />
            Пешком
          </span>
          <span className="font-mono-num text-sm font-extrabold" data-testid="pct-walk">
            {pctWalk}%
          </span>
        </div>
      </div>

      {/* Interactive Barycentric Triangle */}
      <div
        className="relative flex flex-col items-center rounded-3xl border px-2 pt-2 pb-1"
        style={{
          backgroundColor: 'var(--bg-input)',
          borderColor: 'var(--border-hairline)',
        }}
      >
        <svg
          ref={svgRef}
          viewBox={`0 0 ${PAD_W} ${PAD_H}`}
          className="h-48 w-full max-w-[300px] touch-none select-none cursor-crosshair"
          role="slider"
          aria-label="Треугольный регулятор баланса: Трамваи, Транспорт, Пешком"
          aria-valuetext={`Трамваи ${pctTram}%, Транспорт ${pctTransit}%, Пешком ${pctWalk}%`}
          tabIndex={0}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerCancel={handlePointerUp}
        >
          <defs>
            <linearGradient id="tri-grad-base" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="var(--accent-primary)" stopOpacity="0.18" />
              <stop offset="55%" stopColor="#d97706" stopOpacity="0.11" />
              <stop offset="100%" stopColor="#059669" stopOpacity="0.16" />
            </linearGradient>
          </defs>

          {/* Outer triangle fill */}
          <polygon
            points={`${vTram.x},${vTram.y} ${vTransit.x},${vTransit.y} ${vWalk.x},${vWalk.y}`}
            fill="url(#tri-grad-base)"
            stroke="var(--border-strong)"
            strokeWidth={2}
            strokeLinejoin="round"
          />

          {/* Medians to centroid */}
          <g stroke="var(--border-hairline)" strokeWidth={1.2} strokeDasharray="4 4">
            <line x1={vTram.x} y1={vTram.y} x2={(vTransit.x + vWalk.x) / 2} y2={(vTransit.y + vWalk.y) / 2} />
            <line x1={vTransit.x} y1={vTransit.y} x2={(vTram.x + vWalk.x) / 2} y2={(vTram.y + vWalk.y) / 2} />
            <line x1={vWalk.x} y1={vWalk.y} x2={(vTram.x + vTransit.x) / 2} y2={(vTram.y + vTransit.y) / 2} />
          </g>

          {/* Subtle center marker */}
          <circle cx={center.x} cy={center.y} r={3} fill="var(--text-muted)" opacity={0.45} />

          {/* Tension lines from active thumb to each vertex */}
          <g stroke="var(--accent-primary)" strokeOpacity={0.35} strokeWidth={1.5}>
            <line x1={thumbX} y1={thumbY} x2={vTram.x} y2={vTram.y} />
            <line x1={thumbX} y1={thumbY} x2={vTransit.x} y2={vTransit.y} />
            <line x1={thumbX} y1={thumbY} x2={vWalk.x} y2={vWalk.y} />
          </g>

          {/* Vertex nodes & labels */}
          <circle cx={vTram.x} cy={vTram.y} r={6} fill="var(--accent-primary)" stroke="#ffffff" strokeWidth={2} />
          <text
            x={vTram.x}
            y={vTram.y - 10}
            textAnchor="middle"
            fontSize={11}
            fontWeight={700}
            fill="var(--text-primary)"
          >
            Трамваи
          </text>

          <circle cx={vTransit.x} cy={vTransit.y} r={6} fill="#d97706" stroke="#ffffff" strokeWidth={2} />
          <text
            x={vTransit.x - 4}
            y={vTransit.y + 18}
            textAnchor="start"
            fontSize={11}
            fontWeight={700}
            fill="var(--text-primary)"
          >
            Транспорт
          </text>

          <circle cx={vWalk.x} cy={vWalk.y} r={6} fill="#059669" stroke="#ffffff" strokeWidth={2} />
          <text
            x={vWalk.x + 4}
            y={vWalk.y + 18}
            textAnchor="end"
            fontSize={11}
            fontWeight={700}
            fill="var(--text-primary)"
          >
            Пешком
          </text>

          {/* Draggable Thumb */}
          <circle
            cx={thumbX}
            cy={thumbY}
            r={dragging ? 15 : 13}
            fill="var(--accent-primary)"
            fillOpacity={0.22}
          />
          <circle
            cx={thumbX}
            cy={thumbY}
            r={dragging ? 9.5 : 8}
            fill="var(--accent-primary)"
            stroke="#ffffff"
            strokeWidth={2.5}
          />
        </svg>

        {/* Quick 1-tap presets */}
        <div className="mb-1.5 flex flex-wrap justify-center gap-1.5 px-1">
          {PRESETS.map((preset) => {
            const isClose =
              Math.abs(value.tram - preset.value.tram) < 0.06 &&
              Math.abs(value.transit - preset.value.transit) < 0.06 &&
              Math.abs(value.walk - preset.value.walk) < 0.06;
            return (
              <button
                key={preset.label}
                type="button"
                onClick={() => {
                  triggerHaptic('light');
                  onChange(preset.value);
                }}
                className="m3-tactile cursor-pointer rounded-xl border px-2.5 py-1 text-[11px] font-semibold"
                style={{
                  backgroundColor: isClose ? 'var(--md-primary-container)' : 'var(--bg-surface)',
                  color: isClose ? 'var(--md-on-primary-container)' : 'var(--text-secondary)',
                  borderColor: isClose ? 'transparent' : 'var(--border-hairline)',
                }}
              >
                {preset.label}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
