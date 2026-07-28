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
          <el-dropdown-item command="password">
            <el-icon><Lock /></el-icon>修改密码
          </el-dropdown-item>
          <el-dropdown-item command="logout" divided>
            <el-icon><SwitchButton /></el-icon>退出登录
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <el-dialog
      v-model="dialogVisible"
      title="修改密码"
      width="420px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPassword">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElDialog,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox
} from 'element-plus'
import { ArrowDown, Lock, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { getErrorMessage } from '@/api/response'

const router = useRouter()
const authStore = useAuthStore()
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const initials = computed(() => authStore.displayName.slice(0, 1).toUpperCase())

const rules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度应为 8–64 位', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '密码必须同时包含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [{
    validator: (_rule, value, callback) => {
      if (!value) callback(new Error('请再次输入新密码'))
      else if (value !== form.newPassword) callback(new Error('两次输入的新密码不一致'))
      else callback()
    },
    trigger: 'blur'
  }]
}

function resetForm() {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  formRef.value?.clearValidate()
}

async function handleCommand(command) {
  if (command === 'password') {
    resetForm()
    dialogVisible.value = true
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

async function submitPassword() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await authStore.changePassword({
      currentPassword: form.oldPassword,
      newPassword: form.newPassword
    })
    ElMessage.success('密码修改成功')
    dialogVisible.value = false
    resetForm()
    authStore.clearSession()
    await router.replace({ path: '/login', query: { reason: 'password-changed' } })
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '密码修改失败'))
  } finally {
    submitting.value = false
  }
}
</script>
