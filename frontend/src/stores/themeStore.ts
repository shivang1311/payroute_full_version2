/**
 * Light/dark theme switcher. Reads the user's last choice from localStorage,
 * falling back to the OS preference (`prefers-color-scheme`). Writes to the
 * `<html data-theme="...">` attribute so global CSS can react.
 */
import { create } from 'zustand';

/** UI theme options. */
export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'pr-theme-mode';

const readInitial = (): ThemeMode => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'light' || stored === 'dark') return stored;
  } catch {
    /* ignore */
  }
  if (typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches) {
    return 'dark';
  }
  return 'light';
};

const applyToDom = (mode: ThemeMode) => {
  if (typeof document === 'undefined') return;
  document.documentElement.dataset.theme = mode;
  document.documentElement.style.colorScheme = mode;
};

/** State + actions exposed by {@link useThemeStore}. */
export interface ThemeStore {
  /** Current theme. */
  mode: ThemeMode;
  /** Flip light↔dark and persist. */
  toggle: () => void;
  /** Set an explicit mode and persist. */
  setMode: (m: ThemeMode) => void;
}

/** Hook returning the active theme and the controls to change it. */
export const useThemeStore = create<ThemeStore>((set, get) => {
  const initial = readInitial();
  applyToDom(initial);
  return {
    mode: initial,
    toggle: () => {
      const next: ThemeMode = get().mode === 'light' ? 'dark' : 'light';
      localStorage.setItem(STORAGE_KEY, next);
      applyToDom(next);
      set({ mode: next });
    },
    setMode: (m) => {
      localStorage.setItem(STORAGE_KEY, m);
      applyToDom(m);
      set({ mode: m });
    },
  };
});
