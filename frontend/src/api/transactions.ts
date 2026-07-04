import request from './axios'

export interface CreateTransactionPayload {
  accountId: number
  holdingId?: number | null
  txnType: string
  symbol?: string
  market?: string
  tradeDate?: string
  quantity?: number | null
  price?: number | null
  amount: number
  fee?: number
  currency: string
  cnyRate?: number
  note?: string
}

export interface TransactionListParams {
  from?: string
  to?: string
  txnType?: string
  accountId?: number
  holdingId?: number
  page?: number
  size?: number
}

export const transactionsApi = {
  list: (params: TransactionListParams = {}) =>
    request.get('/transactions', { params }),

  create: (data: CreateTransactionPayload) =>
    request.post('/transactions', data),

  update: (id: number, data: Partial<CreateTransactionPayload>) =>
    request.put(`/transactions/${id}`, data),

  remove: (id: number) =>
    request.delete(`/transactions/${id}`),

  generateOpening: () =>
    request.post('/transactions/generate-opening')
}

export const TXN_TYPES = [
  { value: 'BUY', label: '加仓' },
  { value: 'SELL', label: '卖出' },
  { value: 'CASH_IN', label: '入金' },
  { value: 'CASH_OUT', label: '出金' },
  { value: 'DIVIDEND', label: '分红' },
  { value: 'FEE', label: '费用' },
  { value: 'OPENING', label: '开仓初始' }
]

export const TXN_TYPE_LABEL: Record<string, string> = Object.fromEntries(
  TXN_TYPES.map(t => [t.value, t.label])
)
