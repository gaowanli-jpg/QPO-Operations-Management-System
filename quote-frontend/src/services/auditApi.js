import { request } from '../api'

export const listAuditLogs = (params) => request('/api/admin/audit-logs', { params })

export const getAuditLog = (id) => request(`/api/admin/audit-logs/${id}`)

export const getAuditLogOptions = () => request('/api/admin/audit-logs/options')
