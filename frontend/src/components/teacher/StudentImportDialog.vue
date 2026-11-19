<template>
  <el-dialog v-model="visible" title="导入学生" width="840px" :close-on-click-modal="false">
    <p class="si-help">从系统班级库选择班级整班导入，也可粘贴学号补录；两种方式可同时使用，重复学生只导入一次。</p>

    <div class="si-tabs">
      <button type="button" :class="{ active: tab === 'classes' }" @click="tab = 'classes'">从班级库导入</button>
      <button type="button" :class="{ active: tab === 'numbers' }" @click="tab = 'numbers'">
        按学号导入<span v-if="parsedNos.length" class="si-badge">{{ parsedNos.length }}</span>
      </button>
    </div>

    <section v-if="tab === 'classes'" class="si-classes">
      <div class="si-toolbar">
        <input v-model="keyword" class="si-search" placeholder="搜索班级、院系或年级" />
        <button type="button" :disabled="!filteredClasses.length" @click="selectAllVisible">全选可见</button>
        <button type="button" :disabled="!selectedIds.length" @click="selectedIds = []">清空</button>
      </div>

      <div v-if="loading" class="si-state">正在加载班级库…</div>
      <div v-else-if="errorMessage" class="si-state si-state--error">
        {{ errorMessage }}<button type="button" @click="loadClasses">重试</button>
      </div>
      <div v-else-if="!filteredClasses.length" class="si-state">
        {{ classes.length ? '没有匹配的班级。' : '班级库暂无可用班级。' }}学生账号需先在管理员端「组织与账号」中分配班级。
      </div>

      <div v-else class="si-class-list">
        <article v-for="item in filteredClasses" :key="item.id" class="si-class-row" :class="{ selected: isSelected(item.id) }">
          <div class="si-class-head">
            <label class="si-class-main">
              <input type="checkbox" :checked="isSelected(item.id)" @change="toggleClass(item.id)" />
              <span class="si-class-name">{{ item.name || '未命名班级' }}</span>
              <span class="si-class-sub">{{ item.departmentName || '未分配院系' }} · {{ item.grade || '未设置年级' }}</span>
            </label>
            <span class="si-class-counts">
              共 {{ counts(item).studentCount }} 人 · 已在课程 {{ counts(item).enrolledCount }} 人 ·
              <b>可导入 {{ counts(item).pendingCount }} 人</b>
            </span>
            <button type="button" class="si-preview-button" @click="togglePreview(item.id)">
              {{ String(previewId) === String(item.id) ? '收起名册' : '查看名册' }}
            </button>
          </div>

          <div v-if="String(previewId) === String(item.id)" class="si-roster">
            <div v-if="rosterLoading" class="si-state">正在加载班级名册…</div>
            <div v-else-if="rosterError" class="si-state si-state--error">
              {{ rosterError }}<button type="button" @click="togglePreview(item.id, true)">重试</button>
            </div>
            <div v-else-if="!roster.length" class="si-state">该班级暂无有效学生账号。</div>
            <ul v-else class="si-roster-list">
              <li v-for="student in roster" :key="student.studentId">
                <strong>{{ student.name || '未命名' }}</strong>
                <span>{{ student.studentNo || '—' }}</span>
                <span>{{ student.majorName || '—' }}</span>
                <i :class="{ enrolled: student.enrolled }">{{ student.enrolled ? '已在课程' : '待导入' }}</i>
              </li>
            </ul>
          </div>
        </article>
      </div>
    </section>

    <section v-else class="si-numbers">
      <p class="si-note">每行一个学号，也支持逗号、空格或分号分隔；可直接粘贴表格列，或选择 .csv / .txt 文件。系统只会加入已有学生账号。</p>
      <label class="si-file">
        选择 .csv / .txt 文件
        <input type="file" accept=".csv,.txt,text/csv,text/plain" @change="onFileChange" />
      </label>
      <textarea v-model="importText" class="si-textarea" rows="8" :placeholder="'20260001\n20260002'" />
      <p class="si-hint">已识别 {{ parsedNos.length }} 个学号（单次最多 {{ MAX_STUDENT_NOS }} 个）</p>
    </section>

    <div v-if="result" class="si-result">
      <strong>{{ formatImportResult(result) }}</strong>
      <p v-if="result.notFound?.length">未找到的学号：{{ result.notFound.join('、') }}</p>
      <p>已在课程中的学生不会重复导入；此前被移除的学生会被重新加入。</p>
    </div>

    <template #footer>
      <span class="si-summary">{{ validationMessage || summaryText }}</span>
      <span class="si-spacer" />
      <button type="button" class="si-cancel" @click="visible = false">{{ result ? '完成' : '取消' }}</button>
      <button v-if="!result" type="button" class="si-primary" :disabled="!canSubmit" @click="submit">
        {{ importing ? '导入中…' : '批量导入' }}
      </button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElDialog, ElMessage } from 'element-plus'
