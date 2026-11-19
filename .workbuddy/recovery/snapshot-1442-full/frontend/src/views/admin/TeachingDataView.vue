<template>
  <div>
    <PageHeader title="教学数据" description="从全局视角检索课程及其团队作业数据，日常教学操作由教师端完成。">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadCourses">刷新</el-button>
      </template>
    </PageHeader>

    <section class="surface-card filter-card">
      <div class="filter-row">
        <el-input v-model.trim="filters.keyword" clearable placeholder="课程名称或课程编号" @keyup.enter="search" />
        <el-input v-model.trim="filters.teacherName" clearable placeholder="授课教师" @keyup.enter="search" />
        <el-select v-model="filters.status" clearable placeholder="全部状态">
          <el-option label="开放" value="OPEN" />
          <el-option label="关闭" value="CLOSED" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </section>

    <section class="surface-card table-card">
      <div class="table-card__header">
        <h2>课程列表</h2>
        <span class="muted-text">共 {{ coursePage.total }} 门</span>
      </div>
      <el-table v-loading="loading" :data="coursePage.items">
        <el-table-column prop="courseCode" label="课程编号" min-width="120">
          <template #default="{ row }">{{ displayValue(row.courseCode || row.code) }}</template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程名称" min-width="180">
          <template #default="{ row }">{{ displayValue(row.courseName || row.name) }}</template>
        </el-table-column>
        <el-table-column prop="teacherName" label="授课教师" min-width="110">
          <template #default="{ row }">{{ displayValue(row.teacherName) }}</template>
        </el-table-column>
        <el-table-column prop="term" label="学期" min-width="120">
          <template #default="{ row }">{{ displayValue(row.term || row.semester) }}</template>
        </el-table-column>
        <el-table-column prop="studentCount" label="学生" width="80" align="right">
          <template #default="{ row }">{{ displayValue(row.studentCount) }}</template>
        </el-table-column>
        <el-table-column prop="assignmentCount" label="作业" width="80" align="right">
          <template #default="{ row }">{{ displayValue(row.assignmentCount) }}</template>
        </el-table-column>
        <el-table-column prop="groupCount" label="小组" width="80" align="right">
          <template #default="{ row }">{{ displayValue(row.groupCount) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetails(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="coursePage.total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadCourses"
          @size-change="changePageSize"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="课程详情" size="480px">
      <dl v-if="selectedCourse" class="detail-list detail-list--single">
        <div class="detail-item"><dt>课程编号</dt><dd>{{ displayValue(selectedCourse.courseCode || selectedCourse.code) }}</dd></div>
        <div class="detail-item"><dt>课程名称</dt><dd>{{ displayValue(selectedCourse.courseName || selectedCourse.name) }}</dd></div>
        <div class="detail-item"><dt>授课教师</dt><dd>{{ displayValue(selectedCourse.teacherName) }}</dd></div>
        <div class="detail-item"><dt>所属院系</dt><dd>{{ displayValue(selectedCourse.departmentName) }}</dd></div>
        <div class="detail-item"><dt>学期</dt><dd>{{ displayValue(selectedCourse.term || selectedCourse.semester) }}</dd></div>
        <div class="detail-item"><dt>状态</dt><dd>{{ statusLabel(selectedCourse.status) }}</dd></div>
        <div class="detail-item"><dt>学生 / 作业 / 小组</dt><dd>{{ countSummary(selectedCourse) }}</dd></div>
        <div class="detail-item"><dt>创建时间</dt><dd>{{ formatDateTime(selectedCourse.createdAt || selectedCourse.createTime) }}</dd></div>
      </dl>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  ElButton,
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
import { getCourses } from '@/api/admin'
import { getErrorMessage } from '@/api/response'
import { displayValue, formatDateTime, statusLabel, statusType } from '@/utils/format'

const loading = ref(false)
const detailVisible = ref(false)
const selectedCourse = ref(null)
const coursePage = ref({ items: [], total: 0 })
const pagination = reactive({ page: 1, pageSize: 10 })
const filters = reactive({ keyword: '', teacherName: '', status: '' })

async function loadCourses() {
  loading.value = true
  try {
    coursePage.value = await getCourses({
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: filters.keyword || undefined,
      teacherName: filters.teacherName || undefined,
      status: filters.status || undefined
    })
  } catch (error) {
    coursePage.value = { items: [], total: 0 }
    ElMessage.error(getErrorMessage(error, '课程数据加载失败'))
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  loadCourses()
}

function resetFilters() {
  filters.keyword = ''
  filters.teacherName = ''
  filters.status = ''
  search()
}

function changePageSize() {
  pagination.page = 1
  loadCourses()
}

function showDetails(row) {
  selectedCourse.value = row
  detailVisible.value = true
}

function countSummary(row) {
  const values = [row.studentCount, row.assignmentCount, row.groupCount]
  if (values.every((value) => value === null || value === undefined)) return '—'
  return `${displayValue(row.studentCount)} / ${displayValue(row.assignmentCount)} / ${displayValue(row.groupCount)}`
}

onMounted(loadCourses)
</script>

<style scoped>
.muted-text {
  color: #94a3b8;
  font-size: 12px;
}

.detail-list--single {
  grid-template-columns: 1fr;
}
</style>
