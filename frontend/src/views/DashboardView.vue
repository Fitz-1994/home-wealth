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

    <!-- 持仓排行 -->
    <n-card title="持仓排行榜" class="chart-card">
      <n-spin :show="loading.rank">
        <n-empty v-if="!holdingRank.items?.length" description="暂无持仓数据" />
        <div v-else class="holding-rank">
          <div v-for="(item, idx) in holdingRank.items" :key="item.holdingId" class="rank-item">
            <div class="rank-no">{{ idx + 1 }}</div>
            <div class="rank-info">
              <div class="rank-name">{{ item.symbolName || item.symbol }}</div>
              <div class="rank-market">{{ marketLabel(item.market) }} · {{ item.symbol }}</div>
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
      </n-spin>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { dashboardApi } from '@/api/dashboard'
import { formatCny, formatPct, ASSET_CATEGORY_LABELS, MARKET_TYPE_LABELS } from '@/utils/currency'
import SankeyChart from '@/components/charts/SankeyChart.vue'
import LineChart from '@/components/charts/LineChart.vue'

const message = useMessage()

const overview = ref<any>(null)
const sankeyData = ref<any>(null)
const netAssetChart = ref({ dates: [] as string[], values: [] as number[] })
const investmentChart = ref({ dates: [] as string[], values: [] as number[] })
const holdingRank = ref<any>({ items: [] })
const netAssetDays = ref(90)
const investmentDays = ref(90)
const refreshing = ref(false)

const loading = ref({
  overview: false, sankey: false, netAsset: false, investment: false, rank: false
})

const dayOptions = [
  { label: '近90天', value: 90 },
  { label: '近180天', value: 180 },
  { label: '近365天', value: 365 },
  { label: '全部', value: 0 }
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
    loadNetAssetHistory(), loadInvestmentHistory(), loadHoldingRank()
  ])
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
</style>
