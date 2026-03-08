import request from './axios'

export const accountsApi = {
  list: (params?: { type?: string; category?: string }) =>
    request.get('/accounts', { params }),

  create: (data: any) => request.post('/accounts', data),

  update: (id: number, data: any) => request.put(`/accounts/${id}`, data),

  delete: (id: number) => request.delete(`/accounts/${id}`),

  summary: () => request.get('/accounts/summary'),

  // 普通账户记录
  getRecords: (accountId: number) => request.get(`/accounts/${accountId}/records`),

  getCurrentRecord: (accountId: number) => request.get(`/accounts/${accountId}/records/current`),

  addRecord: (accountId: number, data: any) => request.post(`/accounts/${accountId}/records`, data),

  deleteRecord: (accountId: number, recordId: number) =>
    request.delete(`/accounts/${accountId}/records/${recordId}`),

  /** 返回每个账户的 CNY 估值，key = accountId（字符串） */
  getValues: () => request.get('/accounts/values') as Promise<Record<string, number>>
}
