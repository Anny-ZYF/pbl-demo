<template>
  <section class="workload-card">
    <div class="wl-head">
      <div>
        <h2>{{ cardTitle }}</h2>
        <p>{{ cardHint }}</p>
      </div>
      <span class="wl-role">{{ roleHint }}</span>
    </div>

    <div v-if="errorMessage" class="wl-error" role="alert">{{ errorMessage }}</div>

    <div v-if="loading" class="wl-state">正在加载工作量分配…</div>
    <template v-else>
      <div class="wl-status" :class="statusClass">
        <span>{{ statusBanner }}</span>
      </div>

      <div v-if="!parts.length || !members.length" class="wl-state">当前作业没有评价分支或小组成员</div>
      <div v-else class="wl-scroll">
        <table class="wl-table">
          <thead>
            <tr>
              <th scope="col" class="wl-part-head">评价分支 / 权重</th>
              <th v-for="member in members" :key="`head-${member.studentId}`" scope="col">
                {{ member.name }}
                <small v-if="member.role === 'LEADER'">组长</small>
              </th>
              <th v-if="!teacherMode" scope="col" class="wl-remain-head">剩余可分配</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <th scope="row">
                <span class="wl-part-name">{{ row.name }}</span>
                <small class="wl-weight">权重 {{ pct(row.weight) }}</small>
                <small v-if="row.description" class="wl-part-desc" :title="row.description">{{ row.description }}</small>
              </th>
              <td v-for="member in members" :key="`${row.id}-${member.studentId}`">
                <input
                  :value="draft[cellKey(row.id, member.studentId)]"
                  type="number"
                  min="0"
                  :max="cellLimitFor(row.id, member.studentId)"
                  step="1"
                  inputmode="numeric"
                  :disabled="!editable"
                  :aria-label="`${row.name} - ${member.name} 工作量份额`"
                  :class="{ 'wl-cell--mine': isMine(member), 'wl-cell--clamped': clampFlash === cellKey(row.id, member.studentId) }"
                  placeholder="—"
                  @input="onCellInput(row.id, member.studentId, $event)"
                >
              </td>
              <td v-if="!teacherMode" class="wl-remain" :class="remainClass(row)">{{ remainText(row) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="editable" class="wl-foot">
        <span class="wl-hint" :class="hintClass">{{ validationHint }}</span>
        <button class="wl-primary" :disabled="!canSubmit" @click="submit">
          {{ saving ? '保存中…' : workload ? '保存分工（新版本）' : '保存分工' }}
        </button>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getWorkload, proposeWorkload } from '@/api/student'
import { getTeacherWorkload } from '@/api/teacher'
import { getErrorMessage } from '@/api/response'
import { isAutoLocked, parseDateValue } from '@/utils/assignmentWindow'
import { ROW_TOLERANCE, allRowsComplete, buildAllocationPayload, buildRows, cellKey, cellLimit, clampCellValue } from '@/utils/workload'

const props = defineProps({
  assignmentId: { type: [String, Number], required: true },
  groupId: { type: [String, Number], default: null },
  parts: { type: Array, default: () => [] },
  members: { type: Array, default: () => [] },
  isLeader: { type: Boolean, default: false },
  readOnly: { type: Boolean, default: false },
  currentStudentId: { type: [String, Number], default: null },
  deadline: { type: [String, Number, Date], default: null },
  teacherMode: { type: Boolean, default: false }
})
const emit = defineEmits(['changed', 'status', 'dirty'])

const workload = ref(null)
const draft = reactive({})
const baseline = ref({})
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')

const cardTitle = computed(() => (props.teacherMode ? '小组分工与贡献度' : '小组与工作量分配'))
const cardHint = computed(() => {
  if (!props.teacherMode) return '按评价分支填写每位成员的工作量份额，每行合计须为 100'
  return '各成员在每个评价分支的工作量份额，由学生组长提交，教师不可修改'
})

const pct = (weight) => `${(Number(weight) * 100).toFixed(0)}%`
const isMine = (member) => !props.teacherMode && props.currentStudentId != null
  && String(member.studentId) === String(props.currentStudentId)

const rows = computed(() => buildRows(props.parts, props.members, draft))
const allComplete = computed(() => allRowsComplete(rows.value, props.members.length))

const now = ref(Date.now())
const clock = setInterval(() => { now.value = Date.now() }, 30_000)
onUnmounted(() => clearInterval(clock))
const deadlinePassed = computed(() => {
  const end = parseDateValue(props.deadline)
  return end != null && now.value >= end
})

// 一个分支一行，一行最多 100 个百分点：先填的成员先占额度，后填的只剩「100 − 其他成员合计」。
const rowOthersTotal = (partId, studentId) => props.members
  .filter((member) => String(member.studentId) !== String(studentId))
  .reduce((sum, member) => sum + Number(draft[cellKey(partId, member.studentId)] || 0), 0)
const cellLimitFor = (partId, studentId) => cellLimit(rowOthersTotal(partId, studentId))

const clampFlash = ref('')
let clampTimer = 0
const flashClamped = (key) => {
  clampFlash.value = key
  clearTimeout(clampTimer)
  clampTimer = setTimeout(() => { clampFlash.value = '' }, 900)
}

// 输入即钳位：140 只能留 100；额度已被前面的成员占满时，这一格只能填 0。
// 直接改 DOM value，因为钳位后的字符串可能和上一次渲染一致，Vue 不会重新 patch。
const onCellInput = (partId, studentId, event) => {
  const key = cellKey(partId, studentId)
  const raw = String(event.target.value ?? '')
  // 允许「12.」这类没输完的小数，否则小数点会被吃掉
  if (raw === '' || raw === '-' || raw.endsWith('.')) {
    draft[key] = raw
    return
  }
  const parsed = Number(raw)
  const clamped = String(clampCellValue(raw, rowOthersTotal(partId, studentId)))
  draft[key] = clamped
  if (event.target.value !== clamped) event.target.value = clamped
  if (Number.isFinite(parsed) && parsed > Number(clamped)) flashClamped(key)
}

// 学生端只有组长能改；教师端始终只读——贡献度只能由学生提交，教师没有任何写入口。
const editable = computed(() => !props.teacherMode && props.isLeader && !props.readOnly)
const canSubmit = computed(() => editable.value && allComplete.value && !saving.value && props.groupId != null)

const autoLocked = computed(() => isAutoLocked(workload.value?.autoLocked))

const roleHint = computed(() => {
  if (props.teacherMode) return '学生提交 · 教师只读'
  if (autoLocked.value) return '截止自动锁定 · 不可编辑'
  if (props.readOnly) return '当前阶段只读'
  if (!props.isLeader) return '由组长填写，仅供查看'
  return editable.value ? '我是组长 · 可编辑' : '已锁定 · 不可编辑'
})

const statusBanner = computed(() => {
  const version = workload.value?.versionNo ?? '—'
  if (!workload.value) {
    if (props.teacherMode) {
      return deadlinePassed.value
        ? '该组尚无分工版本，评分按组内均分计算'
        : '该组尚未填写分工；截止后仍未填写时按组内均分计算'
    }
    return props.isLeader ? '尚无分工方案，请填写各成员份额后保存' : '组长尚未填写工作量分配'
  }
  if (workload.value.status === 'LOCKED') {
    if (props.teacherMode) return `第 ${version} 版已保存并锁定，评分将采用此版本`
    return '工作量分配已保存'
  }
  if (workload.value.status === 'SUPERSEDED') {
    return `第 ${version} 版已被新版本替代`
  }
  return `第 ${version} 版状态：${workload.value.status}`
})

const statusClass = computed(() => ({
  'wl-status--locked': workload.value?.status === 'LOCKED',
  'wl-status--empty': !workload.value
}))

const validationHint = computed(() => {
  if (props.teacherMode) return '分工由学生组长提交，教师不可修改'
  if (!editable.value) return props.readOnly ? '当前阶段不可编辑' : '由组长填写'
  if (allComplete.value) return '全部分支合计均为 100，可提交'
  const over = rows.value.filter((row) => row.remaining < -ROW_TOLERANCE).length
  if (over) return `有 ${over} 个分支超出 100，请调减`
  const pending = rows.value.filter((row) => row.remaining > ROW_TOLERANCE).length
  return `还有 ${pending} 个分支未分配完`
})

const hintClass = computed(() => ({
  'wl-hint--ok': allComplete.value,
  'wl-hint--over': !allComplete.value && rows.value.some((row) => row.remaining < -ROW_TOLERANCE)
}))

const remainText = (row) => Math.abs(row.remaining) < ROW_TOLERANCE
  ? '0 ✓' : String(Math.round(row.remaining * 100) / 100)
const remainClass = (row) => {
  if (row.remaining > ROW_TOLERANCE) return 'wl-remain--pending'
  if (row.remaining < -ROW_TOLERANCE) return 'wl-remain--over'
  return 'wl-remain--done'
}

const fillDraft = (items) => {
  Object.keys(draft).forEach((key) => delete draft[key])
  const stored = new Map((items || []).map((item) => [cellKey(item.assignmentPartId, item.studentId), item.share]))
  // 逐格按剩余额度回填：历史数据或成员变动后若出现超配，也保证一行不超过 100。
  for (const part of props.parts) {
    let used = 0
    for (const member of props.members) {
      const key = cellKey(part.id, member.studentId)
      const share = stored.get(key)
      const value = share == null ? '' : String(clampCellValue(Number(share) * 100, used))
      draft[key] = value
      used += Number(value || 0)
    }
  }
  baseline.value = { ...draft }
}

// 改了份额但还没保存：教师端保存团队成绩前要先拦住，否则新分工不会进评分。
const isDirty = computed(() => {
  const keys = new Set([...Object.keys(baseline.value), ...Object.keys(draft)])
  for (const key of keys) {
    if (String(baseline.value[key] ?? '') !== String(draft[key] ?? '')) return true
  }
  return false
})

const emitStatus = () => emit('status', {
  hasVersion: Boolean(workload.value),
  status: workload.value?.status ?? null,
  versionNo: workload.value?.versionNo ?? null,
  locked: workload.value?.status === 'LOCKED',
  autoLocked: autoLocked.value,
  complete: allComplete.value
})

const load = async () => {
  if (props.groupId == null) return
  loading.value = true
  errorMessage.value = ''
  try {
    const data = props.teacherMode
      ? await getTeacherWorkload(props.assignmentId, props.groupId)
      : await getWorkload(props.assignmentId)
    workload.value = data
    fillDraft(data?.items || [])
  } catch (error) {
    if (error?.status === 404) {
      workload.value = null
      fillDraft([])
    } else {
      errorMessage.value = getErrorMessage(error, '工作量分配加载失败')
    }
  } finally {
    loading.value = false
    emitStatus()
    emit('dirty', false)
  }
}

const submit = async () => {
  if (!canSubmit.value) return
  saving.value = true
  errorMessage.value = ''
  try {
    await proposeWorkload(props.assignmentId, props.groupId,
      buildAllocationPayload(props.parts, props.members, draft))
    ElMessage.success('分工已提交，评分将采用这一版本')
    await load()
    emit('changed')
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '分工保存失败')
  } finally {
    saving.value = false
  }
}

