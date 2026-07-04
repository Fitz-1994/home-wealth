import request from './axios'

export const benchmarksApi = {
  list: () => request.get('/benchmarks'),

  returns: (symbol: string, granularity: 'monthly' | 'yearly', year?: number) =>
    request.get(`/benchmarks/${encodeURIComponent(symbol)}/returns`, {
      params: { granularity, ...(year ? { year } : {}) }
    }),

  /** 所有基准指数的日收盘序列（用于累计收益曲线对比） */
  series: (from?: string, to?: string) =>
    request.get('/benchmarks/series', {
      params: { ...(from ? { from } : {}), ...(to ? { to } : {}) }
    }),

  fetch: () => request.post('/benchmarks/fetch')
}

export const DEFAULT_BENCHMARK = '000300.SS'

/** 与后端 BenchmarkServiceImpl.BENCHMARKS 保持一致 */
export const BENCHMARK_OPTIONS = [
  { label: '沪深300', value: '000300.SS' },
  { label: '上证指数', value: '000001.SS' },
  { label: '恒生指数', value: '^HSI' },
  { label: '标普500', value: '^GSPC' },
  { label: '纳斯达克', value: '^IXIC' }
]
