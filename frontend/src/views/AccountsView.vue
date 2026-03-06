<template>
  <div class="accounts-view">
    <div class="page-header">
      <h2>账户管理</h2>
      <n-button type="primary" @click="openCreateDialog">新建账户</n-button>
    </div>

    <n-spin :show="loading">
      <div v-for="category in categories" :key="category.key" class="category-section">
        <div class="category-header">
          <span class="category-title">{{ category.label }}</span>
          <n-tag size="small">{{ getCategoryAccounts(category.key).length }} 个账户</n-tag>
        </div>

        <div v-if="getCategoryAccounts(category.key).length" class="account-list">
          <n-card
            v-for="account in getCategoryAccounts(category.key)"
            :key="account.id"
            class="account-card"
            hoverable
          >
            <div class="account-header">
              <div class="account-name">{{ account.accountName }}</div>
              <n-tag :type="account.accountType === 'INVESTMENT' ? 'info' : 'default'" size="small">
                {{ account.accountType === 'INVESTMENT' ? '投资账户' : '普通账户' }}
              </n-tag>
            </div>
            <div class="account-currency">主币种：{{ account.currency }}</div>
            <div class="account-desc" v-if="account.description">{{ account.description }}</div>
            <div class="account-actions">
              <n-button text size="small" @click="openUpdateRecord(account)" v-if="account.accountType === 'REGULAR'">
                更新余额
              </n-button>
              <n-button text size="small" @click="viewHistory(account)" v-if="account.accountType === 'REGULAR'">
                历史记录
              </n-button>
              <n-button text size="small" @click="editAccount(account)">编辑</n-button>
              <n-popconfirm @positive-click="deleteAccount(account.id)">
                <template #trigger>
                  <n-button text size="small" type="error">删除</n-button>
                </template>
                确认删除账户？
              </n-popconfirm>
            </div>
          </n-card>
        </div>
        <n-empty v-else size="small" description="暂无账户" />
      </div>
    </n-spin>

    <!-- 新建/编辑账户对话框 -->
    <n-modal v-model:show="showAccountDialog" preset="dialog" :title="editingAccount ? '编辑账户' : '新建账户'">
      <n-form :model="accountForm" label-placement="left" label-width="90">
        <n-form-item label="账户名称" required>
          <n-input v-model:value="accountForm.accountName" placeholder="如：招商银行活期" />
        </n-form-item>
        <n-form-item label="账户类型" required v-if="!editingAccount">
          <n-select v-model:value="accountForm.accountType" :options="accountTypeOptions" />
        </n-form-item>
        <n-form-item label="资产类型" required>
          <n-select
            v-model:value="accountForm.assetCategory"
            :options="getCategoryOptions(accountForm.accountType)"
          />
        </n-form-item>
        <n-form-item label="主币种">
          <n-select v-model:value="accountForm.currency" :options="currencyOptions" />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="accountForm.description" type="textarea" :rows="2" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showAccountDialog = false">取消</n-button>
        <n-button type="primary" :loading="submitting" @click="submitAccount">保存</n-button>
      </template>
    </n-modal>

    <!-- 更新余额对话框 -->
    <n-modal v-model:show="showRecordDialog" preset="dialog" title="更新账户余额">
      <n-form :model="recordForm" label-placement="left" label-width="80">
        <n-form-item label="金额" required>
          <n-input-number v-model:value="recordForm.amount" :precision="2" style="width:100%" />
        </n-form-item>
        <n-form-item label="币种">
          <n-select v-model:value="recordForm.currency" :options="currencyOptions" />
        </n-form-item>
        <n-form-item label="日期">
          <n-date-picker v-model:formatted-value="recordForm.recordDate" value-format="yyyy-MM-dd" />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="recordForm.note" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showRecordDialog = false">取消</n-button>
        <n-button type="primary" :loading="submitting" @click="submitRecord">保存</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { accountsApi } from '@/api/accounts'
