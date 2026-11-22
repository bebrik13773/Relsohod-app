import { useEffect, useState } from 'react';

interface StartupSplashProps {
  visible: boolean;
  onFinish: () => void;
}

export function StartupSplash({ visible, onFinish }: StartupSplashProps) {
  const [fadingOut, setFadingOut] = useState(false);

  useEffect(() => {
    if (!visible) return;

    const fadeTimer = window.setTimeout(() => {
      setFadingOut(true);
    }, 1050);

    const finishTimer = window.setTimeout(() => {
      onFinish();
    }, 1400);

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' || e.key === 'Enter' || e.key === ' ') {
        onFinish();
      }
    };
    window.addEventListener('keydown', handleKeyDown);

    return () => {
      window.clearTimeout(fadeTimer);
      window.clearTimeout(finishTimer);
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [visible, onFinish]);

  if (!visible) return null;

  return (
    <div
      role="dialog"
      aria-label="Заставка Рельсоход"
      data-testid="startup-splash"
      onClick={onFinish}
      className={`fixed inset-0 z-[100] flex cursor-pointer flex-col items-center justify-center px-6 text-white transition-opacity duration-350 select-none ${
        fadingOut ? 'opacity-0' : 'opacity-100'
      }`}
      style={{
        background: 'linear-gradient(135deg, #072B47 0%, #0F5E8C 52%, #0D7A6F 100%)',
      }}
    >
      <div className="flex flex-col items-center text-center">
        <div
          className="relative flex h-32 w-32 items-center justify-center rounded-[32px] border border-white/15 shadow-2xl"
          style={{
            backgroundColor: 'rgba(255, 255, 255, 0.08)',
            boxShadow: '0 24px 60px rgba(4, 24, 42, 0.45)',
          }}
        >
          <svg
            viewBox="0 0 512 512"
            className="h-28 w-28"
            aria-hidden="true"
          >
            {/* Overhead wire */}
            <path
              d="M126 118 Q256 104 386 118"
              fill="none"
              stroke="#7DD3FC"
              strokeOpacity="0.62"
              strokeWidth="6"
              strokeLinecap="round"
            />
            {/* Twin converging rails */}
            <path
              d="M204 344 L158 410 M308 344 L354 410"
              fill="none"
              stroke="#E0F2FE"
              strokeWidth="14"
              strokeLinecap="round"
            />
            <path
              d="M184 370 L328 370"
              fill="none"
              stroke="#BAE6FD"
              strokeOpacity="0.82"
              strokeWidth="10"
              strokeLinecap="round"
            />

            {/* Animated tram body */}
            <g>
              <animateTransform
                attributeName="transform"
                type="translate"
                values="0,0; 0,-4; 0,0"
                dur="1.1s"
                repeatCount="indefinite"
              />
              <path
                d="M206 124 L306 124"
                fill="none"
                stroke="#FFFFFF"
                strokeWidth="14"
                strokeLinecap="round"
              />
              <path
                d="M224 124 L256 156 L288 124"
                fill="none"
                stroke="#E0F2FE"
                strokeWidth="12"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              <rect x="220" y="150" width="72" height="16" rx="8" fill="#BAE6FD" />
              <rect x="162" y="162" width="188" height="178" rx="44" fill="#FFFFFF" />
              <rect x="212" y="176" width="88" height="11" rx="5.5" fill="#14B8A6" />
              <rect x="184" y="198" width="144" height="72" rx="20" fill="#0A3D5E" />
              <path
                d="M198 260 L234 206 L256 206 L220 260 Z"
                fill="#38BDF8"
                fillOpacity="0.26"
              />
              <path
                d="M242 260 L272 214 L286 214 L256 260 Z"
                fill="#5EEAD4"
                fillOpacity="0.18"
              />
              <circle cx="204" cy="304" r="15" fill="#0F5E8C" />
              <circle cx="204" cy="304" r="7.5" fill="#7DD3FC">
                <animate
                  attributeName="opacity"
                  values="0.6;1;0.6"
                  dur="0.9s"
                  repeatCount="indefinite"
                />
              </circle>
              <circle cx="308" cy="304" r="15" fill="#0F5E8C" />
              <circle cx="308" cy="304" r="7.5" fill="#7DD3FC">
                <animate
                  attributeName="opacity"
                  values="0.6;1;0.6"
                  dur="0.9s"
                  repeatCount="indefinite"
                />
              </circle>
              <rect x="234" y="298" width="44" height="12" rx="6" fill="#94A3B8" />
            </g>

            {/* Animated Neva wave crest */}
            <path
              d="M118 400 Q187 378 256 400 T394 400"
              fill="none"
              stroke="#5EEAD4"
              strokeWidth="14"
              strokeLinecap="round"
            >
              <animateTransform
                attributeName="transform"
                type="translate"
                values="-8,0; 8,0; -8,0"
                dur="1.4s"
                repeatCount="indefinite"
              />
            </path>
          </svg>
        </div>

        <h1 className="font-display mt-6 text-3xl font-extrabold tracking-tight text-white">
          Рельсоход
        </h1>
        <p className="mt-1.5 text-sm font-medium text-sky-100/90">
          Трамвайные прогулки · СПб и Омск
        </p>

        <div className="mt-6 h-1 w-40 overflow-hidden rounded-full bg-white/20">
          <div
            className="h-full rounded-full"
            style={{
              width: fadingOut ? '100%' : '82%',
              background: 'linear-gradient(90deg, #7DD3FC 0%, #5EEAD4 100%)',
              transition: 'width 1050ms cubic-bezier(0.16, 1, 0.3, 1)',
            }}
          />
        </div>

        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onFinish();
          }}
          className="mt-5 min-h-[40px] cursor-pointer px-4 py-1.5 text-xs font-medium text-sky-100/80 underline-offset-4 hover:text-white hover:underline focus-visible:outline-2 focus-visible:outline-white"
        >
          Пропустить
        </button>
      </div>
    </div>
  );
}
