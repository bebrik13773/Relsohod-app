interface BrandLogoProps {
  size?: 'sm' | 'lg';
}

export function BrandLogo({ size = 'sm' }: BrandLogoProps) {
  const boxClass = size === 'lg' ? 'h-14 w-14 rounded-2xl' : 'h-8 w-8 rounded-xl';
  const svgClass = size === 'lg' ? 'h-8 w-8' : 'h-4.5 w-4.5';

  return (
    <span
      aria-hidden="true"
      className={`inline-flex shrink-0 items-center justify-center shadow-xs ${boxClass}`}
      style={{
        backgroundColor: 'var(--accent-primary)',
        color: 'var(--accent-on-primary)',
      }}
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className={svgClass}
      >
        <path d="M9 3h6" />
        <path d="M12 3v3" />
        <rect x="6" y="6" width="12" height="11" rx="3" />
        <path d="M6 12h12" />
        <circle cx="9.5" cy="14.5" r="0.9" fill="currentColor" stroke="none" />
        <circle cx="14.5" cy="14.5" r="0.9" fill="currentColor" stroke="none" />
        <path d="M8 17l-2.5 4" />
        <path d="M16 17l2.5 4" />
        <path d="M7.5 19.5h9" />
      </svg>
    </span>
  );
}
