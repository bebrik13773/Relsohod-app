import { ModeBalance } from '../types/tram';

export interface Point2D {
  x: number;
  y: number;
}

/**
 * Generates a C1-smooth cubic Bezier SVG path string from a sequence of 2D points
 * using Catmull-Rom tangent interpolation with gentle tension so tram lines flow
 * smoothly instead of zig-zagging with sharp polygonal corners.
 */
export function buildSmoothSvgPath(points: Point2D[], tension = 0.28): string {
  if (points.length === 0) return '';
  if (points.length === 1) return `M ${points[0].x.toFixed(1)} ${points[0].y.toFixed(1)}`;
  if (points.length === 2) {
    return `M ${points[0].x.toFixed(1)} ${points[0].y.toFixed(1)} L ${points[1].x.toFixed(1)} ${points[1].y.toFixed(1)}`;
  }

  let d = `M ${points[0].x.toFixed(1)} ${points[0].y.toFixed(1)}`;
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[Math.max(0, i - 1)];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[Math.min(points.length - 1, i + 2)];

    const cp1x = p1.x + (p2.x - p0.x) * tension;
    const cp1y = p1.y + (p2.y - p0.y) * tension;
    const cp2x = p2.x - (p3.x - p1.x) * tension;
    const cp2y = p2.y - (p3.y - p1.y) * tension;

    d += ` C ${cp1x.toFixed(1)} ${cp1y.toFixed(1)}, ${cp2x.toFixed(1)} ${cp2y.toFixed(1)}, ${p2.x.toFixed(1)} ${p2.y.toFixed(1)}`;
  }
  return d;
}

/**
 * Interpolates geo coordinates into smooth Catmull-Rom intermediate vertices
 * for Google Maps Polylines so turns look smooth instead of jagged straight spikes.
 */
export function smoothLatLngPolyline(
  points: Array<{ lat: number; lng: number }>,
  subdivisionsPerSegment = 4,
  tension = 0.25
): Array<{ lat: number; lng: number }> {
  if (points.length <= 2) return points;
  const out: Array<{ lat: number; lng: number }> = [];

  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[Math.max(0, i - 1)];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[Math.min(points.length - 1, i + 2)];

    const cp1Lat = p1.lat + (p2.lat - p0.lat) * tension;
    const cp1Lng = p1.lng + (p2.lng - p0.lng) * tension;
    const cp2Lat = p2.lat - (p3.lat - p1.lat) * tension;
    const cp2Lng = p2.lng - (p3.lng - p1.lng) * tension;

    for (let step = 0; step < subdivisionsPerSegment; step++) {
      const t = step / subdivisionsPerSegment;
      const u = 1 - t;
      const lat =
        u * u * u * p1.lat +
        3 * u * u * t * cp1Lat +
        3 * u * t * t * cp2Lat +
        t * t * t * p2.lat;
      const lng =
        u * u * u * p1.lng +
        3 * u * u * t * cp1Lng +
        3 * u * t * t * cp2Lng +
        t * t * t * p2.lng;
      out.push({ lat, lng });
    }
  }
  out.push(points[points.length - 1]);
  return out;
}

/**
 * Offsets a polyline perpendicular to its travel direction by `offsetPx`
 * so overlapping tram routes on the same corridor appear neatly side-by-side.
 */
export function offsetPolylinePoints(points: Point2D[], offsetPx: number): Point2D[] {
  if (Math.abs(offsetPx) < 0.05 || points.length < 2) return points;
  return points.map((pt, idx) => {
    const prev = points[Math.max(0, idx - 1)];
    const next = points[Math.min(points.length - 1, idx + 1)];
    const dx = next.x - prev.x;
    const dy = next.y - prev.y;
    const len = Math.hypot(dx, dy) || 1;
    // Perpendicular unit normal (-dy, dx) / len
    return {
      x: pt.x + (-dy / len) * offsetPx,
      y: pt.y + (dx / len) * offsetPx,
    };
  });
}

/**
 * Computes symmetric lane index offsets for all routes sharing corridor edges,
 * returning an offset in pixels per route ID.
 */
