<template>
  <section class="workload-card">
    <div class="wl-head">
      <div>
        <h2>小组与工作量分配</h2>
        <p>按评价分支填写每位成员的工作量份额，每行合计须为 100</p>
      </div>
      <span class="wl-role">{{ roleHint }}</span>
    </div>

    <div v-if="errorMessage" class="wl-error" role="alert">{{ errorMessage }}</div>

    <div v-if="loading" class="wl-state">正在加载工作量分配…</div>
    <template v-else>
      <div class="wl-status" :class="statusClass">
        <span>{{ statusBanner }}</span>
      </div>

      <div class="wl-scroll">
        <table class="wl-table">
          <thead>
            <tr>
              <th scope="col" class="wl-part-head">评价分支 / 权重</th>
              <th v-for="member in members" :key="`head-${member.studentId}`" scope="col">
                {{ member.name }}
                <small v-if="member.role === 'LEADER'">组长</small>
              </th>
              <th scope="col" class="wl-remain-head">剩余可分配</th>
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
                  v-model="draft[cellKey(row.id, member.studentId)]"
                  type="number"
                  min="0"
                  max="100"
                  step="1"
                  inputmode="numeric"
                  :disabled="!editable"
                  :aria-label="`${row.name} - ${member.name} 工作量份额`"
                  :class="{ 'wl-cell--mine': isMine(member) }"
                  placeholder="—"
                >
              </td>
              <td class="wl-remain" :class="remainClass(row)">{{ remainText(row) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="workload && editable" class="wl-note">
        重新保存会生成新版本，旧版本自动作废，评分采用最新版本
      </p>

      <div class="wl-foot">
        <div class="wl-contrib">
          <span class="wl-label">加权贡献 C = Σ（分支权重 W × 本人份额 Q）</span>
          <div class="wl-contrib-list">
            <span v-for="member in members" :key="`c-${member.studentId}`">
              {{ member.name }} <b>{{ contributionText(member) }}</b>
            </span>
          </div>
        </div>
        <div class="wl-actions">
          <span class="wl-hint" :class="hintClass">{{ validationHint }}</span>
          <button v-if="editable" class="wl-primary" :disabled="!canSubmit" @click="submit">
            {{ saving ? '保存中…' : workload ? '保存分工（新版本）' : '提交分工' }}
          </button>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getWorkload, proposeWorkload } from '@/api/student'
import { getErrorMessage } from '@/api/response'
import { isAutoLocked } from '@/utils/assignmentWindow'
import { ROW_TOLERANCE, allRowsComplete, buildAllocationPayload, buildContributions, buildRows, cellKey } from '@/utils/workload'

const props = defineProps({
  assignmentId: { type: [String, Number], required: true },
  groupId: { type: [String, Number], required: true },
  parts: { type: Array, default: () => [] },
  members: { type: Array, default: () => [] },
  isLeader: { type: Boolean, default: false },
  readOnly: { type: Boolean, default: false },
  currentStudentId: { type: [String, Number], default: null }
})
const emit = defineEmits(['changed', 'status'])

const workload = ref(null)
const draft = reactive({})
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')

const pct = (weight) => `${(Number(weight) * 100).toFixed(0)}%`
const isMine = (member) => props.currentStudentId != null
  && String(member.studentId) === String(props.currentStudentId)

const rows = computed(() => buildRows(props.parts, props.members, draft))
const allComplete = computed(() => allRowsComplete(rows.value, props.members.length))
const contributions = computed(() => buildContributions(props.parts, props.members, draft))

// No member confirmation any more: the leader edits and saves, and scoring reads the newest
// saved version. Saving again replaces the previous snapshot, so a leader can revise until the
// deadline (or until the group is graded).
const editable = computed(() => props.isLeader && !props.readOnly)
const canSubmit = computed(() => editable.value && allComplete.value && !saving.value)

const autoLocked = computed(() => isAutoLocked(workload.value?.autoLocked))

const roleHint = computed(() => {
  if (autoLocked.value) return '截止自动锁定 · 不可编辑'
  if (props.readOnly) return '当前阶段只读'
  if (!props.isLeader) return '由组长填写，仅供查看'
  return editable.value ? '我是组长 · 可编辑' : '已锁定 · 不可编辑'
})

const statusBanner = computed(() => {
  const version = workload.value?.versionNo ?? '—'
  if (!workload.value) {
    return props.isLeader ? '尚无分工方案，请填写各成员份额后保存' : '组长尚未填写工作量分配'
  }
  if (workload.value.status === 'LOCKED') {
    return autoLocked.value
      ? `第 ${version} 版已在截止时间自动锁定，评分将采用此版本`
      : `第 ${version} 版已保存，评分将采用此版本${editable.value ? '，可继续修改后另存新版本' : ''}`
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
  if (!editable.value) {
    return props.readOnly ? '当前阶段不可编辑' : '由组长填写'
  }
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

const contributionText = (member) => {
  const value = contributions.value[member.studentId]
  if (!Number.isFinite(value)) return '—'
  return `${allComplete.value ? '' : '≈'}${value.toFixed(1)}%`
}
const remainText = (row) => Math.abs(row.remaining) < ROW_TOLERANCE
  ? '0 ✓' : String(Math.round(row.remaining * 100) / 100)
const remainClass = (row) => {
  if (row.remaining > ROW_TOLERANCE) return 'wl-remain--pending'
  if (row.remaining < -ROW_TOLERANCE) return 'wl-remain--over'
  return 'wl-remain--done'
}

const fillDraft = (items) => {
  Object.keys(draft).forEach((key) => delete draft[key])
  for (const part of props.parts) {
    for (const member of props.members) draft[cellKey(part.id, member.studentId)] = ''
  }
  for (const item of items) {
    const key = cellKey(item.assignmentPartId, item.studentId)
    if (key in draft) draft[key] = String(Math.round(Number(item.share) * 10000) / 100)
  }
}

const emitStatus = () => emit('status', {
  hasVersion: Boolean(workload.value),
  status: workload.value?.status ?? null,
  versionNo: workload.value?.versionNo ?? null,
  locked: workload.value?.status === 'LOCKED',
  autoLocked: autoLocked.value,
  complete: allComplete.value
})

const load = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await getWorkload(props.assignmentId)
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
  }
}

const submit = async () => {
  if (!canSubmit.value) return
  saving.value = true
  errorMessage.value = ''
  try {
    await proposeWorkload(props.assignmentId, props.groupId,
      buildAllocationPayload(props.parts, props.members, draft))
    ElMessage.success('分工已保存，评分将采用这一版本')
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

.wl-remain {
  font-size: 12px;
  text-align: right !important;
  white-space: nowrap;
}

.wl-remain--done { color: #2e8a62; }
.wl-remain--pending { color: #c1832f; }
.wl-remain--over { color: #c0483f; }

.wl-note {
  margin: 12px 0 0;
  color: #9aa6b5;
  font-size: 11px;
}

.wl-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #eef2f8;
}

.wl-label {
  color: #8795a7;
  font-size: 11px;
}

.wl-contrib-list {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 6px;
  color: #60738c;
  font-size: 12px;
}

.wl-contrib-list b {
  color: #4575d2;
  font-weight: 500;
}

.wl-actions {
  display: grid;
  gap: 7px;
  justify-items: end;
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
  .wl-actions { justify-items: start; width: 100%; }
}
</style>
