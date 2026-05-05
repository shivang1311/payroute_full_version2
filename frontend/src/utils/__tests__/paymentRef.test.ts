import { describe, it, expect } from 'vitest';
import { paymentRef, paymentDisplayId } from '../paymentRef';

describe('paymentRef', () => {
  describe('placeholder cases', () => {
    it('returns placeholder for undefined', () => {
      expect(paymentRef(undefined)).toBe('PR----------');
    });

    it('returns placeholder for null', () => {
      expect(paymentRef(null as unknown as undefined)).toBe('PR----------');
    });
  });

  describe('object form', () => {
    it('prefers backend reference when present', () => {
      const ref = paymentRef({ id: 42, reference: 'PRBACKEND001', createdAt: '2026-01-01' });
      expect(ref).toBe('PRBACKEND001');
    });

    it('computes locally when reference missing', () => {
      const ref = paymentRef({ id: 42, createdAt: '2026-01-01' });
      expect(ref).toMatch(/^PR[A-Z2-9]{10}$/);
    });

    it('computed reference is deterministic for same id+createdAt', () => {
      const a = paymentRef({ id: 100, createdAt: '2026-05-05' });
      const b = paymentRef({ id: 100, createdAt: '2026-05-05' });
      expect(a).toBe(b);
    });

    it('different ids produce different references', () => {
      const a = paymentRef({ id: 100, createdAt: '2026-05-05' });
      const b = paymentRef({ id: 101, createdAt: '2026-05-05' });
      expect(a).not.toBe(b);
    });

    it('coerces string id to number', () => {
      const a = paymentRef({ id: '42', createdAt: '2026-01-01' });
      const b = paymentRef({ id: 42, createdAt: '2026-01-01' });
      expect(a).toBe(b);
    });
  });

  describe('primitive form', () => {
    it('accepts a numeric id', () => {
      expect(paymentRef(99)).toMatch(/^PR[A-Z2-9]{10}$/);
    });

    it('accepts a string id', () => {
      expect(paymentRef('99')).toMatch(/^PR[A-Z2-9]{10}$/);
    });

    it('numeric and string forms agree', () => {
      expect(paymentRef('42', '2026-01-01')).toBe(paymentRef(42, '2026-01-01'));
    });
  });

  describe('alphabet — no confusable characters', () => {
    it('output never contains 0, 1, I, O', () => {
      // Sample 200 different ids and ensure none of the generated chars are confusable.
      for (let i = 0; i < 200; i++) {
        const ref = paymentRef(i, '2026-01-01');
        expect(ref).not.toMatch(/[01IO]/);
      }
    });
  });
});

describe('paymentDisplayId', () => {
  it('placeholder when payment missing', () => {
    expect(paymentDisplayId(undefined, false)).toBe('PR----------');
  });

  it('customer view shows only the ref', () => {
    const out = paymentDisplayId({ id: 42, reference: 'PRTEST123ABC' }, true);
    expect(out).toBe('PRTEST123ABC');
  });

  it('staff view shows id and ref', () => {
    const out = paymentDisplayId({ id: 42, reference: 'PRTEST123ABC' }, false);
    expect(out).toBe('#42 · PRTEST123ABC');
  });

  it('falls back to computed ref when backend ref missing', () => {
    const out = paymentDisplayId({ id: 42, createdAt: '2026-01-01' }, true);
    expect(out).toMatch(/^PR[A-Z2-9]{10}$/);
  });
});
