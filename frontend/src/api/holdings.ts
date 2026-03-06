import request from './axios'

export const holdingsApi = {
  list: (params?: { accountId?: number; market?: string }) =>
    request.get('/holdings', { params }),

  get: (id: number) => request.get(`/holdings/${id}`),

  create: (data: any) => request.post('/holdings', data),

  update: (id: number, data: any) => request.put(`/holdings/${id}`, data),

  close: (id: number) => request.delete(`/holdings/${id}`),

  validateSymbol: (symbol: string, priceCurrency?: string) =>
    request.post('/holdings/validate-symbol', { symbol, priceCurrency })
}
