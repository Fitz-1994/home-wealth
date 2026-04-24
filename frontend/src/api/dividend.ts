import request from './axios'

export const dividendApi = {
  summary: () => request.get('/dividend/summary'),

  history: (months = 12) =>
    request.get('/dividend/history', { params: { months } }),

  detail: (year: number, month?: number) =>
    request.get('/dividend/detail', { params: { year, month } }),

  fetch: () => request.post('/dividend/fetch'),

  backfill: (months = 3) =>
    request.post('/dividend/backfill', null, { params: { months } }),
}
