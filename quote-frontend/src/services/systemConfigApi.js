import { request } from '../api'

export const listStorageConfigs = () => request('/api/system/storage-configs')

export const getActiveStorageConfig = () => request('/api/system/storage-configs/active')

export const saveStorageConfig = (payload) => {
  const id = payload?.id
  return request(id ? `/api/system/storage-configs/${id}` : '/api/system/storage-configs', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const testStorageConfig = (id) => request(`/api/system/storage-configs/${id}/test`, {
  method: 'POST'
})

export const activateStorageConfig = (id) => request(`/api/system/storage-configs/${id}/activate`, {
  method: 'PUT'
})

export const disableStorageConfig = (id) => request(`/api/system/storage-configs/${id}/disable`, {
  method: 'PUT'
})

export const listIntegrationConfigs = (params = {}) => request('/api/system/integration-configs', { params })

export const getActiveIntegrationConfigs = () => request('/api/system/integration-configs/active')

export const saveIntegrationConfig = (payload) => {
  const id = payload?.id
  return request(id ? `/api/system/integration-configs/${id}` : '/api/system/integration-configs', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const testIntegrationConfig = (id) => request(`/api/system/integration-configs/${id}/test`, {
  method: 'POST'
})

export const activateIntegrationConfig = (id) => request(`/api/system/integration-configs/${id}/activate`, {
  method: 'PUT'
})

export const disableIntegrationConfig = (id) => request(`/api/system/integration-configs/${id}/disable`, {
  method: 'PUT'
})
