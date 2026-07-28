<template>
  <div>
    <PageHeader title="组织与账号" description="维护院系、专业、班级概况以及教师和学生账号。">
      <template #actions>
        <el-button :icon="Refresh" :loading="overviewLoading || accountsLoading" @click="reloadAll">刷新</el-button>
        <el-button :icon="Upload" @click="openImport">批量导入</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增账号</el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="page-tabs">
      <el-tab-pane label="组织概况" name="overview">
        <div v-loading="overviewLoading">
          <div class="metric-grid">
            <article v-for="item in organizationMetrics" :key="item.label" class="surface-card metric-card">
              <div class="metric-card__label">{{ item.label }}</div>
              <div class="metric-card__value">{{ displayValue(item.value) }}</div>
              <div class="metric-card__hint">{{ item.hint }}</div>
            </article>
          </div>

          <section class="surface-card table-card">
            <div class="table-card__header">
              <h2>组织结构</h2>
              <span class="muted-text">院系 / 专业 / 班级</span>
            </div>
            <el-table
              v-if="organizationRows.length"
              :data="organizationRows"
              row-key="treeKey"
              :tree-props="{ children: 'children' }"
              default-expand-all
            >
              <el-table-column prop="name" label="名称" min-width="220" />
              <el-table-column prop="typeLabel" label="类型" width="100" />
              <el-table-column prop="code" label="编码" min-width="130">
                <template #default="{ row }">{{ displayValue(row.code) }}</template>
              </el-table-column>
              <el-table-column prop="memberCount" label="关联人数" width="110" align="right">
                <template #default="{ row }">{{ displayValue(row.memberCount) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无组织结构数据" :image-size="80" />
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="账号管理" name="accounts">
        <section class="surface-card filter-card">
          <div class="filter-row">
            <el-input v-model.trim="filters.keyword" clearable placeholder="账号或姓名" @keyup.enter="searchAccounts" />
            <el-select v-model="filters.role" clearable placeholder="全部角色">
              <el-option label="教师" value="ROLE_TEACHER" />
              <el-option label="学生" value="ROLE_STUDENT" />
            </el-select>
            <el-select v-model="filters.status" clearable placeholder="全部状态">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="searchAccounts">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </div>
        </section>

        <section class="surface-card table-card">
          <div class="table-card__header">
            <h2>账号列表</h2>
            <span class="muted-text">共 {{ accountPage.total }} 条</span>
          </div>
          <el-table v-loading="accountsLoading" :data="accountPage.items">
            <el-table-column prop="username" label="账号" min-width="130" />
            <el-table-column prop="name" label="姓名" min-width="110">
              <template #default="{ row }">{{ displayValue(row.name || row.realName) }}</template>
            </el-table-column>
            <el-table-column prop="role" label="角色" width="90">
              <template #default="{ row }">{{ roleLabel(row.role) }}</template>
            </el-table-column>
            <el-table-column prop="organizationName" label="所属组织" min-width="180">
              <template #default="{ row }">{{ displayValue(row.organizationName || row.className || row.departmentName) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="isAccountEnabled(row) ? 'success' : 'info'">
                  {{ isAccountEnabled(row) ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" min-width="150">
              <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.updateTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="225" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="primary" @click="toggleStatus(row)">{{ isAccountEnabled(row) ? '停用' : '启用' }}</el-button>
                <el-button link type="primary" @click="resetPassword(row)">重置密码</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="pagination.page"
              v-model:page-size="pagination.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="accountPage.total"
              layout="total, sizes, prev, pager, next"
              @current-change="loadAccounts"
              @size-change="handlePageSizeChange"
            />
          </div>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="accountDialogVisible"
      :title="editingId ? '编辑账号' : '新增账号'"
      width="560px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="accountFormRef" :model="accountForm" :rules="accountRules" label-position="top">
        <div class="form-grid">
          <el-form-item label="角色" prop="role">
            <el-select v-model="accountForm.role" :disabled="Boolean(editingId)" @change="clearOrganizationSelection">
              <el-option label="教师" value="ROLE_TEACHER" />
              <el-option label="学生" value="ROLE_STUDENT" />
            </el-select>
          </el-form-item>
          <el-form-item label="账号" prop="username">
            <el-input v-model.trim="accountForm.username" :disabled="Boolean(editingId)" />
          </el-form-item>
          <el-form-item label="姓名" prop="name">
            <el-input v-model.trim="accountForm.name" />
          </el-form-item>
          <el-form-item label="院系">
            <el-select v-model="accountForm.departmentId" clearable filterable @change="clearMajorAndClass">
              <el-option v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="accountForm.role === 'ROLE_STUDENT'" label="专业">
            <el-select v-model="accountForm.majorId" clearable filterable @change="accountForm.classId = null">
              <el-option v-for="item in availableMajors" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="accountForm.role === 'ROLE_STUDENT'" label="班级">
            <el-select v-model="accountForm.classId" clearable filterable>
              <el-option v-for="item in availableClasses" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="accountForm.role === 'ROLE_STUDENT'" label="年级">
            <el-input-number v-model="accountForm.grade" :min="2000" :max="2100" controls-position="right" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="accountDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingAccount" @click="saveAccount">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="credentialVisible"
      title="账号已创建"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-alert
        title="临时密码仅在本次创建结果中返回，请立即安全交付给用户。用户首次登录后必须修改密码。"
        type="warning"
        :closable="false"
        show-icon
        class="dialog-alert"
      />
      <el-descriptions :column="1" border>
        <el-descriptions-item label="账号">{{ createdCredential.username }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ roleLabel(createdCredential.role) }}</el-descriptions-item>
        <el-descriptions-item label="临时密码">
          <span class="credential-value">{{ createdCredential.temporaryPassword }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="credentialVisible = false">我已保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importDialogVisible"
      title="批量导入账号"
      width="820px"
      :close-on-click-modal="false"
      @closed="resetImportDialog"
    >
      <el-alert
        title="文件必须为 UTF-8 编码的 CSV，大小不超过 2MB，单次最多 5000 行。空白初始密码将由系统自动生成。"
        type="info"
        :closable="false"
        show-icon
        class="dialog-alert"
      />

      <div v-if="!importReport" class="import-guide">
        <div class="import-guide__title">
          <span>固定表头（顺序不可变）</span>
          <el-button link type="primary" @click="downloadImportTemplate">下载 UTF-8 模板</el-button>
        </div>
        <code>role,username,displayName,departmentId,majorId,classId,grade,initialPassword</code>
        <p>角色填写 ROLE_TEACHER 或 ROLE_STUDENT；教师填写 departmentId，学生填写 majorId、classId 和 grade。</p>
        <el-upload
          ref="importUploadRef"
          v-model:file-list="importFileList"
          drag
          :auto-upload="false"
          :limit="1"
          accept=".csv,text/csv"
          :on-change="handleImportChange"
          :on-exceed="handleImportExceed"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">将 CSV 文件拖到此处，或<em>点击选择</em></div>
          <template #tip><div class="el-upload__tip">选中文件后点击“开始导入”，系统会逐行返回结果。</div></template>
        </el-upload>
      </div>

      <div v-else class="import-report">
        <div class="import-batch">导入批次：<code>{{ importReport.batchNo }}</code></div>
        <div class="import-stat-grid">
          <div><span>总行数</span><strong>{{ importReport.totalRows }}</strong></div>
          <div class="success-stat"><span>成功</span><strong>{{ importReport.successRows }}</strong></div>
          <div class="failure-stat"><span>失败</span><strong>{{ importReport.failureRows }}</strong></div>
        </div>

        <section v-if="importReport.createdAccounts?.length" class="report-section">
          <h3>已创建账号与临时密码</h3>
          <p class="report-hint">临时密码只应安全交付给对应用户，用户首次登录后必须修改。</p>
          <el-table :data="importReport.createdAccounts" max-height="260">
            <el-table-column prop="username" label="账号" min-width="130" />
            <el-table-column prop="role" label="角色" width="90">
              <template #default="{ row }">{{ roleLabel(row.role) }}</template>
            </el-table-column>
            <el-table-column prop="temporaryPassword" label="临时密码" min-width="180">
              <template #default="{ row }"><span class="credential-value">{{ row.temporaryPassword }}</span></template>
            </el-table-column>
          </el-table>
        </section>

        <section v-if="importReport.errors?.length" class="report-section">
          <h3>失败明细</h3>
          <el-table :data="importReport.errors" max-height="240">
            <el-table-column prop="row" label="CSV 行号" width="100" />
            <el-table-column prop="message" label="失败原因" min-width="320" />
          </el-table>
        </section>
      </div>

      <template #footer>
        <el-button @click="importDialogVisible = false">关闭</el-button>
        <el-button v-if="importReport" @click="prepareAnotherImport">导入另一批</el-button>
        <el-button v-else type="primary" :loading="importing" :disabled="!importFileList.length" @click="submitImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
  ElUpload
} from 'element-plus'
import { Plus, Refresh, Search, Upload, UploadFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  createAccount,
  getAccounts,
  getOrganizationOverview,
  importAccounts,
  resetAccountPassword,
  updateAccount,
  updateAccountStatus
} from '@/api/admin'
import { getErrorMessage } from '@/api/response'
import { displayValue, formatDateTime } from '@/utils/format'
import { normalizeRole, roleLabel } from '@/utils/roles'

const activeTab = ref('overview')
const overviewLoading = ref(false)
const accountsLoading = ref(false)
const savingAccount = ref(false)
const overview = ref({})
const accountPage = ref({ items: [], total: 0 })
const pagination = reactive({ page: 1, pageSize: 10 })
const filters = reactive({ keyword: '', role: '', status: '' })

const accountDialogVisible = ref(false)
const editingId = ref(null)
const accountFormRef = ref()
const credentialVisible = ref(false)
const createdCredential = ref({})
const importDialogVisible = ref(false)
const importUploadRef = ref()
const importFileList = ref([])
const importReport = ref(null)
const importing = ref(false)
const accountForm = reactive({
  role: 'ROLE_STUDENT',
  username: '',
  name: '',
  departmentId: null,
  majorId: null,
  classId: null,
  grade: null
})
const accountRules = {
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

const departments = computed(() => Array.isArray(overview.value.departments) ? overview.value.departments : [])
const availableMajors = computed(() => {
  const department = departments.value.find((item) => String(item.id) === String(accountForm.departmentId))
  return Array.isArray(department?.majors) ? department.majors : []
})
const availableClasses = computed(() => {
  const major = availableMajors.value.find((item) => String(item.id) === String(accountForm.majorId))
  return Array.isArray(major?.classes) ? major.classes : []
})

const organizationMetrics = computed(() => {
  const summary = overview.value.summary || overview.value
  return [
    { label: '院系', value: summary.departmentCount, hint: '学校教学组织' },
    { label: '专业', value: summary.majorCount, hint: '纳入平台的专业' },
    { label: '班级', value: summary.classCount, hint: '当前班级数量' },
    { label: '师生账号', value: summary.accountCount, hint: '教师与学生账号总数' }
  ]
})

const organizationRows = computed(() => departments.value.map((department, departmentIndex) => ({
  treeKey: `department-${department.id ?? departmentIndex}`,
  name: department.name,
  code: department.code,
  typeLabel: '院系',
  memberCount: department.memberCount ?? department.accountCount,
  children: (department.majors || []).map((major, majorIndex) => ({
    treeKey: `major-${major.id ?? `${departmentIndex}-${majorIndex}`}`,
    name: major.name,
    code: major.code,
    typeLabel: '专业',
    memberCount: major.memberCount ?? major.studentCount,
    children: (major.classes || []).map((classItem, classIndex) => ({
      treeKey: `class-${classItem.id ?? `${departmentIndex}-${majorIndex}-${classIndex}`}`,
      name: classItem.name,
      code: classItem.code,
      typeLabel: '班级',
      memberCount: classItem.memberCount ?? classItem.studentCount
    }))
  }))
})))

async function loadOverview() {
  overviewLoading.value = true
  try {
    overview.value = await getOrganizationOverview() || {}
  } catch (error) {
    overview.value = {}
    ElMessage.error(getErrorMessage(error, '组织概况加载失败'))
  } finally {
    overviewLoading.value = false
  }
}

async function loadAccounts() {
  accountsLoading.value = true
  try {
    accountPage.value = await getAccounts({
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: filters.keyword || undefined,
      role: filters.role || undefined,
      status: filters.status || undefined
    })
  } catch (error) {
    accountPage.value = { items: [], total: 0 }
    ElMessage.error(getErrorMessage(error, '账号列表加载失败'))
  } finally {
    accountsLoading.value = false
  }
}

function reloadAll() {
  return Promise.all([loadOverview(), loadAccounts()])
}

function searchAccounts() {
  pagination.page = 1
  loadAccounts()
}

function resetFilters() {
  filters.keyword = ''
  filters.role = ''
  filters.status = ''
  searchAccounts()
}

function handlePageSizeChange() {
  pagination.page = 1
  loadAccounts()
}

function resetAccountForm() {
  Object.assign(accountForm, {
    role: 'ROLE_STUDENT',
    username: '',
    name: '',
    departmentId: null,
    majorId: null,
    classId: null,
    grade: null
  })
  accountFormRef.value?.clearValidate()
}

function openCreate() {
  editingId.value = null
  resetAccountForm()
  accountDialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(accountForm, {
    role: normalizeRole(row.role),
    username: row.username,
    name: row.name || row.realName || '',
    departmentId: row.departmentId ?? null,
    majorId: row.majorId ?? null,
    classId: row.classId ?? null,
    grade: row.grade ?? null
  })
  accountDialogVisible.value = true
}

function clearOrganizationSelection() {
  accountForm.departmentId = null
  clearMajorAndClass()
}

function clearMajorAndClass() {
  accountForm.majorId = null
  accountForm.classId = null
}

async function saveAccount() {
  const valid = await accountFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingAccount.value = true
  try {
    const payload = {
      role: accountForm.role,
      displayName: accountForm.name,
      departmentId: accountForm.departmentId,
      majorId: accountForm.majorId,
      classId: accountForm.classId,
      grade: accountForm.grade
    }
    let created = null
    if (editingId.value) {
      await updateAccount(editingId.value, payload)
    } else {
      created = await createAccount({ ...payload, username: accountForm.username })
    }
    ElMessage.success(editingId.value ? '账号更新成功' : '账号创建成功')
    accountDialogVisible.value = false
    activeTab.value = 'accounts'
    await Promise.all([loadAccounts(), loadOverview()])
    if (created) {
      createdCredential.value = created
      credentialVisible.value = true
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '账号保存失败'))
  } finally {
    savingAccount.value = false
  }
}

function openImport() {
  resetImportDialog()
  importDialogVisible.value = true
}

function resetImportDialog() {
  importUploadRef.value?.clearFiles()
  importFileList.value = []
  importReport.value = null
  importing.value = false
}

function prepareAnotherImport() {
  resetImportDialog()
}

function handleImportChange(uploadFile) {
  const file = uploadFile.raw
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.csv')) {
    importFileList.value = []
    ElMessage.warning('请选择 CSV 文件')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    importFileList.value = []
    ElMessage.warning('账号导入文件不能超过 2MB')
  }
}

