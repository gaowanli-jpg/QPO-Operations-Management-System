import { request } from '../api'

export const listDataCenterSsoConfigs = () => request('/api/system/data-center-sso-configs')

export const saveDataCenterSsoConfig = (payload) => {
  const id = payload?.id
  return request(id ? `/api/system/data-center-sso-configs/${id}` : '/api/system/data-center-sso-configs', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const testDataCenterSsoConfig = (id) => request(`/api/system/data-center-sso-configs/${id}/test`, {
  method: 'POST'
})

export const activateDataCenterSsoConfig = (id) => request(`/api/system/data-center-sso-configs/${id}/activate`, {
  method: 'PUT'
})

export const disableDataCenterSsoConfig = (id) => request(`/api/system/data-center-sso-configs/${id}/disable`, {
  method: 'PUT'
})
