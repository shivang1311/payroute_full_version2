import apiClient from './client';
import type { ApiResponse } from '../types';
import type { Statement } from '../types/statement';

export const statementApi = {
  get: (accountId: number, from: string, to: string) =>
    apiClient.get<ApiResponse<Statement>>('/ledger/statement', {
      params: { accountId, from, to },
    }),

  downloadCsv: (accountId: number, from: string, to: string) =>
    apiClient.get('/ledger/statement.csv', {
      params: { accountId, from, to },
      responseType: 'blob',
    }),
};
