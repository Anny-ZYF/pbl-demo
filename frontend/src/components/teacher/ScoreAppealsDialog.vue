<template>
  <el-dialog v-model="visible" title="课程成绩复核" width="min(720px, 94vw)"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @open="load">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <p v-if="loading" role="status">正在加载复核…</p>
    <el-empty v-else-if="!items.length && !error" description="本课程暂无成绩复核" />
    <article v-for="item in items" v-else :key="item.id" class="appeal-card">
      <strong>{{ item.assignmentTitle }} · {{ item.studentName }}</strong>
      <p>当前成绩 {{ item.currentScore }}</p>
      <p>复核理由：{{ item.reason }}</p>
      <p v-if="evidenceText(item.evidence)">复核证据：{{ evidenceText(item.evidence) }}</p>
      <p v-if="item.resolution">{{ item.status === 'ACCEPTED' ? '已接受' : '已驳回' }}：{{ item.resolution }}</p>
      <el-button v-if="pending(item.status) && editing?.id !== item.id" @click="edit(item)">处理复核</el-button>
      <el-form v-if="editing?.id === item.id" label-position="top" novalidate @submit.prevent="save">
        <el-form-item label="处理结论">
          <select v-model="form.status" aria-label="处理结论" :disabled="saving">
            <option value="ACCEPTED">接受并调整成绩</option>
            <option value="REJECTED">驳回并维持成绩</option>
          </select>
        </el-form-item>
        <el-form-item v-if="form.status === 'ACCEPTED'" label="调整后成绩">
          <el-input-number v-model="form.adjustedScore" :min="0" :max="100" :step="0.01" :precision="2" :disabled="saving" />
        </el-form-item>
        <el-form-item label="处理说明">
          <el-input v-model="form.resolution" type="textarea" :rows="3" maxlength="2000" :disabled="saving" />
        </el-form-item>
        <el-button :disabled="saving" @click="editing = null">取消处理</el-button>
        <el-button type="primary" :loading="saving" :disabled="!valid" @click="save">保存复核结论</el-button>
      </el-form>
    </article>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElAlert, ElButton, ElDialog, ElEmpty, ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElMessageBox } from 'element-plus'
import { getTeacherAppeals, resolveScoreAppeal } from '@/api/teacher'
import { getErrorMessage } from '@/api/response'

const visible = defineModel({ type: Boolean, required: true })
const props = defineProps({ courseId: { type: [String, Number], required: true } })
const emit = defineEmits(['resolved'])
const items = ref([])
const editing = ref(null)
const form = reactive({ status: 'ACCEPTED', adjustedScore: null, resolution: '' })
const error = ref('')
const loading = ref(false)
const saving = ref(false)
const pending = status => ['SUBMITTED', 'PROCESSING'].includes(status)
const valid = computed(() => Boolean(form.resolution.trim()) && (form.status === 'REJECTED'
  || (Number.isFinite(form.adjustedScore) && Math.abs(form.adjustedScore - Number(editing.value?.currentScore)) >= 0.009)))

function evidenceText(evidence) {
  if (!evidence) return ''
  if (typeof evidence === 'object') return evidence.text || ''
  try { return JSON.parse(evidence)?.text || '' } catch { return evidence }
}

async function load() {
  loading.value = true
  editing.value = null
  error.value = ''
  try {
    items.value = (await getTeacherAppeals()).filter(item => String(item.courseId) === String(props.courseId))
  } catch (err) {
    items.value = []
    error.value = getErrorMessage(err, '复核列表加载失败')
  } finally {
    loading.value = false
  }
}

function edit(item) {
  editing.value = item
  error.value = ''
  Object.assign(form, { status: 'ACCEPTED', adjustedScore: Number(item.currentScore), resolution: '' })
}

async function save() {
  if (!editing.value || saving.value || !valid.value) return
  const item = editing.value
  const data = { status: form.status, resolution: form.resolution.trim(),
    adjustedScore: form.status === 'ACCEPTED' ? form.adjustedScore : null }
  saving.value = true
  try {
    await ElMessageBox.confirm('处理结论将对该学生可见。确认提交？', '确认处理复核', {
      confirmButtonText: '确认处理', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    saving.value = false
    return
  }
  error.value = ''
  try {
    await resolveScoreAppeal(item.id, data)
    ElMessage.success('复核结论已保存')
    await load()
    emit('resolved')
  } catch (err) {
    error.value = getErrorMessage(err, '保存复核结论失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.appeal-card { padding: 16px; margin: 12px 0; border: 1px solid #e2e8f0; border-radius: 8px; overflow-wrap: anywhere; }
.appeal-card p { margin: 8px 0; white-space: pre-wrap; }
.appeal-card select { min-height: 32px; padding: 4px 8px; }
</style>