import { getClassRoster, getImportClasses, importStudents } from '@/api/teacher'
import { getErrorMessage } from '@/api/response'
import {
  MAX_STUDENT_NOS,
  buildImportPayload,
  classCounts,
  classImportEstimate,
  filterClasses,
  formatImportResult,
  parseStudentNos,
  validateImportPayload
} from '@/utils/studentImport'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  courseId: { type: [String, Number], required: true }
})
const emit = defineEmits(['update:modelValue', 'imported'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const tab = ref('classes')
const classes = ref([])
const loading = ref(false)
const errorMessage = ref('')
const keyword = ref('')
const selectedIds = ref([])
const previewId = ref(null)
const roster = ref([])
const rosterLoading = ref(false)
const rosterError = ref('')
const rosterCache = ref({})
const importText = ref('')
const importing = ref(false)
const result = ref(null)

const counts = classCounts
const filteredClasses = computed(() => filterClasses(classes.value, keyword.value))
const parsedNos = computed(() => parseStudentNos(importText.value))
const payload = computed(() => buildImportPayload({ classIds: selectedIds.value, studentNos: parsedNos.value }))
const estimate = computed(() => classImportEstimate(classes.value, selectedIds.value))
const validationMessage = computed(() => validateImportPayload(payload.value))
const canSubmit = computed(() => !importing.value && !loading.value && !validationMessage.value && !!payload.value)
const summaryText = computed(() => {
  const parts = []
  if (selectedIds.value.length) parts.push(`已选 ${selectedIds.value.length} 个班级（预计 ${estimate.value} 人）`)
  if (parsedNos.value.length) parts.push(`补充学号 ${parsedNos.value.length} 个`)
  return parts.length ? parts.join(' · ') : '尚未选择班级或填写学号'
})

const isSelected = (id) => selectedIds.value.some((value) => String(value) === String(id))

const toggleClass = (id) => {
  const key = String(id)
  selectedIds.value = isSelected(id)
    ? selectedIds.value.filter((value) => String(value) !== key)
    : [...selectedIds.value, key]
}

const selectAllVisible = () => {
  selectedIds.value = [...new Set([
    ...selectedIds.value.map(String),
    ...filteredClasses.value.map((item) => String(item.id))
  ])]
}

const loadClasses = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const list = await getImportClasses(props.courseId)
    classes.value = Array.isArray(list) ? list : []
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '班级库加载失败')
    classes.value = []
  } finally {
    loading.value = false
  }
}

const togglePreview = async (id, force = false) => {
  if (!force && String(previewId.value) === String(id)) {
    previewId.value = null
    roster.value = []
    return
  }
  previewId.value = id
  rosterError.value = ''
  const cached = force ? null : rosterCache.value[id]
  if (cached) {
    roster.value = cached
    return
  }
  rosterLoading.value = true
  try {
    const list = await getClassRoster(props.courseId, id)
    roster.value = Array.isArray(list) ? list : []
    rosterCache.value = { ...rosterCache.value, [id]: roster.value }
  } catch (error) {
    rosterError.value = getErrorMessage(error, '班级名册加载失败')
    roster.value = []
  } finally {
    rosterLoading.value = false
  }
}

const readFileText = (file) => {
  if (typeof file.text === 'function') return file.text()
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('read failed'))
    reader.readAsText(file, 'UTF-8')
  })
}

const onFileChange = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  try {
    const text = await readFileText(file)
    importText.value = importText.value.trim() ? `${importText.value.trim()}\n${text}` : text
  } catch {
    ElMessage.error('文件读取失败，请改用粘贴方式')
  } finally {
    event.target.value = ''
  }
}

const submit = async () => {
  if (!canSubmit.value || !payload.value) return
  importing.value = true
  try {
    const data = await importStudents(props.courseId, payload.value)
    result.value = data || { success: 0, skipped: 0, notFound: [] }
    ElMessage.success(formatImportResult(result.value))
    emit('imported')
    // 导入后班级的「已在课程」人数变化，刷新列表与名册缓存以便继续导入其它班级
    rosterCache.value = {}
    await loadClasses()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '导入学生失败'))
  } finally {
    importing.value = false
  }
}

watch(() => props.modelValue, (open) => {
  if (!open) return
  tab.value = 'classes'
  keyword.value = ''
  selectedIds.value = []
  previewId.value = null
  roster.value = []
  rosterCache.value = {}
  rosterError.value = ''
  importText.value = ''
  importing.value = false
  result.value = null
  errorMessage.value = ''
  loadClasses()
})
</script>

