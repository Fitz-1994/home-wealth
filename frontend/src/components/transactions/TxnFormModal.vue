<template>
  <n-modal v-model:show="visible" preset="dialog" :title="title" :style="{ width: '480px' }">
    <n-form :model="form" label-placement="left" label-width="90">
      <n-form-item label="交易类型" required>
        <n-select v-model:value="form.txnType" :options="txnTypeOptions" :disabled="lockedType" />
      </n-form-item>

      <n-form-item label="投资账户" required>
        <n-select v-model:value="form.accountId" :options="accountOptions" :disabled="lockedAccount" />
      </n-form-item>

      <!-- 持仓选择：BUY/SELL/DIVIDEND -->
      <n-form-item v-if="needsHolding" :label="holdingLabel" :required="needsHoldingRequired">
        <n-select
          v-model:value="form.holdingId"
          :options="holdingOptions"
          :disabled="lockedHolding"
          placeholder="选择持仓"
          clearable
        />
      </n-form-item>

      <!-- BUY 新建持仓时手填 symbol/market -->
      <template v-if="form.txnType === 'BUY' && !form.holdingId">
        <n-form-item label="标的代码">
          <n-input v-model:value="form.symbol" placeholder="如：600519.SS / AAPL" />
        </n-form-item>
        <n-form-item label="市场">
          <n-select v-model:value="form.market" :options="marketOptions" />
        </n-form-item>
      </template>

      <n-form-item label="交易日期" required>
        <n-date-picker v-model:value="form.tradeDateMs" type="date" style="width:100%" />
      </n-form-item>

      <!-- 数量/单价：BUY/SELL/DIVIDEND -->
      <template v-if="needsQuantity">
        <n-form-item label="数量" :required="form.txnType !== 'DIVIDEND'">
          <n-input-number v-model:value="form.quantity" :precision="6" style="width:100%" />
        </n-form-item>
        <n-form-item :label="form.txnType === 'DIVIDEND' ? '每股分红' : '单价'">
          <n-input-number v-model:value="form.price" :precision="6" style="width:100%" @update:value="onPriceChange" />
        </n-form-item>
      </template>

      <n-form-item label="金额" required>
        <n-input-number v-model:value="form.amount" :precision="4" style="width:100%" />
        <template #feedback>
          <span v-if="form.txnType === 'BUY'" style="font-size:11px;color:var(--hw-text-muted)">含手续费</span>
          <span v-else-if="form.txnType === 'SELL'" style="font-size:11px;color:var(--hw-text-muted)">不含手续费</span>
        </template>
      </n-form-item>

      <n-form-item v-if="needsFee" label="手续费">
        <n-input-number v-model:value="form.fee" :precision="4" style="width:100%" />
      </n-form-item>

      <n-form-item label="币种" required>
        <n-select v-model:value="form.currency" :options="currencyOptions" :disabled="lockedCurrency" />
      </n-form-item>

      <n-form-item label="备注">
        <n-input v-model:value="form.note" type="textarea" :autosize="{ minRows: 1, maxRows: 3 }" />
      </n-form-item>
    </n-form>
    <template #action>
      <n-button @click="close">取消</n-button>
      <n-button type="primary" :loading="submitting" @click="submit">保存</n-button>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { transactionsApi, TXN_TYPES } from '@/api/transactions'

interface PrefillData {
  txnType?: string
  accountId?: number | null
  holdingId?: number | null
  symbol?: string
  market?: string
  currency?: string
  /** 锁定哪些字段（无法在 Modal 里修改） */
  lockTxnType?: boolean
  lockAccount?: boolean
  lockHolding?: boolean
  lockCurrency?: boolean
}

const props = defineProps<{
  show: boolean
  prefill?: PrefillData
  accountOptions: Array<{ label: string; value: number }>
  holdings: any[]   // 全量持仓，按账户筛选选项
}>()

const emit = defineEmits<{
  (e: 'update:show', val: boolean): void
  (e: 'created'): void
}>()

const message = useMessage()
const submitting = ref(false)

const visible = computed({
  get: () => props.show,
  set: (v) => emit('update:show', v)
})

const txnTypeOptions = TXN_TYPES.map(t => ({ label: t.label, value: t.value }))
const marketOptions = [
  { label: 'A股', value: 'CN_A' },
  { label: '港股', value: 'HK' },
  { label: '美股', value: 'US' },
  { label: '港股期权', value: 'HK_OPT' },
  { label: '美股期权', value: 'US_OPT' },
  { label: '基金', value: 'CN_FUND' },
  { label: '其他', value: 'FX' }
]
const currencyOptions = ['CNY', 'USD', 'HKD', 'EUR', 'JPY', 'GBP'].map(c => ({ label: c, value: c }))

