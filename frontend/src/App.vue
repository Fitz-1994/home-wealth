<template>
  <n-config-provider :theme="themeStore.isDark ? darkTheme : null" :locale="zhCN" :date-locale="dateZhCN">
    <n-message-provider>
      <n-dialog-provider>
        <router-view />
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { watchEffect } from 'vue'
import { darkTheme, zhCN, dateZhCN } from 'naive-ui'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()

watchEffect(() => {
  document.documentElement.classList.toggle('dark', themeStore.isDark)
})
</script>
