import React from 'react';

interface Props {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  extra?: React.ReactNode;
  icon?: React.ReactNode;
  /** Visual variant for the icon badge */
  iconVariant?: 'brand' | 'success' | 'warning' | 'error' | 'info' | 'purple' | 'teal';
  style?: React.CSSProperties;
}

/**
 * Shared page header — title, optional subtitle, gradient icon badge, and a right-aligned actions slot.
 * Uses the global `.pr-page-header` / `.pr-stat-icon` classes from index.css so it themes automatically.
 */
export default function PageHeader({
  title,
  subtitle,
  extra,
  icon,
  iconVariant = 'brand',
  style,
}: Props) {
  return (
    <div className="pr-page-header" style={style}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14, minWidth: 0 }}>
        {icon && (
          <span
            className={`pr-stat-icon ${iconVariant}`}
            style={{ marginBottom: 0, width: 40, height: 40, borderRadius: 10, fontSize: 18 }}
          >
            {icon}
          </span>
        )}
        <div style={{ minWidth: 0 }}>
          <h1 className="pr-page-header-title">{title}</h1>
          {subtitle && <div className="pr-page-header-subtitle">{subtitle}</div>}
        </div>
      </div>
      {extra && <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>{extra}</div>}
    </div>
  );
}
