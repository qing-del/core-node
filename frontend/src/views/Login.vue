<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  Zap, ChevronRight, Link, FileDiff, User, Mail, Lock,
  ShieldCheck, Loader2, ArrowRight, CheckCircle2, BookOpen, Send, KeyRound
} from 'lucide-vue-next'

const router = useRouter()
const authStore = useAuthStore()

type AuthView = 'login' | 'register' | 'admin' | 'activation'

// 视图状态：login, register, admin, activation
const currentView = ref<AuthView>('login')
const showFeatures = ref(false)
const isAnimating = ref(false)

// 表单数据
const formData = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  activationCode: ''
})
const activationAccount = ref('')
const isResendingActivation = ref(false)
const isVerifyingActivationCode = ref(false)

// DOM refs
const cardRef = ref<HTMLElement | null>(null)
const formContentRef = ref<HTMLElement | null>(null)
const loaderIconRef = ref<HTMLElement | null>(null)
const submitIconRef = ref<HTMLElement | null>(null)
const submitTextRef = ref<HTMLElement | null>(null)
const toastTimeoutRef = ref<number | null>(null)

// Canvas 相关
const canvasRef = ref<HTMLCanvasElement | null>(null)
let animationFrameId: number
let particles: any[] = []
const mouse = { x: -1000, y: -1000, radius: 220 }

function handleMouseMove(e: MouseEvent) {
  mouse.x = e.clientX
  mouse.y = e.clientY
}

function handleMouseLeave() {
  mouse.x = -1000
  mouse.y = -1000
}

const viewConfig = computed(() => {
  switch (currentView.value) {
    case 'admin':
      return { title: 'PORTAL', bgClass: 'bg-rose-600', lineClass: 'h-1 w-8 bg-rose-500 rounded-full shadow-[0_0_10px_rgba(244,63,94,0.5)]' }
    case 'register':
      return { title: 'CREATE', bgClass: 'bg-purple-600', lineClass: 'h-1 w-12 bg-purple-500 rounded-full shadow-[0_0_10px_rgba(168,85,247,0.5)]' }
    case 'activation':
      return { title: 'VERIFY', bgClass: 'bg-amber-500', lineClass: 'h-1 w-12 bg-amber-400 rounded-full shadow-[0_0_10px_rgba(251,191,36,0.5)]' }
    default:
      return { title: 'ACCESS', bgClass: 'bg-indigo-600', lineClass: 'h-1 w-8 bg-indigo-500 rounded-full shadow-[0_0_10px_rgba(99,102,241,0.5)]' }
  }
})

// Antigravity-inspired monochrome particle field.
class MonoParticle {
  x: number
  y: number
  angle: number
  orbitRadius: number
  speed: number
  driftX: number
  driftY: number
  size: number
  color: string

  constructor(canvas: HTMLCanvasElement) {
    this.x = Math.random() * canvas.width
    this.y = Math.random() * canvas.height
    this.angle = Math.random() * Math.PI * 2
    this.orbitRadius = Math.random() * 1.6 + 0.4
    this.speed = (Math.random() * 0.006) + 0.002
    this.driftX = (Math.random() - 0.5) * 0.16
    this.driftY = (Math.random() - 0.5) * 0.16
    this.size = Math.random() * 1.6 + 0.45
    this.color = Math.random() > 0.72 ? '#111111' : (Math.random() > 0.5 ? '#5f5f58' : '#b8b8b2')
  }

  update() {
    this.angle += this.speed
    let vx = Math.cos(this.angle) * this.orbitRadius * 0.06 + this.driftX
    let vy = Math.sin(this.angle) * this.orbitRadius * 0.06 + this.driftY

    const dx = mouse.x - this.x
    const dy = mouse.y - this.y
    const dist = Math.sqrt(dx * dx + dy * dy)

    if (dist < mouse.radius && dist > 0) {
      const force = (mouse.radius - dist) / mouse.radius
      const nx = dx / dist
      const ny = dy / dist
      const tx = -ny
      const ty = nx
      const orbitSpeed = 0.58 * force
      vx += tx * orbitSpeed
      vy += ty * orbitSpeed
      const targetRadius = 92
      if (dist > targetRadius) {
        vx += nx * force * 0.18
      } else {
        vx -= nx * force * 0.16
      }
    }

    this.x += vx
    this.y += vy

    const canvas = canvasRef.value!
    if (this.x < -50) this.x = canvas.width + 50
    if (this.x > canvas.width + 50) this.x = -50
    if (this.y < -50) this.y = canvas.height + 50
    if (this.y > canvas.height + 50) this.y = -50
  }

  draw(ctx: CanvasRenderingContext2D) {
    ctx.beginPath()
    ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2)
    ctx.fillStyle = this.color
    ctx.globalAlpha = Math.sin(this.angle) * 0.12 + 0.34
    ctx.fill()
  }
}

function resizeCanvas() {
  const canvas = canvasRef.value
  if (!canvas) return
  const oldWidth = canvas.width || window.innerWidth
  const oldHeight = canvas.height || window.innerHeight

  canvas.width = window.innerWidth
  canvas.height = window.innerHeight

  if (particles.length > 0) {
    const ratioX = canvas.width / oldWidth
    const ratioY = canvas.height / oldHeight
    particles.forEach(p => {
      p.x *= ratioX
      p.y *= ratioY
    })
  }
}

