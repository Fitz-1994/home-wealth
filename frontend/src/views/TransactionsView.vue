<template>
  <div class="txn-view">
    <div class="page-header">
      <h2>交易流水</h2>
      <div class="header-actions">
        <n-popconfirm @positive-click="generateOpening">
          <template #trigger>
            <n-button>初始化开仓</n-button>
          </template>
          为现有持仓批量生成 OPENING 交易？已存在则跳过。
        </n-popconfirm>
        <n-button type="primary" @click="openCreate">+ 新增交易</n-button>
      </div>
    </div>

    <div class="filters">
      <n-select
        v-model:value="filter.txnType"
        :options="typeFilterOptions"
        clearable
        placeholder="全部类型"
        style="width:140px"
        @update:value="reload"
      />
      <n-select
        v-model:value="filter.accountId"
        :options="accountFilterOptions"
        clearable
        placeholder="全部账户"
        style="width:160px"
        @update:value="reload"
      />
      <n-date-picker
        v-model:value="filter.dateRange"
        type="daterange"
        clearable
        @update:value="reload"
      />
    </div>

    <n-spin :show="loading">
      <n-empty v-if="!rows.length" description="暂无交易记录" style="margin-top:60px" />
      <div v-else class="txn-list">
        <div class="list-header">
          <div class="col-date">日期</div>
          <div class="col-type">类型</div>
          <div class="col-symbol">标的/账户</div>
          <div class="col-qty">数量</div>
          <div class="col-price">单价</div>
          <div class="col-amount">金额</div>
          <div class="col-cny">CNY</div>
          <div class="col-actions">操作</div>
        </div>
        <div v-for="r in rows" :key="r.id" class="list-row">
          <div class="col-date">{{ r.tradeDate }}</div>
          <div class="col-type">
            <n-tag :type="typeColor(r.txnType)" size="small" :bordered="false">
              {{ TXN_TYPE_LABEL[r.txnType] || r.txnType }}
            </n-tag>
            <n-tag v-if="r.isSynthetic" size="tiny" :bordered="false" style="margin-left:4px">合成</n-tag>
          </div>
          <div class="col-symbol">
            <div v-if="r.symbol" class="symbol-line">
              <span class="symbol-name">{{ r.symbolName || r.symbol }}</span>
              <span class="symbol-meta">{{ r.symbol }}</span>
            </div>
            <div class="account-line">{{ r.accountName }}</div>
          </div>
          <div class="col-qty">{{ r.quantity ?? '—' }}</div>
          <div class="col-price">{{ r.price ?? '—' }}</div>
          <div class="col-amount">
            {{ r.currency }} {{ formatNumber(r.amount, 2) }}
            <span v-if="r.fee && +r.fee > 0" class="fee-note">费 {{ formatNumber(r.fee, 2) }}</span>
          </div>
          <div class="col-cny">{{ formatCny(r.amountCny) }}</div>
          <div class="col-actions">
            <n-popconfirm @positive-click="remove(r.id)">
              <template #trigger><n-button text size="tiny" type="error">删除</n-button></template>
              确认删除？删除流水不会自动回滚已修改的持仓和现金余额，请通过反向交易冲销。
            </n-popconfirm>
          </div>
        </div>
      </div>
    </n-spin>

    <div v-if="total > size" class="pager">
      <n-pagination
        :page="page + 1"
        :page-size="size"
        :item-count="total"
        @update:page="(p) => { page = p - 1; reload() }"
      />
    </div>

    <TxnFormModal
      v-model:show="showForm"
      :prefill="prefill"
      :account-options="accountOptions"
      :holdings="holdings"
      @created="reload"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useMessage } from 'naive-ui'
import { transactionsApi, TXN_TYPES, TXN_TYPE_LABEL } from '@/api/transactions'
import { accountsApi } from '@/api/accounts'
import { holdingsApi } from '@/api/holdings'
import { formatCny, formatNumber } from '@/utils/currency'
import TxnFormModal from '@/components/transactions/TxnFormModal.vue'

