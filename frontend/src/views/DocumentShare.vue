<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CircleAlert, FileText, Loader2, LogIn, ShieldCheck } from 'lucide-vue-next'
import { documentApi } from '@/api/documents'
import { hasGrantedScope, readAuthSession, clearStoredAuth } from '@/utils/authSession'
import {
  isValidShareLinkCode,
  shareDocumentRouteFromId,
  shareLoginLocation
} from '@/utils/shareLink'

const route = useRoute()
const router = useRouter()

type ShareState = 'checking' | 'redeeming' | 'redirecting' | 'error'

const state = ref<ShareState>('checking')
const message = ref('正在验证分享链接…')
let redemptionAttempt = 0
const pendingCodes = new Set<string>()

function responseStatus(cause: unknown): number | null {
  if (!cause || typeof cause !== 'object') return null
  const response = (cause as { response?: { status?: unknown } }).response
  return typeof response?.status === 'number' ? response.status : null
}

/** 分享码、Axios 原文和后端实现细节都不能进入页面提示。 */
function shareFailureMessage(cause: unknown): string {
  const status = responseStatus(cause)
  if (status === 401) return '登录状态已失效，请重新登录后再试'
  if (status === 403) return '当前账号没有兑换此分享链接所需的文档权限'
  return '分享链接无效、已过期、已撤销或已达到使用次数'
}

function goToLogin(): void {
  void router.replace(shareLoginLocation(route.path))
}

async function redeemCurrentShare(): Promise<void> {
  const rawCode = route.params.code
  const code = typeof rawCode === 'string' ? rawCode : null

  if (!isValidShareLinkCode(code)) {
    state.value = 'error'
    message.value = '分享链接无效或格式不正确'
    return
  }
  // Keep one in-flight redeem request per opaque code even if the route/view
  // is re-entered before the first response has settled.
  if (pendingCodes.has(code)) return

  const session = readAuthSession()
  if (!session.accessToken) {
    goToLogin()
    return
  }
  if (session.clientId !== 'user') {
    clearStoredAuth()
    state.value = 'error'
    message.value = '分享链接需要使用用户账号登录'
    goToLogin()
    return
  }
  if (!hasGrantedScope(session.scopes, 'document:read') && !hasGrantedScope(session.scopes, 'document:write')) {
    state.value = 'error'
    message.value = '当前账号没有文档访问权限'
    return
  }

  pendingCodes.add(code)
  const attempt = ++redemptionAttempt
  state.value = 'checking'
  message.value = '正在验证分享链接…'
  try {
    state.value = 'redeeming'
    message.value = '正在获取文档权限…'
    const result = await documentApi.redeemShareLink(code)
    if (attempt !== redemptionAttempt) return

    state.value = 'redirecting'
    message.value = '权限已确认，正在打开文档…'
    // documentId is accepted only from the validated redeem response; the
    // opaque code is never decoded or used to infer a document ID.
    await router.replace(shareDocumentRouteFromId(result.documentId))
  } catch (cause) {
    if (attempt !== redemptionAttempt) return
    state.value = 'error'
    message.value = shareFailureMessage(cause)
  } finally {
    pendingCodes.delete(code)
  }
}

watch(() => route.params.code, () => { void redeemCurrentShare() }, { immediate: true })
onUnmounted(() => { redemptionAttempt += 1 })
</script>

<template>
  <main class="share-landing-page">
    <section class="share-landing-card" aria-live="polite">
      <div class="share-landing-icon" :class="{ 'is-error': state === 'error' }">
        <Loader2 v-if="state === 'checking' || state === 'redeeming'" class="h-8 w-8 animate-spin" />
        <ShieldCheck v-else-if="state === 'redirecting'" class="h-8 w-8" />
        <CircleAlert v-else class="h-8 w-8" />
      </div>
      <div class="share-landing-copy">
        <span class="share-landing-eyebrow">DOCUMENT SHARE</span>
        <h1>文档分享</h1>
        <p>{{ message }}</p>
      </div>
      <div v-if="state === 'checking' || state === 'redeeming' || state === 'redirecting'" class="share-landing-note">
        <FileText class="h-4 w-4" /> 分享链接只授予文档级权限，不会改变账号的全局权限。
      </div>
      <button v-if="state === 'error'" class="share-landing-login-button" type="button" @click="goToLogin">
        <LogIn class="h-4 w-4" /> 返回登录
      </button>
    </section>
  </main>
</template>

<style scoped>
.share-landing-page { display: flex; min-height: 72vh; align-items: center; justify-content: center; padding: 24px; }
.share-landing-card { display: grid; width: min(100%, 500px); justify-items: center; gap: 18px; border: 1px solid var(--cn-border); border-radius: var(--cn-radius-lg); background: var(--cn-surface); box-shadow: var(--cn-shadow-sm); padding: 42px 32px; text-align: center; }
.share-landing-icon { display: flex; height: 72px; width: 72px; align-items: center; justify-content: center; border: 1px solid color-mix(in srgb, var(--cn-accent) 40%, var(--cn-border)); border-radius: 22px; background: color-mix(in srgb, var(--cn-accent) 10%, var(--cn-surface)); color: var(--cn-accent); }
.share-landing-icon.is-error { border-color: color-mix(in srgb, var(--cn-danger) 40%, var(--cn-border)); background: color-mix(in srgb, var(--cn-danger) 8%, var(--cn-surface)); color: var(--cn-danger); }
.share-landing-copy { display: grid; gap: 7px; }
.share-landing-eyebrow { color: var(--cn-text-muted); font-size: 10px; font-weight: 800; letter-spacing: .18em; }
.share-landing-copy h1 { margin: 0; color: var(--cn-text); font-size: 24px; font-weight: 800; }
.share-landing-copy p { margin: 0; color: var(--cn-text-soft); font-size: 13px; line-height: 1.7; }
.share-landing-note { display: flex; align-items: center; gap: 7px; color: var(--cn-text-muted); font-size: 11px; line-height: 1.55; }
.share-landing-login-button { display: inline-flex; align-items: center; gap: 7px; border: 1px solid var(--cn-accent); border-radius: var(--cn-radius-sm); background: var(--cn-accent); color: #fff; padding: 10px 15px; font-size: 12px; font-weight: 750; }
@media (max-width: 640px) { .share-landing-page { padding: 16px; } .share-landing-card { padding: 32px 20px; } }
</style>
