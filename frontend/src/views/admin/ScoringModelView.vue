<template>
  <div>
    <PageHeader title="评分模型" description="查看模型版本、评分运行和异常记录，确保差异化评分过程可追溯。">
      <template #actions>
        <el-button :icon="Refresh" :loading="currentLoading" @click="refreshCurrent">刷新当前页</el-button>
        <el-button v-if="activeTab === 'versions'" type="primary" :icon="Plus" @click="openRegistration">注册模型</el-button>
      </template>
    </PageHeader>

    <el-alert
      title="模型注册门槛为 RMSE≤1、MAE≤0.8、R²≥0.99。激活或回滚前，还必须确保算法服务已加载完全一致的模型版本和制品。"
      type="info"
      :closable="false"
      show-icon
      class="model-note"
    />

    <section class="surface-card model-card">
      <el-tabs v-model="activeTab" @tab-change="loadActiveTab">
        <el-tab-pane label="模型版本" name="versions">
          <div class="inline-filter">
            <el-select v-model="versionFilters.status" clearable placeholder="全部状态" @change="searchVersions">
              <el-option label="当前激活" value="ACTIVE" />
              <el-option label="非激活" value="INACTIVE" />
            </el-select>
          </div>
          <el-table v-loading="versionLoading" :data="versionPage.items">
            <el-table-column prop="modelKey" label="模型键" min-width="160">
              <template #default="{ row }">{{ displayValue(row.modelKey || row.name) }}</template>
            </el-table-column>
            <el-table-column prop="version" label="版本" min-width="110" />
            <el-table-column prop="algorithm" label="算法" min-width="120">
              <template #default="{ row }">{{ displayValue(row.algorithm || row.algorithmType) }}</template>
            </el-table-column>
            <el-table-column label="RMSE" width="90" align="right">
              <template #default="{ row }">{{ metricValue(row, 'rmse') }}</template>
            </el-table-column>
            <el-table-column label="MAE" width="90" align="right">
              <template #default="{ row }">{{ metricValue(row, 'mae') }}</template>
            </el-table-column>
            <el-table-column label="R²" width="90" align="right">
              <template #default="{ row }">{{ metricValue(row, 'r2') }}</template>
            </el-table-column>
            <el-table-column label="准入" width="90">
              <template #default="{ row }">
                <el-tag :type="modelGatePass(row) ? 'success' : 'danger'">
                  {{ modelGatePass(row) ? '通过' : '未通过' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }"><el-tag :type="modelStatusType(row.status)">{{ modelStatusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="activatedAt" label="激活时间" min-width="150">
              <template #default="{ row }">{{ formatDateTime(row.activatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canActivateModel(row)" link type="primary" @click="activateVersion(row)">
                  {{ String(row.status).toUpperCase() === 'RETIRED' ? '回滚至此版本' : '激活' }}
                </el-button>
                <span v-else class="muted-text">—</span>
              </template>
            </el-table-column>
          </el-table>
          <PagePagination v-model:page="versionPagination.page" v-model:page-size="versionPagination.pageSize" :total="versionPage.total" @change="loadVersions" />
        </el-tab-pane>

        <el-tab-pane label="评分运行" name="runs">
          <div class="inline-filter">
            <el-input v-model.trim="runFilters.keyword" clearable placeholder="运行编号或作业名称" @keyup.enter="searchRuns" />
            <el-select v-model="runFilters.status" clearable placeholder="全部状态" @change="searchRuns">
              <el-option label="运行中" value="RUNNING" />
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="searchRuns">查询</el-button>
          </div>
          <el-table v-loading="runLoading" :data="runPage.items">
            <el-table-column prop="runNo" label="运行编号" min-width="130">
              <template #default="{ row }">{{ displayValue(row.runNo || row.id) }}</template>
            </el-table-column>
            <el-table-column prop="assignmentName" label="作业" min-width="180">
              <template #default="{ row }">{{ displayValue(row.assignmentName || row.assignmentTitle) }}</template>
            </el-table-column>
            <el-table-column prop="modelVersion" label="模型版本" min-width="100" />
            <el-table-column prop="sampleCount" label="样本数" width="90" align="right">
              <template #default="{ row }">{{ displayValue(row.sampleCount) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="startedAt" label="开始时间" min-width="150">
              <template #default="{ row }">{{ formatDateTime(row.startedAt || row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="finishedAt" label="完成时间" min-width="150">
              <template #default="{ row }">{{ formatDateTime(row.finishedAt || row.updatedAt) }}</template>
            </el-table-column>
          </el-table>
          <PagePagination v-model:page="runPagination.page" v-model:page-size="runPagination.pageSize" :total="runPage.total" @change="loadRuns" />
        </el-tab-pane>

        <el-tab-pane label="异常处理" name="anomalies">
          <div class="inline-filter">
            <el-input v-model.trim="anomalyFilters.keyword" clearable placeholder="作业、学生或小组" @keyup.enter="searchAnomalies" />
            <el-select v-model="anomalyFilters.severity" clearable placeholder="全部等级" @change="searchAnomalies">
              <el-option label="高" value="HIGH" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="低" value="LOW" />
            </el-select>
            <el-select v-model="anomalyFilters.status" clearable placeholder="全部状态" @change="searchAnomalies">
              <el-option label="待处理" value="PENDING" />
              <el-option label="已处理" value="RESOLVED" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="searchAnomalies">查询</el-button>
          </div>
          <el-table v-loading="anomalyLoading" :data="anomalyPage.items">
            <el-table-column prop="type" label="异常类型" min-width="130">
              <template #default="{ row }">{{ displayValue(row.type || row.anomalyType) }}</template>
            </el-table-column>
            <el-table-column prop="assignmentName" label="作业" min-width="160">
              <template #default="{ row }">{{ displayValue(row.assignmentName || row.assignmentTitle) }}</template>
            </el-table-column>
            <el-table-column prop="subjectName" label="对象" min-width="120">
              <template #default="{ row }">{{ displayValue(row.subjectName || row.studentName || row.groupName) }}</template>
            </el-table-column>
            <el-table-column prop="severity" label="等级" width="80">
              <template #default="{ row }"><el-tag :type="severityType(row.severity)">{{ severityLabel(row.severity) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="detectedAt" label="发现时间" min-width="150">
              <template #default="{ row }">{{ formatDateTime(row.detectedAt || row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-if="!isResolved(row.status)" link type="primary" @click="openResolution(row)">处理</el-button>
                <span v-else class="muted-text">已完成</span>
              </template>
            </el-table-column>
          </el-table>
          <PagePagination v-model:page="anomalyPagination.page" v-model:page-size="anomalyPagination.pageSize" :total="anomalyPage.total" @change="loadAnomalies" />
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog
      v-model="registrationVisible"
      title="注册 XGBoost 模型版本"
      width="760px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-alert
        title="注册只保存受控元数据，不会上传或替换算法服务中的模型文件。请先部署制品，并核对 URI、SHA-256 和服务实际加载版本。"
        type="warning"
        :closable="false"
        show-icon
        class="registration-alert"
      />
      <el-form ref="registrationFormRef" :model="registrationForm" :rules="registrationRules" label-position="top">
        <div class="registration-grid">
          <el-form-item label="模型键" prop="modelKey">
            <el-input v-model.trim="registrationForm.modelKey" disabled />
          </el-form-item>
          <el-form-item label="版本" prop="version">
            <el-input v-model.trim="registrationForm.version" placeholder="例如 2026.07.15-v1" />
          </el-form-item>
        </div>
        <el-form-item label="制品 URI" prop="artifactUri">
          <el-input v-model.trim="registrationForm.artifactUri" placeholder="algorithm-service://artifacts/xgboost-model.json" />
        </el-form-item>
        <el-form-item label="制品 SHA-256" prop="artifactSha256">
          <el-input v-model.trim="registrationForm.artifactSha256" maxlength="64" placeholder="64 位十六进制摘要" />
        </el-form-item>
        <el-form-item label="特征模式 JSON" prop="featureSchemaJson">
          <el-input
            v-model="registrationForm.featureSchemaJson"
            type="textarea"
            :rows="3"
            readonly
          />
        </el-form-item>
        <el-form-item label="超参数 JSON" prop="hyperparametersJson">
          <el-input
            v-model="registrationForm.hyperparametersJson"
            type="textarea"
            :rows="3"
            placeholder='例如 {"n_estimators":300,"max_depth":4}'
          />
        </el-form-item>
        <el-form-item label="评估指标 JSON" prop="metricsJson">
          <el-input
            v-model="registrationForm.metricsJson"
            type="textarea"
            :rows="4"
            placeholder='例如 {"test":{"rmse":0.9,"mae":0.7,"r2":0.995}}'
          />
        </el-form-item>

        <div class="admission-preview">
          <div>
            <span>RMSE</span>
            <strong>{{ previewMetric('rmse') }}</strong>
            <small>≤ 1</small>
          </div>
          <div>
            <span>MAE</span>
            <strong>{{ previewMetric('mae') }}</strong>
            <small>≤ 0.8</small>
          </div>
          <div>
            <span>R²</span>
            <strong>{{ previewMetric('r2') }}</strong>
            <small>≥ 0.99</small>
          </div>
          <el-tag :type="registrationAdmissionPassed ? 'success' : 'danger'">
            {{ registrationAdmissionPassed ? '达到准入门槛' : '尚未达到准入门槛' }}
          </el-tag>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="registrationVisible = false">取消</el-button>
        <el-button type="primary" :loading="registering" @click="submitRegistration">验证并注册</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resolutionVisible" title="处理评分异常" width="520px" :close-on-click-modal="false">
      <el-form ref="resolutionFormRef" :model="resolutionForm" :rules="resolutionRules" label-position="top">
        <el-form-item label="异常对象">
          <el-input :model-value="selectedAnomalyName" disabled />
        </el-form-item>
        <el-form-item label="处理说明" prop="resolutionNote">
          <el-input v-model.trim="resolutionForm.resolutionNote" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolutionVisible = false">取消</el-button>
        <el-button type="primary" :loading="resolving" @click="submitResolution">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag
} from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  activateModelVersion,
  getAnomalies,
  getModelVersions,
  getScoringRuns,
  registerModelVersion,
  resolveAnomaly
} from '@/api/admin'
import { getErrorMessage } from '@/api/response'
import { displayValue, formatDateTime, statusLabel, statusType } from '@/utils/format'
import { meetsModelAdmissionGate, readModelMetrics } from '@/utils/modelMetrics'

