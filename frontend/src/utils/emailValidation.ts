/**
 * Stricter-than-RFC email validation used across registration, profile,
 * admin user creation, and account-directory aliases.
 *
 * Layered on top of antd's permissive {@code type:'email'} rule:
 *   1. Domain must start with a letter (rejects {@code a@0.com}).
 *   2. TLD must be one of a curated whitelist of real top-level domains
 *      (rejects garbage like {@code user@example.ckfkkskf}).
 *
 * Compound TLDs like {@code .co.in} or {@code .com.au} still work — the
 * regex matches against the final segment (e.g. {@code in} / {@code au}).
 */
export const EMAIL_PATTERN =
  /^[a-zA-Z0-9._%+-]+@[a-zA-Z][a-zA-Z0-9-]*(\.[a-zA-Z][a-zA-Z0-9-]*)*\.(com|org|net|edu|gov|info|biz|in|us|uk|co|io|me|ai|app|dev|au|ca|de|fr|jp|cn|sg|ae|nz)$/i;

export const EMAIL_VALIDATION_MESSAGE =
  'Enter a valid email with a real domain (e.g. you@gmail.com, user@company.in)';
