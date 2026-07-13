import { request } from '../api'

export const listProcesses = () => request('/api/aps/processes')

export const syncProcessesFromErp = (params = null) => {
  const suffix = buildSyncQuery(params)
  return request(`/api/aps/processes/sync-from-erp${suffix}`, {
    method: 'POST'
  })
}

const buildSyncQuery = (params) => {
  if (!params) return ''
  if (params === true) return '?importMissing=true'
  if (typeof params === 'string') return `?processType=${encodeURIComponent(params)}`
  const query = new URLSearchParams(params).toString()
  return query ? `?${query}` : ''
}