function handleImportExceed() {
  ElMessage.warning('单次只能选择一个 CSV 文件，请先移除当前文件')
}

function downloadImportTemplate() {
  const header = 'role,username,displayName,departmentId,majorId,classId,grade,initialPassword'
  const examples = [
    'ROLE_TEACHER,T2026001,示例教师,1,,,,',
    'ROLE_STUDENT,20260101,示例学生,,1,1,2026,'
  ]
  const blob = new Blob([`\uFEFF${header}\r\n${examples.join('\r\n')}\r\n`], {
    type: 'text/csv;charset=utf-8'
  })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = 'account-import-template.csv'
  anchor.click()
  URL.revokeObjectURL(url)
}

async function submitImport() {
  const file = importFileList.value[0]?.raw
  if (!file) {
    ElMessage.warning('请先选择 CSV 文件')
    return
  }

  importing.value = true
  try {
    importReport.value = await importAccounts(file)
    ElMessage.success(`导入完成：成功 ${importReport.value.successRows} 行，失败 ${importReport.value.failureRows} 行`)
    activeTab.value = 'accounts'
    await Promise.all([loadAccounts(), loadOverview()])
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '账号导入失败'))
  } finally {
    importing.value = false
  }
}

function isAccountEnabled(row) {
  if (typeof row.enabled === 'boolean') return row.enabled
  return ['ACTIVE', 'ENABLED', '1', 'TRUE'].includes(String(row.status ?? '').toUpperCase())
}

