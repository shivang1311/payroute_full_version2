export interface DayPoint {
  date: string; // ISO date (yyyy-MM-dd)
  count: number;
  amount: number;
}

export interface FeeDayPoint {
  date: string;
  amount: number;
}

export interface PaymentStats {
  totalCount: number;
  totalAmount: number;
  successRate: number;
  failureRate: number;
  avgTatSeconds: number | null;
  completedCount: number;
  failedCount: number;
  byStatus: Record<string, number>;
  byChannel: Record<string, number>;
  byDay: DayPoint[];
}

export interface RailStat {
  rail: string;
  count: number;
}

export interface FeeIncomeStats {
  total: number;
  byDay: FeeDayPoint[];
}
