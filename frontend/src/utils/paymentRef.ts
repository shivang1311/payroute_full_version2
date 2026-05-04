/**
 * Customer-facing payment reference helpers.
 *
 * The payment-service stores a deterministic 12-char reference (e.g. `PRX9K3M7F2A8`)
 * on every payment. We prefer that value when present; the locally-computed fallback
 * exists only for old API responses that may not yet include the field.
 */

const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // 32 chars, no 0/1/I/O confusables

/** Simple deterministic mixing — kept identical to the backend generator. */
function mix(seed: number): number {
  let x = seed | 0;
  x = (x ^ 0x9e3779b9) >>> 0;
  x = Math.imul(x ^ (x >>> 16), 0x85ebca6b) >>> 0;
  x = Math.imul(x ^ (x >>> 13), 0xc2b2ae35) >>> 0;
  x = (x ^ (x >>> 16)) >>> 0;
  return x;
}

/**
 * Local fallback used only when the backend response is missing `reference`
 * (e.g. cached old responses). Returns the same 12-char shape.
 */
function computeRef(id: number, createdAt?: string | null): string {
  const numericId = id || 0;
  const dayStamp = createdAt ? Math.floor(new Date(createdAt).getTime() / 86_400_000) : 0;
  let acc = mix(numericId * 1_000_003 + dayStamp);
  let out = '';
  for (let i = 0; i < 10; i++) {
    out += ALPHABET[acc % ALPHABET.length];
    acc = mix(acc + numericId + i);
  }
  return `PR${out}`;
}

/**
 * Resolve a payment's customer-facing reference. Prefers the value from
 * the backend (`payment.reference`); falls back to a local computation.
 */
export function paymentRef(
  idOrPayment: number | string | { id?: number | string; reference?: string; createdAt?: string | null } | undefined,
  createdAt?: string | null,
): string {
  if (idOrPayment === undefined || idOrPayment === null) return 'PR----------';

  // Object form: read backend reference if present
  if (typeof idOrPayment === 'object') {
    if (idOrPayment.reference) return idOrPayment.reference;
    const id = typeof idOrPayment.id === 'number' ? idOrPayment.id : parseInt(String(idOrPayment.id), 10) || 0;
    return computeRef(id, idOrPayment.createdAt);
  }

  // Primitive form (legacy callsites): compute from id+createdAt
  const numericId = typeof idOrPayment === 'number' ? idOrPayment : parseInt(String(idOrPayment), 10) || 0;
  return computeRef(numericId, createdAt);
}

/**
 * What to *display* as the payment identifier:
 *  - Customers see only the opaque ref.
 *  - Staff see `#id · REF` so they can still query by row id.
 */
export function paymentDisplayId(
  payment: { id?: number | string; reference?: string; createdAt?: string | null } | undefined,
  isCustomer: boolean,
): string {
  if (!payment || payment.id === undefined) return 'PR----------';
  const ref = paymentRef(payment);
  if (isCustomer) return ref;
  return `#${payment.id} · ${ref}`;
}
