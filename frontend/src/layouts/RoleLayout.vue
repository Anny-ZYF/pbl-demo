<template>
  <div class="role-shell">
    <header class="role-topbar">
      <button v-if="isWorkspace" class="teacher-mobile-menu" type="button" aria-label="打开教师导航" @click="drawerVisible = true"><el-icon><Menu /></el-icon></button>
      <RouterLink class="brand" :to="homePath"><span class="brand-mark">GD</span><span><strong>团队作业评分</strong><small>{{ roleLabel(role) }}端</small></span></RouterLink>
      <div v-if="isTeacher" class="role-topbar__nav">课程工作区 · 教学管理</div>
      <AccountMenu />
    </header>
    <div class="role-body">
      <aside v-if="isWorkspace" class="teacher-sidebar"><TeacherNavigation /><div class="teacher-sidebar__note">以真实课程、作业和评分数据为准</div></aside>
      <main class="role-content" :class="{ 'role-content--course-center': isTeacher && !isWorkspace }"><RouterView /></main>
    </div>
    <el-drawer v-if="isWorkspace" v-model="drawerVisible" direction="ltr" size="250px" :with-header="false"><TeacherNavigation /></el-drawer>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElDrawer, ElIcon } from 'element-plus'
import { Menu } from '@element-plus/icons-vue'
import AccountMenu from '@/components/AccountMenu.vue'
import TeacherNavigation from '@/components/TeacherNavigation.vue'
import { homeForRole, roleLabel } from '@/utils/roles'
const props = defineProps({ role: { type: String, required: true } })
const route = useRoute(); const drawerVisible = ref(false)
const isTeacher = computed(() => props.role === 'ROLE_TEACHER' || props.role === 'TEACHER')
const isWorkspace = computed(() => isTeacher.value && route.name !== 'teacher-courses')
const homePath = computed(() => homeForRole(props.role))
</script>

<style scoped>
.role-shell{min-height:100vh;background:#f5f7fa}.role-body{display:flex;min-height:calc(100vh - var(--topbar-height))}.role-content{flex:1;min-width:0;padding:28px}.role-content--course-center{padding:42px 34px 0}.role-topbar__nav{flex:1;color:#64748b;font-size:13px}.teacher-sidebar{display:flex;width:174px;flex-direction:column;border:0;background:#2a86e2}.teacher-sidebar__note{margin-top:auto;padding:16px;border-top:1px solid rgba(255,255,255,.16);color:rgba(255,255,255,.72);font-size:11px;line-height:1.5}.teacher-mobile-menu{display:none;width:36px;height:36px;border:1px solid var(--color-border);border-radius:8px;background:#fff}@media(max-width:760px){.teacher-sidebar{display:none}.teacher-mobile-menu{display:grid;place-items:center}.role-topbar__nav{display:none}.role-content,.role-content--course-center{padding:18px}}
</style>
