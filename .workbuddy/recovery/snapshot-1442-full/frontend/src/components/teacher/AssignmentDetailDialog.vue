<template>
  <el-dialog v-model="visible" title="作业详情" width="760px" :close-on-click-modal="false">
    <div v-if="loading" class="ad-loading">正在加载作业详情…</div>

    <template v-else-if="detail">
      <div class="ad-meta">
        <span class="ad-status" :class="{ 'ad-status--muted': archived }">{{ statusLabel }}</span>
        <span>团队 {{ detail.groupCount || 0 }} 个</span>
        <span>提交 {{ detail.submissionCount || 0 }} 份</span>
        <span>{{ detail.scorePublished ? '成绩已发布' : '成绩未发布' }}</span>
      </div>

      <p v-if="archived" class="ad-notice ad-notice--warn">该作业已归档，不能编辑或再次删除。</p>
      <p v-else-if="partsLocked" class="ad-notice">
        该作业已有团队、工作量、提交或评分记录，评价分支与标准团队人数已锁定；其余信息仍可修改。
      </p>
      <p v-if="scorePublished" class="ad-notice">成绩已发布，作业总分不可修改。</p>
      <div v-if="errorMessage" class="ad-error">{{ errorMessage }}</div>

      <div class="ad-grid">
        <label class="ad-full">
          <span>作业名称</span>
          <input v-model.trim="form.title" maxlength="200" :disabled="readonly" />
        </label>
        <label class="ad-full">
          <span>作业说明</span>
          <textarea v-model="form.description" maxlength="5000" rows="3" :disabled="readonly" />
        </label>
        <label>
          <span>截止时间</span>
          <input v-model="form.deadline" type="datetime-local" :disabled="readonly" />
        </label>
        <label>
          <span>标准团队人数</span>
          <select v-model.number="form.standardGroupSize" :disabled="readonly || partsLocked">
            <option :value="1">1 人</option>
            <option :value="2">2 人</option>
            <option :value="3">3 人</option>
            <option :value="4">4 人</option>
          </select>
        </label>
        <label>
          <span>作业总分</span>
          <input v-model.number="form.totalScore" type="number" min="1" max="100" :disabled="readonly || scorePublished" />
        </label>
      </div>

      <section class="ad-branches">
        <div class="ad-branches-head">
          <h3>评价分支</h3>
          <span :class="{ 'ad-weight--invalid': !validation.valid && weightMismatch }">
            权重合计 {{ validation.weightTotal }}%
          </span>
        </div>
        <div v-for="(branch, index) in form.branches" :key="index" class="ad-branch">
          <span class="ad-branch-no">{{ index + 1 }}</span>
          <input v-model.trim="branch.name" maxlength="255" placeholder="分支名称" :disabled="readonly || partsLocked" />
          <input v-model="branch.description" maxlength="2000" placeholder="分支说明（可选）" :disabled="readonly || partsLocked" />
          <div class="ad-weight">
            <input v-model.number="branch.weightPercent" type="number" min="0" max="100" :disabled="readonly || partsLocked" />
            <span>%</span>
          </div>
          <button
            v-if="form.branches.length > 1"
            type="button"
            class="ad-remove"
            :disabled="readonly || partsLocked"
            @click="form.branches.splice(index, 1)"
          >删除</button>
        </div>
        <button type="button" class="ad-add" :disabled="readonly || partsLocked" @click="addBranch">
          ＋ 添加评价分支
        </button>
        <p class="ad-hint">{{ validation.valid ? '权重配置正确' : validation.message }}</p>
      </section>
    </template>

    <div v-else-if="errorMessage" class="ad-error">{{ errorMessage }}</div>

    <template #footer>
      <button type="button" class="ad-danger" :disabled="!detail || readonly || deleting || saving" @click="remove">
        {{ deleting ? '删除中…' : '删除作业' }}
      </button>
      <span class="ad-spacer" />
      <button type="button" class="ad-cancel" @click="visible = false">取消</button>
      <button type="button" class="ad-primary" :disabled="!canSave" @click="save">
        {{ saving ? '保存中…' : '保存修改' }}
      </button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElDialog, ElMessage, ElMessageBox } from 'element-plus'
