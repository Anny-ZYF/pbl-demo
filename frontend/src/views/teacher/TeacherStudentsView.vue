<template>
  <div class="students-page">
    <header class="heading">
      <div>
        <h1>学生管理</h1>
        <span>{{ course?.name || '当前课程' }} · 查看并管理本课程的学生名单</span>
      </div>
      <div class="heading-actions">
        <button type="button" class="import-button" @click="importVisible = true">＋ 导入学生</button>
        <div class="summary"><strong>{{ students.length }}</strong><span>课程学生</span></div>
      </div>
    </header>
    <div v-if="errorMessage" class="error-box">{{ errorMessage }}</div>
    <div class="toolbar"><div class="search"><el-icon><Search /></el-icon><input v-model="keyword" placeholder="搜索姓名、学号或班级" /></div></div>
    <section class="panel">
      <div class="panel-head"><h2>学生名单</h2></div>
      <div v-if="selectedIds.length" class="batch-bar">
        <span class="batch-count">已选 <b>{{ selectedIds.length }}</b> 人</span>
        <button v-if="allFilteredCount > selectedIds.length" type="button" class="batch-link" @click="selectAllFiltered">选择当前筛选的全部 {{ allFilteredCount }} 人</button>
        <span class="batch-spacer"></span>
        <button type="button" class="batch-clear" @click="clearSelection">取消选择</button>
        <button type="button" class="batch-remove" :disabled="batchRemoving" @click="removeSelected">
          {{ batchRemoving ? '移除中…' : `移除所选（${selectedIds.length}）` }}
        </button>
      </div>
      <div v-if="loading" class="loading">正在加载学生数据…</div>
      <el-empty v-else-if="!filteredStudents.length" description="暂无符合条件的学生" />
      <table v-else>
        <thead><tr>
          <th class="select-col">
            <input type="checkbox" :checked="pageAllSelected" :indeterminate.prop="pageSomeSelected" :disabled="!pageIds.length" aria-label="全选本页学生" @change="togglePageSelection">
          </th>
          <th>学生</th><th>学号</th><th>班级</th><th>专业</th><th class="action-col">操作</th>
        </tr></thead>
        <tbody>
          <tr v-for="student in filteredStudents" :key="student.studentId" :class="{ 'row--selected': isSelected(student.studentId) }">
            <td class="select-col">
              <input type="checkbox" :checked="isSelected(student.studentId)" :aria-label="`选择 ${student.name || '未命名'}`" @change="toggleOne(student.studentId)">
            </td>
            <td><div class="student"><span>{{ (student.name || '?').slice(0, 1) }}</span><strong>{{ student.name || '未命名' }}</strong></div></td>
            <td>{{ student.studentNo || '—' }}</td>
            <td>{{ student.className || '—' }}</td>
            <td>{{ student.majorName || '—' }}</td>
            <td class="action-col"><button type="button" class="row-remove" :disabled="removingId === student.studentId" @click="removeStudent(student)">{{ removingId === student.studentId ? '移除中…' : '移除' }}</button></td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
  <StudentImportDialog v-model="importVisible" :course-id="courseId" @imported="reloadStudents" />
  <PaginationBar v-if="pageCount > 1" v-model:page="page" :page-count="pageCount" />
