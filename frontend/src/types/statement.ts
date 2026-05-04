export type EntryType = 'DEBIT' | 'CREDIT' | 'FEE' | 'TAX' | 'REVERSAL';

export interface StatementLine {
  entryId: number;
  paymentId: number | null;
  entryDate: string;
  postedAt: string | null;
  entryType: EntryType;
  debit: string | number;
  credit: string | number;
  currency: string;
  narrative: string | null;
  runningBalance: string | number;
}

export interface Statement {
  accountId: number;
  currency: string | null;
  fromDate: string;
  toDate: string;
  openingBalance: string | number;
  closingBalance: string | number;
  totalCredits: string | number;
  totalDebits: string | number;
  entryCount: number;
  entries: StatementLine[];
}
