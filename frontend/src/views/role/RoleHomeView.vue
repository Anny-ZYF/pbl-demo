<template>
  <section class="placeholder-page">
    <div class="placeholder-card">
      <div class="placeholder-icon">
        <el-icon><Tools /></el-icon>
      </div>
      <el-tag effect="plain">{{ roleLabel(role) }}端开发骨架</el-tag>
      <h1>模块待团队开发</h1>
      <p>统一登录、角色鉴权、页面布局与接口基础设施已经就绪。后续业务页面请按项目设计文档和 <code>/api/v1</code> 契约扩展。</p>
      <div class="placeholder-paths">
        <span>路由扩展：<code>src/router/index.js</code></span>
        <span>页面目录：<code>src/views/{{ folderName }}</code></span>
        <span>接口目录：<code>src/api</code></span>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { ElIcon, ElTag } from 'element-plus'
import { Tools } from '@element-plus/icons-vue'
import { normalizeRole, roleLabel } from '@/utils/roles'

const props = defineProps({ role: { type: String, required: true } })
const folderName = computed(() => normalizeRole(props.role) === 'ROLE_TEACHER' ? 'teacher' : 'student')
</script>