</template>
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElEmpty, ElIcon, ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { dropStudent, dropStudents, getCourses, getStudents } from '@/api/teacher'
import { getErrorMessage } from '@/api/response'
import PaginationBar from '@/components/PaginationBar.vue'
import StudentImportDialog from '@/components/teacher/StudentImportDialog.vue'
const route = useRoute(); const courseId = route.params.courseId
const students = ref([]); const courses = ref([]); const page = ref(1); const pageSize = 8
const loading = ref(false); const errorMessage = ref(''); const keyword = ref(''); const importVisible = ref(false); const removingId = ref(null)
// 勾选按 studentId 记，跨页保留：老师可以第 1 页选几个、翻页再选几个，最后一次性移除。
const selectedIds = ref([]); const batchRemoving = ref(false)
const course = computed(() => courses.value.find((item) => String(item.id) === String(courseId)))
const matchingStudents = computed(() => students.value.filter((student) => { const text = `${student.name || ''} ${student.studentNo || ''} ${student.className || ''}`.toLowerCase(); return !keyword.value || text.includes(keyword.value.toLowerCase()) }))
const pageCount = computed(() => Math.max(1, Math.ceil(matchingStudents.value.length / pageSize))); const filteredStudents = computed(() => matchingStudents.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const allFilteredCount = computed(() => matchingStudents.value.length)
const pageIds = computed(() => filteredStudents.value.map((student) => student.studentId))
const isSelected = (studentId) => selectedIds.value.includes(studentId)
const pageAllSelected = computed(() => pageIds.value.length > 0 && pageIds.value.every(isSelected))
const pageSomeSelected = computed(() => !pageAllSelected.value && pageIds.value.some(isSelected))
const togglePageSelection = () => {
  if (pageAllSelected.value) {
    selectedIds.value = selectedIds.value.filter((id) => !pageIds.value.includes(id))
  } else {
    selectedIds.value = [...new Set([...selectedIds.value, ...pageIds.value])]
  }
}
const toggleOne = (studentId) => {
  selectedIds.value = isSelected(studentId)
    ? selectedIds.value.filter((id) => id !== studentId)
    : [...selectedIds.value, studentId]
}
const selectAllFiltered = () => { selectedIds.value = matchingStudents.value.map((student) => student.studentId) }
const clearSelection = () => { selectedIds.value = [] }
// 搜索条件变了就清空勾选，避免移除了搜索后看不到的学生。
watch(keyword, () => { page.value = 1; selectedIds.value = [] })
// 移除后总页数可能变小，校正页码避免停在空白页。
watch(pageCount, (count) => { if (page.value > count) page.value = count })
const reloadStudents = async () => { try { students.value = await getStudents(courseId) } catch (error) { ElMessage.error(getErrorMessage(error, '学生名单刷新失败')) } }
// 行内按钮直接持有 studentId，无需教师手填不可见的内部 ID。
const removeStudent = async (student) => {
  const label = `${student.name || '未命名'}（${student.studentNo || '无学号'}）`
  try {
    await ElMessageBox.confirm(`确定将 ${label} 从当前课程移除吗？移除后不会删除账号，可通过「导入学生」重新加入。`, '移除学生', { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' })
  } catch { return }
  removingId.value = student.studentId
  try {
    await dropStudent(courseId, student.studentId)
    ElMessage.success(`${label} 已从当前课程移除`)
    selectedIds.value = selectedIds.value.filter((id) => id !== student.studentId)
    await reloadStudents()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '移除学生失败'))
  } finally {
    removingId.value = null
  }
}
// 批量移除：确认框里列出前几个人名，让老师能核对选中的是不是想移除的那批。
const removeSelected = async () => {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  const names = students.value.filter((student) => ids.includes(student.studentId)).map((student) => student.name || '未命名')
  const preview = names.slice(0, 6).join('、') + (names.length > 6 ? ` 等 ${names.length} 人` : '')
  try {
    await ElMessageBox.confirm(`确定将 ${preview} 共 ${ids.length} 人从当前课程移除吗？移除后不会删除账号，可通过「导入学生」重新加入。`, `移除 ${ids.length} 名学生`, { type: 'warning', confirmButtonText: '全部移除', cancelButtonText: '取消' })
  } catch { return }
  batchRemoving.value = true
  try {
    const result = await dropStudents(courseId, ids)
    const removed = Number(result?.removed ?? ids.length)
    const skipped = Number(result?.skipped || 0)
    ElMessage.success(`已移除 ${removed} 名学生${skipped ? `，${skipped} 人已不在课程中` : ''}`)
    clearSelection()
    await reloadStudents()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '批量移除学生失败'))
  } finally {
    batchRemoving.value = false
  }
}
onMounted(async () => { loading.value = true; try { [courses.value, students.value] = await Promise.all([getCourses(), getStudents(courseId)]) } catch (error) { errorMessage.value = getErrorMessage(error, '学生数据加载失败') } finally { loading.value = false } })
</script>
<style scoped>

