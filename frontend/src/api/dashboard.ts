import request from './axios'

export const dashboardApi = {
  overview: () => request.get('/dashboard/overview'),

  sankey: () => request.get('/dashboard/sankey'),

  netAssetHistory: (days = 90) =>
    request.get('/dashboard/net-asset/history', { params: { days } }),

  investmentHistory: (days = 90) =>
    request.get('/dashboard/investment/history', { params: { days } }),

  holdingRank: (top = 20) =>
    request.get('/dashboard/holding-rank', { params: { top } }),

  triggerSnapshot: () => request.post('/snapshots/trigger'),

  refreshMarket: () => request.post('/market/refresh')
}
