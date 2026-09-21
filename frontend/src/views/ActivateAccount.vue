<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { Loader2, CheckCircle2, XCircle } from 'lucide-vue-next'
import { sanitizeShareRedirect } from '@/utils/shareLink'

const route = useRoute()
const router = useRouter()

const state = ref<'loading' | 'success' | 'error'>('loading')
const message = ref('正在激活账号...')

function loginLocation() {
  const redirect = sanitizeShareRedirect(route.query.redirect)
  return redirect ? { path: '/login', query: { redirect } } : '/login'
}

async function activate() {
  const token = route.params.token as string
  if (!token) {
    state.value = 'error'
    message.value = '未提供激活令牌'
    return
  }

  try {
    const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
    await axios.get(`${baseURL}/user/user/active/${token}`, {
      headers: { activeToken: token }
    })
    state.value = 'success'
    message.value = '账号激活成功，3 秒后跳转到登录页...'
    setTimeout(() => {
      void router.replace(loginLocation())
    }, 3000)
  } catch {
    state.value = 'error'
    message.value = '激活失败，链接可能已过期或账号已激活'
  }
}

onMounted(() => {
  activate()
})
</script>

<template>
  <div class="activation-page flex min-h-screen items-center justify-center overflow-hidden bg-[var(--cn-bg)] p-8 text-[var(--cn-text)] selection:bg-black/10">
    <div class="activation-grid" aria-hidden="true"></div>

    <div class="activation-card cn-fade-up">
      <div class="flex justify-center">
        <div :class="[
          'status-icon',
          state === 'loading'
            ? 'status-icon-loading'
            : state === 'success'
              ? 'status-icon-success'
              : 'status-icon-error'
        ]">
          <Loader2 v-if="state === 'loading'" class="h-9 w-9 animate-spin" />
          <CheckCircle2 v-else-if="state === 'success'" class="h-9 w-9" />
          <XCircle v-else class="h-9 w-9" />
        </div>
      </div>

      <div class="text-center">
        <h1 class="text-xl font-bold tracking-[0.12em] text-[var(--cn-text)]">CORE<span class="font-normal"> NODE</span></h1>
        <p class="mt-2 text-[10px] font-semibold uppercase tracking-[0.22em] text-[var(--cn-text-muted)]">Account Activation</p>
      </div>

      <p :class="[
        'text-center text-sm leading-7',
        state === 'loading' ? 'text-[var(--cn-text-soft)]' : state === 'success' ? 'text-[var(--cn-success)]' : 'text-[var(--cn-danger)]'
      ]">
        {{ message }}
      </p>

      <button
        v-if="state === 'error'"
        type="button"
        class="cn-btn cn-btn-primary mx-auto px-5"
        @click="router.replace(loginLocation())"
      >
        前往登录页
      </button>
    </div>
  </div>
</template>

<style scoped>
.activation-page {
  position: relative;
  background:
    radial-gradient(circle at 20% 10%, rgba(255, 255, 255, 0.95), transparent 30%),
    linear-gradient(135deg, #fbfbfa 0%, #f2f2ef 100%);
}

.activation-grid {
  position: fixed;
  inset: 0;
  background-image:
    linear-gradient(rgba(17, 17, 17, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(17, 17, 17, 0.035) 1px, transparent 1px);
  background-size: 36px 36px;
  mask-image: linear-gradient(to bottom, black, transparent 76%);
  pointer-events: none;
}

.activation-card {
  position: relative;
  z-index: 1;
  display: flex;
  width: min(100%, 420px);
  flex-direction: column;
  gap: 24px;
  border: 1px solid var(--cn-border);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.88);
  padding: 40px;
  box-shadow: var(--cn-shadow-sm);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.status-icon {
  display: flex;
  height: 72px;
  width: 72px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--cn-border);
  border-radius: 18px;
  background: var(--cn-surface);
}

.status-icon-loading {
  color: var(--cn-text);
}

.status-icon-success {
  color: var(--cn-success);
}

.status-icon-error {
  color: var(--cn-danger);
}
</style>
