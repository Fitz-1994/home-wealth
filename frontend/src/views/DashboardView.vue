<template>
  <div class="dashboard">
    <div class="page-header">
      <h2>资产大盘</h2>
      <div class="header-actions">
        <n-button size="small" :loading="refreshing" @click="refreshMarket">刷新行情</n-button>
        <n-button size="small" @click="triggerSnapshot">生成快照</n-button>
      </div>
    </div>

    <!-- 总览卡片 -->
    <div v-if="overview" class="overview-cards">
      <n-card class="overview-card net-asset">
        <div class="card-label">净资产</div>
        <div class="card-value">{{ formatCny(overview.netAssetCny) }}</div>
      </n-card>
      <n-card class="overview-card">
        <div class="card-label">总资产</div>
        <div class="card-value">{{ formatCny(overview.totalAssetCny) }}</div>
      </n-card>
      <n-card class="overview-card">
        <div class="card-label">总负债</div>
        <div class="card-value">{{ formatCny(overview.totalLiabilityCny) }}</div>
      </n-card>
      <n-card class="overview-card">
        <div class="card-label">投资市值</div>
        <div class="card-value">{{ formatCny(overview.investmentMarketValue) }}</div>
      </n-card>
    </div>

    <!-- 资产分类 -->
    <div v-if="overview" class="category-tags">
      <n-tag v-for="(val, key) in overview.categories" :key="key" type="default" size="medium">
        {{ categoryLabel(key) }}：{{ formatCny(val) }}
      </n-tag>
    </div>

    <!-- 桑基图 -->
    <n-card title="资产全景" class="chart-card">
      <n-spin :show="loading.sankey">
        <SankeyChart :data="sankeyData" />
      </n-spin>
    </n-card>

    <div class="charts-row">
      <!-- 净资产折线图 -->
      <n-card title="净资产走势" class="chart-card half">
        <template #header-extra>
          <n-select
            v-model:value="netAssetDays"
            :options="dayOptions"
            size="small"
            style="width:100px"
            @update:value="loadNetAssetHistory"
          />
        </template>
        <n-spin :show="loading.netAsset">
          <LineChart
            v-if="netAssetChart.dates.length"
            :dates="netAssetChart.dates"
            :values="netAssetChart.values"
            title="净资产"
            color="#18a058"
          />
          <n-empty v-else description="暂无历史快照数据，请先生成快照" />
        </n-spin>
      </n-card>

      <!-- 投资折线图 -->
      <n-card title="投资资产走势" class="chart-card half">
        <template #header-extra>
          <n-select
            v-model:value="investmentDays"
            :options="dayOptions"
            size="small"
            style="width:100px"
            @update:value="loadInvestmentHistory"
          />
        </template>
        <n-spin :show="loading.investment">
          <LineChart
            v-if="investmentChart.dates.length"
            :dates="investmentChart.dates"
            :values="investmentChart.values"
            title="投资市值"
            color="#2080f0"
          />
          <n-empty v-else description="暂无投资历史数据" />
        </n-spin>
      </n-card>
    </div>

    <!-- 分红统计 -->
    <n-card title="分红收入" class="chart-card">
      <template #header-extra>
        <n-space :size="8">
          <n-button size="small" :loading="loading.dividendBackfill" @click="backfillDividends">回填3月</n-button>
          <n-button size="small" :loading="loading.dividendFetch" @click="fetchDividends">抓取分红</n-button>
        </n-space>
      </template>
      <div v-if="dividendSummary" class="overview-cards" style="margin-bottom:16px">
        <n-card class="overview-card">
          <div class="card-label">近12月分红</div>
          <div class="card-value dividend-value">{{ formatCny(dividendSummary.last12MonthsTotal) }}</div>
        </n-card>
        <n-card class="overview-card">
          <div class="card-label">本年分红</div>
          <div class="card-value dividend-value">{{ formatCny(dividendSummary.currentYearTotal) }}</div>
        </n-card>
        <n-card class="overview-card">
          <div class="card-label">本月分红</div>
          <div class="card-value dividend-value">{{ formatCny(dividendSummary.currentMonthTotal) }}</div>
        </n-card>
      </div>
      <n-spin :show="loading.dividend">
        <div class="dividend-chart-header">
          <span>月度分红</span>
          <n-select
            v-model:value="dividendMonths"
            :options="dividendMonthOptions"
            size="small"
            style="width:100px"
            @update:value="loadDividendHistory"
          />
        </div>
        <div v-if="dividendChart.months.length" ref="dividendChartRef" class="chart-container" />
        <n-empty v-else description="暂无分红数据" />
      </n-spin>

      <!-- 分红明细 -->
      <div v-if="dividendDetail.length" class="dividend-detail">
        <div class="dividend-detail-header">
          <span>{{ selectedDividendMonth }} 分红明细</span>
          <n-button size="tiny" quaternary @click="dividendDetail = []; selectedDividendMonth = ''">关闭</n-button>
        </div>
        <div class="dividend-detail-list">
          <div v-for="item in dividendDetail" :key="item.symbol" class="dividend-detail-item">
            <div class="detail-info">
              <div class="detail-name">{{ item.symbolName || item.symbol }}</div>
              <div class="detail-meta">{{ marketLabel(item.market) }} · {{ item.symbol }} · {{ item.eventCount }}次</div>
            </div>
            <div class="detail-amount">
              <div class="detail-cny">{{ formatCny(item.totalDividendCny) }}</div>
              <div v-if="item.currency !== 'CNY'" class="detail-original">{{ item.currency }} {{ item.totalDividendOriginal }}</div>
            </div>
          </div>
        </div>
      </div>
    </n-card>

    <!-- 持仓排行 -->
    <n-card title="持仓排行榜" class="chart-card">
      <template #header-extra>
        <n-button-group size="small">
          <n-button :type="rankView === 'list' ? 'primary' : 'default'" @click="rankView = 'list'">列表</n-button>
          <n-button :type="rankView === 'treemap' ? 'primary' : 'default'" @click="rankView = 'treemap'">树图</n-button>
        </n-button-group>
      </template>
      <n-spin :show="loading.rank">
        <n-empty v-if="!holdingRank.items?.length" description="暂无持仓数据" />
        <template v-else>
          <div v-if="concentration" class="concentration-stats">
            <div class="stat-cell">
              <div class="stat-label">持仓数</div>
              <div class="stat-value">{{ concentration.count }}</div>
            </div>
            <div class="stat-cell">
              <div class="stat-label">前3集中度</div>
              <div class="stat-value">{{ (concentration.top3 * 100).toFixed(1) }}%</div>
            </div>
            <div class="stat-cell">
              <div class="stat-label">前5集中度</div>
              <div class="stat-value">{{ (concentration.top5 * 100).toFixed(1) }}%</div>
            </div>
            <div class="stat-cell">
              <div class="stat-label">前10集中度</div>
              <div class="stat-value">{{ (concentration.top10 * 100).toFixed(1) }}%</div>
            </div>
            <div class="stat-cell">
              <div class="stat-label">
                HHI
                <n-tooltip trigger="hover" placement="top">
                  <template #trigger><span class="stat-hint">?</span></template>
                  赫芬达尔指数 = Σ(单仓占比²)。&lt;0.15 分散，0.15–0.25 适中，&gt;0.25 集中
                </n-tooltip>
              </div>
              <div class="stat-value">{{ (concentration.hhi * 10000).toFixed(0) }}</div>
            </div>
          </div>
          <TreemapChart v-if="rankView === 'treemap'" :items="holdingRank.items" />
          <div v-else class="holding-rank">
            <div v-for="(item, idx) in holdingRank.items" :key="item.groupId ? `g${item.groupId}` : item.holdingId" class="rank-item">
              <div class="rank-no">{{ idx + 1 }}</div>
              <div class="rank-info">
                <div class="rank-name">{{ item.groupName || item.symbolName || item.symbol }}</div>
                <div class="rank-market">
                  <template v-if="item.groupName">组合 · {{ item.memberCount }}个持仓</template>
                  <template v-else>{{ marketLabel(item.market) }} · {{ item.symbol }}</template>
                </div>
              </div>
              <div class="rank-value">
                <div>{{ formatCny(item.marketValueCny) }}</div>
                <n-progress
                  type="line"
                  :percentage="(item.ratio || 0) * 100"
                  :show-indicator="false"
                  :height="4"
                  style="width:80px"
                />
                <div class="rank-ratio">{{ ((item.ratio || 0) * 100).toFixed(1) }}%</div>
              </div>
              <div class="rank-change" :class="item.priceChangePct >= 0 ? 'up' : 'down'">
                {{ formatPct(item.priceChangePct) }}
              </div>
            </div>
          </div>
        </template>
      </n-spin>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useMessage } from 'naive-ui'