export function computeRouteLaneOffsets(
  routes: Array<{ id: string; stopIds: string[] }>,
  laneSpacingPx = 3.2
): Record<string, number> {
  const edgeUsers: Record<string, string[]> = {};
  for (const r of routes) {
    for (let i = 0; i < r.stopIds.length - 1; i++) {
      const a = r.stopIds[i];
      const b = r.stopIds[i + 1];
      const key = a < b ? `${a}|${b}` : `${b}|${a}`;
      if (!edgeUsers[key]) edgeUsers[key] = [];
      if (!edgeUsers[key].includes(r.id)) edgeUsers[key].push(r.id);
    }
  }

  const routeOffsets: Record<string, number> = {};
  for (const r of routes) {
    let maxSlot = 0;
    let bestOffset = 0;
    for (let i = 0; i < r.stopIds.length - 1; i++) {
      const a = r.stopIds[i];
      const b = r.stopIds[i + 1];
      const key = a < b ? `${a}|${b}` : `${b}|${a}`;
      const users = edgeUsers[key];
      if (users.length > maxSlot) {
        maxSlot = users.length;
        const idx = users.indexOf(r.id);
        bestOffset = (idx - (users.length - 1) / 2) * laneSpacingPx;
      }
    }
    routeOffsets[r.id] = bestOffset;
  }
  return routeOffsets;
}

export const DEFAULT_MODE_BALANCE: ModeBalance = {
  tram: 0.72,
  transit: 0.08,
  walk: 0.20,
};

/**
 * Normalizes weights so tram + transit + walk === 1.
 */
export function normalizeModeBalance(b: ModeBalance): ModeBalance {
  const t = Math.max(0, b.tram);
  const tr = Math.max(0, b.transit);
  const w = Math.max(0, b.walk);
  const sum = t + tr + w;
  if (sum <= 0.0001) return { ...DEFAULT_MODE_BALANCE };
  return {
    tram: t / sum,
    transit: tr / sum,
    walk: w / sum,
  };
}

// Equilateral triangle vertices in normalized [0..1] coordinate space
export const TRIANGLE_VERTICES = {
  tram: { x: 0.5, y: 0.09 },
  transit: { x: 0.11, y: 0.87 },
  walk: { x: 0.89, y: 0.87 },
} as const;

export function balanceToTrianglePoint(balance: ModeBalance): Point2D {
  const n = normalizeModeBalance(balance);
  return {
    x:
      n.tram * TRIANGLE_VERTICES.tram.x +
      n.transit * TRIANGLE_VERTICES.transit.x +
      n.walk * TRIANGLE_VERTICES.walk.x,
    y:
      n.tram * TRIANGLE_VERTICES.tram.y +
      n.transit * TRIANGLE_VERTICES.transit.y +
      n.walk * TRIANGLE_VERTICES.walk.y,
  };
}

/**
 * Converts any point (x, y) in [0..1] space to clamped barycentric ModeBalance
 * inside the equilateral triangle.
 */
export function trianglePointToBalance(px: number, py: number): ModeBalance {
  const { tram: A, transit: B, walk: C } = TRIANGLE_VERTICES;
  const detT = (B.y - C.y) * (A.x - C.x) + (C.x - B.x) * (A.y - C.y);
  let wA = ((B.y - C.y) * (px - C.x) + (C.x - B.x) * (py - C.y)) / detT;
  let wB = ((C.y - A.y) * (px - C.x) + (A.x - C.x) * (py - C.y)) / detT;
  let wC = 1 - wA - wB;

  // Project onto simplex (wA, wB, wC >= 0, sum = 1) when dragged outside an edge
  const sorted = [
    { key: 'a', val: wA },
    { key: 'b', val: wB },
    { key: 'c', val: wC },
  ].sort((u, v) => v.val - u.val);

  let tm = 0;
  let sum = 0;
  let k = 0;
  for (let i = 0; i < 3; i++) {
    sum += sorted[i].val;
    const tCandidate = (sum - 1) / (i + 1);
    if (sorted[i].val - tCandidate > 0) {
      tm = tCandidate;
      k = i + 1;
    }
  }
  const threshold = k > 0 ? tm : sorted[0].val - 1;
  wA = Math.max(0, wA - threshold);
  wB = Math.max(0, wB - threshold);
  wC = Math.max(0, wC - threshold);

  return normalizeModeBalance({ tram: wA, transit: wB, walk: wC });
}
