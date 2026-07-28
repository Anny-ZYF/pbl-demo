import http from './http'

export const getCourses = () => http.get('/teacher/courses')
export const getAssignments = (courseId) => http.get(`/teacher/courses/${courseId}/assignments`)
export const getStudents = (courseId, assignmentId) => http.get(`/teacher/courses/${courseId}/students`, { params: { assignmentId } })
export const getGroups = (assignmentId) => http.get(`/teacher/assignments/${assignmentId}/groups`)
export const getSubmissions = (assignmentId) => http.get(`/teacher/assignments/${assignmentId}/submissions`)
export const runScoring = (submissionId) => http.post(`/teacher/submissions/${submissionId}/scoring-runs`)