const PagePagination = defineComponent({
  props: {
    page: { type: Number, required: true },
    pageSize: { type: Number, required: true },
    total: { type: Number, default: 0 }
  },
  emits: ['update:page', 'update:pageSize', 'change'],
  setup(props, { emit }) {
    const updatePage = (value) => {
      emit('update:page', value)
      emit('change')
    }
    const updatePageSize = (value) => {
      emit('update:pageSize', value)
      emit('update:page', 1)
      emit('change')
    }
    return () => h('div', { class: 'pagination-bar' }, [
      h(ElPagination, {
        currentPage: props.page,
        pageSize: props.pageSize,
        pageSizes: [10, 20, 50],
        total: props.total,
        layout: 'total, sizes, prev, pager, next',
        'onUpdate:currentPage': updatePage,
        'onUpdate:pageSize': updatePageSize
      })
    ])
  }
})

const activeTab = ref('versions')
const versionLoading = ref(false)
const runLoading = ref(false)
const anomalyLoading = ref(false)
const versionPage = ref({ items: [], total: 0 })
const runPage = ref({ items: [], total: 0 })
const anomalyPage = ref({ items: [], total: 0 })
const versionPagination = reactive({ page: 1, pageSize: 10 })
const runPagination = reactive({ page: 1, pageSize: 10 })
const anomalyPagination = reactive({ page: 1, pageSize: 10 })
const versionFilters = reactive({ status: '' })
const runFilters = reactive({ keyword: '', status: '' })
const anomalyFilters = reactive({ keyword: '', severity: '', status: '' })

