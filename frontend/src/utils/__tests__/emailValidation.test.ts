import { describe, it, expect } from 'vitest';
import { EMAIL_PATTERN } from '../emailValidation';

describe('EMAIL_PATTERN', () => {
  describe('accepts real-world emails', () => {
    const valid = [
      'john@gmail.com',
      'JOHN@GMAIL.COM', // case-insensitive
      'user.name+filter@yahoo.com',
      'user_name@company.in',
      'abc123@example.org',
      'admin@sub.example.co.uk',
      'first.last@multi.sub.domain.io',
      'a@b.com', // single-letter local + single-letter domain are RFC-valid
      'user@xyz.ai',
      'user@startup.app',
      'business@example.net',
      'student@uni.edu',
      'gov-user@dept.gov',
    ];

    valid.forEach((email) => {
      it(`accepts ${email}`, () => {
        expect(EMAIL_PATTERN.test(email)).toBe(true);
      });
    });
  });

  describe('rejects invalid emails', () => {
    const invalid: { email: string; reason: string }[] = [
      { email: '', reason: 'empty' },
      { email: 'no-at-sign.com', reason: 'no @' },
      { email: '@nodomain.com', reason: 'no local part' },
      { email: 'a@', reason: 'no domain' },
      // Domain-starts-with-digit cases — the original bug we fixed
      { email: 'a@0.com', reason: 'domain starts with digit' },
      { email: 'user@123.com', reason: 'domain is purely digits' },
      { email: 'test@9999.org', reason: 'all-digit second-level' },
      // TLD whitelist enforcement — the new requirement
      { email: 'user@example.ckfkkskf', reason: 'unknown TLD' },
      { email: 'user@example.xyz123', reason: 'gibberish TLD' },
      { email: 'user@example.x', reason: 'TLD too short' },
      { email: 'test@a.b', reason: 'single-char TLD' },
      // Structural problems
      { email: 'no-space allowed@gmail.com', reason: 'space in local' },
      { email: 'two@@signs.com', reason: 'double @' },
      { email: 'plain.text', reason: 'no @ or TLD' },
    ];

    invalid.forEach(({ email, reason }) => {
      it(`rejects "${email}" (${reason})`, () => {
        expect(EMAIL_PATTERN.test(email)).toBe(false);
      });
    });
  });

  it('regex is anchored — does not match substrings', () => {
    expect(EMAIL_PATTERN.test('prefix john@gmail.com suffix')).toBe(false);
  });
});
