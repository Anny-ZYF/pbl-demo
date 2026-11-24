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
              <el-table-column label="操作" width="110" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="canDeleteOrganization(row)" link type="danger" @click="deleteOrganizationNode(row)">删除</el-button>
                  <span v-else class="muted-text">—</span>
                </template>
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
            <el-table-column label="操作" width="285" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="primary" @click="toggleStatus(row)">{{ isAccountEnabled(row) ? '停用' : '启用' }}</el-button>
                <el-button link type="primary" @click="resetPassword(row)">重置密码</el-button>
                <el-button v-if="canDeleteAccount(row)" link type="danger" @click="deleteAccountRow(row)">删除</el-button>
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
          <el-form-item :label="accountForm.role === 'ROLE_TEACHER' ? '工号（登录账号）' : '学号（登录账号）'" prop="username">
            <el-input v-model.trim="accountForm.username" :disabled="Boolean(editingId)" maxlength="20" />
          </el-form-item>
          <el-form-item label="姓名" prop="name">
            <el-input v-model.trim="accountForm.name" />
          </el-form-item>
          <el-form-item label="所属学院" prop="departmentName">
            <el-select
              v-model="accountForm.departmentName"
              filterable
              :filter-method="filterDepartments"
              placeholder="搜索学院，或输入新学院名"
              @change="clearMajorAndClass"
              @visible-change="handleDepartmentVisible"
            >
              <el-option
                v-for="item in departmentOptions"
                :key="item.id ?? item.name"
                :label="item.name"
                :value="item.name"
              >
                <span>{{ item.name }}</span>
                <span class="option-meta">{{ item.memberCount }} 人</span>
              </el-option>
              <el-option v-if="departmentCreateName" :label="departmentCreateName" :value="departmentCreateName">
                <span class="option-create">+ 新建学院「{{ departmentCreateName }}」</span>
              </el-option>
            </el-select>
            <div v-if="departmentCreateName && accountForm.departmentName === departmentCreateName" class="field-hint">
              该学院尚不存在，保存时会自动创建
            </div>
          </el-form-item>
          <el-form-item v-if="accountForm.role === 'ROLE_STUDENT'" label="专业" prop="majorName">
            <el-select
              v-model="accountForm.majorName"
              filterable
              :filter-method="filterMajors"
              :disabled="!accountForm.departmentName"
              :placeholder="accountForm.departmentName ? '搜索专业，或输入新专业名' : '请先选择所属学院'"
              @visible-change="handleMajorVisible"
            >
              <el-option
                v-for="item in majorOptions"
                :key="item.id ?? item.name"
                :label="item.name"
                :value="item.name"
              >
                <span>{{ item.name }}</span>
                <span class="option-meta">{{ item.memberCount }} 人</span>
              </el-option>
              <el-option v-if="majorCreateName" :label="majorCreateName" :value="majorCreateName">
                <span class="option-create">+ 新建专业「{{ majorCreateName }}」</span>
              </el-option>
            </el-select>
            <div v-if="majorCreateName && accountForm.majorName === majorCreateName" class="field-hint">
              该专业尚不存在，保存时会自动创建
            </div>
          </el-form-item>
          <el-form-item v-if="accountForm.role === 'ROLE_STUDENT'" label="班级" prop="className">
            <el-select
              v-model="accountForm.className"
              filterable
              :filter-method="filterClasses"
              :disabled="!accountForm.departmentName"
              :placeholder="accountForm.departmentName ? '搜索班级，或输入新班级名' : '请先选择所属学院'"
              @visible-change="handleClassVisible"
            >
              <el-option
                v-for="item in classOptions"
                :key="item.id ?? item.name"
                :label="item.name"
                :value="item.name"
              >
                <span>{{ item.name }}</span>
                <span class="option-meta">{{ item.memberCount }} 人</span>
              </el-option>
              <el-option v-if="classCreateName" :label="classCreateName" :value="classCreateName">
                <span class="option-create">+ 新建班级「{{ classCreateName }}」</span>
              </el-option>
            </el-select>
            <div v-if="classCreateName && accountForm.className === classCreateName" class="field-hint">
              该班级尚不存在，保存时会自动创建
            </div>
          </el-form-item>
          <el-form-item v-if="accountForm.role === 'ROLE_STUDENT'" label="年级" prop="grade">
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
        title="初始密码为 ABC 加登录账号，请安全交付给用户。用户首次登录后必须修改密码。"
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
        title="文件必须为 UTF-8 编码的 CSV，大小不超过 2MB，单次最多 5000 行。初始密码留空时使用 ABC 加登录账号。"
        type="info"
        :closable="false"
        show-icon
        class="dialog-alert"
      />

      <div v-if="!importReport" class="import-guide">
        <div class="import-role-picker">
          <strong>第一步：选择导入账号类型</strong>
          <el-radio-group v-model="importRole" @change="handleImportRoleChange">
            <el-radio-button value="ROLE_STUDENT">学生账号</el-radio-button>
            <el-radio-button value="ROLE_TEACHER">教师账号</el-radio-button>
          </el-radio-group>
        </div>

        <template v-if="importRole">
          <div class="import-guide__title">
            <span>第二步：下载并填写{{ importRoleLabel }}模板</span>
            <el-button link type="primary" @click="downloadImportTemplate">下载{{ importRoleLabel }}模板</el-button>
          </div>
          <code>role,username,displayName,department,major,class,grade,initialPassword</code>
          <p>{{ importTemplateHint }}</p>
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
            <div class="el-upload__text">将{{ importRoleLabel }} CSV 文件拖到此处，或<em>点击选择</em></div>
            <template #tip><div class="el-upload__tip">选中文件后点击“开始导入”，系统会逐行返回结果。</div></template>
          </el-upload>
        </template>
        <el-empty v-else description="请先选择要导入学生账号还是教师账号" :image-size="72" />
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
        <el-button v-else type="primary" :loading="importing" :disabled="!importRole || !importFileList.length" @click="submitImport">
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
  ElRadioButton,
  ElRadioGroup,
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
  deleteAccount,
  deleteOrganization,
  getAccounts,
  getOrganizationOverview,
  importAccounts,
  resetAccountPassword,
  updateAccount,
  updateAccountStatus
} from '@/api/admin'
import { getErrorMessage } from '@/api/response'
import { displayValue, formatDateTime } from '@/utils/format'
import {
  canDeleteAccount,
  canDeleteOrganization,
  organizationDeleteTarget,
  organizationMemberCount
} from '@/utils/organization'
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
const importRole = ref('')
const importUploadRef = ref()
const importFileList = ref([])
const importReport = ref(null)
const importing = ref(false)
const importRoleLabel = computed(() => importRole.value === 'ROLE_TEACHER' ? '教师账号' : '学生账号')
const importTemplateHint = computed(() => importRole.value === 'ROLE_TEACHER'
  ? '教师模板直接填写工号、姓名和学院名称；专业、班级、年级请留空。新学院会自动创建，初始密码留空时为 ABC+工号。'
  : '学生模板直接填写学号、姓名、学院名称、专业名称、班级名称和年级（如 24莞信管01班）。不存在的学院、专业或班级会自动创建，初始密码留空时为 ABC+学号。')