function initParticles() {
  const canvas = canvasRef.value
  if (!canvas) return
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  particles = Array.from({ length: reducedMotion ? 36 : 118 }, () => new MonoParticle(canvas))
}

function animateCanvas() {
  const canvas = canvasRef.value
  const ctx = canvas?.getContext('2d')
  if (!canvas || !ctx) return

  ctx.fillStyle = 'rgba(247, 247, 245, 0.32)'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.globalCompositeOperation = 'source-over'

  particles.forEach(p => {
    if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      p.update()
    }
    p.draw(ctx)
  })

  for (let i = 0; i < particles.length; i++) {
    for (let j = i + 1; j < particles.length; j++) {
      const dx = particles[i].x - particles[j].x
      const dy = particles[i].y - particles[j].y
      const dist = Math.sqrt(dx * dx + dy * dy)
      if (dist < 118) {
        ctx.beginPath()
        ctx.strokeStyle = `rgba(17, 17, 17, ${0.075 * (1 - dist / 118)})`
        ctx.lineWidth = 0.5
        ctx.moveTo(particles[i].x, particles[i].y)
        ctx.lineTo(particles[j].x, particles[j].y)
        ctx.stroke()
      }
    }
  }
  ctx.globalCompositeOperation = 'source-over'
  animationFrameId = requestAnimationFrame(animateCanvas)
}

// 核心优化：重构的平滑伸缩动画
async function setView(newView: AuthView) {
  if (currentView.value === newView || isAnimating.value) return
  isAnimating.value = true
  const oldView = currentView.value
  const isPortalSwitch = (oldView === 'login' && newView === 'admin') || (oldView === 'admin' && newView === 'login')
  const contentDuration = isPortalSwitch ? 170 : 240
  const cardDuration = isPortalSwitch ? 420 : 520

  const card = cardRef.value
  const formContent = formContentRef.value
  if (!card || !formContent) {
    isAnimating.value = false
    return
  }

  // 1. 先将内部表单内容优雅淡出（缩小且透明）
  formContent.style.opacity = '0'
  formContent.style.filter = isPortalSwitch ? 'blur(4px)' : 'blur(8px)'
  formContent.style.transform = isPortalSwitch
    ? 'translateY(8px) scale(0.98)'
    : 'translateY(18px) scale(0.94) rotateX(6deg)'

  // 等待内容完全消失
  await new Promise(resolve => setTimeout(resolve, contentDuration))

  // 2. 锁定卡片当前尺寸，防止 Vue 更新 DOM 时发生闪烁
  const startHeight = card.offsetHeight
  const startWidth = card.offsetWidth
  card.style.height = startHeight + 'px'
  card.style.width = startWidth + 'px'
  card.style.transition = 'none'
  card.style.transform = 'translateZ(0)'
  card.style.willChange = 'width, height'

  // 3. 切换视图状态，Vue 会根据 v-if 插入/移除节点
  currentView.value = newView
  await nextTick()

  // 4. 重置样式，让容器根据新内容自然撑开，以便我们测量目标尺寸
  card.classList.remove('max-w-[380px]', 'max-w-[420px]', 'max-w-[440px]', 'max-w-[460px]')
  if (newView === 'admin') card.classList.add('max-w-[380px]')
  else if (newView === 'register') card.classList.add('max-w-[460px]')
  else if (newView === 'activation') card.classList.add('max-w-[440px]')
  else card.classList.add('max-w-[420px]')

  card.style.height = 'auto'
  card.style.width = '100%'
  const targetHeight = card.offsetHeight
  const targetWidth = card.offsetWidth

  // 5. 将卡片瞬间还原到原始尺寸，准备执行真实的左右收缩动画
  card.style.height = startHeight + 'px'
  card.style.width = startWidth + 'px'
  await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))

  // 6. 开启 CSS 平滑过渡。登录 ↔ 管理员重点优化横向收缩，保留真实宽度变化。
  const cardEase = isPortalSwitch ? 'cubic-bezier(0.16, 1, 0.3, 1)' : 'cubic-bezier(0.22, 1, 0.36, 1)'
  card.style.transition = `width ${cardDuration}ms ${cardEase}, height ${cardDuration}ms ${cardEase}`
  card.style.height = targetHeight + 'px'
  card.style.width = targetWidth + 'px'

  // 等待卡片尺寸伸缩完成
  await new Promise(resolve => setTimeout(resolve, cardDuration))

  // 7. 清理内联样式，交回给 Tailwind 的类名接管，保持响应式能力
  card.style.height = ''
  card.style.width = ''
  card.style.transition = ''
  card.style.willChange = ''
  card.style.transform = ''

  // 8. 卡片稳定后，淡入新表单内容
  formContent.style.opacity = '1'
  formContent.style.filter = 'blur(0)'
  formContent.style.transform = 'translateY(0) scale(1) rotateX(0)'

  // 等待内容完全浮现
  await new Promise(resolve => setTimeout(resolve, contentDuration))
  isAnimating.value = false
}

function toggleFeatures() {
  showFeatures.value = !showFeatures.value
  const panel = document.getElementById('features-panel')
  const iconContainer = document.getElementById('toggle-icon-container')
  if (!panel || !iconContainer) return

  if (showFeatures.value) {
    panel.classList.remove('feature-panel-hide')
    panel.classList.add('feature-panel-show')
    iconContainer.classList.add('bg-indigo-500', 'border-indigo-500', 'rotate-90', 'text-white', 'shadow-[0_0_15px_rgba(99,102,241,0.5)]')
  } else {
    panel.classList.remove('feature-panel-show')
    panel.classList.add('feature-panel-hide')
    iconContainer.classList.remove('bg-indigo-500', 'border-indigo-500', 'rotate-90', 'text-white', 'shadow-[0_0_15px_rgba(99,102,241,0.5)]')
  }
}

