interface BrandLogoProps {
  size?: 'sm' | 'lg';
}

export function BrandLogo({ size = 'sm' }: BrandLogoProps) {
  const boxClass = size === 'lg' ? 'h-14 w-14 rounded-2xl' : 'h-8 w-8 rounded-xl';
  const svgClass = size === 'lg' ? 'h-9 w-9' : 'h-5 w-5';

  return (
    <span
      aria-hidden="true"
      className={`inline-flex shrink-0 items-center justify-center shadow-xs ${boxClass}`}
      style={{
        background: 'linear-gradient(135deg, #072B47 0%, #0F5E8C 55%, #0D7A6F 100%)',
        color: '#ffffff',
      }}
    >
      <svg viewBox="0 0 64 64" fill="none" className={svgClass}>
        <path
          d="M17 14 Q32 12 47 14"
          stroke="#7DD3FC"
          strokeWidth="1.8"
          strokeLinecap="round"
          opacity="0.65"
        />
        <path d="M25 15.5 H39" stroke="#FFFFFF" strokeWidth="2.4" strokeLinecap="round" />
        <path
          d="M27.5 15.5 L32 20 L36.5 15.5"
          stroke="#E0F2FE"
          strokeWidth="2.1"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <rect x="20" y="20.5" width="24" height="22" rx="6" fill="#FFFFFF" />
        <rect x="26.5" y="22.5" width="11" height="1.8" rx="0.9" fill="#14B8A6" />
        <rect x="23" y="25.5" width="18" height="9" rx="2.6" fill="#0A3D5E" />
        <circle cx="25.5" cy="38.5" r="2" fill="#0F5E8C" />
        <circle cx="38.5" cy="38.5" r="2" fill="#0F5E8C" />
        <rect x="29" y="37.6" width="6" height="1.8" rx="0.9" fill="#94A3B8" />
        <path
          d="M25.5 43 L20.5 50.5 M38.5 43 L43.5 50.5"
          stroke="#E0F2FE"
          strokeWidth="2.2"
          strokeLinecap="round"
        />
        <path
          d="M17 50 Q24.5 47 32 50 T47 50"
          stroke="#5EEAD4"
          strokeWidth="2.4"
          strokeLinecap="round"
        />
      </svg>
    </span>
  );
}
