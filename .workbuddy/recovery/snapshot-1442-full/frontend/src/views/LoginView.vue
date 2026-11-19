<template>
  <main class="login-page">
    <section class="login-intro" aria-labelledby="system-title">
      <div class="login-brand">
        <span class="brand-mark brand-mark--large">GD</span>
        <span>广东医科大学</span>
      </div>
      <div>
        <p class="login-eyebrow">Cooperative Learning Assessment</p>
        <h1 id="system-title">团队作业<br />差异化评分系统</h1>
        <p class="login-description">围绕团队贡献量化、个体评分与全过程审计，为校园协作学习提供可信的数据支撑。</p>
      </div>
      <div class="login-principles" aria-label="系统能力">
        <span>多维贡献量化</span>
        <span>评分过程可追溯</span>
        <span>角色权限隔离</span>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <div class="login-card__heading">
          <span class="mobile-school">广东医科大学</span>
          <h2>欢迎登录</h2>
          <p>系统将根据账号角色进入对应工作台</p>
        </div>

        <el-alert
          v-if="route.query.reason === 'expired'"
          title="登录状态已失效，请重新登录"
          type="warning"
          :closable="false"
          show-icon
          class="login-alert"
        />
        <el-alert
          v-else-if="route.query.reason === 'password-changed'"
          title="密码修改成功，请使用新密码重新登录"
          type="success"
          :closable="false"
          show-icon
          class="login-alert"
        />

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="账号" prop="username">
            <el-input
              v-model.trim="form.username"
              size="large"
              placeholder="请输入账号"
              autocomplete="username"
              :prefix-icon="User"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              show-password
              :prefix-icon="Lock"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button class="login-button" type="primary" size="large" native-type="submit" :loading="submitting">
            登录
          </el-button>
        </el-form>

        <p class="login-help">如无法登录，请联系学校系统管理员。</p>
      </div>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { getErrorMessage } from '@/api/response'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const formRef = ref()
const submitting = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || submitting.value) return

  submitting.value = true
  try {
    const homePath = await authStore.login({
      username: form.username,
      password: form.password
    })
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : homePath
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '登录失败，请检查账号和密码'))
  } finally {
    submitting.value = false
  }
}
</script>