function showToast(msg: string, _type: 'success' | 'error' = 'success') {
  const toast = document.getElementById('toast-container')
  const toastMsg = document.getElementById('toast-msg')
  if (!toast || !toastMsg) return

  toastMsg.textContent = msg
  toast.classList.remove('translate-y-20', 'opacity-0', 'pointer-events-none')

  if (toastTimeoutRef.value) {
    clearTimeout(toastTimeoutRef.value)
  }
  toastTimeoutRef.value = window.setTimeout(() => {
    toast.classList.add('translate-y-20', 'opacity-0', 'pointer-events-none')
  }, 3000)
}

function enterGuestMode() {
  router.push('/guest/notes')
}

function fillActivationAccount() {
  activationAccount.value = formData.email || formData.username || activationAccount.value
}

function openActivationView() {
  fillActivationAccount()
  setView('activation')
}

function isAccountNotActivatedMessage(message: string) {
  return message.includes('未激活') || message.includes('激活账号')
}

async function handleResendActivation() {
  if (isResendingActivation.value) return

  fillActivationAccount()
  const account = activationAccount.value.trim()
  if (!account) {
    showToast('请先输入用户名或邮箱', 'error')
    return
  }

  isResendingActivation.value = true
  try {
    const message = await authStore.resendActivation(account)
    showToast(message || '激活邮件已重新发送，请查收邮箱', 'success')
  } catch (error: any) {
    showToast(error.message || '激活邮件发送失败', 'error')
  } finally {
    isResendingActivation.value = false
  }
}

async function handleVerifyActivationCode() {
  if (isVerifyingActivationCode.value) return

  const code = formData.activationCode.trim()
  if (!/^\d{6}$/.test(code)) {
    showToast('请输入 6 位数字激活码', 'error')
    return
  }

  isVerifyingActivationCode.value = true
  try {
    const message = await authStore.verifyActivationCode(code)
    showToast(message || '账号激活成功，请登录', 'success')
    formData.activationCode = ''
    setTimeout(() => {
      setView('login')
    }, 900)
  } catch (error: any) {
    showToast(error.message || '激活失败', 'error')
  } finally {
    isVerifyingActivationCode.value = false
  }
}

async function handleSubmit() {
  if (isAnimating.value) return

  const btn = document.getElementById('submit-btn') as HTMLButtonElement
  if (!btn) return

  btn.disabled = true
  if (submitTextRef.value) submitTextRef.value.classList.add('opacity-0')
  if (submitIconRef.value) submitIconRef.value.classList.add('hidden')
  if (loaderIconRef.value) loaderIconRef.value.classList.remove('hidden')

  try {
    if (currentView.value === 'admin') {
      await authStore.adminLogin({ username: formData.username, password: formData.password })
      showToast('安全认证通过...', 'success')
      setTimeout(() => {
        router.push('/admin')
      }, 1000)
    } else if (currentView.value === 'register') {
      const message = await authStore.register({
        username: formData.username,
        password: formData.password,
        confirmPassword: formData.confirmPassword,
        email: formData.email
      })
      activationAccount.value = formData.email || formData.username
      showToast(message || '注册成功，请查收邮箱激活账号', 'success')
      setTimeout(() => {
        setView('activation')
      }, 1200)
    } else {
      await authStore.login({ username: formData.username, password: formData.password })
      showToast('安全认证通过...', 'success')
      setTimeout(() => {
        router.push('/user')
      }, 1000)
    }
  } catch (error: any) {
    const message = error.message || '操作失败'
    if (currentView.value === 'login' && isAccountNotActivatedMessage(message)) {
      activationAccount.value = formData.username
      setTimeout(() => {
        setView('activation')
      }, 250)
    }
    showToast(message, 'error')
  } finally {
    btn.disabled = false
    if (submitTextRef.value) submitTextRef.value.classList.remove('opacity-0')
    if (submitIconRef.value) submitIconRef.value.classList.remove('hidden')
    if (loaderIconRef.value) loaderIconRef.value.classList.add('hidden')
  }
}

onMounted(() => {
  resizeCanvas()
  initParticles()
  animateCanvas()

  window.addEventListener('resize', resizeCanvas)
  window.addEventListener('mousemove', handleMouseMove)
  document.body.addEventListener('mouseleave', handleMouseLeave)
})

onUnmounted(() => {
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
  }
  if (toastTimeoutRef.value) {
    clearTimeout(toastTimeoutRef.value)
  }
  window.removeEventListener('resize', resizeCanvas)
  window.removeEventListener('mousemove', handleMouseMove)
  document.body.removeEventListener('mouseleave', handleMouseLeave)
})
</script>

