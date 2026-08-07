import http from './http'
export const getCourses = () => http.get('/student/courses')
export const getAssignments = (courseId) => http.get(`/student/courses/${courseId}/assignments`)
export const getAssignment = (assignmentId) => http.get(`/student/assignments/${assignmentId}`)
export const getScores = () => http.get('/student/scores')
export const appealScore = (scoreResultId, data) => http.post(`/student/scores/${scoreResultId}/appeals`, data)
export const submitAssignment = (assignmentId, data) => http.post(`/student/assignments/${assignmentId}/submission`, data)
export const createStudentGroup = (assignmentId) => http.post(`/student/assignments/${assignmentId}/groups`)
export const joinStudentGroup = (assignmentId, groupNo) => http.post(`/student/assignments/${assignmentId}/groups/join`, { groupNo })
export const getAssignmentGroups = (assignmentId) => http.get(`/student/assignments/${assignmentId}/groups`)
export const leaveStudentGroup = (assignmentId) => http.delete(`/student/assignments/${assignmentId}/groups/me`)
export const deleteSubmissionAttachment = (assignmentId, attachmentId) => http.delete(`/student/assignments/${assignmentId}/attachments/${attachmentId}`)
export const uploadStudentFile = (file) => { const data = new FormData(); data.append('file', file); return http.post('/files', data, { headers: { 'Content-Type': 'multipart/form-data' } }) }
