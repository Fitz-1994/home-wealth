import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>((localStorage.getItem('hw-theme') as ThemeMode) || 'system')
  const systemDark = ref(window.matchMedia('(prefers-color-scheme: dark)').matches)

  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => {
    systemDark.value = e.matches
  })

  const isDark = computed(() => {
    if (mode.value === 'dark') return true
    if (mode.value === 'light') return false
    return systemDark.value
  })

  function setMode(m: ThemeMode) {
    mode.value = m
    localStorage.setItem('hw-theme', m)
  }

  return { mode, isDark, setMode }
})