import * as echarts from 'echarts'
import { dashboardApi } from '@/api/dashboard'
import { dividendApi } from '@/api/dividend'
import { formatCny, formatPct, ASSET_CATEGORY_LABELS, MARKET_TYPE_LABELS } from '@/utils/currency'
import SankeyChart from '@/components/charts/SankeyChart.vue'
import LineChart from '@/components/charts/LineChart.vue'
import TreemapChart from '@/components/charts/TreemapChart.vue'

const message = useMessage()

const overview = ref<any>(null)
const sankeyData = ref<any>(null)
const netAssetChart = ref({ dates: [] as string[], values: [] as number[] })
const investmentChart = ref({ dates: [] as string[], values: [] as number[] })
const holdingRank = ref<any>({ items: [] })
const rankView = ref<'list' | 'treemap'>('list')
const netAssetDays = ref(90)
const investmentDays = ref(90)
const refreshing = ref(false)

const dividendSummary = ref<any>(null)
const dividendChart = ref({ months: [] as string[], totals: [] as number[] })
const dividendMonths = ref(12)
const dividendChartRef = ref<HTMLElement>()
const dividendDetail = ref<any[]>([])
const selectedDividendMonth = ref('')
let dividendEchart: echarts.ECharts | null = null

const loading = ref({
  overview: false, sankey: false, netAsset: false, investment: false, rank: false,
  dividend: false, dividendFetch: false, dividendBackfill: false
})