const defaultForm = () => ({
  txnType: 'BUY',
  accountId: null as number | null,
  holdingId: null as number | null,
  symbol: '',
  market: 'CN_A',
  tradeDateMs: Date.now(),
  quantity: null as number | null,
  price: null as number | null,
  amount: null as number | null,
  fee: 0,
  currency: 'CNY',
  note: ''
})

const form = ref(defaultForm())

const needsHolding = computed(() => ['BUY', 'SELL', 'DIVIDEND'].includes(form.value.txnType))
const needsHoldingRequired = computed(() => ['SELL', 'DIVIDEND'].includes(form.value.txnType))
const needsQuantity = computed(() => ['BUY', 'SELL', 'DIVIDEND'].includes(form.value.txnType))
const needsFee = computed(() => ['BUY', 'SELL'].includes(form.value.txnType))

const holdingLabel = computed(() => {
  if (form.value.txnType === 'BUY') return '持仓 (新建留空)'
  return '持仓'
})

const lockedType = computed(() => !!props.prefill?.lockTxnType)
const lockedAccount = computed(() => !!props.prefill?.lockAccount)
const lockedHolding = computed(() => !!props.prefill?.lockHolding)
const lockedCurrency = computed(() => !!props.prefill?.lockCurrency)

const holdingOptions = computed(() => {
  const acctId = form.value.accountId
  return (props.holdings || [])
    .filter(h => !acctId || h.accountId === acctId)
    .map(h => ({
      label: `${h.symbolName || h.symbol} (${h.symbol})`,
      value: h.id,
      currency: h.priceCurrency,
      market: h.market,
      symbol: h.symbol
    }))
})

watch(() => props.show, (v) => {
  if (v) initForm()
})

function initForm() {
  const p = props.prefill || {}
  const base = defaultForm()
  form.value = {
    ...base,
    ...(p.txnType ? { txnType: p.txnType } : {}),
    ...(p.accountId ? { accountId: p.accountId } : {}),
    ...(p.holdingId ? { holdingId: p.holdingId } : {}),
    ...(p.symbol ? { symbol: p.symbol } : {}),
    ...(p.market ? { market: p.market } : {}),
    ...(p.currency ? { currency: p.currency } : {})
  }
  // 兜底：如果只有一个账户且未指定，自动填上
  if (!form.value.accountId && props.accountOptions.length === 1) {
    form.value.accountId = props.accountOptions[0].value
  }
}

watch(() => form.value.holdingId, (id) => {
  if (!id) return
  const h = (props.holdings || []).find((x: any) => x.id === id)
  if (h) {
    if (h.priceCurrency) form.value.currency = h.priceCurrency
    if (h.symbol) form.value.symbol = h.symbol
    if (h.market) form.value.market = h.market
  }
})

function onPriceChange() {
  const t = form.value.txnType
  if (!['BUY', 'SELL', 'DIVIDEND'].includes(t)) return
  const q = Number(form.value.quantity) || 0
  const p = Number(form.value.price) || 0
  if (q > 0 && p > 0) {
    form.value.amount = +(q * p).toFixed(4)
  }
}

function close() {
  visible.value = false
}

async function submit() {
  if (!form.value.accountId) {
    message.warning('请选择账户')
    return
  }
  if (!form.value.amount && form.value.amount !== 0) {
    message.warning('请输入金额')
    return
  }
  if (needsHoldingRequired.value && !form.value.holdingId) {
    message.warning('请选择持仓')
    return
  }

  submitting.value = true
  try {
    const tradeDate = new Date(form.value.tradeDateMs).toISOString().slice(0, 10)
    await transactionsApi.create({
      accountId: form.value.accountId,
      holdingId: form.value.holdingId || null,
      txnType: form.value.txnType,
      symbol: form.value.symbol || undefined,
      market: form.value.market || undefined,
      tradeDate,
      quantity: form.value.quantity,
      price: form.value.price,
      amount: form.value.amount,
      fee: form.value.fee || 0,
      currency: form.value.currency,
      note: form.value.note
    })
    message.success('已保存')
    emit('created')
    visible.value = false
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const title = computed(() => {
  const t = TXN_TYPES.find(x => x.value === form.value.txnType)
  return `新增交易 — ${t?.label || ''}`
})
</script>