const registrationVisible = ref(false)
const registrationFormRef = ref()
const registering = ref(false)
const registrationForm = reactive({
  modelKey: 'paper-score-p4',
  version: '',
  artifactUri: '',
  artifactSha256: '',
  featureSchemaJson: '["contribution","actual_group_size","group_score"]',
  hyperparametersJson: '',
  metricsJson: ''
})

function validateJson(_rule, value, callback) {
  if (!value?.trim()) {
    callback(new Error('请输入 JSON'))
    return
  }
  try {
    JSON.parse(value)
    callback()
  } catch {
    callback(new Error('请输入合法 JSON'))
  }
}

function validateFeatureSchema(_rule, value, callback) {
  try {
    const parsed = JSON.parse(value)
    const expected = ['contribution', 'actual_group_size', 'group_score']
    if (!Array.isArray(parsed) || parsed.length !== expected.length
      || parsed.some((item, index) => item !== expected[index])) {
      callback(new Error('特征顺序必须与生产模型契约一致'))
      return
    }
    callback()
  } catch {
    callback(new Error('请输入合法 JSON'))
  }
}

function validateJsonObject(_rule, value, callback) {
  try {
    const parsed = JSON.parse(value)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      callback(new Error('请输入 JSON 对象'))
      return
    }
    callback()
  } catch {
    callback(new Error('请输入合法 JSON'))
  }
}