<style scoped>
.si-help{margin:0 0 14px;color:#6b7c90;font-size:13px;line-height:1.6}
.si-tabs{display:flex;gap:8px;margin-bottom:14px;border-bottom:1px solid #e6edf3}
.si-tabs button{padding:9px 16px;border:0;border-bottom:2px solid transparent;color:#718096;background:none;font-size:13px;cursor:pointer}
.si-tabs button.active{color:#356798;border-bottom-color:#356798;font-weight:600}
.si-badge{display:inline-block;margin-left:6px;padding:1px 7px;border-radius:10px;color:#fff;background:#6b9ed8;font-size:11px}
.si-toolbar{display:flex;align-items:center;gap:8px;margin-bottom:12px}
.si-search{flex:1;min-width:0;padding:9px 12px;border:1px solid #dce5ec;border-radius:6px;color:#334155;font-size:13px;outline:0}
.si-search:focus{border-color:#8ab0d4}
.si-toolbar button{padding:8px 12px;border:1px solid #dce5ec;border-radius:6px;color:#52657a;background:#fff;font-size:12px;cursor:pointer}
.si-toolbar button:disabled{color:#b5c0ca;background:#f5f7f9;cursor:not-allowed}
.si-state{padding:22px;text-align:center;color:#8795a6;background:#f8fbfd;border-radius:6px;font-size:13px}
.si-state--error{color:#a5362c;background:#fff5f4}
.si-state button{margin-left:10px;padding:5px 10px;border:1px solid currentColor;border-radius:5px;color:inherit;background:none;cursor:pointer}
.si-class-list{max-height:340px;overflow-y:auto;padding-right:4px}
.si-class-row{margin-bottom:8px;border:1px solid #e8edf2;border-radius:7px;background:#fff}
.si-class-row.selected{border-color:#8ab0d4;background:#f6faff}
.si-class-head{display:flex;align-items:center;gap:12px;padding:11px 14px}
.si-class-main{display:flex;align-items:center;gap:9px;flex:1;min-width:0;cursor:pointer}
.si-class-main input{flex:0 0 auto;accent-color:#356798}
.si-class-name{color:#263b53;font-size:13px;font-weight:600;white-space:nowrap}
.si-class-sub{color:#8795a6;font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.si-class-counts{color:#718096;font-size:12px;white-space:nowrap}
.si-class-counts b{color:#356798}
.si-preview-button{padding:5px 9px;border:1px solid #dce5ec;border-radius:5px;color:#356798;background:#fff;font-size:12px;cursor:pointer;white-space:nowrap}
.si-roster{padding:10px 14px 14px;border-top:1px solid #edf1f4}
.si-roster-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:6px;max-height:190px;overflow-y:auto;margin:0;padding:0;list-style:none}
.si-roster-list li{display:flex;align-items:center;gap:8px;padding:6px 9px;border-radius:5px;background:#f8fbfd;color:#718096;font-size:12px}
.si-roster-list strong{color:#334155;font-weight:500}
.si-roster-list i{margin-left:auto;padding:2px 7px;border-radius:4px;color:#356798;background:#eaf2fa;font-style:normal}
.si-roster-list i.enrolled{color:#4a8f6b;background:#eaf6ef}
.si-note{margin:0 0 12px;color:#6b7c90;font-size:13px;line-height:1.6}
.si-file{display:inline-flex;align-items:center;gap:8px;margin-bottom:10px;padding:8px 12px;border:1px dashed #aac5dc;border-radius:6px;color:#356798;background:#f8fbfd;font-size:12px;cursor:pointer}
.si-file input{display:none}
.si-textarea{width:100%;padding:11px 12px;border:1px solid #dce5ec;border-radius:6px;color:#334155;font-size:13px;line-height:1.6;resize:vertical;outline:0;box-sizing:border-box}
.si-textarea:focus{border-color:#8ab0d4}
.si-hint{margin:8px 0 0;color:#8795a6;font-size:12px}
.si-result{margin-top:14px;padding:12px 14px;border:1px solid #cfe4d6;border-radius:6px;background:#f3fbf6;color:#3a7a58;font-size:13px}
.si-result p{margin:6px 0 0;color:#5a7f6b;font-size:12px;word-break:break-all}
.si-summary{color:#718096;font-size:12px}
.si-spacer{flex:1}
.si-cancel,.si-primary{padding:9px 18px;border-radius:6px;font-size:13px;cursor:pointer}
.si-cancel{border:1px solid #dbe3ea;color:#5a6b7f;background:#fff}
.si-primary{border:0;color:#fff;background:#356798}
.si-primary:disabled{opacity:.5;cursor:not-allowed}
@media (max-width:700px){
  .si-class-head{flex-wrap:wrap}
  .si-class-counts{order:3;width:100%}
  .si-roster-list{grid-template-columns:1fr}
}
</style>
