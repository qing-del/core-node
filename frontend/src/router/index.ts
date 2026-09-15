import { createRouter, createWebHistory } from 'vue-router'

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
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/user/dashboard'
        },
        {
          path: 'dashboard',
          name: 'UserDashboard',
          component: () => import('@/views/user/Dashboard.vue')
        },
        {
          path: 'notes',
          name: 'UserNotes',
          component: () => import('@/views/user/Notes.vue')
        },
        {
          path: 'notes/new',
          name: 'UserNoteCreate',
          component: () => import('@/views/user/NoteEdit.vue')
        },
        {
          path: 'notes/:noteId/edit',
          name: 'UserNoteEdit',
          component: () => import('@/views/user/NoteEdit.vue')
        },
        {
          path: 'notes/:noteId',
          name: 'UserNoteDetail',
          component: () => import('@/views/user/NoteDetail.vue')
        },
        {
          path: 'notes/:noteId/relations',
          name: 'UserNoteRelations',
          component: () => import('@/views/user/NoteRelation.vue')
        },
        {
          path: 'notes/:noteId/diff',
          name: 'UserNoteDiff',
          component: () => import('@/views/user/NoteDiff.vue')
        },
        {
          path: 'public-notes',
          name: 'UserPublicNotes',
          component: () => import('@/views/user/PublicNotes.vue')
        },
        {
          path: 'public-notes/:noteId',
          name: 'UserPublicNoteDetail',
          component: () => import('@/views/user/PublicNoteDetail.vue')
        },
        {
          path: 'topics',
          name: 'UserTopics',
          component: () => import('@/views/user/Topics.vue')
        },
        {
          path: 'tags',
          name: 'UserTags',
          component: () => import('@/views/user/Tags.vue')
        },
        {
          path: 'images',
          name: 'UserImages',
          component: () => import('@/views/user/Images.vue')
        },
        {
          path: 'audio',
          name: 'UserAudioTasks',
          component: () => import('@/views/user/AudioTasks.vue')
        },
        {
          path: 'profile',
          name: 'UserProfile',
          component: () => import('@/views/user/Profile.vue')
        }
      ]
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/admin/dashboard'
        },
        {
          path: 'dashboard',
          name: 'AdminDashboard',
          component: () => import('@/views/admin/Dashboard.vue')
        },
        {
          path: 'audit',
          name: 'AdminAudit',
          component: () => import('@/views/admin/Audit.vue')
        },
        {
          path: 'users',
          name: 'AdminUsers',
          component: () => import('@/views/admin/Users.vue')
        },
        {
          path: 'notes',
          name: 'AdminNotes',
          component: () => import('@/views/admin/Notes.vue')
        },
        {
          path: 'notes/:noteId',
          name: 'AdminNoteDetail',
          component: () => import('@/views/admin/NoteDetail.vue')
        },
        {
          path: 'topics',
          name: 'AdminTopics',
          component: () => import('@/views/admin/Topics.vue')
        },
        {
          path: 'tags',
          name: 'AdminTags',
          component: () => import('@/views/admin/Tags.vue')
        },
        {
          path: 'images',
          name: 'AdminImages',
          component: () => import('@/views/admin/Images.vue')
        },
        {
          path: 'email',
          name: 'AdminEmail',
          component: () => import('@/views/admin/Email.vue')
        },
        {
          path: 'audio',
          name: 'AdminAudioTasks',
          component: () => import('@/views/admin/AudioTasks.vue')
        },
        {
          path: 'profile',
          name: 'AdminProfile',
          component: () => import('@/views/user/Profile.vue')
        }
      ]
    }
  ]
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)

  if (requiresAuth && !token) {
    next('/login')
  } else if ((to.path === '/login' || to.path === '/') && token) {
    // 如果访问 login 或根路径且有 token，去 dashboard
    next('/dashboard')
  } else {
    next()
  }
})

export default router
