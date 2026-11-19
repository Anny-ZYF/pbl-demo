<template>
  <div class="student-actions"><button type="button" class="import-button" @click="importVisible = true">＋ 导入学生</button></div>
  <div class="students-page">
    <div class="breadcrumb"><button type="button" @click="router.push(`/teacher/course/${courseId}/workspace`)">课程首页</button><span>›</span><strong>学生管理</strong></div>
    <header class="heading"><div><p>COURSE STUDENTS</p><h1>学生管理</h1><span>{{ course?.name || '当前课程' }} · 共 {{ students.length }} 名学生</span></div><div class="summary"><strong>{{ students.length }}</strong><span>课程学生</span></div></header>
    <div v-if="errorMessage" class="error-box">{{ errorMessage }}</div>
    <div class="toolbar"><div class="search"><el-icon><Search /></el-icon><input v-model="keyword" placeholder="搜索姓名、学号或班级" /></div></div>
    <section class="panel">
      <div class="panel-head"><h2>学生名单</h2><span>显示 {{ filteredStudents.length }} 人</span></div>
      <div v-if="loading" class="loading">正在加载学生数据…</div>
      <el-empty v-else-if="!filteredStudents.length" description="暂无符合条件的学生" />
      <table v-else>
        <thead><tr><th>学生</th><th>学号</th><th>班级</th><th>专业</th><th class="action-col">操作</th></tr></thead>
        <tbody>
          <tr v-for="student in filteredStudents" :key="student.studentId">
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
  <div class="pagination-bar" v-if="pageCount > 1"><button type="button" :disabled="page <= 1" @click="page--">上一页</button><span>第 {{ page }} / {{ pageCount }} 页</span><button type="button" :disabled="page >= pageCount" @click="page++">下一页</button></div>
</template>
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElEmpty, ElIcon, ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { dropStudent, getCourses, getStudents } from '@/api/teacher'
import { getErrorMessage } from '@/api/response'
import StudentImportDialog from '@/components/teacher/StudentImportDialog.vue'
const route = useRoute(); const router = useRouter(); const courseId = route.params.courseId
const students = ref([]); const courses = ref([]); const page = ref(1); const pageSize = 8
const loading = ref(false); const errorMessage = ref(''); const keyword = ref(''); const importVisible = ref(false); const removingId = ref(null)
const course = computed(() => courses.value.find((item) => String(item.id) === String(courseId)))
const matchingStudents = computed(() => students.value.filter((student) => { const text = `${student.name || ''} ${student.studentNo || ''} ${student.className || ''}`.toLowerCase(); return !keyword.value || text.includes(keyword.value.toLowerCase()) }))
const pageCount = computed(() => Math.max(1, Math.ceil(matchingStudents.value.length / pageSize))); const filteredStudents = computed(() => matchingStudents.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch(keyword, () => { page.value = 1 })
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
    await reloadStudents()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '移除学生失败'))
  } finally {
    removingId.value = null
  }
}
onMounted(async () => { loading.value = true; try { [courses.value, students.value] = await Promise.all([getCourses(), getStudents(courseId)]) } catch (error) { errorMessage.value = getErrorMessage(error, '学生数据加载失败') } finally { loading.value = false } })
</script>
<style scoped>
.pagination-bar{display:flex;align-items:center;justify-content:center;gap:16px;margin:22px 0 0;color:#718096;font-size:14px}.pagination-bar button{min-width:76px;padding:9px 14px;border:1px solid #dce5ec;border-radius:5px;color:#52657a;background:#fff;cursor:pointer}.pagination-bar button:disabled{color:#b5c0ca;background:#f5f7f9;cursor:not-allowed}
.student-actions{display:flex;justify-content:flex-end;margin-bottom:14px}.import-button{padding:9px 15px;border:0;border-radius:5px;color:#fff;background:#356798;cursor:pointer}
.students-page{max-width:1180px;margin:0 auto}.breadcrumb{display:flex;gap:8px;margin-bottom:24px;color:#8795a6;font-size:14px}.breadcrumb button{padding:0;border:0;background:none;color:inherit;cursor:pointer}.breadcrumb strong{color:#263b53}.heading{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:26px}.heading p{margin:0 0 7px;color:#6bb58b;font-size:11px;letter-spacing:1.5px}.heading h1{margin:0 0 8px;color:#263b53;font-size:30px}.heading span{color:#8795a6;font-size:14px}.summary{display:flex;align-items:baseline;gap:10px;padding:14px 20px;border:1px solid #e1ebf2;border-radius:8px;background:#f8fbfd;color:#8795a6}.summary strong{color:#356798;font-size:28px}.toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px;padding:14px 18px;border:1px solid #e8edf2;border-radius:8px;background:#fff}.search{display:flex;align-items:center;gap:8px;width:280px;color:#9aa8b7}.search input{width:100%;border:0;outline:0;color:#334155;font-size:14px}.panel{overflow:hidden;border-radius:8px;background:#fff;box-shadow:0 2px 8px rgba(38,59,83,.05)}.panel-head{display:flex;justify-content:space-between;padding:18px 22px;border-bottom:1px solid #edf1f4}.panel-head h2{margin:0;color:#263b53;font-size:17px}.panel-head span{color:#6bb58b;font-size:13px}table{width:100%;border-collapse:collapse;text-align:left}th{padding:13px 22px;color:#8795a6;background:#fafcfd;font-size:12px;font-weight:500}td{padding:15px 22px;border-top:1px solid #edf1f4;color:#52657a;font-size:13px}.student{display:flex;align-items:center;gap:10px}.student span{display:grid;width:32px;height:32px;place-items:center;border-radius:50%;color:#fff;background:#6b9ed8}.student strong{color:#334155;font-weight:500}.action-col{text-align:right}.row-remove{padding:6px 14px;border:1px solid #f3c9c4;border-radius:5px;color:#b42318;background:#fff1f1;font-size:12px;cursor:pointer}.row-remove:hover{background:#ffe4e1}.row-remove:disabled{color:#b5c0ca;border-color:#e4e9ee;background:#f5f7f9;cursor:not-allowed}.loading{padding:30px;text-align:center;color:#8795a6}.error-box{margin-bottom:16px;padding:13px;border-radius:6px;color:#b42318;background:#fff5f5}@media(max-width:720px){.heading,.toolbar{align-items:stretch;flex-direction:column;gap:15px}.search{width:100%}.panel{overflow-x:auto}table{min-width:680px}}
</style>
