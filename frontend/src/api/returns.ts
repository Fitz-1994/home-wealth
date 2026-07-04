import request from './axios'

export const returnsApi = {
  daily: (params?: { from?: string; to?: string }) =>
    request.get('/returns/daily', { params }),

  monthly: (year?: number) =>
    request.get('/returns/monthly', { params: year ? { year } : {} }),

  yearly: () =>
    request.get('/returns/yearly'),

  summary: () =>
    request.get('/returns/summary')
}
