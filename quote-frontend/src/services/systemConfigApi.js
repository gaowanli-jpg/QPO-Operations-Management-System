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

export const listDataCenterConfigs = () => request('/api/system/data-center-configs')

export const saveDataCenterConfig = (payload) => {
  const id = payload?.id
  return request(id ? `/api/system/data-center-configs/${id}` : '/api/system/data-center-configs', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const testDataCenterToken = (id) => request(`/api/system/data-center-configs/${id}/test-token`, {
  method: 'POST'
})

export const activateDataCenterConfig = (id) => request(`/api/system/data-center-configs/${id}/activate`, {
  method: 'PUT'
})

export const disableDataCenterConfig = (id) => request(`/api/system/data-center-configs/${id}/disable`, {
  method: 'PUT'
})

export const listDataCenterEndpoints = () => request('/api/system/data-center-endpoints')

export const saveDataCenterEndpoint = (endpointCode, payload) => request(`/api/system/data-center-endpoints/${endpointCode}`, {
  method: 'PUT',
  data: payload
})

export const testDataCenterEndpoint = (endpointCode) => request(`/api/system/data-center-endpoints/${endpointCode}/test`, {
  method: 'POST'
})

export const getU9RuntimeConfig = () => request('/api/system/u9-runtime-config')

export const saveU9RuntimeConfig = (payload) => request('/api/system/u9-runtime-config', {
  method: 'PUT',
  data: payload
})

export const listInboundCallbackConfigs = () => request('/api/system/inbound-callback-configs')

export const getOpenApiTokenStatus = () => request('/api/system/inbound-callback-configs/open-api-token')

export const rotateOpenApiToken = () => request('/api/system/inbound-callback-configs/open-api-token', {
  method: 'POST'
})

export const getInboundCallbackConfig = (interfaceCode) => request(`/api/system/inbound-callback-configs/${interfaceCode}`)

export const saveInboundCallbackConfig = (interfaceCode, payload) => request(`/api/system/inbound-callback-configs/${interfaceCode}`, {
  method: 'PUT',
  data: payload
})

export const validateInboundCallbackConfig = (interfaceCode) => request(`/api/system/inbound-callback-configs/${interfaceCode}/validate`, {
  method: 'POST'
})
