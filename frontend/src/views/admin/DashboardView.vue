<template>
  <div>
    <PageHeader title="工作台" description="查看校园协作学习与差异化评分的关键运行状态。">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadDashboard">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="dashboard-alert"
    >
      <template #default>
        <el-button text type="primary" @click="loadDashboard">重新加载</el-button>
      </template>
    </el-alert>

    <div v-loading="loading" class="metric-grid" aria-label="关键指标">
      <article v-for="metric in metrics" :key="metric.label" class="surface-card metric-card">
        <div class="metric-card__label">{{ metric.label }}</div>
        <div class="metric-card__value">{{ displayValue(metric.value) }}</div>
        <div class="metric-card__hint">{{ metric.hint }}</div>
      </article>
    </div>

    <div class="content-grid">
      <section class="surface-card section-card">
        <div class="section-card__heading">
          <h2>最近评分运行</h2>
          <RouterLink class="text-link" to="/admin/scoring-model">查看全部</RouterLink>
        </div>
        <el-table v-if="recentRuns.length" :data="recentRuns" size="small">
          <el-table-column prop="assignmentName" label="作业" min-width="150">
            <template #default="{ row }">{{ displayValue(row.assignmentName || row.assignmentTitle) }}</template>
          </el-table-column>
          <el-table-column prop="modelVersion" label="模型版本" min-width="100" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="finishedAt" label="完成时间" min-width="145">
            <template #default="{ row }">{{ formatDateTime(row.finishedAt || row.updatedAt) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else class="empty-block" description="暂无评分运行记录" :image-size="72" />
      </section>

      <section class="surface-card section-card">
        <div class="section-card__heading">
          <h2>系统状态</h2>
        </div>
        <dl class="status-list">
          <div>
            <dt>评分服务</dt>
            <dd><el-tag :type="statusType(systemStatus.scoringService)">{{ statusLabel(systemStatus.scoringService) }}</el-tag></dd>
          </div>
          <div>
            <dt>当前模型</dt>
            <dd>{{ displayValue(systemStatus.activeModelVersion || summary.activeModelVersion) }}</dd>
          </div>
          <div>
            <dt>最近同步</dt>
            <dd>{{ formatDateTime(systemStatus.lastUpdatedAt || dashboardData.updatedAt) }}</dd>
          </div>
        </dl>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElEmpty, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { getDashboard } from '@/api/admin'
import { getErrorMessage } from '@/api/response'
import { displayValue, formatDateTime, statusLabel, statusType } from '@/utils/format'

const loading = ref(false)
const errorMessage = ref('')
const dashboardData = ref({})

const summary = computed(() => dashboardData.value.summary || {})
const systemStatus = computed(() => dashboardData.value.systemStatus || {})
const recentRuns = computed(() => dashboardData.value.recentScoringRuns || dashboardData.value.recentRuns || [])

const metrics = computed(() => {
  const studentCount = summary.value.studentCount
  const teacherCount = summary.value.teacherCount
  const hasStudentCount = studentCount !== null && studentCount !== undefined && studentCount !== ''
  const hasTeacherCount = teacherCount !== null && teacherCount !== undefined && teacherCount !== ''
  const accountCount = summary.value.accountCount ?? (
    hasStudentCount && hasTeacherCount && Number.isFinite(Number(studentCount)) && Number.isFinite(Number(teacherCount))
      ? Number(studentCount) + Number(teacherCount)
      : null
  )
  return [
    { label: '师生账号', value: accountCount, hint: '当前纳入平台管理的账号' },
    { label: '课程数量', value: summary.value.courseCount, hint: '当前教学数据范围' },
    { label: '评分运行', value: summary.value.scoringRunCount, hint: '已发起的算法运行总数' },
    { label: '待处理异常', value: summary.value.pendingAnomalyCount, hint: '需要管理员复核的记录' }
  ]
})

async function loadDashboard() {
  loading.value = true
  errorMessage.value = ''
  try {
    dashboardData.value = await getDashboard() || {}
  } catch (error) {
    dashboardData.value = {}
    errorMessage.value = getErrorMessage(error, '工作台数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped>
.dashboard-alert {
  margin-bottom: 16px;
}

.text-link {
  color: #2563eb;
  font-size: 13px;
}

.status-list {
  display: grid;
  gap: 18px;
  margin: 0;
}

.status-list > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eef2f7;
}

.status-list > div:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.status-list dt {
  color: #64748b;
  font-size: 13px;
}

.status-list dd {
  margin: 0;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  text-align: right;
}
</style>
