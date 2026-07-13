import { downloadFile, request } from '../api'

export const listMaterials = (params = { limit: '200' }) => request(
  withQuery('/api/aps/inventory/materials', params)
)

export const createMaterial = (payload) => request('/api/aps/inventory/materials', {
  method: 'POST',
  data: payload
})

export const deleteMaterial = (materialId) => request(`/api/aps/inventory/materials/${materialId}`, {
  method: 'DELETE'
})

export const listMaterialLocks = (params) => request(
  withQuery('/api/aps/inventory/materials/locks', params)
)

export const listMaterialLockCustomers = () => request('/api/aps/inventory/materials/locks/customers')

export const confirmMaterialLock = (lockId) => request(
  `/api/aps/inventory/materials/locks/${lockId}/confirm`,
  { method: 'POST' }
)

export const releaseMaterialLock = (lockId) => request(
  `/api/aps/inventory/materials/locks/${lockId}/release`,
  { method: 'POST' }
)

export const listMaterialShortages = (params = { limit: '200' }) => request(
  withQuery('/api/aps/inventory/materials/shortages', params)
)

export const listPartCustomers = () => request('/api/aps/inventory/parts/customers')

export const listPartInventoryPage = (params) => request(
  withQuery('/api/aps/inventory/parts/page', params)
)

export const createPartInventory = (payload) => request('/api/aps/inventory/parts', {
  method: 'POST',
  data: payload
})

export const listPartPending = (partId) => request(`/api/aps/inventory/parts/${partId}/pending`)

export const addPartStock = (payload) => request('/api/aps/inventory/parts/add-stock', {
  method: 'POST',
  data: payload
})

export const transferPartPending = (payload) => request('/api/aps/inventory/parts/transfer-pending', {
  method: 'POST',
  data: payload
})

export const modifyPartInventory = (payload) => request('/api/aps/inventory/parts/modify', {
  method: 'POST',
  data: payload
})

export const deletePartInventory = (partId) => request(`/api/aps/inventory/parts/${partId}`, {
  method: 'DELETE'
})

export const listPartLocks = (params) => request(
  withQuery('/api/aps/inventory/parts/locks', params)
)

export const confirmPartLock = (lockId) => request(
  `/api/aps/inventory/parts/locks/${lockId}/confirm`,
  { method: 'POST' }
)

export const releasePartLock = (lockId) => request(
  `/api/aps/inventory/parts/locks/${lockId}/release`,
  { method: 'POST' }
)

export const exportPartLocks = (params) => downloadFile(
  withQuery('/api/aps/inventory/parts/locks/export', params),
  '零件锁定记录.xlsx'
)

export const listPartProductionRequirements = (params) => request(
  withQuery('/api/aps/inventory/parts/production-requirements', params)
)

const withQuery = (path, params) => {
  const query = toQueryString(params)
  return query ? `${path}?${query}` : path
}

const toQueryString = (params) => {
  if (!params) return ''
  if (params instanceof URLSearchParams) return params.toString()
  return new URLSearchParams(params).toString()
}
