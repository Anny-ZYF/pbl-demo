<template>
  <div>
    <PageHeader title="审计日志" description="追踪登录、数据变更、评分运行和人工处理等关键事件。">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadEvents">刷新</el-button>
      </template>
    </PageHeader>

    <section class="surface-card filter-card">
      <div class="filter-row">
        <el-input v-model.trim="filters.keyword" clearable placeholder="操作人、对象或事件" @keyup.enter="search" />
        <el-select v-model="filters.eventType" clearable placeholder="全部事件">
          <el-option label="登录" value="LOGIN" />
          <el-option label="数据新增" value="CREATE" />
          <el-option label="数据修改" value="UPDATE" />
          <el-option label="数据删除" value="DELETE" />
          <el-option label="评分运行" value="SCORING" />
          <el-option label="异常处理" value="ANOMALY_RESOLUTION" />
        </el-select>
        <el-select v-model="filters.actorRole" clearable placeholder="全部角色">
          <el-option label="管理员" value="ROLE_ADMIN" />
          <el-option label="教师" value="ROLE_TEACHER" />
          <el-option label="学生" value="ROLE_STUDENT" />
        </el-select>
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          clearable
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </section>

    <section class="surface-card table-card">
      <div class="table-card__header">
        <h2>事件列表</h2>
        <span class="muted-text">审计事件只读，不允许前端删除</span>
      </div>
      <el-table v-loading="loading" :data="eventPage.items">
        <el-table-column prop="occurredAt" label="时间" min-width="155">
          <template #default="{ row }">{{ formatDateTime(row.occurredAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="actorName" label="操作人" min-width="110">
          <template #default="{ row }">{{ displayValue(row.actorName || row.operatorName) }}</template>
        </el-table-column>
        <el-table-column prop="actorRole" label="角色" width="90">
          <template #default="{ row }">{{ roleLabel(row.actorRole || row.operatorRole) }}</template>
        </el-table-column>
        <el-table-column prop="eventType" label="事件" min-width="120">
          <template #default="{ row }">{{ eventTypeLabel(row.eventType || row.action) }}</template>
        </el-table-column>
        <el-table-column prop="resourceName" label="对象" min-width="165">
          <template #default="{ row }">{{ displayValue(row.resourceName || row.targetName || row.resourceType) }}</template>
        </el-table-column>
        <el-table-column prop="result" label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="resultType(row.result || row.status)">{{ resultLabel(row.result || row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP 地址" min-width="125">
          <template #default="{ row }">{{ displayValue(row.ipAddress || row.ip) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetails(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="eventPage.total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadEvents"
          @size-change="changePageSize"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="审计事件详情" size="520px">
      <template v-if="selectedEvent">
        <dl class="detail-list">
          <div class="detail-item"><dt>事件编号</dt><dd>{{ displayValue(selectedEvent.eventId || selectedEvent.id) }}</dd></div>
          <div class="detail-item"><dt>发生时间</dt><dd>{{ formatDateTime(selectedEvent.occurredAt || selectedEvent.createdAt) }}</dd></div>
          <div class="detail-item"><dt>操作人</dt><dd>{{ displayValue(selectedEvent.actorName || selectedEvent.operatorName) }}</dd></div>
          <div class="detail-item"><dt>角色</dt><dd>{{ roleLabel(selectedEvent.actorRole || selectedEvent.operatorRole) }}</dd></div>
          <div class="detail-item"><dt>事件类型</dt><dd>{{ eventTypeLabel(selectedEvent.eventType || selectedEvent.action) }}</dd></div>
          <div class="detail-item"><dt>结果</dt><dd>{{ resultLabel(selectedEvent.result || selectedEvent.status) }}</dd></div>
          <div class="detail-item"><dt>资源</dt><dd>{{ displayValue(selectedEvent.resourceName || selectedEvent.targetName) }}</dd></div>
          <div class="detail-item"><dt>IP 地址</dt><dd>{{ displayValue(selectedEvent.ipAddress || selectedEvent.ip) }}</dd></div>
        </dl>
        <h3 class="detail-heading">事件上下文</h3>
        <pre class="json-block">{{ formattedContext }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElDatePicker,
  ElDrawer,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag
} from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { getAuditEvents } from '@/api/admin'
import { getErrorMessage } from '@/api/response'
import { displayValue, formatDateTime } from '@/utils/format'
import { roleLabel } from '@/utils/roles'

const loading = ref(false)
const detailVisible = ref(false)
const selectedEvent = ref(null)
const eventPage = ref({ items: [], total: 0 })
const pagination = reactive({ page: 1, pageSize: 10 })
const filters = reactive({ keyword: '', eventType: '', actorRole: '', dateRange: [] })

const formattedContext = computed(() => {
  const context = selectedEvent.value?.context
    ?? selectedEvent.value?.details
    ?? selectedEvent.value?.metadata
    ?? null
  if (context === null) return '无附加上下文'
  if (typeof context === 'string') return context
  try {
    return JSON.stringify(context, null, 2)
  } catch {
    return String(context)
  }
})

async function loadEvents() {
  loading.value = true
  try {
    eventPage.value = await getAuditEvents({
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: filters.keyword || undefined,
      eventType: filters.eventType || undefined,
      actorRole: filters.actorRole || undefined,
      startDate: filters.dateRange?.[0] || undefined,
      endDate: filters.dateRange?.[1] || undefined
    })
  } catch (error) {
    eventPage.value = { items: [], total: 0 }
    ElMessage.error(getErrorMessage(error, '审计事件加载失败'))
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  loadEvents()
}

function resetFilters() {
  filters.keyword = ''
  filters.eventType = ''
  filters.actorRole = ''
  filters.dateRange = []
  search()
}

function changePageSize() {
  pagination.page = 1
  loadEvents()
}

function showDetails(row) {
  selectedEvent.value = row
  detailVisible.value = true
}

function eventTypeLabel(type) {
  const value = String(type || '').toUpperCase()
  return {
    LOGIN: '登录',
    CREATE: '数据新增',
    UPDATE: '数据修改',
    DELETE: '数据删除',
    SCORING: '评分运行',
    ANOMALY_RESOLUTION: '异常处理',
    PASSWORD_CHANGE: '修改密码'
  }[value] || displayValue(type)
}

function resultLabel(result) {
  const value = String(result || '').toUpperCase()
  return { SUCCESS: '成功', FAILED: '失败', DENIED: '拒绝' }[value] || displayValue(result)
}

function resultType(result) {
  const value = String(result || '').toUpperCase()
  return value === 'SUCCESS' ? 'success' : value === 'FAILED' || value === 'DENIED' ? 'danger' : 'info'
}

onMounted(loadEvents)
</script>

<style scoped>
.muted-text {
  color: #94a3b8;
  font-size: 12px;
}

.detail-heading {
  margin: 28px 0 10px;
  color: #334155;
  font-size: 14px;
}
</style>