const dayOptions = [
  { label: '近90天', value: 90 },
  { label: '近180天', value: 180 },
  { label: '近365天', value: 365 },
  { label: '全部', value: 0 }
]

const dividendMonthOptions = [
  { label: '近12月', value: 12 },
  { label: '近24月', value: 24 },
  { label: '全部', value: 120 }
]

const categoryLabel = (key: string) => ASSET_CATEGORY_LABELS[key] || key
const marketLabel = (key: string) => MARKET_TYPE_LABELS[key] || key

async function loadOverview() {
  loading.value.overview = true
  try { overview.value = await dashboardApi.overview() } finally { loading.value.overview = false }
}

async function loadSankey() {
  loading.value.sankey = true
  try { sankeyData.value = await dashboardApi.sankey() } finally { loading.value.sankey = false }
}

async function loadNetAssetHistory() {
  loading.value.netAsset = true
  try {
    const data: any = await dashboardApi.netAssetHistory(netAssetDays.value)
    netAssetChart.value = data
  } finally { loading.value.netAsset = false }
}

async function loadInvestmentHistory() {
  loading.value.investment = true
  try {
    const data: any = await dashboardApi.investmentHistory(investmentDays.value)
    investmentChart.value = data
  } finally { loading.value.investment = false }
}

async function loadHoldingRank() {
  loading.value.rank = true
  try { holdingRank.value = await dashboardApi.holdingRank() } finally { loading.value.rank = false }
}

const concentration = computed(() => {
  const rank = holdingRank.value
  if (!rank?.items?.length || !rank.concentration) return null
  return {
    count: rank.totalCount ?? rank.items.length,
    top3: Number(rank.concentration.top3) || 0,
    top5: Number(rank.concentration.top5) || 0,
    top10: Number(rank.concentration.top10) || 0,
    hhi: Number(rank.concentration.hhi) || 0
  }
})

async function refreshMarket() {
  refreshing.value = true
  try {
    await dashboardApi.refreshMarket()
    message.success('行情刷新成功')
    await Promise.all([loadOverview(), loadSankey(), loadHoldingRank()])
  } catch (e: any) {
    message.error(e.message)
  } finally {
    refreshing.value = false
  }
}

async function loadDividendSummary() {
  try { dividendSummary.value = await dividendApi.summary() } catch {}
}

async function loadDividendHistory() {
  loading.value.dividend = true
  try {
    const data: any = await dividendApi.history(dividendMonths.value)
    dividendChart.value = data
    await nextTick()
    renderDividendChart()
  } finally { loading.value.dividend = false }
}

async function loadDividendDetail(month: string) {
  const [yearStr, monthStr] = month.split('-')
  const year = parseInt(yearStr)
  const mon = parseInt(monthStr)
  try {
    dividendDetail.value = await dividendApi.detail(year, mon) as any
    selectedDividendMonth.value = month
  } catch (e: any) {
    message.error(e.message || '加载分红明细失败')
  }
}

function renderDividendChart() {
  if (!dividendChartRef.value) return
  if (!dividendEchart) {
    dividendEchart = echarts.init(dividendChartRef.value)
    dividendResizeObserver.observe(dividendChartRef.value)
    dividendEchart.on('click', (params: any) => {
      if (params.componentType === 'series') {
        loadDividendDetail(params.name)
      }
    })
  }
  dividendEchart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any[]) => {
        const p = params[0]
        const val = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(p.value)
        return `${p.axisValue}<br/>${p.marker}分红收入：${val}`
      }
    },
    grid: { left: 16, right: 16, top: 16, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: dividendChart.value.months,
      axisLabel: { fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        fontSize: 11,
        formatter: (v: number) => {
          if (v >= 1e4) return `${(v / 1e4).toFixed(0)}万`
          return v.toString()
        }
      }
    },
    series: [{
      type: 'bar',
      data: dividendChart.value.totals,
      barMaxWidth: 40,
      cursor: 'pointer',
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#f0a020' },
          { offset: 1, color: '#f0a02060' }
        ]),
        borderRadius: [4, 4, 0, 0]
      }
    }]
  })
}

