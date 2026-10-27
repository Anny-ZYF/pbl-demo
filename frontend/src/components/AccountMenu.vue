<template>
  <div>
    <el-dropdown trigger="click" @command="handleCommand">
      <button class="account-trigger" type="button" aria-label="打开个人菜单">
        <span class="account-avatar">{{ initials }}</span>
        <span class="account-name">{{ authStore.displayName }}</span>
        <el-icon><ArrowDown /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="profile">
            <el-icon><User /></el-icon>个人中心
          </el-dropdown-item>
          <el-dropdown-item command="logout" divided>
            <el-icon><SwitchButton /></el-icon>退出登录
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElIcon,
  ElMessageBox
} from 'element-plus'
import { ArrowDown, SwitchButton, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const initials = computed(() => authStore.displayName.slice(0, 1).toUpperCase())

async function handleCommand(command) {
  if (command === 'profile') {
    await router.push({ name: `${authStore.role.replace('ROLE_', '').toLowerCase()}-profile` })
    return
  }

  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出当前账号吗？', '退出登录', { type: 'warning' })
      await authStore.logout()
      await router.replace('/login')
    } catch {
      // User cancelled the confirmation.
    }
  }
}

</script>