const registrationRules = {
  modelKey: [{ required: true, message: '请输入模型键', trigger: 'blur' }],
  version: [{ required: true, message: '请输入版本', trigger: 'blur' }],
  artifactUri: [
    { required: true, message: '请输入制品 URI', trigger: 'blur' },
    {
      pattern: /^algorithm-service:\/\/artifacts\/[A-Za-z0-9._-]+\.json$/,
      message: 'URI 必须指向算法服务的 JSON 模型产物',
      trigger: 'blur'
    }
  ],
  artifactSha256: [
    { required: true, message: '请输入制品 SHA-256', trigger: 'blur' },
    { pattern: /^[a-fA-F0-9]{64}$/, message: 'SHA-256 必须为 64 位十六进制字符', trigger: 'blur' }
  ],
  featureSchemaJson: [{ validator: validateFeatureSchema, trigger: 'blur' }],
  hyperparametersJson: [{ validator: validateJsonObject, trigger: 'blur' }],
  metricsJson: [{ validator: validateJson, trigger: 'blur' }]
}

const resolutionVisible = ref(false)
const resolutionFormRef = ref()
const resolving = ref(false)
const selectedAnomaly = ref(null)
const resolutionForm = reactive({ resolutionNote: '' })
const resolutionRules = {
  resolutionNote: [
    { required: true, message: '请输入处理说明', trigger: 'blur' },
    { min: 5, message: '处理说明至少 5 个字符', trigger: 'blur' }
  ]
}

const currentLoading = computed(() => ({
  versions: versionLoading.value,
  runs: runLoading.value,
  anomalies: anomalyLoading.value
})[activeTab.value])
const registrationMetrics = computed(() => readModelMetrics(registrationForm.metricsJson))
const registrationAdmissionPassed = computed(() => meetsModelAdmissionGate(registrationForm.metricsJson))
const selectedAnomalyName = computed(() => selectedAnomaly.value?.subjectName
  || selectedAnomaly.value?.studentName
  || selectedAnomaly.value?.groupName
  || '—')

async function loadVersions() {
  versionLoading.value = true
  try {
    versionPage.value = await getModelVersions({
      page: versionPagination.page,
      pageSize: versionPagination.pageSize,
      status: versionFilters.status || undefined
    })
  } catch (error) {
    versionPage.value = { items: [], total: 0 }
    ElMessage.error(getErrorMessage(error, '模型版本加载失败'))
  } finally {
    versionLoading.value = false
  }
}

async function loadRuns() {
  runLoading.value = true
  try {
    runPage.value = await getScoringRuns({
      page: runPagination.page,
      pageSize: runPagination.pageSize,
      keyword: runFilters.keyword || undefined,
      status: runFilters.status || undefined
    })
  } catch (error) {
    runPage.value = { items: [], total: 0 }
    ElMessage.error(getErrorMessage(error, '评分运行加载失败'))
  } finally {
    runLoading.value = false
  }
}

async function loadAnomalies() {
  anomalyLoading.value = true
  try {
    anomalyPage.value = await getAnomalies({
      page: anomalyPagination.page,
      pageSize: anomalyPagination.pageSize,
      keyword: anomalyFilters.keyword || undefined,
      severity: anomalyFilters.severity || undefined,
      status: anomalyFilters.status || undefined
    })
  } catch (error) {
    anomalyPage.value = { items: [], total: 0 }
    ElMessage.error(getErrorMessage(error, '异常记录加载失败'))
  } finally {
    anomalyLoading.value = false
  }
}

function loadActiveTab(name = activeTab.value) {
  if (name === 'versions') return loadVersions()
  if (name === 'runs') return loadRuns()
  return loadAnomalies()
}

function refreshCurrent() {
  return loadActiveTab()
}

function searchVersions() {
  versionPagination.page = 1
  loadVersions()
}

function searchRuns() {
  runPagination.page = 1
  loadRuns()
}

function searchAnomalies() {
  anomalyPagination.page = 1
  loadAnomalies()
}

function resetRegistrationForm() {
  Object.assign(registrationForm, {
    modelKey: 'paper-score-p4',
    version: '',
    artifactUri: '',
    artifactSha256: '',
    featureSchemaJson: '["contribution","actual_group_size","group_score"]',
    hyperparametersJson: '',
    metricsJson: ''
  })
  registrationFormRef.value?.clearValidate()
}

