/**
 * We don't render real PDFs in tests — that would require a binary-comparison harness.
 * Instead we mock jsPDF + jspdf-autotable and assert the *data shape* we hand them:
 *  - header strings include the report name
 *  - autoTable is called with the right rows for scalars & nested breakdowns
 *  - filename is sanitized
 * That catches the kind of regression we actually care about (passing wrong field
 * to autoTable) without coupling the test to PDF binary formatting.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { PaymentReport } from '../../types';

// vi.mock factories are hoisted above imports — declare shared spies via
// vi.hoisted() so they exist when the factory runs.
const { saveSpy, textSpy, setPageSpy, autoTableSpy } = vi.hoisted(() => ({
  saveSpy: vi.fn(),
  textSpy: vi.fn(),
  setPageSpy: vi.fn(),
  autoTableSpy: vi.fn(),
}));

vi.mock('jspdf', () => {
  // Plain function (not arrow) so `new jsPDF(...)` works. Methods point at the
  // hoisted spies so we can assert what the module-under-test passes through.
  function jsPDFMock(this: Record<string, unknown>) {
    this.internal = { pageSize: { getWidth: () => 595, getHeight: () => 842 } };
    this.setFont = () => undefined;
    this.setFontSize = () => undefined;
    this.setTextColor = () => undefined;
    this.text = textSpy;
    this.setPage = setPageSpy;
    this.getNumberOfPages = () => 1;
    this.save = saveSpy;
    this.lastAutoTable = { finalY: 200 };
  }
  return { default: jsPDFMock };
});

vi.mock('jspdf-autotable', () => ({ default: autoTableSpy }));

// Import AFTER mocks so the module-under-test picks them up.
import { downloadReportPdf } from '../reportPdf';

const baseReport: PaymentReport = {
  id: 7,
  reportName: 'Daily Payments — May 5',
  scope: 'DAILY',
  status: 'COMPLETED',
  metrics: {
    totalAmount: 125000.5,
    totalCount: 42,
    statusBreakdown: { COMPLETED: 38, FAILED: 4 },
    railBreakdown: {
      NEFT: { count: 30, amount: 100000 },
      RTGS: { count: 12, amount: 25000.5 },
    },
  } as unknown as Record<string, unknown>,
  generatedAt: '2026-05-05T10:00:00',
  createdAt: '2026-05-05T09:00:00',
};

describe('downloadReportPdf', () => {
  beforeEach(() => {
    saveSpy.mockClear();
    textSpy.mockClear();
    setPageSpy.mockClear();
    autoTableSpy.mockClear();
  });

  it('writes the document title in the header', () => {
    downloadReportPdf(baseReport);
    const titleCall = textSpy.mock.calls.find((c) =>
      String(c[0]).includes('PayRoute Hub'),
    );
    expect(titleCall).toBeDefined();
  });

  it('passes report metadata into the metadata table body', () => {
    downloadReportPdf(baseReport);
    const metadataCall = autoTableSpy.mock.calls.find((c) => {
      const opts = c[1] as { body?: unknown[][] };
      return opts.body?.some((row) => row[0] === 'Report Name');
    });
    expect(metadataCall).toBeDefined();
    const body = (metadataCall![1] as { body: string[][] }).body;
    expect(body).toContainEqual(['Report Name', 'Daily Payments — May 5']);
    expect(body).toContainEqual(['Report ID', '#7']);
    expect(body).toContainEqual(['Scope', 'DAILY']);
    expect(body).toContainEqual(['Status', 'COMPLETED']);
  });

  it('builds a Summary table from top-level scalar metrics', () => {
    downloadReportPdf(baseReport);
    const summaryCall = autoTableSpy.mock.calls.find((c) => {
      const opts = c[1] as { head?: unknown[][] };
      return opts.head?.[0]?.[0] === 'Summary';
    });
    expect(summaryCall).toBeDefined();
    const body = (summaryCall![1] as { body: string[][] }).body;
    // pretty-cased keys
    expect(body.map((r) => r[0])).toEqual(['Total Amount', 'Total Count']);
  });

  it('builds a flat key→value table for non-nested breakdowns', () => {
    downloadReportPdf(baseReport);
    const statusCall = autoTableSpy.mock.calls.find((c) => {
      const opts = c[1] as { head?: unknown[][] };
      return opts.head?.[0]?.[0] === 'Status Breakdown';
    });
    expect(statusCall).toBeDefined();
    const body = (statusCall![1] as { body: string[][] }).body;
    // prettyLabel inserts a space before every capital letter, so ALL_CAPS keys
    // come out spaced (matches the on-screen rendering — this asserts parity).
    expect(body).toContainEqual(['C O M P L E T E D', '38']);
    expect(body).toContainEqual(['F A I L E D', '4']);
  });

  it('builds a multi-column table for nested breakdowns (rail)', () => {
    downloadReportPdf(baseReport);
    const railCall = autoTableSpy.mock.calls.find((c) => {
      const opts = c[1] as { head?: unknown[][] };
      // Header drops "Breakdown" suffix for nested tables
      return opts.head?.[0]?.[0] === 'Rail';
    });
    expect(railCall).toBeDefined();
    const opts = railCall![1] as { head: string[][]; body: string[][] };
    expect(opts.head[0]).toEqual(['Rail', 'Count', 'Amount']);
    expect(opts.body[0][0]).toBe('NEFT');
    expect(opts.body[1][0]).toBe('RTGS');
  });

  it('saves with a filename that sanitizes the report name', () => {
    downloadReportPdf(baseReport);
    expect(saveSpy).toHaveBeenCalledOnce();
    const filename = saveSpy.mock.calls[0][0] as string;
    expect(filename).toMatch(/^Daily_Payments[_-]+May_5-\d{8}-\d{4}\.pdf$/);
  });

  it('falls back to a default filename when reportName is empty', () => {
    downloadReportPdf({ ...baseReport, reportName: '' });
    const filename = saveSpy.mock.calls[0][0] as string;
    expect(filename).toMatch(/^report-7-\d{8}-\d{4}\.pdf$/);
  });

  it('handles metrics passed as a JSON string', () => {
    const stringified: PaymentReport = {
      ...baseReport,
      metrics: JSON.stringify(baseReport.metrics) as unknown as Record<string, unknown>,
    };
    expect(() => downloadReportPdf(stringified)).not.toThrow();
    const summaryCall = autoTableSpy.mock.calls.find((c) => {
      const opts = c[1] as { head?: unknown[][] };
      return opts.head?.[0]?.[0] === 'Summary';
    });
    expect(summaryCall).toBeDefined();
  });

  it('renders a "no metrics" message when metrics is null', () => {
    const empty: PaymentReport = { ...baseReport, metrics: null as unknown as Record<string, unknown> };
    downloadReportPdf(empty);
    const noMetricsCall = textSpy.mock.calls.find((c) =>
      String(c[0]).includes('No metrics'),
    );
    expect(noMetricsCall).toBeDefined();
  });
});
