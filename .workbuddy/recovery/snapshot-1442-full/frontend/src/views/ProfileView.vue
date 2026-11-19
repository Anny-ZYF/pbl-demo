<template>
  <div class="profile-page">
    <PageHeader title="个人中心" description="查看账号信息并维护登录密码。">
      <template #actions>
        <el-button :icon="ArrowLeft" @click="router.push(authStore.homePath)">{{ returnLabel }}</el-button>
      </template>
    </PageHeader>

    <div class="profile-grid">
      <section class="surface-card profile-card">
        <div class="profile-summary">
          <span class="profile-avatar">{{ initials }}</span>
          <div>
            <h2>{{ authStore.displayName }}</h2>
            <el-tag type="primary" effect="light">{{ roleLabel(authStore.role) }}</el-tag>
          </div>
        </div>

        <el-descriptions :column="1" border>
          <el-descriptions-item label="姓名">{{ authStore.displayName }}</el-descriptions-item>
          <el-descriptions-item label="登录账号">{{ authStore.user?.username || '—' }}</el-descriptions-item>
          <el-descriptions-item label="账号角色">{{ roleLabel(authStore.role) }}</el-descriptions-item>
          <el-descriptions-item label="密码状态">
            <el-tag :type="authStore.user?.mustChangePassword ? 'warning' : 'success'" effect="plain">
              {{ authStore.user?.mustChangePassword ? '需要修改' : '已设置' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="surface-card password-card">
        <div class="section-heading">
          <div>
            <h2>修改密码</h2>
            <p>新密码长度为 8–64 位，且必须同时包含字母和数字。</p>
          </div>
          <el-icon><Lock /></el-icon>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="当前密码" prop="currentPassword">
            <el-input v-model="form.currentPassword" type="password" show-password autocomplete="current-password" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <el-button type="primary" native-type="submit" :loading="submitting">保存新密码</el-button>
        </el-form>
        <p class="security-note">密码修改成功后，当前会话将退出，请使用新密码重新登录。</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  ElTag
} from 'element-plus'
import { ArrowLeft, Lock } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { getErrorMessage } from '@/api/response'
import { useAuthStore } from '@/stores/auth'
import { roleLabel } from '@/utils/roles'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref()
const submitting = ref(false)
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const initials = computed(() => authStore.displayName.slice(0, 1).toUpperCase())
const returnLabel = computed(() => {
  if (authStore.role === 'ROLE_TEACHER') return '返回课程中心'
  if (authStore.role === 'ROLE_STUDENT') return '返回学习中心'
  return '返回工作台'
})

const rules = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
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

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || submitting.value) return

  submitting.value = true
  try {
    await authStore.changePassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    authStore.clearSession()
    await router.replace({ path: '/login', query: { reason: 'password-changed' } })
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '密码修改失败'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.profile-page { max-width: 1080px; margin: 0 auto; }
.profile-grid { display: grid; grid-template-columns: minmax(300px, .85fr) minmax(380px, 1.15fr); gap: 20px; }
.profile-card, .password-card { padding: 24px; }
.profile-summary { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
.profile-avatar { display: grid; width: 62px; height: 62px; place-items: center; border-radius: 18px; color: #fff; background: linear-gradient(135deg, #2563eb, #60a5fa); font-size: 24px; font-weight: 700; }
.profile-summary h2, .section-heading h2 { margin: 0 0 8px; color: #172554; font-size: 20px; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 22px; }
.section-heading p { margin: 0; color: #64748b; font-size: 13px; line-height: 1.6; }
.section-heading > .el-icon { flex: 0 0 auto; padding: 10px; border-radius: 10px; color: #2563eb; background: #eff6ff; font-size: 20px; }
.password-card .el-button { width: 100%; }
.security-note { margin: 16px 0 0; color: #94a3b8; font-size: 12px; line-height: 1.6; }
@media (max-width: 860px) { .profile-grid { grid-template-columns: 1fr; } }
@media (max-width: 520px) { .profile-card, .password-card { padding: 18px; } }
</style>
