import http from './http'

export const getCourses = () => http.get('/teacher/courses')
export const getAssignments = (courseId) => http.get(`/teacher/courses/${courseId}/assignments`)
export const createAssignment = (courseId, data) => http.post(`/teacher/courses/${courseId}/assignments`, data)
export const publishAssignment = (assignmentId) => http.post(`/teacher/assignments/${assignmentId}/publish`)
export const getStudents = (courseId, assignmentId) => http.get(`/teacher/courses/${courseId}/students`, { params: { assignmentId } })
export const getTeacherScores = (courseId, assignmentId) => http.get(`/teacher/courses/${courseId}/scores`, { params: { assignmentId } })
export const importStudents = (courseId, studentNos) => http.post(`/teacher/courses/${courseId}/students/import`, { studentNos })
export const dropStudent = (courseId, studentId) => http.delete(`/teacher/courses/${courseId}/students/${studentId}`)
export const getGroups = (assignmentId) => http.get(`/teacher/assignments/${assignmentId}/groups`)
export const getSubmissions = (assignmentId) => http.get(`/teacher/assignments/${assignmentId}/submissions`)
export const runScoring = (submissionId) => http.post(`/teacher/submissions/${submissionId}/scoring-runs`)
export const gradeSubmission = (submissionId, data) => http.put(`/teacher/submissions/${submissionId}/grade`, data)
