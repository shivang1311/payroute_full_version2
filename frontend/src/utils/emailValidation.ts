/**
 * Stricter-than-RFC email validation used across registration, profile,
 * admin user creation, and account-directory aliases.
 *
 * Layered on top of antd's permissive `type:'email'` rule:
 *   1. Domain must start with a letter (rejects `a@0.com`).
 *   2. TLD must be one of a curated whitelist of real top-level domains
 *      (rejects garbage like `user@example.ckfkkskf`).
 *
 * Compound TLDs like `.co.in` or `.com.au` still work — the regex matches
 * against the final segment (e.g. `in` / `au`).
 */
export const EMAIL_PATTERN =
  /^[a-zA-Z0-9._%+-]+@[a-zA-Z][a-zA-Z0-9-]*(\.[a-zA-Z][a-zA-Z0-9-]*)*\.(com|org|net|edu|gov|info|biz|in|us|uk|co|io|me|ai|app|dev|au|ca|de|fr|jp|cn|sg|ae|nz)$/i;

export const EMAIL_VALIDATION_MESSAGE =
  'Enter a valid email with a real domain (e.g. you@gmail.com, user@company.in)';
