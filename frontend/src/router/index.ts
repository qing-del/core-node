import { createRouter, createWebHistory } from 'vue-router'
import { toastError } from '@/utils/feedback'
import {
  clearStoredAuth,
  hasAllGrantedScopes,
  readAuthSession,
  type AuthClientId
} from '@/utils/authSession'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    clientId?: AuthClientId
    requiredScopes?: readonly string[]
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/activate/:token',
      name: 'ActivateAccount',
      component: () => import('@/views/ActivateAccount.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/guest',
      component: () => import('@/layouts/GuestLayout.vue'),
      meta: { requiresAuth: false },
      children: [
        {
          path: '',
          redirect: '/guest/notes'
        },
        {
          path: 'notes',
          name: 'GuestNotes',
          component: () => import('@/views/guest/Notes.vue')
        },
        {
          path: 'notes/:noteId',
          name: 'GuestNoteDetail',
          component: () => import('@/views/guest/NoteDetail.vue')
        }
      ]
    },
    {
      path: '/dashboard',
      redirect: '/user/dashboard'
    },
    {
      path: '/',
      redirect: '/login'
    },
    {
      path: '/user',
      component: () => import('@/layouts/UserLayout.vue'),
      meta: { requiresAuth: true, clientId: 'user' },
      children: [
        {
          path: '',
          redirect: '/user/dashboard'
        },
        {
          path: 'dashboard',
          name: 'UserDashboard',
          component: () => import('@/views/user/Dashboard.vue'),
          meta: { requiredScopes: ['account:read', 'note:read', 'media:read'] }
        },
        {
          path: 'notes',
          name: 'UserNotes',
          component: () => import('@/views/user/Notes.vue'),
          meta: { requiredScopes: ['note:read'] }
        },
        {
          path: 'notes/new',
          name: 'UserNoteCreate',
          component: () => import('@/views/user/NoteEdit.vue'),
          meta: { requiredScopes: ['note:read'] }
        },
        {
          path: 'notes/:noteId/edit',
          name: 'UserNoteEdit',
          component: () => import('@/views/user/NoteEdit.vue'),
          meta: { requiredScopes: ['note:read'] }
        },
        {
          path: 'notes/:noteId',
          name: 'UserNoteDetail',
          component: () => import('@/views/user/NoteDetail.vue'),
          meta: { requiredScopes: ['note:read'] }
        },
        {
          path: 'notes/:noteId/relations',
          name: 'UserNoteRelations',
          component: () => import('@/views/user/NoteRelation.vue'),
          meta: { requiredScopes: ['note:read'] }
        },
        {
          path: 'notes/:noteId/diff',
          name: 'UserNoteDiff',
          component: () => import('@/views/user/NoteDiff.vue'),
          meta: { requiredScopes: ['note:read'] }
        },
        {
          path: 'public-notes',
          name: 'UserPublicNotes',
          component: () => import('@/views/user/PublicNotes.vue'),
          meta: { requiredScopes: ['note:read'] }
        },
        {
          path: 'public-notes/:noteId',
          name: 'UserPublicNoteDetail',
          component: () => import('@/views/user/PublicNoteDetail.vue'),
          meta: { requiredScopes: ['note:read'] }
        },
        {
          path: 'topics',
          name: 'UserTopics',
          component: () => import('@/views/user/Topics.vue'),
          meta: { requiredScopes: ['note:read'] }
        },
        {
          path: 'tags',
          name: 'UserTags',
          component: () => import('@/views/user/Tags.vue'),
          meta: { requiredScopes: ['note:read'] }
        },
        {
          path: 'images',
          name: 'UserImages',
          component: () => import('@/views/user/Images.vue'),
          meta: { requiredScopes: ['media:read'] }
        },
        {
          path: 'audio',
          name: 'UserAudioTasks',
          component: () => import('@/views/user/AudioTasks.vue'),
          meta: { requiredScopes: ['audio:read'] }
        },
        {
          path: 'profile',
          name: 'UserProfile',
          component: () => import('@/views/user/Profile.vue'),
          meta: { requiredScopes: ['account:read'] }
        }
      ]
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true, clientId: 'admin' },
      children: [
        {
          path: '',
          redirect: '/admin/dashboard'
        },
        {
          path: 'dashboard',
          name: 'AdminDashboard',
          component: () => import('@/views/admin/Dashboard.vue'),
          meta: { requiredScopes: ['account:read'] }
        },
        {
          path: 'audit',
          name: 'AdminAudit',
          component: () => import('@/views/admin/Audit.vue'),
          meta: { requiredScopes: ['audit:read'] }
        },
        {
          path: 'users',
          name: 'AdminUsers',
          component: () => import('@/views/admin/Users.vue'),
          meta: { requiredScopes: ['account:read'] }
        },
        {
          path: 'notes',
          name: 'AdminNotes',
          component: () => import('@/views/admin/Notes.vue'),
          meta: { requiredScopes: ['note:read'] }
        },
        {
          path: 'notes/:noteId',
          name: 'AdminNoteDetail',
          component: () => import('@/views/admin/NoteDetail.vue'),
          meta: { requiredScopes: ['note:read'] }
        },
        {
          path: 'topics',
          name: 'AdminTopics',
          component: () => import('@/views/admin/Topics.vue'),
          meta: { requiredScopes: ['note:read'] }
        },
        {
          path: 'tags',
          name: 'AdminTags',
          component: () => import('@/views/admin/Tags.vue'),
          meta: { requiredScopes: ['note:read'] }
        },
        {
          path: 'images',
          name: 'AdminImages',
          component: () => import('@/views/admin/Images.vue'),
          meta: { requiredScopes: ['media:read'] }
        },
        {
          path: 'email',
          name: 'AdminEmail',
          component: () => import('@/views/admin/Email.vue'),
          meta: { requiredScopes: ['account:manage'] }
        },
        {
          path: 'audio',
          name: 'AdminAudioTasks',
          component: () => import('@/views/admin/AudioTasks.vue'),
          meta: { requiredScopes: ['audio:read'] }
        },
        {
          path: 'profile',
          name: 'AdminProfile',
          component: () => import('@/views/user/Profile.vue'),
          meta: { requiredScopes: ['account:read'] }
        }
      ]
    }
  ]
})

// 路由守卫：客户端入口和业务页准入都依据存储的认证 client/scope，不依据角色名称。
router.beforeEach((to) => {
  const session = readAuthSession()
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)

  if (!session.accessToken) {
    return requiresAuth ? '/login' : true
  }

  if (!session.clientId) {
    clearStoredAuth()
    return '/login'
  }

  if (to.path === '/login' || to.path === '/') {
    return session.clientId === 'admin' ? '/admin' : '/user'
  }

  if (!requiresAuth) return true

  const requiredClientId = to.matched
    .map(record => record.meta.clientId)
    .find((clientId): clientId is AuthClientId => Boolean(clientId))
  if (requiredClientId && session.clientId !== requiredClientId) {
    toastError('当前登录入口无权访问')
    return session.clientId === 'admin' ? '/admin' : '/user'
  }

  const requiredScopes = [...new Set(to.matched.flatMap(record => record.meta.requiredScopes ?? []))]
  if (!hasAllGrantedScopes(session.scopes, requiredScopes)) {
    toastError('当前账号没有访问此页面的权限')
    return false
  }
})

export default router
