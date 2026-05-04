import type { RailType } from './index';

export interface SlaConfig {
  id: number;
  rail: RailType;
  targetTatSeconds: number;
  warningThresholdSeconds: number;
  active: boolean;
  createdAt: string;
  updatedAt?: string;
}

export interface SlaConfigRequest {
  rail: RailType;
  targetTatSeconds: number;
  warningThresholdSeconds: number;
  active?: boolean;
}
