const STORAGE_KEY = 'wisecollab.student.recentProject'
export const RECENT_PROJECT_EVENT = 'wisecollab:recent-project-changed'

export function getRecentProject() {
  try {
    const value = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')
    return value?.assignmentId ? value : null
  } catch {
    return null
  }
}

export function saveRecentProject(project) {
  if (!project?.assignmentId) return
  const value = {
    assignmentId: String(project.assignmentId),
    courseId: project.courseId == null ? null : String(project.courseId),
    title: project.title || '未命名作业',
    courseName: project.courseName || '',
    deadline: project.deadline || null,
    openedAt: new Date().toISOString()
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(value))
  window.dispatchEvent(new CustomEvent(RECENT_PROJECT_EVENT, { detail: value }))
}
