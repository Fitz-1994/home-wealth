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
