<template>
  <div class="holdings-view">
    <div class="page-header">
      <h2>投资持仓</h2>
      <n-button type="primary" @click="openCreateDialog">添加持仓</n-button>
    </div>

    <!-- 筛选 -->
    <div class="filters">
      <n-select
        v-model:value="filterMarket"
        :options="marketFilterOptions"
        placeholder="全部市场"
        clearable
        style="width:140px"
        @update:value="loadHoldings"
      />
    </div>

    <n-spin :show="loading">
      <n-empty v-if="!holdings.length" description="暂无持仓，请添加" />
      <div v-else class="holdings-grid">
        <n-card
          v-for="h in holdings"
          :key="h.id"
          class="holding-card"
          hoverable
        >
          <div class="holding-header">
            <div>
              <div class="symbol-name">{{ h.symbolName || h.symbol }}</div>
              <div class="symbol-code">{{ h.symbol }} · {{ marketLabel(h.market) }}</div>
            </div>
            <div class="change-pct" :class="h.priceChangePct >= 0 ? 'up' : 'down'">
              {{ formatPct(h.priceChangePct) }}
            </div>
          </div>

          <n-divider style="margin: 10px 0" />

          <div class="holding-stats">
            <div class="stat-item">
              <div class="stat-label">持仓数量</div>
              <div class="stat-value">{{ h.quantity }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">现价</div>
              <div class="stat-value">{{ h.priceCurrency }} {{ h.currentPrice }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">市值</div>
              <div class="stat-value primary">{{ formatCny(h.marketValueCny) }}</div>
            </div>
            <div class="stat-item" v-if="h.unrealizedPnl != null">
              <div class="stat-label">浮盈亏</div>
              <div class="stat-value" :class="h.unrealizedPnl >= 0 ? 'up' : 'down'">
                {{ formatCny(h.unrealizedPnl) }}
                <span style="font-size:11px">（{{ formatPct(h.unrealizedPnlPct) }}）</span>
              </div>
            </div>
          </div>

          <n-alert v-if="h.isStale" type="warning" size="small" :show-icon="false" style="margin-top:8px">
            行情数据可能不是最新
          </n-alert>

          <div class="holding-actions">
            <n-button text size="small" @click="editHolding(h)">编辑</n-button>
            <n-popconfirm @positive-click="closeHolding(h.id)">
              <template #trigger>
                <n-button text size="small" type="error">清仓</n-button>
              </template>
              确认清仓 {{ h.symbolName || h.symbol }}？
            </n-popconfirm>
          </div>
        </n-card>
      </div>
    </n-spin>

    <!-- 新建/编辑持仓对话框 -->
    <n-modal v-model:show="showDialog" preset="dialog" :title="editingHolding ? '编辑持仓' : '添加持仓'">
      <n-form :model="form" label-placement="left" label-width="90">
        <n-form-item label="投资账户" required v-if="!editingHolding">
          <n-select v-model:value="form.accountId" :options="investmentAccountOptions" />
        </n-form-item>
        <n-form-item label="标的代码" required v-if="!editingHolding">
          <n-input-group>
            <n-input v-model:value="form.symbol" placeholder="如：600519.SS / AAPL" />
            <n-button :loading="validating" @click="validateSymbol">验证</n-button>
          </n-input-group>
          <div v-if="validatedName" class="validated-name">✓ {{ validatedName }}</div>
        </n-form-item>
        <n-form-item label="市场类型" required v-if="!editingHolding">
          <n-select v-model:value="form.market" :options="marketOptions" />
        </n-form-item>
        <n-form-item label="价格币种" required v-if="!editingHolding">
          <n-select v-model:value="form.priceCurrency" :options="currencyOptions" />
        </n-form-item>
        <n-form-item label="持仓数量" required>
          <n-input-number v-model:value="form.quantity" :precision="6" style="width:100%" />
        </n-form-item>
        <n-form-item label="成本价">
          <n-input-number v-model:value="form.costPrice" :precision="6" style="width:100%" />
        </n-form-item>
        <n-form-item label="成本币种">
          <n-select v-model:value="form.costCurrency" :options="currencyOptions" />
        </n-form-item>
        <n-form-item label="每手股数">
          <n-input-number v-model:value="form.lotSize" :min="1" style="width:100%" />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="form.note" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showDialog = false">取消</n-button>
        <n-button type="primary" :loading="submitting" @click="submitHolding">保存</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useMessage } from 'naive-ui'
import { holdingsApi } from '@/api/holdings'
import { accountsApi } from '@/api/accounts'
import { formatCny, formatPct, MARKET_TYPE_LABELS } from '@/utils/currency'

const message = useMessage()
const loading = ref(false)
const submitting = ref(false)
const validating = ref(false)
const holdings = ref<any[]>([])
const allAccounts = ref<any[]>([])
const showDialog = ref(false)
const editingHolding = ref<any>(null)
const filterMarket = ref<string | null>(null)
const validatedName = ref('')

const investmentAccountOptions = computed(() =>
  allAccounts.value
    .filter(a => a.accountType === 'INVESTMENT')
    .map(a => ({ label: a.accountName, value: a.id }))
)

const marketLabel = (key: string) => MARKET_TYPE_LABELS[key] || key
const marketOptions = Object.entries(MARKET_TYPE_LABELS).map(([k, v]) => ({ label: v, value: k }))
const marketFilterOptions = [{ label: '全部', value: null }, ...marketOptions]
const currencyOptions = ['CNY', 'USD', 'HKD', 'EUR', 'JPY', 'GBP'].map(c => ({ label: c, value: c }))

const defaultForm = () => ({
  accountId: null as number | null,
  symbol: '',
  market: 'CN_A',
  quantity: 0,
  costPrice: null as number | null,
  costCurrency: 'CNY',
  priceCurrency: 'CNY',
  lotSize: 1,
  note: ''
})

const form = ref(defaultForm())

async function loadHoldings() {
  loading.value = true
  try {
    holdings.value = await holdingsApi.list(filterMarket.value ? { market: filterMarket.value } : {}) as any[]
  } finally {
    loading.value = false
  }
}

async function loadAccounts() {
  allAccounts.value = await accountsApi.list() as any[]
}

async function validateSymbol() {
  if (!form.value.symbol) return
  validating.value = true
  validatedName.value = ''
  try {
    const data: any = await holdingsApi.validateSymbol(form.value.symbol, form.value.priceCurrency)
    validatedName.value = data.symbolName || form.value.symbol
    if (data.priceCurrency) form.value.priceCurrency = data.priceCurrency
    message.success(`验证成功：${validatedName.value}，当前价 ${data.priceCurrency} ${data.currentPrice}`)
  } catch (e: any) {
    message.error('标的代码无效或无法获取价格')
  } finally {
    validating.value = false
  }
}

function openCreateDialog() {
  editingHolding.value = null
  form.value = defaultForm()
  validatedName.value = ''
  showDialog.value = true
}

function editHolding(h: any) {
  editingHolding.value = h
  form.value = {
    accountId: h.accountId,
    symbol: h.symbol,
    market: h.market,
    quantity: h.quantity,
    costPrice: h.costPrice,
    costCurrency: h.costCurrency || 'CNY',
    priceCurrency: h.priceCurrency,
    lotSize: h.lotSize || 1,
    note: h.note || ''
  }
  showDialog.value = true
}

async function submitHolding() {
  submitting.value = true
  try {
    if (editingHolding.value) {
      await holdingsApi.update(editingHolding.value.id, form.value)
      message.success('持仓已更新')
    } else {
      await holdingsApi.create(form.value)
      message.success('持仓已添加')
    }
    showDialog.value = false
    await loadHoldings()
  } catch (e: any) {
    message.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function closeHolding(id: number) {
  try {
    await holdingsApi.close(id)
    message.success('已清仓')
    await loadHoldings()
  } catch (e: any) {
    message.error(e.message)
  }
}

onMounted(() => {
  loadHoldings()
  loadAccounts()
})
</script>

<style scoped>
.holdings-view { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; }
.filters { display: flex; gap: 12px; margin-bottom: 16px; }
.holdings-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.holding-header { display: flex; justify-content: space-between; align-items: flex-start; }
.symbol-name { font-size: 15px; font-weight: 600; }
.symbol-code { font-size: 12px; color: #999; margin-top: 2px; }
.change-pct { font-size: 16px; font-weight: 600; }
.change-pct.up { color: #d03050; }
.change-pct.down { color: #18a058; }
.holding-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.stat-label { font-size: 11px; color: #999; margin-bottom: 2px; }
.stat-value { font-size: 14px; font-weight: 500; }
.stat-value.primary { color: #18a058; }
.stat-value.up { color: #d03050; }
.stat-value.down { color: #18a058; }
.holding-actions { display: flex; gap: 8px; margin-top: 10px; }
.validated-name { font-size: 12px; color: #18a058; margin-top: 4px; }
</style>
