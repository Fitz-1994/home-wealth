export function formatCny(value: number | null | undefined): string {
  if (value == null) return '—'
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value)
}

export function formatNumber(value: number | null | undefined, decimals = 2): string {
  if (value == null) return '—'
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals
  }).format(value)
}

export function formatPct(value: number | null | undefined): string {
  if (value == null) return '—'
  const sign = value >= 0 ? '+' : ''
  return `${sign}${value.toFixed(2)}%`
}

export const ASSET_CATEGORY_LABELS: Record<string, string> = {
  LIQUID: '流动资金',
  FIXED: '固定资产',
  RECEIVABLE: '应收款',
  INVESTMENT: '投资理财',
  LIABILITY: '负债'
}

export const MARKET_TYPE_LABELS: Record<string, string> = {
  CN_A: 'A股',
  HK: '港股',
  US: '美股',
  HK_OPT: '港股期权',
  US_OPT: '美股期权',
  FX: '外汇'
}
