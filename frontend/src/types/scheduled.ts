export type ScheduleType = 'ONCE' | 'DAILY' | 'WEEKLY' | 'MONTHLY';
export type ScheduledPaymentStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'CANCELLED' | 'FAILED';

export interface ScheduledPayment {
  id: number;
  userId?: number;
  name?: string;
  debtorAccountId: number;
  creditorAccountId: number;
  amount: number;
  currency: string;
  purposeCode?: string;
  remittanceInfo?: string;
  scheduleType: ScheduleType;
  startAt: string;
  endAt?: string;
  maxRuns?: number;
  runsCount: number;
  nextRunAt?: string;
  lastRunAt?: string;
  lastPaymentId?: number;
  lastError?: string;
  status: ScheduledPaymentStatus;
  createdBy?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface ScheduledPaymentRequest {
  name?: string;
  debtorAccountId: number;
  creditorAccountId: number;
  amount: number;
  currency: string;
  purposeCode?: string;
  remittanceInfo?: string;
  scheduleType: ScheduleType;
  startAt: string;
  endAt?: string;
  maxRuns?: number;
  /** 4–6 digit transaction PIN. Required for customer-initiated schedules. */
  transactionPin?: string;
}
