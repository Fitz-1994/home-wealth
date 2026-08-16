<template>
  <div class="returns-view">
    <div class="page-header">
      <h2>投资收益分析</h2>
    </div>

    <!-- 收益概览 -->
    <div v-if="summary" class="summary-strip">
      <div class="summary-item">
        <div class="summary-label">今年以来</div>
        <div class="summary-pct" :class="cls(summary.ytdPct)">{{ pct(summary.ytdPct) }}</div>
        <div class="summary-amt" :class="cls(summary.ytdPnl)">{{ cny(summary.ytdPnl) }}</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">近 1 月</div>
        <div class="summary-pct" :class="cls(summary.month1Pct)">{{ pct(summary.month1Pct) }}</div>
        <div class="summary-amt" :class="cls(summary.month1Pnl)">{{ cny(summary.month1Pnl) }}</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">近 3 月</div>
        <div class="summary-pct" :class="cls(summary.month3Pct)">{{ pct(summary.month3Pct) }}</div>
        <div class="summary-amt" :class="cls(summary.month3Pnl)">{{ cny(summary.month3Pnl) }}</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">自上线以来</div>
        <div class="summary-pct" :class="cls(summary.inceptionPct)">{{ pct(summary.inceptionPct) }}</div>
        <div class="summary-amt" :class="cls(summary.inceptionPnl)">{{ cny(summary.inceptionPnl) }}</div>
      </div>
    </div>

    <n-card>
      <div class="controls">
        <n-tabs v-model:value="activeTab" type="segment" style="max-width:380px" @update:value="loadData">
          <n-tab name="cumulative">累计</n-tab>
          <n-tab name="monthly">按月</n-tab>
          <n-tab name="yearly">按年</n-tab>
          <n-tab name="daily">按日</n-tab>
        </n-tabs>
        <div class="controls-right">
          <!-- 累计曲线：区间选择 -->
          <n-radio-group v-if="activeTab === 'cumulative'" v-model:value="cumRange" size="small">
            <n-radio-button value="1m">近1月</n-radio-button>
            <n-radio-button value="3m">近3月</n-radio-button>
            <n-radio-button value="ytd">今年以来</n-radio-button>
            <n-radio-button value="all">成立以来</n-radio-button>
          </n-radio-group>

          <template v-else>
            <n-select
              v-if="activeTab === 'monthly'"
              v-model:value="selectedYear"
              :options="yearOptions"
              size="small"
              style="width:110px"
              @update:value="loadData"
            />
            <n-select
              v-if="activeTab !== 'daily' && unit === 'pct'"
              v-model:value="benchmarkSymbol"
              :options="benchmarkSelectOptions"
              size="small"
              style="width:130px"
              @update:value="loadBenchmark"
            />
            <n-radio-group v-model:value="unit" size="small">
              <n-radio-button value="pct">收益率</n-radio-button>
              <n-radio-button value="cny">收益额</n-radio-button>
            </n-radio-group>
          </template>
        </div>
      </div>

      <n-spin :show="loading">
        <!-- 累计收益曲线（对比主流指数） -->
        <template v-if="activeTab === 'cumulative'">
          <n-empty v-if="!cumData.dates.length" description="暂无收益数据，需先生成每日快照" style="padding:50px 0" />
          <CumulativeReturnChart
            v-else
            :dates="cumData.dates"
            :series="cumData.series"
            :default-selected="cumDefaultSelected"
          />
        </template>

        <template v-else>
          <n-empty v-if="!rows.length" description="暂无收益数据，需先生成每日快照" style="padding:50px 0" />
          <template v-else>
            <ReturnBarChart
              :categories="categories"
              :values="chartValues"
              :unit="unit"
              :overlay-name="overlayActive ? benchmarkName : undefined"
              :overlay-values="overlayActive ? overlayValues : undefined"
            />

            <div class="data-table">
              <div class="table-header">
                <div class="th-period">{{ periodLabel }}</div>
                <div class="th-num">收益率</div>
                <div class="th-num">收益额(CNY)</div>
                <div class="th-num">期初市值</div>
                <div class="th-num">期末市值</div>
                <div class="th-num">净入金</div>
              </div>
              <div v-for="r in tableRows" :key="r.period" class="table-row">
                <div class="td-period">{{ r.period }}</div>
                <div class="td-num" :class="cls(r.returnPct)">{{ pct(r.returnPct) }}</div>
                <div class="td-num" :class="cls(r.pnl)">{{ cny(r.pnl) }}</div>
                <div class="td-num">{{ cny(r.beginValue) }}</div>
                <div class="td-num">{{ cny(r.endValue) }}</div>
                <div class="td-num">{{ cny(r.netCashflow) }}</div>
              </div>
            </div>
          </template>
        </template>
      </n-spin>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useMessage } from 'naive-ui'
