import React from 'react';

interface Props {
  size?: number;
  showText?: boolean;
  textColor?: string;
  /** Accent color for the "Hub" word. Defaults to brand purple, but on coloured
   *  panels (e.g. the login splash) pass a contrasting value like "#fff". */
  accentColor?: string;
}

/**
 * PayRoute brand mark — abstract routed-flow icon over a gradient rounded square,
 * rendered as inline SVG so it scales and themes cleanly without an asset file.
 */
export default function Logo({ size = 32, showText = true, textColor, accentColor }: Props) {
  const gradId = React.useId();
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
      <svg width={size} height={size} viewBox="0 0 40 40" aria-label="PayRoute Hub">
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#5b5bf0" />
            <stop offset="100%" stopColor="#8855ff" />
          </linearGradient>
        </defs>
        <rect x="0" y="0" width="40" height="40" rx="10" fill={`url(#${gradId})`} />
        {/* Routed nodes */}
        <circle cx="11" cy="13" r="3" fill="#fff" />
        <circle cx="29" cy="13" r="3" fill="#fff" fillOpacity="0.6" />
        <circle cx="20" cy="27" r="3.2" fill="#fff" />
        {/* Connecting paths */}
        <path d="M11 13 C 14 20, 17 24, 20 27" stroke="#fff" strokeWidth="1.6" fill="none" strokeLinecap="round" />
        <path d="M29 13 C 26 20, 23 24, 20 27" stroke="#fff" strokeOpacity="0.7" strokeWidth="1.6" fill="none" strokeLinecap="round" />
      </svg>
      {showText && (
        <span
          style={{
            fontSize: Math.max(14, size * 0.55),
            fontWeight: 700,
            letterSpacing: '-0.2px',
            color: textColor || 'var(--pr-text)',
            whiteSpace: 'nowrap',
          }}
        >
          PayRoute{' '}
          <span style={{ color: accentColor || (textColor ? textColor : 'var(--pr-brand-1)') }}>
            Hub
          </span>
        </span>
      )}
    </span>
  );
}
