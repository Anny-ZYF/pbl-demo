<template>
  <section class="course-center-page">
    <header class="course-center-header">
      <div>
        <p class="eyebrow">TEACHER WORKSPACE</p>
        <h1>我的授课课程</h1>
        <p>选择一门课程进入课程工作区，管理作业、团队与评分。</p>
      </div>
      <div class="course-center-header__actions">
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          添加课程
        </el-button>
        <el-button :loading="loading" @click="loadCourses">
          <el-icon><Refresh /></el-icon>
          刷新课程
        </el-button>
      </div>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="course-alert" />
    <el-skeleton v-if="loading" :rows="6" animated />
    <el-empty v-else-if="!courses.length" description="暂时没有授课课程" />

    <div v-else class="course-grid">
      <button v-for="course in courses" :key="course.id" class="course-card" type="button" @click="openCourse(course)">
        <div class="course-card__cover"><span>PBL</span></div>
        <div class="course-card__body">
          <span class="course-card__code">{{ course.code || '未设置课程代码' }}</span>
          <h2>{{ course.name || '未命名课程' }}</h2>
          <div class="course-card__meta">
            <span>{{ course.term || '未设置学期' }}</span>
            <span>{{ course.studentCount ?? 0 }} 名学生</span>
          </div>
          <span class="course-card__action">进入课程工作区 <el-icon><ArrowRight /></el-icon></span>
        </div>
      </button>
    </div>

    <el-dialog v-model="dialogVisible" title="添加课程" width="520px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="课程名称" prop="name">
          <el-input v-model="form.name" maxlength="100" placeholder="请输入课程名称" />
        </el-form-item>
        <el-form-item label="课程代码" prop="code">
          <el-input v-model="form.code" maxlength="50" placeholder="可选，例如 CS101" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="学期" prop="term">
            <el-input v-model="form.term" maxlength="32" placeholder="例如 2026 秋季" />
          </el-form-item>
          <el-form-item label="年级" prop="grade">
            <el-input v-model="form.grade" maxlength="4" placeholder="例如 2026" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="开始日期" prop="beginDate">
            <el-date-picker v-model="form.beginDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
          </el-form-item>
          <el-form-item label="结束日期" prop="endDate">
            <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCourse">创建课程</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElDialog, ElEmpty, ElForm, ElFormItem, ElIcon, ElInput, ElDatePicker, ElMessage, ElSkeleton } from 'element-plus'
import { ArrowRight, Plus, Refresh } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { createCourse, getCourses } from '@/api/teacher'
import { getErrorMessage } from '@/api/response'

const router = useRouter()
const courses = ref([])
const loading = ref(false)
const errorMessage = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const emptyForm = () => ({ code: '', name: '', term: '', grade: '', beginDate: '', endDate: '' })
const form = ref(emptyForm())
const rules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }]
}

const loadCourses = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await getCourses()
    courses.value = Array.isArray(data) ? data : []
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '课程加载失败，请检查后端服务和登录状态')
  } finally {
    loading.value = false
  }
}

const openCourse = (course) => {
  if (!course?.id) {
    ElMessage.warning('课程缺少有效编号，暂时无法进入')
    return
  }
  router.push(`/teacher/course/${course.id}/workspace`)
}

const openCreateDialog = () => {
  form.value = emptyForm()
  dialogVisible.value = true
}

const resetForm = () => {
  form.value = emptyForm()
  formRef.value?.clearValidate()
}

const submitCourse = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createCourse({ ...form.value, code: form.value.code || null, term: form.value.term || null, grade: form.value.grade || null, beginDate: form.value.beginDate || null, endDate: form.value.endDate || null })
    ElMessage.success('课程创建成功')
    dialogVisible.value = false
    await loadCourses()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '课程创建失败，请稍后重试'))
  } finally {
    submitting.value = false
  }
}

onMounted(loadCourses)
</script>

<style scoped>
.course-center-page { max-width: 1240px; margin: 0 auto; }
.course-center-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 28px; }
.course-center-header__actions { display: flex; gap: 10px; }
.eyebrow { margin: 0 0 8px; color: #2a86e2; font-size: 11px; font-weight: 700; letter-spacing: .16em; }
.course-center-header h1 { margin: 0; color: #263b53; font-size: 28px; }
.course-center-header p:last-child { margin: 8px 0 0; color: #8492a4; font-size: 14px; }
.course-alert { margin-bottom: 18px; }
.course-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(270px, 1fr)); gap: 22px; }
.course-card { overflow: hidden; padding: 0; border: 0; border-radius: 8px; color: #334155; background: #fff; box-shadow: 0 3px 12px rgba(34, 74, 112, .1); text-align: left; cursor: pointer; transition: transform .2s ease, box-shadow .2s ease; }
.course-card:hover { transform: translateY(-4px); box-shadow: 0 8px 22px rgba(34, 74, 112, .16); }
.course-card__cover { display: grid; height: 126px; place-items: center; color: rgba(255,255,255,.92); background: linear-gradient(135deg, #2a86e2, #62b0ef); font-size: 30px; font-weight: 800; letter-spacing: .12em; }
.course-card__body { display: grid; gap: 9px; padding: 17px 18px 18px; }
.course-card__code { color: #2a86e2; font-size: 11px; font-weight: 700; letter-spacing: .08em; }
.course-card h2 { margin: 0; color: #263b53; font-size: 18px; }
.course-card__meta { display: flex; gap: 18px; color: #8a97a7; font-size: 12px; }
.course-card__action { display: flex; align-items: center; gap: 4px; margin-top: 6px; color: #2a86e2; font-size: 13px; font-weight: 600; }
@media (max-width: 640px) { .course-center-header { display: block; } .course-center-header .el-button { margin-top: 16px; } }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.form-row .el-date-editor, .form-row .el-input { width: 100%; }
@media (max-width: 520px) { .form-row { grid-template-columns: 1fr; gap: 0; } .course-center-header__actions { margin-top: 16px; } }
</style>
