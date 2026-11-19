import http from './http'

export const getCourses = () => http.get('/teacher/courses')
export const createCourse = (data) => http.post('/teacher/courses', data)
export const getAssignments = (courseId) => http.get(`/teacher/courses/${courseId}/assignments`)
export const createAssignment = (courseId, data) => http.post(`/teacher/courses/${courseId}/assignments`, data)
export const publishAssignment = (assignmentId) => http.post(`/teacher/assignments/${assignmentId}/publish`)
export const getAssignmentDetail = (assignmentId) => http.get(`/teacher/assignments/${assignmentId}`)
export const updateAssignment = (assignmentId, data) => http.put(`/teacher/assignments/${assignmentId}`, data)
export const deleteAssignment = (assignmentId) => http.delete(`/teacher/assignments/${assignmentId}`)
export const getStudents = (courseId, assignmentId) => http.get(`/teacher/courses/${courseId}/students`, { params: { assignmentId } })
export const getTeacherScores = (courseId, assignmentId) => http.get(`/teacher/courses/${courseId}/scores`, { params: { assignmentId } })
export const getImportClasses = (courseId, keyword = '') => http.get(`/teacher/courses/${courseId}/classes`, { params: { keyword } })
export const getClassRoster = (courseId, classId) => http.get(`/teacher/courses/${courseId}/classes/${classId}/students`)
// 兼容旧调用：直接传学号数组时自动包装成 { studentNos }
export const importStudents = (courseId, payload) => http.post(
  `/teacher/courses/${courseId}/students/import`,
  Array.isArray(payload) ? { studentNos: payload } : payload
)
export const dropStudent = (courseId, studentId) => http.delete(`/teacher/courses/${courseId}/students/${studentId}`)
export const getGroups = (assignmentId) => http.get(`/teacher/assignments/${assignmentId}/groups`)
export const getGroupMembers = (assignmentId, groupId) => http.get(`/teacher/assignments/${assignmentId}/groups/${groupId}/members`)
export const getTeacherWorkload = (assignmentId, groupId) => http.get(`/teacher/assignments/${assignmentId}/groups/${groupId}/workload`)
export const saveTeacherWorkload = (assignmentId, groupId, data) => http.put(`/teacher/assignments/${assignmentId}/groups/${groupId}/workload`, data)
export const getSubmissions = (assignmentId) => http.get(`/teacher/assignments/${assignmentId}/submissions`)
export const runScoring = (submissionId) => http.post(`/teacher/submissions/${submissionId}/scoring-runs`)
export const gradeSubmission = (submissionId, data) => http.put(`/teacher/submissions/${submissionId}/grade`, data)
export const publishScoringRun = (runId) => http.post(`/teacher/scoring-runs/${runId}/publish`)
export const getScoringRun = (runId) => http.get(`/teacher/scoring-runs/${runId}`)
export const reviewScoringRun = (runId, data) => http.put(`/teacher/scoring-runs/${runId}/review`, data)
export const getTeacherAppeals = () => http.get('/teacher/appeals')
export const resolveScoreAppeal = (appealId, data) => http.put(`/teacher/appeals/${appealId}`, data)
