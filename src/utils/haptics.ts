export type HapticStyle = 'light' | 'medium' | 'success';

const HAPTIC_PATTERNS: Record<HapticStyle, number | number[]> = {
  light: 10,
  medium: 18,
  success: [12, 28, 20],
};

export function triggerHaptic(style: HapticStyle = 'light'): void {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') return;
  try {
    if ('vibrate' in navigator && typeof navigator.vibrate === 'function') {
      navigator.vibrate(HAPTIC_PATTERNS[style]);
    }
  } catch {
    // Ignore unsupported or blocked vibration calls silently
  }
}
