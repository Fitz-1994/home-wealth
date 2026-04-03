import request from './axios'

export const holdingsApi = {
  list: (params?: { accountId?: number; market?: string }) =>
    request.get('/holdings', { params }),

  get: (id: number) => request.get(`/holdings/${id}`),

  create: (data: any) => request.post('/holdings', data),

  update: (id: number, data: any) => request.put(`/holdings/${id}`, data),

  close: (id: number) => request.delete(`/holdings/${id}`),

  validateSymbol: (symbol: string, priceCurrency?: string) =>
    request.post('/holdings/validate-symbol', { symbol, priceCurrency }),

  parseImage: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/holdings/parse-image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000
    })
  },

  batchImport: (holdings: any[]) =>
    request.post('/holdings/batch', holdings)
}

export const holdingGroupApi = {
  list: () => request.get('/holding-groups'),
  create: (data: { groupName: string; note?: string; symbols?: string[] }) =>
    request.post('/holding-groups', data),
  update: (id: number, data: { groupName: string; note?: string }) =>
    request.put(`/holding-groups/${id}`, data),
  updateMembers: (id: number, symbols: string[]) =>
    request.put(`/holding-groups/${id}/members`, { symbols }),
  delete: (id: number) => request.delete(`/holding-groups/${id}`)
}

export const cashBalanceApi = {
  list: (accountId: number) =>
    request.get(`/accounts/${accountId}/cash-balances`),

  upsert: (accountId: number, data: { currency: string; amount: number; note?: string }) =>
    request.post(`/accounts/${accountId}/cash-balances`, data),

  delete: (accountId: number, id: number) =>
    request.delete(`/accounts/${accountId}/cash-balances/${id}`)
}