<template>
  <div class="login-page min-h-screen w-full flex items-center justify-center overflow-hidden selection:bg-black/10 tracking-tight">

    <!-- Canvas 背景 -->
    <canvas ref="canvasRef" id="bg-canvas" class="fixed inset-0 z-0 bg-[var(--cn-bg)]"></canvas>

    <!-- 环境光晕 -->
    <div class="fixed top-[-20%] right-[-10%] w-[600px] h-[600px] bg-indigo-600/10 blur-[150px] rounded-full pointer-events-none"></div>
    <div class="fixed bottom-[-10%] left-[-10%] w-[400px] h-[400px] bg-purple-600/5 blur-[120px] rounded-full pointer-events-none"></div>

    <div class="container max-w-6xl w-full grid grid-cols-1 lg:grid-cols-12 gap-12 relative z-10 px-6 py-12">

      <!-- 左侧区块 -->
      <div class="lg:col-span-7 flex flex-col justify-center space-y-10">
        <div class="space-y-6 animate-[fade-in_1s_ease-out]">
          <div class="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-white/5 border border-white/10 backdrop-blur-md shadow-[inset_0_1px_1px_rgba(255,255,255,0.1)]">
            <Zap class="text-indigo-400 fill-indigo-400 w-3.5 h-3.5" />
            <span class="text-[10px] text-indigo-300 font-bold uppercase tracking-[0.2em]">Next-Gen Content Hub</span>
          </div>

          <h1 class="text-7xl md:text-[5.5rem] tracking-tighter leading-[0.85] text-white">
            <span class="font-light tracking-[0.05em] text-slate-300">CORE</span><br />
            <span class="font-black text-transparent bg-clip-text bg-[linear-gradient(110deg,#818cf8,45%,#e879f9,55%,#818cf8)] animate-shine drop-shadow-[0_0_25px_rgba(99,102,241,0.3)]">NODE</span>
          </h1>

          <p class="text-xl text-slate-400 font-medium max-w-md leading-relaxed border-l-2 border-indigo-500/50 pl-6">
            构建您的数字化资产，<br />
            沉浸式 Markdown 编辑与智能渲染。
          </p>
        </div>

        <div class="flex flex-col space-y-6">
          <!-- 展开特性按钮 -->
          <button id="toggle-features-btn" @click="toggleFeatures" class="group flex items-center space-x-3 text-xs text-slate-500 hover:text-white transition-all uppercase tracking-widest font-bold w-max">
            <div id="toggle-icon-container" class="flex items-center justify-center w-8 h-8 rounded-full border border-slate-800 transition-all duration-500 group-hover:border-indigo-500">
              <ChevronRight class="w-4 h-4" />
            </div>
            <span>查看架构核心特性</span>
          </button>

          <!-- 特性面板 -->
          <div id="features-panel" class="grid grid-cols-1 md:grid-cols-2 gap-3 transition-all duration-700 ease-in-out feature-panel-hide">
            <div class="p-4 rounded-2xl bg-white/[0.03] border border-white/[0.05] backdrop-blur-xl group hover:bg-white/[0.06] transition-all shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)]">
              <div class="flex items-center space-x-3 mb-2">
                <div class="text-indigo-400 p-1.5 bg-indigo-500/10 rounded-lg">
                  <Link class="w-4 h-4" />
                </div>
                <span class="text-white text-sm font-bold">支持 Obsidian 的双链机制</span>
              </div>
              <p class="text-slate-500 text-xs leading-relaxed">原生支持反向链接与网状引用，打破信息孤岛。</p>
            </div>

            <div class="p-4 rounded-2xl bg-white/[0.03] border border-white/[0.05] backdrop-blur-xl group hover:bg-white/[0.06] transition-all shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)]">
              <div class="flex items-center space-x-3 mb-2">
                <div class="text-indigo-400 p-1.5 bg-indigo-500/10 rounded-lg">
                  <FileDiff class="w-4 h-4" />
                </div>
                <span class="text-white text-sm font-bold">智能 Diff 引擎</span>
              </div>
              <p class="text-slate-500 text-xs leading-relaxed">变更追踪、实时对比与原子级修改确认。</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：液态玻璃伸缩卡片 -->
      <div class="lg:col-span-5 flex items-center justify-center relative">
        <div id="glass-card" ref="cardRef" class="w-full max-w-[420px] p-10 rounded-[3rem] bg-white/[0.02] border border-white/[0.08] backdrop-blur-[50px] shadow-[0_40px_100px_-20px_rgba(0,0,0,0.8),inset_0_1px_1px_rgba(255,255,255,0.2),inset_0_-1px_1px_rgba(0,0,0,0.4)] relative overflow-hidden group/card glass-card-morph">

          <!-- 流动光晕 -->
          <div class="absolute -top-16 -left-16 w-48 h-48 bg-indigo-500/20 blur-[50px] rounded-full mix-blend-screen animate-blob pointer-events-none opacity-50 group-hover/card:opacity-100 transition-opacity duration-700"></div>
          <div class="absolute -bottom-16 -right-16 w-48 h-48 bg-purple-500/20 blur-[50px] rounded-full mix-blend-screen animate-blob animation-delay-2000 pointer-events-none opacity-50 group-hover/card:opacity-100 transition-opacity duration-700"></div>

          <!-- 内部表单内容 -->
          <div id="form-content" ref="formContentRef" class="relative z-10 form-transition">
            <div class="mb-10 text-center">
              <h2 id="form-title" class="text-3xl font-black text-white tracking-tight">{{ viewConfig.title }}</h2>
              <div class="flex justify-center mt-3">
                <div id="form-title-line" :class="viewConfig.lineClass"></div>
              </div>
            </div>

            <template v-if="currentView !== 'activation'">
            <form id="auth-form" @submit.prevent="handleSubmit" class="flex flex-col">
              <!-- 用户名 -->
              <div class="relative group transition-all duration-300">
                <div class="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-500 group-focus-within:text-indigo-400 transition-colors">
                  <User class="w-[18px] h-[18px]" />
                </div>
                <input type="text" v-model="formData.username" name="username" placeholder="用户名" required class="w-full bg-black/20 border border-white/[0.05] shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] rounded-2xl py-4 pl-12 pr-4 outline-none focus:bg-black/40 focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all text-sm text-white placeholder:text-slate-600" />
              </div>

              <!-- 邮箱（通过 v-if 接管，不再使用繁重的 CSS 展开动画） -->
              <div v-if="currentView === 'register'" class="relative group transition-all duration-300 mt-4">
                <div class="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-500 group-focus-within:text-indigo-400 transition-colors">
                  <Mail class="w-[18px] h-[18px]" />
                </div>
                <input type="email" v-model="formData.email" name="email" placeholder="邮箱地址" required class="w-full bg-black/20 border border-white/[0.05] shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] rounded-2xl py-4 pl-12 pr-4 outline-none focus:bg-black/40 focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all text-sm text-white placeholder:text-slate-600" />
              </div>

              <!-- 密码 -->
              <div class="relative group transition-all duration-300 mt-4">
                <div class="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-500 group-focus-within:text-indigo-400 transition-colors">
                  <Lock class="w-[18px] h-[18px]" />
                </div>
                <input type="password" v-model="formData.password" name="password" placeholder="密码" required class="w-full bg-black/20 border border-white/[0.05] shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] rounded-2xl py-4 pl-12 pr-4 outline-none focus:bg-black/40 focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all text-sm text-white placeholder:text-slate-600" />
              </div>

              <!-- 确认密码 -->
              <div v-if="currentView === 'register'" class="relative group transition-all duration-300 mt-4">
                <div class="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-500 group-focus-within:text-indigo-400 transition-colors">
                  <ShieldCheck class="w-[18px] h-[18px]" />
                </div>
                <input type="password" v-model="formData.confirmPassword" name="confirmPassword" placeholder="确认密码" required class="w-full bg-black/20 border border-white/[0.05] shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] rounded-2xl py-4 pl-12 pr-4 outline-none focus:bg-black/40 focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all text-sm text-white placeholder:text-slate-600" />
              </div>

              <!-- 提交按钮 -->
              <button type="submit" id="submit-btn" class="group/btn relative w-full h-16 mt-8 overflow-hidden rounded-[1.5rem] transition-all hover:scale-[1.02] active:scale-[0.98] disabled:opacity-50 shadow-[0_10px_20px_-10px_rgba(99,102,241,0.6)]">
                <div :class="['absolute inset-0 transition-colors duration-500', currentView === 'admin' ? 'bg-rose-600 group-hover/btn:bg-rose-500' : currentView === 'register' ? 'bg-purple-600 group-hover/btn:bg-purple-500' : 'bg-indigo-600 group-hover/btn:bg-indigo-500']"></div>

                <!-- 高光反射 -->
                <div class="absolute inset-x-0 top-0 h-1/2 bg-gradient-to-b from-white/30 to-transparent opacity-60"></div>

                <!-- 扫光动画 -->
                <div class="absolute -inset-16 bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.4),transparent)] -rotate-45 translate-x-[-150%] group-hover/btn:translate-x-[150%] transition-transform duration-1000 ease-out"></div>

                <div class="relative flex items-center justify-center space-x-3 text-white font-black uppercase tracking-[0.2em] text-xs">
                  <Loader2 ref="loaderIconRef" id="loader-icon" class="w-5 h-5 animate-spin hidden" />
                  <span ref="submitTextRef" id="submit-text">{{ currentView === 'admin' ? '管理员核验' : currentView === 'register' ? '注册并加入' : '身份认证' }}</span>
                  <ArrowRight ref="submitIconRef" id="submit-icon" class="w-[18px] h-[18px] group-hover/btn:translate-x-1 transition-transform" />
                </div>
              </button>

            </form>

            <!-- 动态切换链接 -->
            <div class="mt-10 flex flex-col items-center space-y-6">
              <div class="flex items-center space-x-3 w-full opacity-20">
                <div class="h-px flex-1 bg-white"></div>
                <span class="text-[10px] text-white">SYSTEM</span>
                <div class="h-px flex-1 bg-white"></div>
              </div>

              <div id="switch-links" class="flex flex-col items-center space-y-4">
                <template v-if="currentView === 'admin'">
                  <button @click="setView('login')" type="button" class="group flex items-center space-x-2 text-xs text-rose-400 font-bold hover:text-rose-300 transition-all uppercase tracking-widest">
                    <div class="w-1.5 h-1.5 rounded-full bg-rose-500 group-hover:animate-ping"></div>
                    <span>退出管理入口</span>
                  </button>
                </template>
                <template v-else-if="currentView === 'register'">
                  <button @click="setView('login')" type="button" class="text-xs text-slate-400 font-bold hover:text-white transition-all uppercase tracking-wider underline underline-offset-8 decoration-slate-500/20 hover:decoration-slate-500">
                    返回身份认证
                  </button>
                </template>
                <template v-else>
                  <div class="grid grid-cols-2 gap-3 w-full">
                    <button @click="enterGuestMode" type="button" class="group flex h-11 items-center justify-center gap-2 rounded-2xl border border-cyan-400/25 bg-cyan-400/10 px-4 text-xs font-black uppercase tracking-[0.14em] text-cyan-200 shadow-[0_0_18px_rgba(34,211,238,0.08)] transition-all hover:border-cyan-300/60 hover:bg-cyan-400/15 hover:text-white hover:shadow-[0_0_22px_rgba(34,211,238,0.18)]">
                      <BookOpen class="h-4 w-4 transition-transform group-hover:-translate-y-0.5" />
                      <span>访客模式</span>
                    </button>
                    <button @click="setView('register')" type="button" class="group flex h-11 items-center justify-center gap-2 rounded-2xl border border-indigo-400/25 bg-indigo-400/10 px-4 text-xs font-black uppercase tracking-[0.14em] text-indigo-200 shadow-[0_0_18px_rgba(99,102,241,0.08)] transition-all hover:border-indigo-300/60 hover:bg-indigo-400/15 hover:text-white hover:shadow-[0_0_22px_rgba(99,102,241,0.18)]">
                      <User class="h-4 w-4 transition-transform group-hover:-translate-y-0.5" />
                      <span>创建账户</span>
                    </button>
                  </div>
                  <button @click="setView('admin')" type="button" class="text-[10px] text-slate-600 hover:text-slate-300 transition-colors uppercase tracking-[0.2em]">
                    管理控制台授权
                  </button>
                  <button @click="openActivationView" type="button" class="flex items-center gap-2 text-[10px] text-amber-500/70 hover:text-amber-300 transition-colors uppercase tracking-[0.2em]">
                    <KeyRound class="h-3.5 w-3.5" />
                    重新激活账号
                  </button>
                </template>
              </div>
            </div>
            </template>

            <template v-else>
              <div class="relative overflow-hidden rounded-3xl border border-amber-300/15 bg-amber-300/[0.03] p-5 shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)]">
                <div class="absolute -right-14 -top-14 h-32 w-32 rounded-full bg-amber-400/15 blur-3xl"></div>
                <div class="absolute -bottom-16 -left-10 h-32 w-32 rounded-full bg-emerald-400/10 blur-3xl"></div>

                <div class="relative flex items-start gap-4">
                  <div class="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl border border-amber-300/25 bg-amber-300/10 text-amber-200 shadow-[0_0_24px_rgba(251,191,36,0.12)]">
                    <KeyRound class="h-5 w-5" />
                  </div>
                  <div class="min-w-0">
                    <p class="text-sm font-black text-white">重新激活账号</p>
                    <p class="mt-1 text-xs leading-relaxed text-slate-500">输入用户名或邮箱重发激活邮件，也可以直接使用邮件中的 6 位激活码完成激活。</p>
                  </div>
                </div>
              </div>

              <form class="mt-6 flex flex-col" @submit.prevent="handleResendActivation">
                <div class="relative group">
                  <div class="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-500 group-focus-within:text-amber-300 transition-colors">
                    <User class="w-[18px] h-[18px]" />
                  </div>
                  <input
                    type="text"
                    v-model="activationAccount"
                    name="activationAccount"
                    placeholder="用户名或邮箱"
                    class="w-full bg-black/20 border border-white/[0.05] shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] rounded-2xl py-4 pl-12 pr-4 outline-none focus:bg-black/40 focus:border-amber-300/50 focus:ring-4 focus:ring-amber-300/10 transition-all text-sm text-white placeholder:text-slate-600"
                  />
                </div>

                <button
                  type="submit"
                  :disabled="isResendingActivation"
                  class="group/activate relative mt-4 flex h-14 w-full items-center justify-center gap-3 overflow-hidden rounded-[1.25rem] bg-amber-500 text-xs font-black uppercase tracking-[0.18em] text-slate-950 transition-all hover:scale-[1.015] hover:bg-amber-400 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <div class="absolute inset-x-0 top-0 h-1/2 bg-gradient-to-b from-white/35 to-transparent opacity-60"></div>
                  <Loader2 v-if="isResendingActivation" class="relative h-4 w-4 animate-spin" />
                  <Send v-else class="relative h-4 w-4 transition-transform group-hover/activate:translate-x-0.5" />
                  <span class="relative">重发激活邮件</span>
                </button>
              </form>

              <div class="my-6 flex items-center gap-3">
                <div class="h-px flex-1 bg-white/10"></div>
                <span class="text-[9px] font-black uppercase tracking-[0.22em] text-slate-600">OR CODE</span>
                <div class="h-px flex-1 bg-white/10"></div>
              </div>

              <form class="grid grid-cols-[1fr_auto] gap-3" @submit.prevent="handleVerifyActivationCode">
                <input
                  type="text"
                  v-model="formData.activationCode"
                  name="activationCode"
                  inputmode="numeric"
                  maxlength="6"
                  placeholder="6 位激活码"
                  class="min-w-0 bg-black/20 border border-white/[0.05] shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] rounded-2xl py-4 px-4 outline-none focus:bg-black/40 focus:border-emerald-300/50 focus:ring-4 focus:ring-emerald-300/10 transition-all text-sm text-white placeholder:text-slate-600"
                />
                <button
                  type="submit"
                  :disabled="isVerifyingActivationCode"
                  class="flex h-14 w-24 items-center justify-center rounded-2xl bg-emerald-500 text-xs font-black uppercase tracking-[0.12em] text-slate-950 transition-all hover:bg-emerald-400 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <Loader2 v-if="isVerifyingActivationCode" class="h-4 w-4 animate-spin" />
                  <span v-else>激活</span>
                </button>
              </form>

              <div class="mt-10 flex flex-col items-center space-y-6">
                <div class="flex items-center space-x-3 w-full opacity-20">
                  <div class="h-px flex-1 bg-white"></div>
                  <span class="text-[10px] text-white">SYSTEM</span>
                  <div class="h-px flex-1 bg-white"></div>
                </div>
                <button @click="setView('login')" type="button" class="text-xs text-slate-400 font-bold hover:text-white transition-all uppercase tracking-wider underline underline-offset-8 decoration-slate-500/20 hover:decoration-slate-500">
                  返回身份认证
                </button>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部装饰 -->
    <div class="fixed bottom-10 w-full px-12 flex justify-between items-end select-none pointer-events-none">
      <div class="flex flex-col">
        <span class="text-[9px] text-slate-700 uppercase tracking-[0.3em] font-black italic">CoreNode Hybrid Infrastructure</span>
        <span class="text-[8px] text-slate-800 uppercase tracking-[0.5em] mt-1">Distributed Sync Protocol Active</span>
      </div>
      <div class="flex items-center space-x-6">
        <div class="flex flex-col items-end">
          <span class="text-[9px] text-slate-700 font-bold tracking-widest uppercase">System Core v0.0.1</span>
          <span class="text-[8px] text-emerald-900 uppercase font-black tracking-tighter">Status: Optimal</span>
        </div>
        <div class="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-pulse shadow-[0_0_10px_rgba(99,102,241,1)]"></div>
      </div>
    </div>

    <!-- 消息通知 (Toast) -->
    <div id="toast-container" class="fixed bottom-12 right-12 px-8 py-5 rounded-[2.5rem] backdrop-blur-3xl shadow-[0_20px_50px_-10px_rgba(0,0,0,0.5),inset_0_1px_1px_rgba(255,255,255,0.1)] border-l-4 transition-all duration-500 z-50 transform translate-y-20 opacity-0 pointer-events-none bg-emerald-500/10 border-emerald-500/50 text-emerald-400">
      <div class="flex items-center space-x-4">
        <div id="toast-icon-wrapper" class="p-2 rounded-xl bg-emerald-500/20 shadow-[inset_0_1px_1px_rgba(255,255,255,0.2)]">
          <CheckCircle2 id="toast-icon" class="w-5 h-5" />
        </div>
        <div class="flex flex-col">
          <span id="toast-title" class="text-[9px] font-black uppercase tracking-[0.2em] opacity-40 leading-none mb-1">Authorization</span>
          <span id="toast-msg" class="text-sm font-bold leading-none">认证成功</span>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
