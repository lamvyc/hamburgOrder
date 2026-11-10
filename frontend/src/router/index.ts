import { createRouter, createWebHistory } from 'vue-router'
import type { UserRole } from '@/types/auth'
import { homeForRole } from '@/utils/roleHome'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'store-list',
      component: () => import('@/views/StoreListView.vue'),
      meta: { title: '选店', phase: 'STAGE A1 · CATALOG' },
    },
    {
      path: '/stores/:storeId(\\d+)',
      name: 'store-menu',
      component: () => import('@/views/StoreMenuView.vue'),
      meta: { title: '门店菜单', phase: 'STAGE A1 · CATALOG' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录', phase: 'STAGE A2 · SESSION' },
    },
    {
      path: '/orders/:orderId(\\d+)',
      name: 'order-detail',
      component: () => import('@/views/OrderDetailView.vue'),
      meta: {
        title: '订单详情',
        phase: 'STAGE B1 · COUPON',
        requiresAuth: true,
        roles: ['CUSTOMER'] satisfies UserRole[],
      },
    },
    {
      path: '/cart',
      name: 'cart',
      component: () => import('@/views/CartView.vue'),
      meta: {
        title: '购物车',
        phase: 'STAGE B1 · CHECKOUT',
        requiresAuth: true,
        roles: ['CUSTOMER'] satisfies UserRole[],
      },
    },
    {
      path: '/staff',
      name: 'staff-workspace',
      component: () => import('@/views/StaffWorkspaceView.vue'),
      meta: {
        title: '员工工作台',
        phase: 'STAGE A4 · WORKBENCH',
        requiresAuth: true,
        roles: ['STAFF'] satisfies UserRole[],
      },
    },
    {
      path: '/admin/catalog',
      name: 'admin-catalog',
      component: () => import('@/views/AdminCatalogView.vue'),
      meta: {
        title: '商品与库存',
        phase: 'STAGE B5 · ADMIN',
        requiresAuth: true,
        roles: ['ADMIN'] satisfies UserRole[],
      },
    },
    {
      path: '/admin',
      name: 'admin-entry',
      component: () => import('@/views/AdminEntryView.vue'),
      meta: {
        title: '管理员入口',
        phase: 'STAGE A2 · ADMIN',
        requiresAuth: true,
        roles: ['ADMIN'] satisfies UserRole[],
      },
    },
    {
      path: '/system',
      name: 'system-status',
      component: () => import('@/views/SystemStatusView.vue'),
      meta: { title: '系统状态', phase: 'STAGE C3 · OBSERVABILITY' },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.initialized) {
    await auth.initialize()
  }

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return {
      name: 'login',
      query: { redirect: to.fullPath },
    }
  }

  const allowedRoles = to.meta.roles as UserRole[] | undefined
  if (allowedRoles && auth.user && !allowedRoles.includes(auth.user.role)) {
    return homeForRole(auth.user.role)
  }

  if (to.name === 'login' && auth.user) {
    return homeForRole(auth.user.role)
  }

  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : 'Burger Order Lab'
  document.title = `${title} · Burger Order Lab`
})

export default router