const message = useMessage()
const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const page = ref(0)
const size = 50

const accounts = ref<any[]>([])
const holdings = ref<any[]>([])

const showForm = ref(false)
const prefill = ref<any>({})

const filter = ref({
  txnType: null as string | null,
  accountId: null as number | null,
  dateRange: null as [number, number] | null
})

const typeFilterOptions = TXN_TYPES.map(t => ({ label: t.label, value: t.value }))

const accountOptions = computed(() =>
  accounts.value
    .filter(a => a.accountType === 'INVESTMENT')
    .map(a => ({ label: a.accountName, value: a.id }))
)

const accountFilterOptions = computed(() => [
  { label: '全部账户', value: null },
  ...accountOptions.value
])

function typeColor(type: string) {
  switch (type) {
    case 'BUY': return 'error'
    case 'SELL': return 'success'
    case 'DIVIDEND': return 'info'
    case 'CASH_IN': return 'success'
    case 'CASH_OUT': return 'warning'
    case 'FEE': return 'default'
    case 'OPENING': return 'default'
    default: return 'default'
  }
}

async function loadAccounts() {
  accounts.value = (await accountsApi.list()) as any[]
}

async function loadHoldings() {
  holdings.value = (await holdingsApi.list()) as any[]
}

async function reload() {
  loading.value = true
  try {
    const params: any = { page: page.value, size }
    if (filter.value.txnType) params.txnType = filter.value.txnType
    if (filter.value.accountId) params.accountId = filter.value.accountId
    if (filter.value.dateRange) {
      params.from = new Date(filter.value.dateRange[0]).toISOString().slice(0, 10)
      params.to = new Date(filter.value.dateRange[1]).toISOString().slice(0, 10)
    }
    const result: any = await transactionsApi.list(params)
    rows.value = result.items || []
    total.value = result.total || 0
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  prefill.value = {}
  showForm.value = true
}

async function remove(id: number) {
  try {
    await transactionsApi.remove(id)
    message.success('已删除')
    await reload()
  } catch (e: any) {
    message.error(e?.message || '删除失败')
  }
}

async function generateOpening() {
  try {
    const result: any = await transactionsApi.generateOpening()
    message.success(`生成了 ${result.generated} 条开仓交易`)
    await reload()
  } catch (e: any) {
    message.error(e?.message || '生成失败')
  }
}

onMounted(async () => {
  await Promise.all([loadAccounts(), loadHoldings()])
  await reload()
})
</script>

<style scoped>
.txn-view { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; }
.header-actions { display: flex; gap: 8px; }
.filters { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }

.txn-list { border: 1px solid var(--hw-border); border-radius: 8px; overflow: hidden; }
.list-header, .list-row {
  display: grid;
  grid-template-columns: 100px 80px 1.4fr 0.7fr 0.7fr 1fr 1fr 60px;
  align-items: center;
  padding: 10px 12px;
  gap: 8px;
}
.list-header {
  background: var(--hw-bg-secondary);
  font-size: 12px;
  font-weight: 600;
  color: var(--hw-text-secondary);
  border-bottom: 1px solid var(--hw-border);
}
.list-row {
  border-bottom: 1px solid var(--hw-border);
  font-size: 13px;
}
.list-row:hover { background: var(--hw-bg-secondary); }
.list-row:last-child { border-bottom: none; }

.symbol-line { display: flex; gap: 6px; align-items: baseline; }
.symbol-name { font-weight: 600; }
.symbol-meta { font-size: 11px; color: var(--hw-text-muted); }
.account-line { font-size: 11px; color: var(--hw-text-muted); margin-top: 2px; }
.col-amount { font-weight: 600; }
.fee-note { font-size: 11px; color: var(--hw-text-muted); margin-left: 4px; }
.col-cny { font-weight: 600; color: var(--hw-text-secondary); }
.col-actions { text-align: right; }

.pager { display: flex; justify-content: center; margin-top: 16px; }
</style>