watch(() => [props.assignmentId, props.groupId], () => load(), { immediate: true })
watch(allComplete, emitStatus)
watch(isDirty, (value) => emit('dirty', value))
</script>

<style scoped>
.workload-card {
  box-sizing: border-box;
  width: 100%;
  padding: 22px;
  border: 1px solid #e2e9f3;
  border-radius: 13px;
  background: #fff;
  box-shadow: 0 5px 18px #2c486e0d;
}

.wl-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.wl-head h2 {
  margin: 0 0 5px;
  color: #203454;
  font-size: 16px;
}

.wl-head p {
  margin: 0;
  color: #8795a7;
  font-size: 12px;
}

.wl-role {
  flex: none;
  padding: 5px 11px;
  border-radius: 99px;
  color: #5279d2;
  background: #edf3ff;
  font-size: 11px;
}

.wl-error {
  margin-bottom: 14px;
  padding: 11px 13px;
  border-radius: 8px;
  color: #b42318;
  background: #fff1f0;
  font-size: 12px;
}

.wl-state {
  padding: 36px 0;
  color: #8795a7;
  text-align: center;
  font-size: 13px;
}

.wl-status {
  margin-bottom: 15px;
  padding: 11px 14px;
  border-radius: 9px;
  color: #60738c;
  background: #f6f9fd;
  font-size: 12px;
}

