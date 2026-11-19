<template>
  <el-dialog v-model="visible" title="查看或补录小组分工" width="min(800px, 94vw)"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @open="load">
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
    <p v-if="loading" role="status">正在加载分工…</p>
    <template v-else-if="parts.length && members.length">
      <p v-if="locked">当前分工已锁定，可以继续批改。</p>
      <p v-else>请按实际分工填写各成员的占比。每个评价分支合计须为 100%。截止前仍由学生确认，截止后系统锁定补录版本。</p>
      <div class="workload-table">
        <table>
          <thead><tr><th scope="col">评价分支</th><th v-for="member in members" :key="member.studentId" scope="col">{{ member.studentName }}</th><th scope="col">剩余</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <th scope="row">{{ row.name }}</th>
              <td v-for="member in members" :key="member.studentId">
                <input v-model.number="draft[cellKey(row.id, member.studentId)]" type="number" min="0" max="100" step="0.01"
                  :disabled="locked || saving || published" :aria-label="`${row.name} · ${member.studentName}（%）`" />
              </td>
              <td>{{ row.remaining.toFixed(2) }}%</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
    <el-empty v-else-if="!error" description="当前作业没有评价分支或小组成员" />
    <template #footer>
      <el-button :disabled="saving" @click="visible = false">关闭</el-button>
      <el-button v-if="!locked && !published" type="primary" :disabled="!complete || loading" :loading="saving" @click="save">保存分工</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElAlert, ElButton, ElDialog, ElEmpty, ElMessage } from 'element-plus'
import { getAssignmentDetail, getGroupMembers, getTeacherWorkload, saveTeacherWorkload } from '@/api/teacher'
import { getErrorMessage } from '@/api/response'
import { allRowsComplete, buildAllocationPayload, buildRows, cellKey } from '@/utils/workload'

const visible = defineModel({ type: Boolean, required: true })
const props = defineProps({
  assignmentId: { type: [Number, String], required: true },
  groupId: { type: [Number, String], required: true },
  published: Boolean
})
const parts = ref([])
const members = ref([])
const draft = ref({})
const error = ref('')
const loading = ref(false)
const saving = ref(false)
const locked = ref(false)
const rows = computed(() => buildRows(parts.value, members.value, draft.value))
const complete = computed(() => allRowsComplete(rows.value, members.value.length) && parts.value.every(part =>
  members.value.every(member => {
    const value = draft.value[cellKey(part.id, member.studentId)]
    return Number.isFinite(value) && value >= 0 && value <= 100
  })))

async function load() {
  loading.value = true
  error.value = ''
  parts.value = []
  members.value = []
  draft.value = {}
  locked.value = false
  try {
    const [assignment, roster, workload] = await Promise.all([
      getAssignmentDetail(props.assignmentId),
      getGroupMembers(props.assignmentId, props.groupId),
      getTeacherWorkload(props.assignmentId, props.groupId).catch(err => {
        if (err.status === 404) return null
        throw err
      })
    ])
    parts.value = assignment.parts || []
    members.value = roster.filter(member => member.status === 'ACTIVE')
      .map(member => ({ ...member, studentName: member.name || member.studentNo }))
    draft.value = Object.fromEntries((workload?.items || []).map(item =>
      [cellKey(item.assignmentPartId, item.studentId), Math.round(Number(item.share) * 1e6) / 1e4]))
    locked.value = workload?.status === 'LOCKED' && complete.value
  } catch (err) {
    error.value = getErrorMessage(err, '分工数据加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!complete.value || locked.value || props.published || saving.value) return
  saving.value = true
  error.value = ''
  try {
    await saveTeacherWorkload(props.assignmentId, props.groupId, buildAllocationPayload(parts.value, members.value, draft.value))
    ElMessage.success('小组分工已保存')
    visible.value = false
  } catch (err) {
    error.value = getErrorMessage(err, '补录分工失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.workload-table { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; text-align: left; }
th, td { padding: 8px; border-bottom: 1px solid #e2e8f0; }
input { width: 78px; padding: 7px; border: 1px solid #cbd5e1; border-radius: 6px; }
</style>
