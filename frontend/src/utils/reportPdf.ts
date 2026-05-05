/**
 * PDF generator for settlement reports.
 *
 * Builds a multi-section PDF using jsPDF + jspdf-autotable. The shape of
 * `report.metrics` mirrors what the Reports page renders on screen:
 * top-level scalars become a "Summary" key/value table, and nested objects
 * become breakdown tables (e.g. statusBreakdown, railBreakdown).
 */
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import dayjs from 'dayjs';
import type { PaymentReport } from '../types';

// Pretty label for camelCase keys: "totalAmount" -> "Total Amount"
const prettyLabel = (key: string) =>
  key
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, (s) => s.toUpperCase())
    .trim();

const formatValue = (val: unknown): string => {
  if (val == null) return '-';
  if (typeof val === 'number') {
    return val.toLocaleString(undefined, { maximumFractionDigits: 2 });
  }
  if (typeof val === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(val)) {
    return dayjs(val).format('YYYY-MM-DD HH:mm');
  }
  return String(val);
};

export function downloadReportPdf(report: PaymentReport) {
  const doc = new jsPDF({ unit: 'pt', format: 'a4' });
  const pageWidth = doc.internal.pageSize.getWidth();

  // ---------- Header ----------
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(18);
  doc.text('PayRoute Hub — Settlement Report', pageWidth / 2, 50, { align: 'center' });

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10);
  doc.setTextColor(100);
  doc.text(`Generated ${dayjs().format('YYYY-MM-DD HH:mm')}`, pageWidth / 2, 68, { align: 'center' });
  doc.setTextColor(0);

  // ---------- Metadata block ----------
  autoTable(doc, {
    startY: 90,
    theme: 'plain',
    styles: { fontSize: 10, cellPadding: 4 },
    columnStyles: {
      0: { fontStyle: 'bold', cellWidth: 110 },
      1: { cellWidth: 'auto' },
    },
    body: [
      ['Report Name', report.reportName || '-'],
      ['Report ID', `#${report.id}`],
      ['Scope', report.scope || '-'],
      ['Status', report.status || '-'],
      ['Generated At', report.generatedAt ? dayjs(report.generatedAt).format('YYYY-MM-DD HH:mm:ss') : '-'],
      ['Created At', report.createdAt ? dayjs(report.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'],
    ],
  });

  // ---------- Metrics ----------
  let metrics: Record<string, unknown> | null = null;
  try {
    metrics =
      typeof report.metrics === 'string'
        ? JSON.parse(report.metrics)
        : (report.metrics as Record<string, unknown>);
  } catch {
    metrics = null;
  }

  const lastY = (doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY ?? 90;

  if (!metrics) {
    doc.setFontSize(11);
    doc.text('No metrics available for this report.', 40, lastY + 30);
  } else {
    // Split scalars vs nested objects, same as the on-screen view
    const scalars: [string, unknown][] = [];
    const nested: [string, Record<string, unknown>][] = [];
    Object.entries(metrics).forEach(([k, v]) => {
      if (v != null && typeof v === 'object' && !Array.isArray(v)) {
        nested.push([k, v as Record<string, unknown>]);
      } else {
        scalars.push([k, v]);
      }
    });

    // Summary table
    if (scalars.length > 0) {
      autoTable(doc, {
        startY: lastY + 20,
        head: [['Summary', 'Value']],
        body: scalars.map(([k, v]) => [prettyLabel(k), formatValue(v)]),
        styles: { fontSize: 10, cellPadding: 5 },
        headStyles: { fillColor: [99, 102, 241], textColor: 255, fontStyle: 'bold' },
        columnStyles: { 1: { halign: 'right' } },
      });
    }

    // Breakdown tables
    nested.forEach(([title, obj]) => {
      const entries = Object.entries(obj);
      if (entries.length === 0) return;

      const firstVal = entries[0][1];
      const isNested =
        firstVal != null && typeof firstVal === 'object' && !Array.isArray(firstVal);

      const startY =
        ((doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY ?? 0) + 20;

      if (!isNested) {
        // Simple key→value
        autoTable(doc, {
          startY,
          head: [[prettyLabel(title), 'Value']],
          body: entries.map(([k, v]) => [prettyLabel(k), formatValue(v)]),
          styles: { fontSize: 10, cellPadding: 5 },
          headStyles: { fillColor: [99, 102, 241], textColor: 255, fontStyle: 'bold' },
          columnStyles: { 1: { halign: 'right' } },
        });
      } else {
        // Nested: build column set from first object
        const subKeys = Object.keys(firstVal as Record<string, unknown>);
        autoTable(doc, {
          startY,
          head: [[prettyLabel(title).replace(/ Breakdown$/i, ''), ...subKeys.map(prettyLabel)]],
          body: entries.map(([k, v]) => {
            const row = v as Record<string, unknown>;
            return [k, ...subKeys.map((sk) => formatValue(row[sk]))];
          }),
          styles: { fontSize: 10, cellPadding: 5 },
          headStyles: { fillColor: [99, 102, 241], textColor: 255, fontStyle: 'bold' },
          columnStyles: Object.fromEntries(
            subKeys.map((_, i) => [i + 1, { halign: 'right' as const }])
          ),
        });
      }
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

  // Save with a friendly filename
  const safeName = (report.reportName || `report-${report.id}`)
    .replace(/[^a-zA-Z0-9-_]+/g, '_')
    .replace(/^_+|_+$/g, '');
  doc.save(`${safeName}-${dayjs().format('YYYYMMDD-HHmm')}.pdf`);
}