import { deleteAssignment, getAssignmentDetail, updateAssignment } from '@/api/teacher'
import { getErrorMessage } from '@/api/response'
import {
  buildAssignmentPayload,
  normalizeParts,
  toDateTimeLocal,
  validateAssignmentForm
} from '@/utils/assignmentForm'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  assignmentId: { type: [String, Number], default: null }
})
const emit = defineEmits(['update:modelValue', 'saved', 'deleted'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const errorMessage = ref('')
const detail = ref(null)
const form = ref({ title: '', description: '', deadline: '', standardGroupSize: 4, totalScore: 100, branches: [] })

const statusText = { DRAFT: '草稿', PUBLISHED: '已发布', CLOSED: '已结束', ACTIVE: '进行中', ENDED: '已结束', ARCHIVED: '已归档' }
const statusLabel = computed(() => statusText[detail.value?.status] || detail.value?.status || '未知状态')
const archived = computed(() => detail.value?.status === 'ARCHIVED')
const readonly = computed(() => archived.value)
const partsLocked = computed(() => Boolean(detail.value?.partsLocked))
const scorePublished = computed(() => Boolean(detail.value?.scorePublished))
const validation = computed(() => validateAssignmentForm(form.value))
const weightMismatch = computed(() => Math.abs(validation.value.weightTotal - 100) > 0.001)
const canSave = computed(() => Boolean(detail.value) && !readonly.value && !loading.value
  && !saving.value && validation.value.valid)

const addBranch = () => form.value.branches.push({ name: '', description: '', weightPercent: 0 })

const load = async () => {
  loading.value = true
  errorMessage.value = ''
  detail.value = null
  try {
    const data = await getAssignmentDetail(props.assignmentId)
    detail.value = data
    form.value = {
      title: data.title ?? '',
      description: data.description ?? '',
      deadline: toDateTimeLocal(data.deadline),
      standardGroupSize: Number(data.standardGroupSize ?? 4),
      totalScore: Number(data.totalScore ?? 100),
      branches: normalizeParts(data.parts)
    }
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '作业详情加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const save = async () => {
  if (!validation.value.valid) {
    ElMessage.warning(validation.value.message)
    return
  }
  saving.value = true
  errorMessage.value = ''
  try {
    await updateAssignment(props.assignmentId, buildAssignmentPayload(form.value))
    ElMessage.success('作业已保存')
    emit('saved')
    visible.value = false
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '保存作业失败')
  } finally {
    saving.value = false
  }
}

const remove = async () => {
  try {
    await ElMessageBox.confirm(
      '删除后该作业将从作业列表和学生端移除；已产生的提交、评分与审计记录会保留以供追溯。',
      '删除作业',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  deleting.value = true
  errorMessage.value = ''
  try {
    await deleteAssignment(props.assignmentId)
    ElMessage.success('作业已删除')
    emit('deleted')
    visible.value = false
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '删除作业失败')
  } finally {
    deleting.value = false
  }
}

watch(() => props.modelValue, (open) => {
  if (open && props.assignmentId != null) load()
})
</script>

<style scoped>
.ad-loading{padding:28px 0;text-align:center;color:#7c8da0;font-size:13px}
.ad-meta{display:flex;flex-wrap:wrap;align-items:center;gap:10px 18px;margin-bottom:16px;padding:12px 14px;border:1px solid #e8eef7;border-radius:8px;background:#f8fbfe;color:#61748b;font-size:12px}
.ad-status{padding:3px 10px;border-radius:999px;color:#2e7d57;background:#e7f6ee;font-size:12px}
.ad-status--muted{color:#7d8b9b;background:#eef1f4}
.ad-notice{margin:0 0 12px;padding:10px 12px;border-radius:7px;color:#4a6a99;background:#eef4fd;font-size:12px;line-height:1.6}
.ad-notice--warn{color:#96591c;background:#fdf3e6}
.ad-error{margin-bottom:12px;padding:10px 12px;border:1px solid #f2c9c4;border-radius:7px;color:#a5362c;background:#fdf1ef;font-size:12px;line-height:1.6}
.ad-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}
.ad-grid label{display:grid;gap:7px;color:#52657a;font-size:12px}
.ad-grid .ad-full{grid-column:1/-1}
.ad-grid input,.ad-grid textarea,.ad-grid select,.ad-branch input{box-sizing:border-box;width:100%;padding:9px 11px;border:1px solid #dbe3ea;border-radius:6px;outline:0;color:#334155;background:#fff;font:inherit;font-size:13px}
.ad-grid textarea{resize:vertical;line-height:1.6}
.ad-grid input:focus,.ad-grid textarea:focus,.ad-grid select:focus,.ad-branch input:focus{border-color:#6b9ed8}
.ad-grid input:disabled,.ad-grid textarea:disabled,.ad-grid select:disabled,.ad-branch input:disabled{color:#8492a3;background:#f4f6f9;cursor:not-allowed}
.ad-branches{margin-top:20px;padding-top:18px;border-top:1px solid #edf1f4}
.ad-branches-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}
.ad-branches-head h3{margin:0;color:#263b53;font-size:14px;font-weight:500}
.ad-branches-head span{color:#3b7bbf;font-size:12px}
.ad-weight--invalid{color:#b42318!important}
.ad-branch{display:flex;align-items:center;gap:9px;margin-bottom:9px}
.ad-branch-no{flex:0 0 24px;height:24px;display:grid;place-items:center;border-radius:50%;color:#fff;background:#6b9ed8;font-size:12px}
.ad-branch input:nth-of-type(1){flex:1.1}
.ad-branch input:nth-of-type(2){flex:1.6}
.ad-weight{flex:0 0 92px;display:flex;align-items:center;gap:5px;color:#7c8da0;font-size:12px}
.ad-remove{border:0;color:#b42318;background:none;font-size:12px;cursor:pointer}
.ad-remove:disabled{color:#b6c0cc;cursor:not-allowed}
.ad-add{padding:8px 12px;border:1px dashed #aac5dc;border-radius:6px;color:#356798;background:#f8fbfd;font-size:12px;cursor:pointer}
.ad-add:disabled{color:#a5b1bf;background:#f5f7f9;cursor:not-allowed}
.ad-hint{margin:12px 0 0;color:#8a98a8;font-size:12px}
.ad-spacer{flex:1}
.ad-danger,.ad-cancel,.ad-primary{padding:9px 18px;border-radius:6px;font-size:13px;cursor:pointer}
.ad-danger{border:1px solid #eecbc6;color:#a5362c;background:#fff}
.ad-cancel{border:1px solid #dbe3ea;color:#5a6b7f;background:#fff}
.ad-primary{border:0;color:#fff;background:#356798}
.ad-danger:disabled,.ad-primary:disabled{opacity:.5;cursor:not-allowed}
</style>
