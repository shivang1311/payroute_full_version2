/**
 * Global Vitest setup — wires RTL custom matchers, polyfills the bits of the
 * DOM antd expects but jsdom doesn't provide, and clears state between tests.
 */
import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

// jsdom provides Storage but the instances can be brittle across import
// ordering — install a deterministic in-memory shim so module-load-time
// `localStorage.getItem(...)` calls (e.g. authStore hydration) always work.
const createStorage = (): Storage => {
  let store: Record<string, string> = {};
  return {
    get length() { return Object.keys(store).length; },
    clear: () => { store = {}; },
    getItem: (k: string) => (k in store ? store[k] : null),
    setItem: (k: string, v: string) => { store[k] = String(v); },
    removeItem: (k: string) => { delete store[k]; },
    key: (i: number) => Object.keys(store)[i] ?? null,
  };
};
Object.defineProperty(globalThis, 'localStorage', {
  value: createStorage(), configurable: true, writable: true,
});
Object.defineProperty(globalThis, 'sessionStorage', {
  value: createStorage(), configurable: true, writable: true,
});

// AntD reads matchMedia for responsive helpers; jsdom doesn't implement it.
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(), // deprecated
    removeListener: vi.fn(), // deprecated
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

// AntD's <Drawer>/<Carousel> use ResizeObserver internally.
class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}
(globalThis as unknown as { ResizeObserver: typeof ResizeObserver }).ResizeObserver =
  ResizeObserverMock as unknown as typeof ResizeObserver;

// AntD's <Tooltip>/<Select> portals use IntersectionObserver in some places.
class IntersectionObserverMock {
  root = null;
  rootMargin = '';
  thresholds = [];
  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords() { return []; }
}
(globalThis as unknown as { IntersectionObserver: typeof IntersectionObserver }).IntersectionObserver =
  IntersectionObserverMock as unknown as typeof IntersectionObserver;

// Reset DOM and localStorage between every test to avoid leakage.
afterEach(() => {
  cleanup();
  // Defensive: if a test replaced window.localStorage with a mock, swallow.
  try { window.localStorage?.clear?.(); } catch { /* noop */ }
  try { window.sessionStorage?.clear?.(); } catch { /* noop */ }
  vi.clearAllMocks();
});