const accountForm = reactive({
  role: 'ROLE_STUDENT',
  username: '',
  name: '',
  departmentName: '',
  majorName: '',
  className: '',
  grade: null
})
const accountRules = {
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  username: [{ required: true, message: '请输入工号或学号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  departmentName: [
    { required: true, message: '请选择或输入学院', trigger: 'change' },
    { max: 100, message: '学院名称不能超过 100 个字符', trigger: 'change' }
  ],
  majorName: [
    { required: true, message: '请选择或输入专业', trigger: 'change' },
    { max: 100, message: '专业名称不能超过 100 个字符', trigger: 'change' }
  ],
  className: [
    { required: true, message: '请选择或输入班级', trigger: 'change' },
    { max: 100, message: '班级名称不能超过 100 个字符', trigger: 'change' }
  ],
  grade: [{ required: true, message: '请选择年级', trigger: 'change' }]
}

const departments = computed(() => Array.isArray(overview.value.departments) ? overview.value.departments : [])
const selectedDepartment = computed(() => departments.value.find((item) => item.name === accountForm.departmentName))
const availableMajors = computed(() => Array.isArray(selectedDepartment.value?.majors) ? selectedDepartment.value.majors : [])
const availableClasses = computed(() => Array.isArray(selectedDepartment.value?.classes) ? selectedDepartment.value.classes : [])

// 学院、专业、班级都允许「输入即建」：下拉既能搜已有节点，也能在搜不到时给出一条「+ 新建」候选。
// 用户必须显式点这一项才会新建，避免把搜索过程中的半截文字直接当成组织建出来。
function useCreatableOptions(source) {
  const keyword = ref('')
  const options = computed(() => {
    const query = keyword.value.trim().toLowerCase()
    if (!query) return source.value
    return source.value.filter((item) => String(item.name).toLowerCase().includes(query))
  })
  const createName = computed(() => {
    const name = keyword.value.trim()
    if (!name || name.length > 100) return ''
    return source.value.some((item) => String(item.name) === name) ? '' : name
  })
  return {
    keyword,
    options,
    createName,
    filter: (query) => { keyword.value = query ?? '' },
    reset: () => { keyword.value = '' }
  }
}

const departmentField = useCreatableOptions(departments)
const majorField = useCreatableOptions(availableMajors)
const classField = useCreatableOptions(availableClasses)
const { options: departmentOptions, createName: departmentCreateName } = departmentField
const { options: majorOptions, createName: majorCreateName } = majorField
const { options: classOptions, createName: classCreateName } = classField

function filterDepartments(query) {
  departmentField.filter(query)
}

function filterMajors(query) {
  majorField.filter(query)
}

function filterClasses(query) {
  classField.filter(query)
}

function handleDepartmentVisible(visible) {
  if (visible) departmentField.reset()
}

function handleMajorVisible(visible) {
  if (visible) majorField.reset()
}

function handleClassVisible(visible) {
  if (visible) classField.reset()
}

const organizationMetrics = computed(() => {
  const summary = overview.value.summary || overview.value
  return [
    { label: '院系', value: summary.departmentCount, hint: '学校教学组织' },
    { label: '专业', value: summary.majorCount, hint: '纳入平台的专业' },
    { label: '班级', value: summary.classCount, hint: '当前班级数量' },
    { label: '师生账号', value: summary.accountCount, hint: '教师与学生账号总数' }
  ]
})

// 组织树按「院系 › 专业 › 班级」三级展示。班级在库里只归属学院（V6 起 major_id 可为空），
// 后端会按在册学生的专业推断 majorId；推断不出来的班（没有学生、或学生都没填专业）收进「未分配专业」分组，
// 不会因为挂不上专业而从树上消失。
const sameId = (left, right) => left != null && right != null && String(left) === String(right)

const classRow = (classItem, keyPrefix) => ({
  treeKey: `${keyPrefix}-class-${classItem.id ?? keyPrefix}`,
  id: classItem.id,
  type: 'class',
  name: classItem.name,
  code: classItem.code,
  typeLabel: '班级',
  memberCount: organizationMemberCount(classItem)
})

const organizationRows = computed(() => departments.value.map((department, departmentIndex) => {
  const classList = Array.isArray(department.classes) ? department.classes : []
  const majorRows = (department.majors || []).map((major, majorIndex) => {
    const key = `major-${major.id ?? `${departmentIndex}-${majorIndex}`}`
    return {
      treeKey: key,
      id: major.id,
      type: 'major',
      name: major.name,
      code: major.code,
      typeLabel: '专业',
      memberCount: organizationMemberCount(major),
      children: classList.filter((classItem) => sameId(classItem.majorId, major.id)).map((classItem) => classRow(classItem, key))
    }
  })
  // 只有挂不到任何专业下的班级才需要这个兜底分组；正常情况不出现。
  const orphans = classList.filter((classItem) => !majorRows.some((major) => sameId(classItem.majorId, major.id)))
  const grouped = [...majorRows]
  if (orphans.length) {
    grouped.push({
      treeKey: `unassigned-${department.id ?? departmentIndex}`,
      id: null,
      type: 'unassigned',
      name: '未分配专业',
      code: null,
      typeLabel: '分组',
      memberCount: orphans.reduce((sum, classItem) => sum + organizationMemberCount(classItem), 0),
      children: orphans.map((classItem) => classRow(classItem, `unassigned-${department.id ?? departmentIndex}`))
    })
  }
  return {
    treeKey: `department-${department.id ?? departmentIndex}`,
    id: department.id,
    type: 'department',
    name: department.name,
    code: department.code,
    typeLabel: '院系',
    memberCount: organizationMemberCount(department),
    children: grouped
  }
}))

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
    departmentName: '',
    majorName: '',
    className: '',
    grade: null
  })
  departmentField.reset()
  majorField.reset()
  classField.reset()
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
    departmentName: row.departmentName || '',
    majorName: row.majorName || '',
    className: row.className || '',
    grade: row.grade ?? null
  })
  departmentField.reset()
  majorField.reset()
  classField.reset()
  accountDialogVisible.value = true
}