async function toggleStatus(row) {
  const enabled = isAccountEnabled(row)
  const nextStatus = enabled ? 0 : 1
  try {
    await ElMessageBox.confirm(`确认${enabled ? '停用' : '启用'}账号“${row.username}”吗？`, '账号状态', { type: 'warning' })
    await updateAccountStatus(row.id, row.role, nextStatus)
    ElMessage.success('账号状态已更新')
    await loadAccounts()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error, '状态更新失败'))
  }
}

async function resetPassword(row) {
  try {
    const { value: newPassword } = await ElMessageBox.prompt(
      `为账号“${row.username}”设置新密码。`,
      '重置密码',
      {
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        inputType: 'password',
        inputPlaceholder: '请输入 8–64 位且包含字母和数字的新密码',
        inputValidator: (value) => {
          if (!value) return '请输入新密码'
          if (!/^(?=.*[A-Za-z])(?=.*\d).{8,64}$/.test(value)) return '密码应为 8–64 位且包含字母和数字'
          return true
        }
      }
    )
    await resetAccountPassword(row.id, row.role, newPassword)
    ElMessage.success('密码已重置')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error, '密码重置失败'))
  }
}

onMounted(reloadAll)
</script>

<style scoped>
.page-tabs :deep(.el-tabs__header) {
  margin-bottom: 18px;
}

