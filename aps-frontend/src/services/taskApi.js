import { request } from '../api'

export const listPmcTasks = (params) => request(withQuery('/api/aps/pmc/tasks/page', params))

export const getPmcTask = (taskId) => request(`/api/aps/pmc/tasks/${taskId}`)

export const releaseAssignment = (taskId) => request(
  `/api/aps/pmc/tasks/${taskId}/release-assignment`,
  { method: 'POST' }
)

export const listDepartmentCenterTasks = (params) => request(
  withQuery('/api/aps/department-center/tasks/page', params)
)

export const getDepartmentCenterTask = (taskId) => request(`/api/aps/department-center/tasks/${taskId}`)

export const listTaskCustomers = (params) => request(withQuery('/api/aps/pmc/tasks/customers', params))

export const assignDepartment = (payload) => request('/api/aps/pmc/tasks/assign-department', {
  method: 'POST',
  data: payload
})

export const deleteTasks = (ids) => request('/api/aps/pmc/tasks', {
  method: 'DELETE',
  data: { ids }
})

export const createTasks = (ids) => request('/api/aps/pmc/tasks', {
  method: 'POST',
  data: { ids }
})

export const syncErpDeliveryItems = () => request('/api/aps/sync/erp-delivery-items', {
  method: 'POST'
})

const withQuery = (path, params) => {
  const query = toQueryString(params)
  return query ? `${path}?${query}` : path
}

const toQueryString = (params) => {
  if (!params) return ''
  if (params instanceof URLSearchParams) return params.toString()
  return new URLSearchParams(params).toString()
}