function clearOrganizationSelection() {
  accountForm.departmentName = ''
  departmentField.reset()
  clearMajorAndClass()
}

function clearMajorAndClass() {
  accountForm.majorName = ''
  accountForm.className = ''
  majorField.reset()
  classField.reset()
}

async function saveAccount() {
  const valid = await accountFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingAccount.value = true
  try {
    const isStudent = accountForm.role === 'ROLE_STUDENT'
    const payload = {
      role: accountForm.role,
      displayName: accountForm.name,
      departmentName: String(accountForm.departmentName || '').trim(),
      majorName: isStudent ? String(accountForm.majorName || '').trim() : null,
      className: isStudent ? String(accountForm.className || '').trim() : null,
      grade: isStudent ? accountForm.grade : null
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
  importRole.value = ''
}

function handleImportRoleChange() {
  importUploadRef.value?.clearFiles()
  importFileList.value = []
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
  const header = 'role,username,displayName,department,major,class,grade,initialPassword'
  const examples = importRole.value === 'ROLE_TEACHER'
    ? ['ROLE_TEACHER,T2026001,示例教师,生物医学工程学院,,,,']
    : ['ROLE_STUDENT,20260101,示例学生,生物医学工程学院,智能科学与技术,24莞信科01班,2026,']
  const blob = new Blob([`\uFEFF${header}\r\n${examples.join('\r\n')}\r\n`], {
    type: 'text/csv;charset=utf-8'
  })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = importRole.value === 'ROLE_TEACHER'
    ? 'teacher-account-import-template.csv'
    : 'student-account-import-template.csv'
  anchor.click()
  URL.revokeObjectURL(url)
}

async function submitImport() {
  if (!importRole.value) {
    ElMessage.warning('请先选择账号类型')
    return
  }
  const file = importFileList.value[0]?.raw
  if (!file) {
    ElMessage.warning('请先选择 CSV 文件')
    return
  }

  const content = (await file.text()).replace(/^\uFEFF/, '')
  const lines = content.split(/\r?\n/)
  const normalizedLines = lines.map((line, index) => {
    if (index === 0 || !line.trim()) return line
    const separatorIndex = line.indexOf(',')
    return separatorIndex < 0 ? line : `${importRole.value}${line.slice(separatorIndex)}`
  })
  const normalizedFile = new File(
    [`\uFEFF${normalizedLines.join('\r\n')}`],
    file.name,
    { type: 'text/csv;charset=utf-8' }
  )

  importing.value = true
  try {
    importReport.value = await importAccounts(normalizedFile)
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

async function deleteAccountRow(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除账号“${row.username}”吗？删除后账号将从列表移除并立即无法登录；已产生的课程、分组、提交与成绩记录会保留（逻辑删除）。`,
      '删除账号',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
    await deleteAccount(row.id, row.role)
    ElMessage.success('账号已删除')
    await reloadAll()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error, '账号删除失败'))
  }
}

async function deleteOrganizationNode(row) {
  const target = organizationDeleteTarget(row)
  if (!target) return
  try {
    await ElMessageBox.confirm(
      `确认删除${row.typeLabel}“${row.name}”吗？仅无关联人数且无课程、小组等业务引用的组织节点可以删除。`,
      '删除组织',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
    await deleteOrganization(target.type, target.id)
    ElMessage.success(`${row.typeLabel}已删除`)
    await reloadAll()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error, '组织删除失败'))
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

.option-meta {
  float: right;
  margin-left: 12px;
  color: #94a3b8;
  font-size: 12px;
}

.option-create {
  color: #2563eb;
  font-weight: 600;
}

.field-hint {
  margin-top: 2px;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.5;
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

.import-role-picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fbff;
  color: #1e3a5f;
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

  .import-role-picker {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