.muted-text {
  color: #94a3b8;
  font-size: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.form-grid :deep(.el-select) {
  width: 100%;
}

.form-grid :deep(.el-input-number) {
  width: 100%;
}

.dialog-alert {
  margin-bottom: 18px;
}

.credential-value {
  color: #0f172a;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.import-guide__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #334155;
  font-weight: 600;
}

.import-guide > code {
  display: block;
  overflow-x: auto;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  color: #334155;
  white-space: nowrap;
}

.import-guide > p,
.report-hint {
  margin: 8px 0 16px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.import-guide :deep(.el-upload) {
  width: 100%;
}

.import-guide :deep(.el-upload-dragger) {
  width: 100%;
}

.import-batch {
  margin-bottom: 12px;
  color: #64748b;
  font-size: 13px;
}

.import-stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.import-stat-grid > div {
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
}

.import-stat-grid span {
  display: block;
  margin-bottom: 6px;
  color: #64748b;
  font-size: 12px;
}

.import-stat-grid strong {
  color: #0f172a;
  font-size: 24px;
}

.import-stat-grid .success-stat strong {
  color: #059669;
}

.import-stat-grid .failure-stat strong {
  color: #dc2626;
}

.report-section + .report-section {
  margin-top: 20px;
}

.report-section h3 {
  margin: 0 0 10px;
  color: #1e293b;
  font-size: 15px;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .import-stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