import { returnsApi } from '@/api/returns'
import { benchmarksApi, DEFAULT_BENCHMARK, BENCHMARK_OPTIONS } from '@/api/benchmarks'
import { formatCny } from '@/utils/currency'
import ReturnBarChart from '@/components/charts/ReturnBarChart.vue'
import CumulativeReturnChart from '@/components/charts/CumulativeReturnChart.vue'

const message = useMessage()
const loading = ref(false)
const activeTab = ref<'cumulative' | 'monthly' | 'yearly' | 'daily'>('cumulative')
const unit = ref<'pct' | 'cny'>('pct')
const rows = ref<any[]>([])
const summary = ref<any>(null)
const selectedYear = ref<number>(new Date().getFullYear())
const benchmarkSymbol = ref<string>(DEFAULT_BENCHMARK)
const benchmarkRows = ref<any[]>([])

// 累计收益曲线数据
const cumRange = ref<'1m' | '3m' | 'ytd' | 'all'>('ytd')
const dailyAll = ref<any[]>([])
const benchSeries = ref<any[]>([])
const cumLoaded = ref(false)
// 默认展示的曲线（其余可通过图例开启）
const cumDefaultSelected = ['我的组合', '沪深300', '标普500']

const yearOptions = computed(() => {
  const cur = new Date().getFullYear()
  return [cur, cur - 1, cur - 2, cur - 3].map(y => ({ label: `${y} 年`, value: y }))
})

const benchmarkSelectOptions = [
  { label: '不对比指数', value: '' },
  ...BENCHMARK_OPTIONS
]

const benchmarkName = computed(() =>
  BENCHMARK_OPTIONS.find(o => o.value === benchmarkSymbol.value)?.label ?? '基准指数'
)

const periodLabel = computed(() => {
  if (activeTab.value === 'monthly') return '月份'
  if (activeTab.value === 'yearly') return '年份'
  return '日期'
})

// 统一行结构：{ period, returnPct, pnl, beginValue, endValue, netCashflow }
const displayRows = computed(() => {
  return rows.value.map((r: any) => ({
    period: r.period ?? r.date,
    returnPct: r.returnPct,
    pnl: r.absolutePnlCny ?? r.pnlCny,
    beginValue: r.beginValue,
    endValue: r.endValue,
    netCashflow: r.netCashflow
  }))
})

// 表格倒排展示（最新在上），图表仍保持时间正序（左旧右新）
const tableRows = computed(() => [...displayRows.value].reverse())

const categories = computed(() => displayRows.value.map(r => r.period))
const chartValues = computed(() =>
  displayRows.value.map(r => Number(unit.value === 'pct' ? r.returnPct : r.pnl) || 0)
)

// 基准对比仅在 月/年 + 收益率模式下生效
const overlayActive = computed(() =>
  (activeTab.value === 'monthly' || activeTab.value === 'yearly') &&
  unit.value === 'pct' &&
  !!benchmarkSymbol.value
)

const overlayValues = computed<(number | null)[]>(() => {
  const map = new Map<string, number>()
  for (const b of benchmarkRows.value) {
    map.set(b.period, Number(b.returnPct))
  }
  return displayRows.value.map(r => {
    const v = map.get(r.period)
    return v == null || isNaN(v) ? null : v
  })
})

// ============ 累计收益曲线计算 ============

function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const cumStartIso = computed<string | null>(() => {
  if (!dailyAll.value.length) return null
  if (cumRange.value === 'all') return dailyAll.value[0].date
  const now = new Date()
  if (cumRange.value === 'ytd') return `${now.getFullYear()}-01-01`
  const d = new Date(now)
  d.setMonth(d.getMonth() - (cumRange.value === '1m' ? 1 : 3))
  return isoDate(d)
})

/**
 * 组合：日收益率链式累乘（跳过基准日自身的收益，它属于窗口之前）。
 * 指数：收盘点位 / 基期点位 - 1，基期 = 窗口首日或之前最近的收盘（非交易日自动沿用前值）。
 */