.heading-actions{display:flex;align-items:center;gap:12px}.import-button{padding:11px 20px;border:0;border-radius:8px;color:#fff;background:linear-gradient(135deg,#3a6ea5,#2f5d8b);box-shadow:0 4px 12px rgba(47,93,139,.24);font-size:14px;font-weight:500;letter-spacing:.3px;white-space:nowrap;cursor:pointer;transition:transform .15s,box-shadow .15s,background .15s}.import-button:hover{background:linear-gradient(135deg,#356798,#2a5480);box-shadow:0 6px 16px rgba(47,93,139,.3);transform:translateY(-1px)}.import-button:active{transform:translateY(0);box-shadow:0 3px 9px rgba(47,93,139,.22)}
.students-page{max-width:1180px;margin:0 auto}.heading{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:26px}.heading p{margin:0 0 7px;color:#6bb58b;font-size:11px;letter-spacing:1.5px}.heading h1{margin:0 0 8px;color:#263b53;font-size:30px}.heading span{color:#8795a6;font-size:14px}.summary{display:flex;align-items:baseline;gap:10px;padding:14px 20px;border:1px solid #e1ebf2;border-radius:8px;background:#f8fbfd;color:#8795a6}.summary strong{color:#356798;font-size:28px}.toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px;padding:14px 18px;border:1px solid #e8edf2;border-radius:8px;background:#fff}.search{display:flex;align-items:center;gap:8px;width:280px;color:#9aa8b7}.search input{width:100%;border:0;outline:0;color:#334155;font-size:14px}.panel{overflow:hidden;border-radius:8px;background:#fff;box-shadow:0 2px 8px rgba(38,59,83,.05)}.panel-head{display:flex;justify-content:space-between;padding:18px 22px;border-bottom:1px solid #edf1f4}.panel-head h2{margin:0;color:#263b53;font-size:17px}.panel-head span{color:#6bb58b;font-size:13px}table{width:100%;border-collapse:collapse;text-align:left}th{padding:13px 22px;color:#8795a6;background:#fafcfd;font-size:12px;font-weight:500}td{padding:15px 22px;border-top:1px solid #edf1f4;color:#52657a;font-size:13px}.student{display:flex;align-items:center;gap:10px}.student span{display:grid;width:32px;height:32px;place-items:center;border-radius:50%;color:#fff;background:#6b9ed8}.student strong{color:#334155;font-weight:500}.action-col{text-align:right}.row-remove{padding:6px 14px;border:1px solid #f3c9c4;border-radius:5px;color:#b42318;background:#fff1f1;font-size:12px;cursor:pointer}.row-remove:hover{background:#ffe4e1}.row-remove:disabled{color:#b5c0ca;border-color:#e4e9ee;background:#f5f7f9;cursor:not-allowed}.loading{padding:30px;text-align:center;color:#8795a6}.error-box{margin-bottom:16px;padding:13px;border-radius:6px;color:#b42318;background:#fff5f5}@media(max-width:720px){.heading,.toolbar{align-items:stretch;flex-direction:column;gap:15px}.heading-actions{align-items:flex-end;flex-direction:column-reverse;gap:10px}.search{width:100%}.panel{overflow-x:auto}table{min-width:680px}}

/* 批量移除：勾选列、选中行底色、顶部操作条 */
.select-col{width:44px;padding-right:0;text-align:center}
.select-col input{width:15px;height:15px;margin:0;accent-color:#356798;cursor:pointer}
tbody tr.row--selected td{background:#f4f8fd}
.batch-bar{display:flex;align-items:center;gap:10px;padding:11px 18px;border-bottom:1px solid #e3edf6;background:#f6faff;color:#52657a;font-size:13px}
.batch-count b{color:#356798}
.batch-spacer{flex:1}
.batch-link{padding:0;border:0;color:#356798;background:none;font-size:13px;cursor:pointer}
.batch-link:hover{text-decoration:underline}
.batch-clear{padding:6px 12px;border:1px solid #dce5ec;border-radius:5px;color:#52657a;background:#fff;font-size:12px;cursor:pointer}
.batch-remove{padding:7px 14px;border:1px solid #f3c9c4;border-radius:5px;color:#b42318;background:#fff1f1;font-size:12px;cursor:pointer}
.batch-remove:hover:not(:disabled){background:#ffe4e1}
.batch-remove:disabled{color:#b5c0ca;border-color:#e2e8ee;background:#f5f7f9;cursor:not-allowed}
@media (max-width:640px){.batch-bar{flex-wrap:wrap}.batch-spacer{display:none}}
</style>