/* 动画定义 */
@keyframes shine {
  to { background-position: 200% center; }
}
.animate-shine {
  background-size: 200% auto;
  animation: shine 4s linear infinite;
}

@keyframes blob {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(20px, -30px) scale(1.1); }
  66% { transform: translate(-20px, 20px) scale(0.9); }
}
.animate-blob {
  animation: blob 8s infinite cubic-bezier(0.4, 0, 0.2, 1);
}
.animation-delay-2000 {
  animation-delay: 2s;
}

/* 优化后的表单内容淡入淡出速度 (更快更顺畅) */
.form-transition {
  transition: opacity 0.2s cubic-bezier(0.4, 0, 0.2, 1),
              filter 0.26s cubic-bezier(0.22, 1, 0.36, 1),
              transform 0.26s cubic-bezier(0.22, 1, 0.36, 1);
  transform-origin: center top;
  transform-style: preserve-3d;
}

/* 卡片容器：移除过渡样式，由 JS 接管保证平滑 */
.glass-card-morph {
  /* JS 内部会自动注入宽高动画，这里仅保留基础样式 */
  box-shadow: 0 40px 100px -20px rgba(0,0,0,0.8), inset 0 1px 1px rgba(255,255,255,0.2), inset 0 -1px 1px rgba(0,0,0,0.4);
}

