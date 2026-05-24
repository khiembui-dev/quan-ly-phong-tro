/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './src/main/resources/templates/**/*.html',
    './src/main/resources/static/js/**/*.js',
  ],
  theme: {
    extend: {
      colors: {
        accent: {
          DEFAULT: '#4f46e5',
          soft:    '#eef0ff',
          hover:   '#4338ca',
          ring:    'rgba(79,70,229,0.16)',
        },
        ok:   { DEFAULT: '#059669', soft: '#ecfdf5' },
        warn: { DEFAULT: '#b45309', soft: '#fffbeb' },
        err:  { DEFAULT: '#dc2626', soft: '#fef2f2' },
        canvas: { 0: '#f7f7fb', 1: '#fbfbfd', 2: '#ffffff' },
        ink: {
          DEFAULT: '#0f172a',
          soft:    '#334155',
          mute:    '#64748b',
          faint:   '#94a3b8',
        },
        line: {
          DEFAULT: 'rgba(15, 23, 42, 0.08)',
          soft:    'rgba(15, 23, 42, 0.05)',
        },
      },
      fontFamily: {
        display: ['"Plus Jakarta Sans"', 'system-ui', 'sans-serif'],
        sans:    ['"Plus Jakarta Sans"', 'system-ui', 'sans-serif'],
        mono:    ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
      borderRadius: {
        sm:    '10px',
        md:    '14px',
        lg:    '18px',
        xl:    '22px',
        '2xl': '28px',
        '3xl': '36px',
      },
      backdropBlur: {
        glass:  '24px',
        strong: '40px',
      },
      boxShadow: {
        glass:    '0 1px 2px rgba(15,23,42,.04), 0 8px 24px rgba(15,23,42,.06)',
        'glass-lg': '0 1px 2px rgba(15,23,42,.04), 0 16px 48px rgba(15,23,42,.08)',
        'accent-glow': '0 12px 32px rgba(79,70,229,0.35)',
      },
      letterSpacing: {
        tightest: '-0.04em',
        tighter:  '-0.025em',
        tight:    '-0.015em',
        eyebrow:  '0.08em',
      },
      keyframes: {
        float: {
          '0%, 100%': { transform: 'translate(0, 0)' },
          '50%':      { transform: 'translate(20px, -20px)' },
        },
        pulseHalo: {
          '0%, 100%': { transform: 'scale(1)',    opacity: '0.4' },
          '50%':      { transform: 'scale(1.15)', opacity: '0.7' },
        },
        shimmer: {
          '0%':   { transform: 'translateX(-100%)' },
          '100%': { transform: 'translateX(100%)' },
        },
        dotPulse: {
          '0%':   { boxShadow: '0 0 0 0 currentColor' },
          '100%': { boxShadow: '0 0 0 8px transparent' },
        },
      },
      animation: {
        float:     'float 18s ease-in-out infinite',
        'pulse-halo': 'pulseHalo 2.4s ease-in-out infinite alternate',
        shimmer:   'shimmer 1.6s linear infinite',
        'dot-pulse': 'dotPulse 1.6s ease-out infinite',
      },
      transitionTimingFunction: {
        'glass-out':   'cubic-bezier(0.16, 1, 0.3, 1)',
        'glass-inout': 'cubic-bezier(0.65, 0, 0.35, 1)',
      },
    },
  },
  plugins: [
    require('@tailwindcss/forms'),
    require('@tailwindcss/typography'),
  ],
};
