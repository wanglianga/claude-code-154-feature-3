import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: () => import('./views/LoginView.vue'), meta: { public: true } },
  { path: '/public', component: () => import('./views/PublicListView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('./layouts/MainLayout.vue'),
    children: [
      { path: 'dashboard', component: () => import('./views/DashboardView.vue') },
      // 社区管理
      { path: 'events', component: () => import('./views/admin/EventManage.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'events/:id/stalls', component: () => import('./views/admin/StallPlan.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'events/:id/applications', component: () => import('./views/admin/ApplicationReview.vue'), meta: { roles: ['ADMIN', 'STAFF'] } },
      { path: 'events/:id/allocate', component: () => import('./views/admin/Allocation.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'events/:id/archive', component: () => import('./views/admin/EventArchive.vue'), meta: { roles: ['ADMIN', 'STAFF'] } },
      { path: 'events/:id/review', component: () => import('./views/admin/EventReview.vue'), meta: { roles: ['ADMIN', 'STAFF'] } },
      { path: 'events/:id/publicity', component: () => import('./views/admin/PublicityEdit.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'users', component: () => import('./views/admin/UserManage.vue'), meta: { roles: ['ADMIN'] } },
      // 现场工作
      { path: 'onsite/checkin', component: () => import('./views/staff/CheckIn.vue'), meta: { roles: ['ADMIN', 'STAFF'] } },
      { path: 'onsite/patrol', component: () => import('./views/staff/Patrol.vue'), meta: { roles: ['ADMIN', 'STAFF'] } },
      { path: 'onsite/power', component: () => import('./views/staff/PowerControl.vue'), meta: { roles: ['ADMIN', 'STAFF'] } },
      { path: 'onsite/complaints', component: () => import('./views/staff/Complaints.vue'), meta: { roles: ['ADMIN', 'STAFF'] } },
      { path: 'onsite/finance', component: () => import('./views/staff/Finance.vue'), meta: { roles: ['ADMIN', 'STAFF'] } },
      // 摊主
      { path: 'vendor/apply', component: () => import('./views/vendor/MyApply.vue'), meta: { roles: ['VENDOR'] } },
      { path: 'vendor/records', component: () => import('./views/vendor/MyRecords.vue'), meta: { roles: ['VENDOR'] } },
      { path: 'vendor/credit', component: () => import('./views/vendor/MyCredit.vue'), meta: { roles: ['VENDOR'] } }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('cm_token')
  if (!to.meta.public && !token) {
    return '/login'
  }
  if (to.meta.roles && token) {
    let user = null
    try {
      user = JSON.parse(localStorage.getItem('cm_user') || 'null')
    } catch {
      user = null
    }
    if (!user || !to.meta.roles.includes(user.role)) {
      return '/dashboard'
    }
  }
  return true
})

export default router
