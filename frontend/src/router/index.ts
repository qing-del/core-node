import { createRouter, createWebHistory } from 'vue-router'
import { toastError } from '@/utils/feedback'
import {
  clearStoredAuth,
  hasAllGrantedScopes,
  hasGrantedScope,
  readAuthSession,
  type AuthClientId
} from '@/utils/authSession'
import { sanitizeShareRedirect, shareLinkCodeFromPath } from '@/utils/shareLink'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    clientId?: AuthClientId
    requiredScopes?: readonly string[]
    anyRequiredScopes?: readonly string[]
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
      path: '/share/documents/:code',
      name: 'DocumentShare',
      component: () => import('@/views/DocumentShare.vue'),
      // 分享页本身不授予权限；只有已登录 user client 且拥有文档 scope 才能调用 redeem。
      meta: {
        requiresAuth: true,
        clientId: 'user',
        anyRequiredScopes: ['document:read', 'document:write']
      }
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
          // 文档列表本轮仍只需要 read scope；编辑已有文档另行支持 read/write 任一 scope。
          path: 'documents',
          name: 'UserDocuments',
          component: () => import('@/views/user/Documents.vue'),
          meta: { requiredScopes: ['document:read'] }
        },
        {
          path: 'documents/new',
          name: 'UserDocumentCreate',
          component: () => import('@/views/user/DocumentEditor.vue'),
          meta: { requiredScopes: ['document:write'] }
        },
        {
          path: 'documents/:documentId',
          name: 'UserDocumentEditor',
          component: () => import('@/views/user/DocumentEditor.vue'),
          meta: { anyRequiredScopes: ['document:read', 'document:write'] }
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
          path: 'documents',
          name: 'AdminDocuments',
          component: () => import('@/views/admin/Documents.vue'),
          meta: { requiredScopes: ['document:read'] }
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
  const isShareRoute = to.name === 'DocumentShare'
  const shareRedirect = sanitizeShareRedirect(to.path)

  // Malformed codes are rendered as a neutral invalid-link state without ever
  // being sent to an API or used to build a redirect.
  if (isShareRoute && !shareLinkCodeFromPath(to.path)) return true

  if (!session.accessToken) {
    return requiresAuth
      ? shareRedirect ? { path: '/login', query: { redirect: shareRedirect } } : '/login'
      : true
  }

  if (!session.clientId) {
    clearStoredAuth()
    return shareRedirect ? { path: '/login', query: { redirect: shareRedirect } } : '/login'
  }

  if (to.path === '/login' || to.path === '/') {
    if (to.path === '/login' && session.clientId === 'user') {
      const redirect = sanitizeShareRedirect(to.query.redirect)
      if (redirect) return redirect
    }
    return session.clientId === 'admin' ? '/admin' : '/user'
  }

  if (isShareRoute && session.clientId !== 'user') {
    // An admin token must never reach redeem. Clear the client boundary and
    // restart through the user login entry while preserving only this route.
    clearStoredAuth()
    return shareRedirect ? { path: '/login', query: { redirect: shareRedirect } } : '/login'
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

  const anyRequiredScopes = [...new Set(to.matched.flatMap(record => record.meta.anyRequiredScopes ?? []))]
  if (anyRequiredScopes.length > 0 && !anyRequiredScopes.some(scope => hasGrantedScope(session.scopes, scope))) {
    toastError('当前账号没有访问此页面的权限')
    return false
  }
})

export default router