function openRegistration() {
  resetRegistrationForm()
  registrationVisible.value = true
}

function previewMetric(key) {
  const value = registrationMetrics.value?.[key]
  return Number.isFinite(value) ? value.toFixed(4) : '—'
}

function metricValue(row, key) {
  const value = readModelMetrics(row.metricsJson)?.[key]
  return Number.isFinite(value) ? value.toFixed(4) : '—'
}

function modelGatePass(row) {
  return meetsModelAdmissionGate(row.metricsJson)
}

function modelStatusLabel(status) {
  return {
    ACTIVE: '当前激活',
    VALIDATED: '已验证',
    ELIGIBLE: '准入通过',
    RETIRED: '已停用'
  }[String(status || '').toUpperCase()] || statusLabel(status)
}

function modelStatusType(status) {
  return {
    ACTIVE: 'success',
    VALIDATED: 'primary',
    ELIGIBLE: 'primary',
    RETIRED: 'info'
  }[String(status || '').toUpperCase()] || statusType(status)
}

function canActivateModel(row) {
  return String(row.modelKey || '').toLowerCase() === 'paper-score-p4'
    && String(row.algorithm || '').toUpperCase() === 'XGBOOST'
    && ['VALIDATED', 'RETIRED'].includes(String(row.status || '').toUpperCase())
}

async function submitRegistration() {
  const valid = await registrationFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!registrationAdmissionPassed.value) {
    ElMessage.error('评估指标未达到准入门槛，不能注册')
    return
  }

  registering.value = true
  try {
    await registerModelVersion({ ...registrationForm })
    ElMessage.success('模型版本已验证并注册')
    registrationVisible.value = false
    versionPagination.page = 1
    await loadVersions()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '模型注册失败'))
  } finally {
    registering.value = false
  }
}

async function activateVersion(row) {
  const status = String(row.status || '').toUpperCase()
  const action = status === 'RETIRED' ? '回滚' : '激活'
  try {
    await ElMessageBox.confirm(
      `${action}版本“${row.version}”会停用同一模型键下当前激活版本。请确认算法服务已加载此版本及对应 SHA-256 制品。`,
      `${action}模型版本`,
      {
        type: 'warning',
        confirmButtonText: `确认${action}`,
        cancelButtonText: '取消'
      }
    )
    await activateModelVersion(row.id)
    ElMessage.success(`模型版本已${action}`)
    await loadVersions()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error, `${action}模型失败`))
  }
}

function severityLabel(severity) {
  return { HIGH: '高', MEDIUM: '中', LOW: '低' }[String(severity || '').toUpperCase()] || displayValue(severity)
}

function severityType(severity) {
  return { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }[String(severity || '').toUpperCase()] || 'info'
}

function isResolved(status) {
  return ['RESOLVED', 'CLOSED'].includes(String(status || '').toUpperCase())
}

function openResolution(row) {
  selectedAnomaly.value = row
  resolutionForm.resolutionNote = ''
  resolutionVisible.value = true
}

async function submitResolution() {
  const valid = await resolutionFormRef.value?.validate().catch(() => false)
  if (!valid || !selectedAnomaly.value) return

  resolving.value = true
  try {
    await resolveAnomaly(selectedAnomaly.value.id, {
      resolution: resolutionForm.resolutionNote
    })
    ElMessage.success('异常已处理')
    resolutionVisible.value = false
    await loadAnomalies()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '异常处理失败'))
  } finally {
    resolving.value = false
  }
}

onMounted(loadVersions)
</script>

<style scoped>
.model-note {
  margin-bottom: 16px;
}

.model-card {
  padding: 0 18px;
  overflow: hidden;
}

.inline-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 4px 0 16px;
}

.inline-filter .el-input,
.inline-filter .el-select {
  width: 220px;
}

.muted-text {
  color: #94a3b8;
  font-size: 12px;
}

.registration-alert {
  margin-bottom: 18px;
}

.registration-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.admission-preview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
}

.admission-preview > div {
  min-width: 0;
}

.admission-preview span,
.admission-preview small {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.admission-preview strong {
  display: block;
  margin: 4px 0;
  color: #0f172a;
  font-size: 20px;
}

@media (max-width: 640px) {
  .inline-filter > *,
  .inline-filter .el-input,
  .inline-filter .el-select {
    width: 100%;
  }

  .registration-grid,
  .admission-preview {
    grid-template-columns: 1fr;
  }
}
</style>
