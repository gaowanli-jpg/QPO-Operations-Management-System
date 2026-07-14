import { request } from '../api'

export const listMaterials = () => request('/api/dict/materials')

export const saveMaterial = (payload) => {
  const id = payload?.id
  return request(id ? `/api/dict/materials/${id}` : '/api/dict/materials', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const deleteMaterial = (id) => request(`/api/dict/materials/${id}`, {
  method: 'DELETE'
})

export const listProcesses = () => request('/api/dict/processes')

export const saveProcess = (payload) => {
  const id = payload?.id
  return request(id ? `/api/dict/processes/${id}` : '/api/dict/processes', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const deleteProcess = (id) => request(`/api/dict/processes/${id}`, {
  method: 'DELETE'
})

export const listMarkupFees = () => request('/api/dict/markup-fees')

export const saveMarkupFee = (payload) => {
  const id = payload?.id
  return request(id ? `/api/dict/markup-fees/${id}` : '/api/dict/markup-fees', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const deleteMarkupFee = (id) => request(`/api/dict/markup-fees/${id}`, {
  method: 'DELETE'
})

export const listExtraFeeItems = ({ includeDisabled = false } = {}) => request(
  includeDisabled ? '/api/dict/extra-fee-items/all' : '/api/dict/extra-fee-items'
)

export const saveExtraFeeItem = (payload) => {
  const id = payload?.id
  return request(id ? `/api/dict/extra-fee-items/${id}` : '/api/dict/extra-fee-items', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const deleteExtraFeeItem = (id) => request(`/api/dict/extra-fee-items/${id}`, {
  method: 'DELETE'
})