const cumData = computed<{ dates: string[]; series: { name: string; values: (number | null)[] }[] }>(() => {
  const all = dailyAll.value
  const startIso = cumStartIso.value
  if (!all.length || !startIso) return { dates: [], series: [] }

  const anchorIdx = all.findIndex((r: any) => r.date >= startIso)
  if (anchorIdx < 0) return { dates: [], series: [] }

  const windowRows = all.slice(anchorIdx)
  const dates = windowRows.map((r: any) => r.date)

  // 我的组合
  const portfolio: number[] = []
  let cum = 1
  windowRows.forEach((r: any, idx: number) => {
    if (idx > 0) cum *= 1 + (Number(r.returnPct) || 0) / 100
    portfolio.push((cum - 1) * 100)
  })
  const series: { name: string; values: (number | null)[] }[] = [
    { name: '我的组合', values: portfolio }
  ]

  // 基准指数（点位向前填充，基期定在窗口起点）
  for (const b of benchSeries.value) {
    const pts: any[] = b.points || []
    if (!pts.length) continue
    let j = 0
    let last: number | null = null
    let base: number | null = null
    const values: (number | null)[] = []
    for (const d of dates) {
      while (j < pts.length && pts[j].date <= d) {
        last = Number(pts[j].close)
        j++
      }
      if (last == null) {
        values.push(null)
        continue
      }
      if (base == null) base = last
      values.push((last / base - 1) * 100)
    }
    if (values.some(v => v != null)) {
      series.push({ name: b.name, values })
    }
  }

  return { dates, series }
})

// ============ 格式化 ============

function pct(v: any): string {
  const n = Number(v)
  if (isNaN(n)) return '—'
  return `${n >= 0 ? '+' : ''}${n.toFixed(2)}%`
}

function cny(v: any): string {
  const n = Number(v)
  if (isNaN(n)) return '—'
  return formatCny(n)
}

function cls(v: any): string {
  const n = Number(v)
  if (isNaN(n) || n === 0) return ''
  return n > 0 ? 'up' : 'down'
}

// ============ 数据加载 ============

async function loadData() {
  loading.value = true
  try {
    if (activeTab.value === 'cumulative') {
      await loadCumulative()
    } else if (activeTab.value === 'monthly') {
      rows.value = (await returnsApi.monthly(selectedYear.value)) as any[]
      await loadBenchmark()
    } else if (activeTab.value === 'yearly') {
      rows.value = (await returnsApi.yearly()) as any[]
      await loadBenchmark()
    } else {
      rows.value = (await returnsApi.daily()) as any[]
    }
  } catch (e: any) {
    message.error(e?.message || '加载失败')
    if (activeTab.value !== 'cumulative') rows.value = []
  } finally {
    loading.value = false
  }
}

async function loadCumulative() {
  if (cumLoaded.value) return
  const [daily, series] = await Promise.all([
    returnsApi.daily(),
    benchmarksApi.series().catch(() => [])
  ])
  dailyAll.value = daily as any[]
  benchSeries.value = series as any[]
  cumLoaded.value = true
}

async function loadBenchmark() {
  if (activeTab.value === 'daily' || activeTab.value === 'cumulative' || !benchmarkSymbol.value) {
    benchmarkRows.value = []
    return
  }
  try {
    const granularity = activeTab.value === 'monthly' ? 'monthly' : 'yearly'
    const year = activeTab.value === 'monthly' ? selectedYear.value : undefined
    benchmarkRows.value = (await benchmarksApi.returns(benchmarkSymbol.value, granularity, year)) as any[]
  } catch {
    benchmarkRows.value = []
  }
}

async function loadSummary() {
  try {
    summary.value = await returnsApi.summary()
  } catch { /* ignore */ }
}

onMounted(async () => {
  await Promise.all([loadData(), loadSummary()])
})
</script>

<style scoped>
.returns-view { padding: 16px; }
.page-header { margin-bottom: 16px; }
.page-header h2 { margin: 0; }

.summary-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.summary-item {
  background: var(--hw-sidebar-bg);
  border: 1px solid var(--hw-border);
  border-radius: 8px;
  padding: 14px 16px;
}
.summary-label { font-size: 12px; color: var(--hw-text-muted); }
.summary-pct { font-size: 22px; font-weight: 700; margin-top: 4px; }
.summary-amt { font-size: 13px; margin-top: 2px; }

.controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}
.controls-right { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }

.data-table { margin-top: 16px; border: 1px solid var(--hw-border); border-radius: 8px; overflow: hidden; }
.table-header, .table-row {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1.2fr 1.2fr 1.2fr 1.2fr;
  align-items: center;
  padding: 8px 12px;
  gap: 8px;
}
.table-header {
  background: var(--hw-bg-secondary);
  font-size: 12px;
  font-weight: 600;
  color: var(--hw-text-secondary);
  border-bottom: 1px solid var(--hw-border);
}
.table-row {
  border-bottom: 1px solid var(--hw-border);
  font-size: 13px;
}
.table-row:last-child { border-bottom: none; }
.table-row:hover { background: var(--hw-bg-secondary); }
.th-num, .td-num { text-align: right; }
.td-period { font-weight: 600; }

.up { color: #d03050; }
.down { color: #18a058; }
</style>