/* 特性面板 */
.feature-panel-hide {
  max-height: 0;
  opacity: 0;
  transform: translateY(-10px);
  pointer-events: none;
}
.feature-panel-show {
  max-height: 400px;
  opacity: 1;
  transform: translateY(0);
}

.login-page {
  background:
    radial-gradient(circle at 12% 18%, rgba(255, 255, 255, 0.92), transparent 28%),
    linear-gradient(135deg, #fbfbfa 0%, #f3f3f1 100%);
  color: var(--cn-text);
}

#bg-canvas {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(247, 247, 245, 0.92)),
    var(--cn-bg) !important;
}

.login-page :where(h1, h2, h3, p, span, button) {
  text-shadow: none !important;
}

.login-page h1,
.login-page h1 span,
#form-title,
#form-title span {
  background: none !important;
  color: var(--cn-text) !important;
  filter: none !important;
  -webkit-text-fill-color: currentColor;
}

.login-page h1 {
  letter-spacing: -0.04em;
}

.login-page h1 span:first-child {
  color: var(--cn-text-soft) !important;
  font-weight: 420 !important;
}

.login-page p {
  border-color: var(--cn-border-strong) !important;
  color: var(--cn-text-soft) !important;
}

.login-page [class*="bg-indigo-"],
.login-page [class*="bg-purple-"],
.login-page [class*="bg-cyan-"],
.login-page [class*="bg-amber-"],
.login-page [class*="bg-emerald-"],
.login-page [class*="bg-rose-"] {
  box-shadow: none !important;
}

