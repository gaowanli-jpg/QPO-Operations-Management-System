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