import { ASSET_CATEGORY_LABELS } from '@/utils/currency'
import dayjs from 'dayjs'

const message = useMessage()
const loading = ref(false)
const submitting = ref(false)
const accounts = ref<any[]>([])
const showAccountDialog = ref(false)
const showRecordDialog = ref(false)
const editingAccount = ref<any>(null)
const selectedAccount = ref<any>(null)

const categories = Object.entries(ASSET_CATEGORY_LABELS).map(([key, label]) => ({ key, label }))

const accountForm = ref({ accountName: '', accountType: 'REGULAR', assetCategory: 'LIQUID', currency: 'CNY', description: '' })
const recordForm = ref({ amount: 0, currency: 'CNY', recordDate: dayjs().format('YYYY-MM-DD'), note: '' })

const accountTypeOptions = [
  { label: '普通账户', value: 'REGULAR' },
  { label: '投资账户', value: 'INVESTMENT' }
]
const currencyOptions = ['CNY', 'USD', 'HKD', 'EUR', 'JPY', 'GBP'].map(c => ({ label: c, value: c }))

function getCategoryOptions(accountType: string) {
  if (accountType === 'INVESTMENT') {
    return [{ label: '投资理财', value: 'INVESTMENT' }]
  }
  return Object.entries(ASSET_CATEGORY_LABELS).map(([k, v]) => ({ label: v, value: k }))
}

function getCategoryAccounts(category: string) {
  return accounts.value.filter(a => a.assetCategory === category)
}

async function loadAccounts() {
  loading.value = true
  try { accounts.value = await accountsApi.list() as any[] }
  finally { loading.value = false }
}

function openCreateDialog() {
  editingAccount.value = null
  accountForm.value = { accountName: '', accountType: 'REGULAR', assetCategory: 'LIQUID', currency: 'CNY', description: '' }
  showAccountDialog.value = true
}

function editAccount(account: any) {
  editingAccount.value = account
  accountForm.value = {
    accountName: account.accountName,
    accountType: account.accountType,
    assetCategory: account.assetCategory,
    currency: account.currency,
    description: account.description || ''
  }
  showAccountDialog.value = true
}

async function submitAccount() {
  submitting.value = true
  try {
    if (editingAccount.value) {
      await accountsApi.update(editingAccount.value.id, accountForm.value)
      message.success('账户已更新')
    } else {
      await accountsApi.create(accountForm.value)
      message.success('账户已创建')
    }
    showAccountDialog.value = false
    await loadAccounts()
  } catch (e: any) {
    message.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function deleteAccount(id: number) {
  try {
    await accountsApi.delete(id)
    message.success('已删除')
    await loadAccounts()
  } catch (e: any) {
    message.error(e.message)
  }
}

function openUpdateRecord(account: any) {
  selectedAccount.value = account
  recordForm.value = { amount: 0, currency: account.currency, recordDate: dayjs().format('YYYY-MM-DD'), note: '' }
  showRecordDialog.value = true
}

async function submitRecord() {
  submitting.value = true
  try {
    await accountsApi.addRecord(selectedAccount.value.id, recordForm.value)
    message.success('余额已更新')
    showRecordDialog.value = false
  } catch (e: any) {
    message.error(e.message)
  } finally {
    submitting.value = false
  }
}

function viewHistory(account: any) {
  // TODO: 跳转历史记录页或打开弹窗
  message.info('历史记录功能即将完善')
}

onMounted(loadAccounts)
</script>

<style scoped>
.accounts-view { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; }
.category-section { margin-bottom: 24px; }
.category-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.category-title { font-size: 16px; font-weight: 600; }
.account-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px; }
.account-card .account-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.account-name { font-weight: 500; }
.account-currency { font-size: 12px; color: #999; margin-bottom: 4px; }
.account-desc { font-size: 12px; color: #666; margin-bottom: 8px; }
.account-actions { display: flex; gap: 4px; }
</style>
