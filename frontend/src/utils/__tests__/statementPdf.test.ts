import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { Statement } from '../../types/statement';

const { saveSpy, textSpy, autoTableSpy } = vi.hoisted(() => ({
  saveSpy: vi.fn(),
  textSpy: vi.fn(),
  autoTableSpy: vi.fn(),
}));

vi.mock('jspdf', () => {
  function jsPDFMock(this: Record<string, unknown>) {
    this.internal = { pageSize: { getWidth: () => 595, getHeight: () => 842 } };
    this.setFont = () => undefined;
    this.setFontSize = () => undefined;
    this.setTextColor = () => undefined;
    this.text = textSpy;
    this.setPage = () => undefined;
    this.getNumberOfPages = () => 1;
    this.save = saveSpy;
    this.lastAutoTable = { finalY: 200 };
  }
  return { default: jsPDFMock };
});
vi.mock('jspdf-autotable', () => ({ default: autoTableSpy }));

import { downloadStatementPdf } from '../statementPdf';

const sample: Statement = {
  accountId: 30,
  currency: 'INR',
  fromDate: '2026-04-20',
  toDate: '2026-05-05',
  openingBalance: 0,
  closingBalance: 950000,
  totalCredits: 1000000,
  totalDebits: 50000,
  entryCount: 2,
  entries: [
    {
      entryId: 145,
      paymentId: null,
      entryDate: '2026-04-21',
      postedAt: '2026-04-21T00:00:00',
      entryType: 'CREDIT',
      debit: 0,
      credit: 1000000,
      currency: 'INR',
      narrative: 'Opening balance — account onboarded',
      runningBalance: 1000000,
    },
    {
      entryId: 146,
      paymentId: 42,
      entryDate: '2026-05-04',
      postedAt: '2026-05-04T15:00:00',
      entryType: 'DEBIT',
      debit: 50000,
      credit: 0,
      currency: 'INR',
      // Has internal id mention — should be stripped for customer view
      narrative: 'Payment debit - PaymentId: 42',
      runningBalance: 950000,
    },
  ],
};

describe('downloadStatementPdf', () => {
  beforeEach(() => {
    saveSpy.mockClear();
    textSpy.mockClear();
    autoTableSpy.mockClear();
  });

  it('writes the document title', () => {
    downloadStatementPdf(sample, { isCustomer: false, from: '2026-04-20', to: '2026-05-05' });
    const titleCall = textSpy.mock.calls.find((c) =>
      String(c[0]).includes('Account Statement'),
    );
    expect(titleCall).toBeDefined();
  });

  it('renders the period and account in the metadata block', () => {
    downloadStatementPdf(sample, {
      isCustomer: false,
      from: '2026-04-20',
      to: '2026-05-05',
      accountLabel: '5555000099991111 · INR · SAVINGS',
    });
    const metaCall = autoTableSpy.mock.calls.find((c) => {
      const opts = c[1] as { body?: unknown[][] };
      return opts.body?.some((r) => r[0] === 'Account');
    });
    expect(metaCall).toBeDefined();
    const body = (metaCall![1] as { body: string[][] }).body;
    expect(body).toContainEqual(['Account', '5555000099991111 · INR · SAVINGS']);
    expect(body).toContainEqual(['Period', '2026-04-20 → 2026-05-05']);
    expect(body).toContainEqual(['Currency', 'INR']);
  });

  it('falls back to "#id" when no accountLabel given', () => {
    downloadStatementPdf(sample, { isCustomer: true, from: '2026-04-20', to: '2026-05-05' });
    const metaCall = autoTableSpy.mock.calls.find((c) => {
      const opts = c[1] as { body?: unknown[][] };
      return opts.body?.some((r) => r[0] === 'Account');
    });
    const body = (metaCall![1] as { body: string[][] }).body;
    expect(body).toContainEqual(['Account', '#30']);
  });

  it('renders the summary with formatted amounts', () => {
    downloadStatementPdf(sample, { isCustomer: false, from: '2026-04-20', to: '2026-05-05' });
    const summaryCall = autoTableSpy.mock.calls.find((c) => {
      const opts = c[1] as { head?: unknown[][] };
      return opts.head?.[0]?.[0] === 'Summary';
    });
    expect(summaryCall).toBeDefined();
    const body = (summaryCall![1] as { body: string[][] }).body;
    expect(body).toContainEqual(['Total Credits', '1,000,000.00']);
    expect(body).toContainEqual(['Total Debits', '50,000.00']);
    expect(body).toContainEqual(['Closing Balance', '950,000.00']);
  });

  it('staff entries table includes Entry # + Payment columns', () => {
    downloadStatementPdf(sample, { isCustomer: false, from: '2026-04-20', to: '2026-05-05' });
    const entryCall = autoTableSpy.mock.calls.find((c) => {
      const opts = c[1] as { head?: unknown[][] };
      return opts.head?.[0]?.includes('Entry #');
    });
    expect(entryCall).toBeDefined();
    const opts = entryCall![1] as { head: string[][]; body: string[][] };
    expect(opts.head[0]).toEqual([
      'Date', 'Entry #', 'Payment', 'Type', 'Narrative', 'Debit', 'Credit', 'Balance',
    ]);
    // PaymentId not stripped for staff
    const debitRow = opts.body.find((r) => r[3] === 'DEBIT');
    expect(debitRow?.[2]).toBe('#42');
    expect(debitRow?.[4]).toBe('Payment debit - PaymentId: 42');
  });

  it('customer entries table uses Reference + strips internal IDs from narrative', () => {
    downloadStatementPdf(sample, { isCustomer: true, from: '2026-04-20', to: '2026-05-05' });
    const entryCall = autoTableSpy.mock.calls.find((c) => {
      const opts = c[1] as { head?: unknown[][] };
      return opts.head?.[0]?.includes('Reference');
    });
    expect(entryCall).toBeDefined();
    const opts = entryCall![1] as { head: string[][]; body: string[][] };
    expect(opts.head[0]).toEqual([
      'Date', 'Reference', 'Type', 'Narrative', 'Debit', 'Credit', 'Balance',
    ]);
    const debitRow = opts.body.find((r) => r[2] === 'DEBIT');
    // Reference computed via paymentRef (12-char PR…)
    expect(debitRow?.[1]).toMatch(/^PR[A-Z2-9]{10}$/);
    // PaymentId mention stripped
    expect(debitRow?.[3]).not.toContain('PaymentId');
  });

  it('shows a placeholder when there are no entries', () => {
    const empty: Statement = { ...sample, entries: [], entryCount: 0 };
    downloadStatementPdf(empty, { isCustomer: false, from: '2026-04-20', to: '2026-05-05' });
    const noEntries = textSpy.mock.calls.find((c) =>
      String(c[0]).includes('No entries'),
    );
    expect(noEntries).toBeDefined();
  });

  it('saves with a deterministic filename', () => {
    downloadStatementPdf(sample, { isCustomer: false, from: '2026-04-20', to: '2026-05-05' });
    expect(saveSpy).toHaveBeenCalledWith('statement-acct30-2026-04-20-to-2026-05-05.pdf');
  });
});
