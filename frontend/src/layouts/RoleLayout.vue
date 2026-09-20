<template>
  <div class="role-shell">
    <header class="role-topbar">
      <button v-if="hasSidebar" class="teacher-mobile-menu" type="button" :aria-label="isStudent ? '打开学生导航' : '打开教师导航'" @click="drawerVisible = true"><el-icon><Menu /></el-icon></button>
      <RouterLink class="brand" :to="homePath"><span class="brand-mark">W</span><span><strong>WiseCollab</strong><small>{{ roleLabel(role) }}学习中心</small></span></RouterLink>
      <div v-if="isTeacher" class="role-topbar__nav">课程工作区 · 教学管理</div>
      <div v-else-if="isStudent" class="role-topbar__nav">学习中心 · 团队作业</div>
      <AccountMenu />
    </header>
    <div class="role-body">
      <aside v-if="isWorkspace" class="teacher-sidebar"><TeacherNavigation /><div class="teacher-sidebar__note">以真实课程、作业和评分数据为准</div></aside>
      <aside v-else-if="isStudent" class="student-sidebar"><StudentNavigation /><div class="student-sidebar__note"><strong>学生学习中心</strong><span>查看课程、完成作业与跟踪成绩</span></div></aside>
      <main class="role-content" :class="{ 'role-content--course-center': isTeacher && !isWorkspace, 'role-content--student': isStudent }"><RouterView /></main>
    </div>
    <el-drawer v-if="hasSidebar" v-model="drawerVisible" direction="ltr" size="250px" :with-header="false"><TeacherNavigation v-if="isWorkspace" /><StudentNavigation v-else /></el-drawer>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElDrawer, ElIcon } from 'element-plus'
import { Menu } from '@element-plus/icons-vue'
import AccountMenu from '@/components/AccountMenu.vue'
import TeacherNavigation from '@/components/TeacherNavigation.vue'
import StudentNavigation from '@/components/StudentNavigation.vue'
import { homeForRole, roleLabel } from '@/utils/roles'
const props = defineProps({ role: { type: String, required: true } })
const route = useRoute(); const drawerVisible = ref(false)
const isTeacher = computed(() => props.role === 'ROLE_TEACHER' || props.role === 'TEACHER')
const isStudent = computed(() => props.role === 'ROLE_STUDENT' || props.role === 'STUDENT')
const isWorkspace = computed(() => isTeacher.value && route.name !== 'teacher-courses')
const hasSidebar = computed(() => isWorkspace.value || isStudent.value)
const homePath = computed(() => homeForRole(props.role))
</script>

<style scoped>
.role-shell{min-height:100vh;background:#f4f7fb}.role-body{display:flex;min-height:calc(100vh - var(--topbar-height))}.role-content{flex:1;min-width:0;padding:28px}.role-content--course-center{padding:42px 34px 0}.role-content--student{padding:34px clamp(22px,3vw,44px) 48px}.role-topbar__nav{flex:1;color:#64748b;font-size:13px}.teacher-sidebar{display:flex;width:174px;flex-direction:column;border:0;background:#2a86e2}.teacher-sidebar__note{margin-top:auto;padding:16px;border-top:1px solid rgba(255,255,255,.16);color:rgba(255,255,255,.72);font-size:11px;line-height:1.5}.student-sidebar{position:sticky;top:var(--topbar-height);display:flex;width:218px;height:calc(100vh - var(--topbar-height));flex:0 0 auto;flex-direction:column;border-right:1px solid #e5ebf3;background:#fff}.student-sidebar__note{display:grid;gap:5px;margin:auto 16px 18px;padding:15px;border-radius:11px;color:#64748b;background:#f6f9fd;font-size:11px;line-height:1.55}.student-sidebar__note strong{color:#334155;font-size:12px}.teacher-mobile-menu{display:none;width:36px;height:36px;border:1px solid var(--color-border);border-radius:8px;background:#fff}@media(max-width:760px){.teacher-sidebar,.student-sidebar{display:none}.teacher-mobile-menu{display:grid;place-items:center}.role-topbar__nav{display:none}.role-content,.role-content--course-center,.role-content--student{padding:18px 14px 32px}}
</style>