.login-page > .fixed:not(#toast-container):not(#bg-canvas) {
  opacity: 0 !important;
}

.login-page .container {
  max-width: 1120px;
}

.login-page .inline-flex {
  border-color: var(--cn-border) !important;
  background: rgba(255, 255, 255, 0.72) !important;
  color: var(--cn-text-soft) !important;
  box-shadow: none !important;
  backdrop-filter: blur(10px);
}

.login-page .inline-flex svg {
  color: var(--cn-text) !important;
  fill: none !important;
}

#toggle-features-btn {
  color: var(--cn-text-muted) !important;
}

#toggle-features-btn:hover {
  color: var(--cn-text) !important;
}

#toggle-icon-container {
  border-color: var(--cn-border) !important;
  background: var(--cn-surface) !important;
  color: var(--cn-text-muted) !important;
}

#features-panel > div,
#glass-card {
  border: 1px solid var(--cn-border) !important;
  background: rgba(255, 255, 255, 0.86) !important;
  box-shadow: var(--cn-shadow-sm) !important;
  backdrop-filter: blur(18px) !important;
  -webkit-backdrop-filter: blur(18px) !important;
}

#features-panel > div {
  border-radius: var(--cn-radius-lg) !important;
}

#features-panel :where(span, p, svg) {
  color: var(--cn-text-soft) !important;
}

