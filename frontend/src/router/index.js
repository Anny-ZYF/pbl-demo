import { createRouter, createWebHistory } from 'vue-router'
import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'
import { homeForRole } from '@/utils/roles'

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/change-password',
    name: 'required-password-change',
    component: () => import('@/views/ChangePasswordView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT'],
      title: '首次修改密码'
    }
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    meta: { requiresAuth: true, roles: ['ROLE_ADMIN'] },
    children: [
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('@/views/admin/DashboardView.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'organization',
        name: 'admin-organization',
        component: () => import('@/views/admin/OrganizationView.vue'),
        meta: { title: '组织与账号' }
      },
      {
        path: 'teaching-data',
        name: 'admin-teaching-data',
        component: () => import('@/views/admin/TeachingDataView.vue'),
        meta: { title: '教学数据' }
      },
      {
        path: 'scoring-model',
        name: 'admin-scoring-model',
        component: () => import('@/views/admin/ScoringModelView.vue'),
        meta: { title: '评分模型' }
      },
      {
        path: 'audit-logs',
        name: 'admin-audit-logs',
        component: () => import('@/views/admin/AuditLogView.vue'),
        meta: { title: '审计日志' }
      }
    ]
  },
  {
    path: '/teacher',
    component: () => import('@/layouts/RoleLayout.vue'),
    redirect: '/teacher/courses',
    props: { role: 'ROLE_TEACHER' },
    meta: { requiresAuth: true, roles: ['ROLE_TEACHER'] },
    children: [
      {
        path: 'courses',
        name: 'teacher-courses',
        component: () => import('@/views/teacher/TeacherCourseCenterView.vue'),
        meta: { title: '教师课程中心' }
      },
      {
        path: 'course/:courseId/workspace',
        name: 'teacher-course-workspace',
        component: () => import('@/views/teacher/TeacherDashboardView.vue'),
        meta: { title: '课程首页' }
      },
      {
        path: 'course/:courseId/assignments',
        name: 'teacher-assignments',
        component: () => import('@/views/teacher/TeacherAssignmentsView.vue'),
        meta: { title: '作业管理' }
      },
      {
        path: 'course/:courseId/assignments/create',
        name: 'teacher-assignment-create',
        component: () => import('@/views/teacher/TeacherAssignmentCreateView.vue'),
        meta: { title: '创建作业' }
      },
      {
        path: 'course/:courseId/assignments/:assignmentId/grading',
        name: 'teacher-assignment-grading',
        component: () => import('@/views/teacher/TeacherAssignmentGradingView.vue'),
        meta: { title: '批改作业' }
      },
      {
        path: 'course/:courseId/students',
        name: 'teacher-students',
        component: () => import('@/views/teacher/TeacherStudentsView.vue'),
        meta: { title: '学生管理' }
      },
      {
        path: 'course/:courseId/scores',
        name: 'teacher-scores',
        component: () => import('@/views/teacher/TeacherScoresView.vue'),
        meta: { title: '成绩查看' }
      },
      {
        path: 'dashboard',
        name: 'teacher-dashboard',
        component: () => import('@/views/teacher/TeacherDashboardView.vue'),
        meta: { title: '教师工作台' }
      }
    ]
  },
  {
    path: '/student',
    component: () => import('@/layouts/RoleLayout.vue'),
    redirect: '/student/dashboard',
    props: { role: 'ROLE_STUDENT' },
    meta: { requiresAuth: true, roles: ['ROLE_STUDENT'] },
    children: [
      {
        path: 'dashboard',
        name: 'student-dashboard',
        component: () => import('@/views/student/StudentCourseCenterView.vue'),
        meta: { title: '学生工作台' }
      },
      {
        path: 'course/:courseId',
        name: 'student-course',
        component: () => import('@/views/student/StudentCourseView.vue'),
        meta: { title: '学生课程' }
      },
      {
        path: 'assignment/:assignmentId',
        name: 'student-assignment-detail',
        component: () => import('@/views/student/StudentAssignmentDetailView.vue'),
        meta: { title: '作业详情' }
      },
      {
        path: 'scores',
        name: 'student-scores',
        component: () => import('@/views/student/StudentScoresView.vue'),
        meta: { title: '我的成绩' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: true, title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore(pinia)
  const hasSession = await authStore.ensureSession()

  if (to.meta.public) {
    if (to.name === 'login' && hasSession) return homeForRole(authStore.role)
    return true
  }

  if (!to.meta.requiresAuth) return true
  if (!hasSession) {
    return {
      name: 'login',
      query: { redirect: to.fullPath }
    }
  }

  const allowedRoles = to.meta.roles || []
  if (!allowedRoles.includes(authStore.role)) return homeForRole(authStore.role)
  if (authStore.user?.mustChangePassword && to.name !== 'required-password-change') {
    return { name: 'required-password-change' }
  }
  if (!authStore.user?.mustChangePassword && to.name === 'required-password-change') {
    return homeForRole(authStore.role)
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '系统'} - 团队作业差异化评分系统`
})

export default router
