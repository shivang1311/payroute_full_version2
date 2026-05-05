/**
 * PDF generator for account statements.
 *
 * Mirrors what the StatementPage shows on screen:
 *   header (account + period)
 *   summary cards (opening balance, total credits/debits, closing balance)
 *   per-entry table (date, type, narrative, debit, credit, running balance)
 *
 * Customers get a "clean" view — internal entry IDs and PaymentId mentions
 * stripped from narratives, same as the on-screen rendering.
 */
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import dayjs from 'dayjs';
import { paymentRef } from './paymentRef';
import type { Statement } from '../types/statement';

const toNum = (v: string | number | null | undefined): number => {
  if (v == null) return 0;
  return typeof v === 'number' ? v : Number(v);
};

const fmtMoney = (v: string | number | null | undefined) =>
  toNum(v).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Strip internal payment-id mentions for customer view, matching the on-screen logic. */
const cleanNarrative = (v: string | null | undefined, isCustomer: boolean): string => {
  if (!v) return '-';
  if (!isCustomer) return v;
  return (
    v
      .replace(/\s*[-–]\s*PaymentId:\s*\d+/gi, '')
      .replace(/,\s*PaymentId:\s*\d+/gi, '')
      .replace(/\s*\(id\s*=\s*\d+\)/gi, '')
      .trim() || '-'
  );
};

export interface StatementPdfOpts {
  isCustomer: boolean;
  /** Period the statement covers (display only — values come from `statement`). */
  from: string;
  to: string;
  /** Optional friendly account label (account number / alias) for the header. */
  accountLabel?: string;
}

export function downloadStatementPdf(statement: Statement, opts: StatementPdfOpts) {
  const { isCustomer, from, to, accountLabel } = opts;
  const doc = new jsPDF({ unit: 'pt', format: 'a4' });
  const pageWidth = doc.internal.pageSize.getWidth();

  // ---------- Header ----------
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(18);
  doc.text('PayRoute Hub — Account Statement', pageWidth / 2, 50, { align: 'center' });

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10);
  doc.setTextColor(100);
  doc.text(`Generated ${dayjs().format('YYYY-MM-DD HH:mm')}`, pageWidth / 2, 68, { align: 'center' });
  doc.setTextColor(0);

  // ---------- Account / period block ----------
  autoTable(doc, {
    startY: 90,
    theme: 'plain',
    styles: { fontSize: 10, cellPadding: 4 },
    columnStyles: {
      0: { fontStyle: 'bold', cellWidth: 110 },
      1: { cellWidth: 'auto' },
    },
    body: [
      ['Account', accountLabel || `#${statement.accountId}`],
      ['Currency', statement.currency || '-'],
      ['Period', `${from} → ${to}`],
      ['Entries', String(statement.entries?.length ?? 0)],
    ],
  });

  const lastY1 =
    (doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY ?? 90;

  // ---------- Summary table ----------
  autoTable(doc, {
    startY: lastY1 + 16,
    head: [['Summary', `Amount (${statement.currency || ''})`]],
    body: [
      ['Opening Balance', fmtMoney(statement.openingBalance)],
      ['Total Credits', fmtMoney(statement.totalCredits)],
      ['Total Debits', fmtMoney(statement.totalDebits)],
      ['Closing Balance', fmtMoney(statement.closingBalance)],
    ],
    styles: { fontSize: 10, cellPadding: 5 },
    headStyles: { fillColor: [99, 102, 241], textColor: 255, fontStyle: 'bold' },
    columnStyles: { 1: { halign: 'right' } },
  });

  const lastY2 =
    (doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY ?? lastY1;

  // ---------- Entries table ----------
  if (!statement.entries || statement.entries.length === 0) {
    doc.setFontSize(11);
    doc.text('No entries in this period.', 40, lastY2 + 30);
  } else {
    // Build rows: customers get a Reference No (paymentRef) instead of raw IDs.
    const head = isCustomer
      ? [['Date', 'Reference', 'Type', 'Narrative', 'Debit', 'Credit', 'Balance']]
      : [['Date', 'Entry #', 'Payment', 'Type', 'Narrative', 'Debit', 'Credit', 'Balance']];

    const body = statement.entries.map((e) => {
      const debit = toNum(e.debit) ? fmtMoney(e.debit) : '-';
      const credit = toNum(e.credit) ? fmtMoney(e.credit) : '-';
      const balance = fmtMoney(e.runningBalance);
      const narrative = cleanNarrative(e.narrative, isCustomer);
      if (isCustomer) {
        const ref = e.paymentId
          ? paymentRef({ id: e.paymentId, createdAt: e.entryDate })
          : '-';
        return [e.entryDate, ref, e.entryType, narrative, debit, credit, balance];
      }
      return [
        e.entryDate,
        String(e.entryId),
        e.paymentId ? `#${e.paymentId}` : '-',
        e.entryType,
        narrative,
        debit,
        credit,
        balance,
      ];
    });

    const numCols = head[0].length;
    autoTable(doc, {
      startY: lastY2 + 20,
      head,
      body,
      styles: { fontSize: 9, cellPadding: 4, overflow: 'linebreak' },
      headStyles: { fillColor: [99, 102, 241], textColor: 255, fontStyle: 'bold' },
      // Right-align Debit / Credit / Balance — last 3 columns regardless of view
      columnStyles: {
        [numCols - 3]: { halign: 'right' },
        [numCols - 2]: { halign: 'right' },
        [numCols - 1]: { halign: 'right', fontStyle: 'bold' },
      },
    });
  }

  // ---------- Footer (page numbers) ----------
  const pageCount = doc.getNumberOfPages();
  for (let i = 1; i <= pageCount; i++) {
    doc.setPage(i);
    doc.setFontSize(9);
    doc.setTextColor(120);
    doc.text(
      `Page ${i} of ${pageCount}`,
      pageWidth / 2,
      doc.internal.pageSize.getHeight() - 20,
      { align: 'center' }
    );
  }

  doc.save(`statement-acct${statement.accountId}-${from}-to-${to}.pdf`);
}
