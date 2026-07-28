<template>
  <main class="password-page">
    <section class="password-card">
      <div class="password-brand"><span class="brand-mark">GD</span> 广东医科大学</div>
      <p class="eyebrow">ACCOUNT SECURITY</p>
      <h1>请先设置新密码</h1>
      <p class="description">这是首次登录或管理员刚刚重置了密码。完成修改后，系统会要求你使用新密码重新登录。</p>

      <el-alert title="在完成改密前，其他系统功能将保持锁定。" type="warning" :closable="false" show-icon />

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="当前临时密码" prop="currentPassword">
          <el-input v-model="form.currentPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-button class="submit-button" type="primary" native-type="submit" :loading="submitting">保存并重新登录</el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getErrorMessage } from '@/api/response'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref()
const submitting = ref(false)
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const rules = {
  currentPassword: [{ required: true, message: '请输入当前临时密码', trigger: 'blur' }],
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
.password-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #f4f7fb;
}

.password-card {
  width: min(100%, 480px);
  padding: 36px;
  border: 1px solid #e5eaf2;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 18px 50px rgba(31, 50, 81, 0.08);
}

.password-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #334155;
  font-size: 14px;
  font-weight: 600;
}

.eyebrow {
  margin: 30px 0 8px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

h1 {
  margin: 0;
  color: #172033;
  font-size: 28px;
}

.description {
  margin: 12px 0 22px;
  color: #64748b;
  line-height: 1.7;
}

.el-alert {
  margin-bottom: 22px;
}

.submit-button {
  width: 100%;
  margin-top: 4px;
}

@media (max-width: 520px) {
  .password-card { padding: 26px 22px; }
}
</style>