.wl-status--empty { color: #7b899b; background: #f6f9fd; }
.wl-status--locked { color: #2e8a62; background: #ebf8f2; }

.wl-scroll {
  overflow-x: auto;
}

.wl-table {
  width: 100%;
  min-width: 560px;
  border-collapse: collapse;
  table-layout: fixed;
}

.wl-table th,
.wl-table td {
  padding: 9px 8px;
  border-bottom: 1px solid #eef2f8;
  font-size: 12px;
  text-align: center;
  vertical-align: middle;
}

.wl-table thead th {
  color: #8795a7;
  font-weight: 400;
  font-size: 11px;
  white-space: nowrap;
}

.wl-table thead th small {
  display: block;
  color: #5279d2;
  font-size: 10px;
}

.wl-part-head { width: 30%; text-align: left !important; }
.wl-remain-head { width: 110px; text-align: right !important; }

.wl-table tbody th {
  color: #40556f;
  font-weight: 400;
  text-align: left;
}

.wl-part-name {
  display: block;
  color: #40556f;
  font-size: 12px;
}

.wl-weight {
  display: block;
  margin-top: 3px;
  color: #5279d2;
  font-size: 11px;
}

.wl-part-desc {
  display: block;
  overflow: hidden;
  max-width: 200px;
  margin-top: 3px;
  color: #9aa6b5;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wl-table input {
  box-sizing: border-box;
  width: 100%;
  min-width: 62px;
  padding: 7px 6px;
  border: 1px solid #dce5ef;
  border-radius: 7px;
  color: #30435f;
  background: #fff;
  font-size: 12px;
  text-align: center;
}

.wl-table input:focus {
  outline: none;
  border-color: #527de0;
}

.wl-table input:disabled {
  color: #5b6c82;
  background: #f6f9fd;
  border-color: #e4ebf4;
}

.wl-cell--mine:not(:disabled) {
  border-color: #a9c1ee;
  background: #fbfdff;
}

/* 输入被钳位时的短暂提示：140 → 100、额度已满 → 0 */
.wl-cell--clamped {
  animation: wl-clamp .9s ease;
}

@keyframes wl-clamp {
  0% { border-color: #e0a33a; background: #fff4e0; }
  100% { border-color: #dce5ef; background: #fff; }
}

.wl-remain {
  font-size: 12px;
  text-align: right !important;
  white-space: nowrap;
}

.wl-remain--done { color: #2e8a62; }
.wl-remain--pending { color: #c1832f; }
.wl-remain--over { color: #c0483f; }

.wl-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #eef2f8;
}

.wl-hint {
  color: #c1832f;
  font-size: 11px;
}

.wl-hint--ok { color: #2e8a62; }
.wl-hint--over { color: #c0483f; }

.wl-primary {
  padding: 9px 18px;
  border: 0;
  border-radius: 99px;
  color: #fff;
  background: linear-gradient(90deg, #4e7ae8, #7194f0);
  font-size: 12px;
  cursor: pointer;
}

.wl-primary:disabled {
  opacity: .5;
  cursor: not-allowed;
}

@media (max-width: 820px) {
  .workload-card { padding: 17px; }
  .wl-head { flex-direction: column; }
  .wl-foot { align-items: flex-start; }
}
</style>