const dividendResizeObserver = new ResizeObserver(() => dividendEchart?.resize())

async function backfillDividends() {
  loading.value.dividendBackfill = true
  try {
    await dividendApi.backfill(3)
    message.success('分红数据回填成功')
    await Promise.all([loadDividendSummary(), loadDividendHistory()])
  } catch (e: any) {
    message.error(e.message || '分红回填失败')
  } finally {
    loading.value.dividendBackfill = false
  }
}

async function fetchDividends() {
  loading.value.dividendFetch = true
  try {
    await dividendApi.fetch()
    message.success('分红数据抓取成功')
    await Promise.all([loadDividendSummary(), loadDividendHistory()])
  } catch (e: any) {
    message.error(e.message || '分红抓取失败')
  } finally {
    loading.value.dividendFetch = false
  }
}

async function triggerSnapshot() {
  try {
    await dashboardApi.triggerSnapshot()
    message.success('快照生成成功')
    await Promise.all([loadNetAssetHistory(), loadInvestmentHistory()])
  } catch (e: any) {
    message.error(e.message)
  }
}

onMounted(() => {
  Promise.all([
    loadOverview(), loadSankey(),
    loadNetAssetHistory(), loadInvestmentHistory(), loadHoldingRank(),
    loadDividendSummary(), loadDividendHistory()
  ])
})

onUnmounted(() => {
  dividendResizeObserver.disconnect()
  dividendEchart?.dispose()
})
</script>

<style scoped>
.dashboard { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; gap: 8px; }

.overview-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; margin-bottom: 16px; }
.overview-card .card-label { font-size: 12px; color: var(--hw-text-secondary); margin-bottom: 4px; }
.overview-card .card-value { font-size: 20px; font-weight: 600; }
.overview-card.net-asset .card-value { color: #18a058; }

.category-tags { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.chart-card { margin-bottom: 16px; }
.charts-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
@media (max-width: 768px) { .charts-row { grid-template-columns: 1fr; } }

.concentration-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
  gap: 12px;
  padding: 12px 14px;
  margin-bottom: 16px;
  background: var(--hw-bg-secondary, rgba(128,128,128,0.06));
  border: 1px solid var(--hw-border);
  border-radius: 8px;
}
.stat-cell { display: flex; flex-direction: column; gap: 4px; }
.stat-label { font-size: 12px; color: var(--hw-text-secondary); display: flex; align-items: center; gap: 4px; }
.stat-value { font-size: 18px; font-weight: 600; }
.stat-hint {
  display: inline-flex; align-items: center; justify-content: center;
  width: 14px; height: 14px; border-radius: 50%;
  background: var(--hw-text-secondary); color: var(--hw-bg, #fff);
  font-size: 10px; font-weight: bold; cursor: help;
}

.holding-rank { display: flex; flex-direction: column; gap: 12px; }
.rank-item { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--hw-border); }
.rank-no { width: 24px; font-weight: bold; color: var(--hw-text-secondary); }
.rank-info { flex: 1; min-width: 0; }
.rank-name { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rank-market { font-size: 12px; color: var(--hw-text-secondary); }
.rank-value { text-align: right; }
.rank-ratio { font-size: 11px; color: var(--hw-text-secondary); }
.rank-change { width: 64px; text-align: right; font-weight: 500; }
.rank-change.up { color: #d03050; }
.rank-change.down { color: #18a058; }

.dividend-value { color: #f0a020; }
.dividend-chart-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-weight: 500; }
.chart-container { width: 100%; height: 300px; }

.dividend-detail { margin-top: 16px; border-top: 1px solid var(--hw-border); padding-top: 12px; }
.dividend-detail-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-weight: 500; }
.dividend-detail-list { display: flex; flex-direction: column; gap: 8px; }
.dividend-detail-item { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; border-radius: 8px; background: var(--hw-bg-secondary); }
.detail-info { flex: 1; min-width: 0; }
.detail-name { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.detail-meta { font-size: 12px; color: var(--hw-text-secondary); margin-top: 2px; }
.detail-amount { text-align: right; }
.detail-cny { font-weight: 600; color: #f0a020; }
.detail-original { font-size: 12px; color: var(--hw-text-secondary); margin-top: 2px; }
</style>