#features-panel :where(span.text-white, .font-bold) {
  color: var(--cn-text) !important;
}

#glass-card {
  border-radius: 24px !important;
  padding: 36px !important;
}

#glass-card > .absolute {
  display: none !important;
}

#form-title {
  font-size: 28px !important;
  font-weight: 760 !important;
  letter-spacing: -0.03em !important;
}

#form-title-line {
  height: 2px !important;
  width: 32px !important;
  border-radius: 999px !important;
  background: var(--cn-accent) !important;
  box-shadow: none !important;
}

#auth-form input,
.login-page input {
  height: 52px;
  border: 1px solid var(--cn-border) !important;
  border-radius: var(--cn-radius-md) !important;
  background: rgba(255, 255, 255, 0.9) !important;
  color: var(--cn-text) !important;
  box-shadow: none !important;
}

#auth-form input:focus,
.login-page input:focus {
  border-color: var(--cn-accent) !important;
  background: var(--cn-surface) !important;
  box-shadow: 0 0 0 3px rgba(17, 17, 17, 0.08) !important;
}

#auth-form input::placeholder,
.login-page input::placeholder {
  color: var(--cn-text-faint) !important;
}

.login-page form .absolute.inset-y-0 {
  color: var(--cn-text-muted) !important;
}

#submit-btn,
.login-page form button[type="submit"] {
  height: 52px !important;
  border-radius: var(--cn-radius-md) !important;
  box-shadow: none !important;
  transform: none !important;
  color: var(--cn-text-inverse) !important;
}

#submit-btn > .absolute.inset-0,
.login-page form button[type="submit"] {
  background: var(--cn-accent) !important;
}

#submit-btn > .absolute:not(.inset-0),
.login-page form button[type="submit"] > .absolute {
  display: none !important;
}

#submit-btn :where(span, svg),
.login-page form button[type="submit"] :where(span, svg) {
  color: var(--cn-text-inverse) !important;
}

#switch-links button,
.login-page button {
  box-shadow: none !important;
}

#switch-links > button,
#switch-links button:not(.grid button) {
  color: var(--cn-text-soft) !important;
}

#switch-links > button:hover,
#switch-links button:not(.grid button):hover {
  color: var(--cn-text) !important;
}

#switch-links .grid button {
  border: 1px solid var(--cn-border) !important;
  border-radius: var(--cn-radius-md) !important;
  background: var(--cn-surface) !important;
  color: var(--cn-text) !important;
}

#switch-links .grid button:hover {
  border-color: var(--cn-border-strong) !important;
  background: var(--cn-surface-muted) !important;
}

.login-page :where(.text-white, .text-slate-100, .text-slate-200, .text-slate-300, .text-cyan-200, .text-indigo-200, .text-amber-200) {
  color: var(--cn-text) !important;
}

.login-page :where(.text-slate-400, .text-slate-500, .text-slate-600, .text-slate-700, .text-slate-800, .text-indigo-300, .text-purple-300, .text-cyan-300, .text-amber-300, .text-emerald-300) {
  color: var(--cn-text-soft) !important;
}

.login-page input {
  -webkit-text-fill-color: currentColor;
}

.login-page form button[type="submit"],
.login-page form button[type="submit"] :where(span, svg),
#submit-btn,
#submit-btn :where(span, svg) {
  color: var(--cn-text-inverse) !important;
}

.login-page .fixed.bottom-10 {
  color: var(--cn-text-faint) !important;
  opacity: 0.72;
}

#toast-container {
  right: 24px;
  bottom: 24px;
  border: 1px solid var(--cn-border) !important;
  border-left: 4px solid var(--cn-success) !important;
  border-radius: var(--cn-radius-xl) !important;
  background: rgba(255, 255, 255, 0.96) !important;
  color: var(--cn-text) !important;
  box-shadow: var(--cn-shadow-md) !important;
}

#toast-container :where(span, svg) {
  color: var(--cn-text) !important;
}

@media (max-width: 1024px) {
  .login-page .container {
    gap: 32px;
  }

  .login-page h1 {
    font-size: clamp(3.5rem, 16vw, 5rem);
  }

  .login-page .fixed.bottom-10 {
    display: none;
  }
}
</style>
