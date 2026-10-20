import { downloadFile, request } from '../api'

export const listAuditLogs = (params) => request('/api/admin/audit-logs', { params })

export const getAuditLog = (id) => request(`/api/admin/audit-logs/${id}`)

export const getAuditLogOptions = () => request('/api/admin/audit-logs/options')

export const getAuditLogRetentionSetting = () => request('/api/admin/audit-logs/retention')

export const updateAuditLogRetentionSetting = (payload) => request('/api/admin/audit-logs/retention', {
  method: 'PUT',
  data: payload
})

export const runAuditLogRetentionCleanup = () => request('/api/admin/audit-logs/retention/run', {
  method: 'POST'
})

export const exportAuditLogs = (params = {}) => {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, value)
    }
  })
  const query = searchParams.toString()
  return downloadFile(`/api/admin/audit-logs/export${query ? `?${query}` : ''}`, 'audit-logs.csv')
}
