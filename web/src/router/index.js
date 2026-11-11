import { createRouter, createWebHashHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

/**
 * 路由使用 hash 模式，便于后端静态托管时无需额外的路由回退配置。
 * meta.roles 控制页面入口权限，实际数据范围仍由服务端校验（NFR03、NFR04）。
 */
const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'equipments',
        name: 'equipments',
        component: () => import('@/views/EquipmentView.vue'),
        meta: { title: '设备台账', roles: ['ADMIN'] }
      },
      {
        path: 'repairs',
        name: 'repairs',
        component: () => import('@/views/RepairListView.vue'),
        meta: { title: '报修管理', roles: ['ADMIN', 'REPORTER'] }
      },
      {
        path: 'repairs/create',
        name: 'repair-create',
        component: () => import('@/views/RepairCreateView.vue'),
        meta: { title: '提交报修', roles: ['REPORTER'] }
      },
      {
        path: 'repairs/:id',
        name: 'repair-detail',
        component: () => import('@/views/RepairDetailView.vue'),
        meta: { title: '报修详情', roles: ['ADMIN', 'REPORTER'] }
      },
      {
        path: 'orders',
        name: 'orders',
        component: () => import('@/views/OrderListView.vue'),
        meta: { title: '工单管理', roles: ['ADMIN', 'TECHNICIAN'] }
      },
      {
        path: 'orders/:id',
        name: 'order-detail',
        component: () => import('@/views/OrderDetailView.vue'),
        meta: { title: '工单详情', roles: ['ADMIN', 'TECHNICIAN'] }
      },
      {
        path: 'acceptances',
        name: 'acceptances',
        component: () => import('@/views/AcceptanceView.vue'),
        meta: { title: '待验收', roles: ['ADMIN'] }
      },
      {
        path: 'statistics',
        name: 'statistics',
        component: () => import('@/views/StatisticsView.vue'),
        meta: { title: '查询统计', roles: ['ADMIN'] }
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/UserView.vue'),
        meta: { title: '账号管理', roles: ['ADMIN'] }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/ProfileView.vue'),
        meta: { title: '个人中心' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    return true
  }
  if (!auth.token) {
    return { path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  if (to.meta.roles && !to.meta.roles.includes(auth.role)) {
    ElMessage.warning('当前角色无权访问该页面')
    return { path: '/dashboard' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 设备维修保养系统` : '设备维修保养系统'
})

export default router
