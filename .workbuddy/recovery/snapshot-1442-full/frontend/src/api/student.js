import http from './http'
import { downloadFileByKey } from './files'
export const getCourses = () => http.get('/student/courses')
export const getAssignments = (courseId) => http.get(`/student/courses/${courseId}/assignments`)
export const getAssignment = (assignmentId) => http.get(`/student/assignments/${assignmentId}`)
export const getScores = () => http.get('/student/scores')
export const appealScore = (scoreResultId, data) => http.post(`/student/scores/${scoreResultId}/appeals`, data)
export const submitAssignment = (assignmentId, data) => http.post(`/student/assignments/${assignmentId}/submission`, data)
// 上传后先登记为小组内可见的草稿附件，正式提交时自动随提交转为教师可见。
export const registerDraftAttachment = (assignmentId, data) => http.post(`/student/assignments/${assignmentId}/draft-attachments`, data)
export const getWorkload = (assignmentId) => http.get(`/student/assignments/${assignmentId}/workload`)
export const proposeWorkload = (assignmentId, groupId, data) => http.put(`/student/assignments/${assignmentId}/groups/${groupId}/workload`, data)
export const confirmWorkload = (setId, data) => http.put(`/student/workload-sets/${setId}/confirmation`, data)
export const createStudentGroup = (assignmentId) => http.post(`/student/assignments/${assignmentId}/groups`)
export const joinStudentGroup = (assignmentId, groupNo) => http.post(`/student/assignments/${assignmentId}/groups/join`, { groupNo })
export const getAssignmentGroups = (assignmentId) => http.get(`/student/assignments/${assignmentId}/groups`)
export const leaveStudentGroup = (assignmentId) => http.delete(`/student/assignments/${assignmentId}/groups/me`)
export const deleteSubmissionAttachment = (assignmentId, attachmentId) => http.delete(`/student/assignments/${assignmentId}/attachments/${attachmentId}`)
// 文件接口是三个角色共用的，实现统一放在 api/files.js
export { uploadFile as uploadStudentFile } from './files'
// 兼容旧调用方：downloadStudentFile 仍只返回 blob，不暴露响应头
export const downloadStudentFile = async (storageKey) => (await downloadFileByKey(storageKey)).blob
