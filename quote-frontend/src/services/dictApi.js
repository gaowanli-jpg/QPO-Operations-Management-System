import { request } from '../api'

const buildQuery = (params = {}) => {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, value)
    }
  })
  const query = search.toString()
  return query ? `?${query}` : ''
}

export const listMaterialTypes = ({ enabledOnly } = {}) => request(
  `/api/dict/material-types${buildQuery({ enabledOnly })}`
)

export const saveMaterialType = (payload) => {
  const id = payload?.id
  return request(id ? `/api/dict/material-types/${id}` : '/api/dict/material-types', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const deleteMaterialType = (id) => request(`/api/dict/material-types/${id}`, {
  method: 'DELETE'
})

export const listMaterials = ({ enabledOnly } = {}) => request(
  `/api/dict/materials${buildQuery({ enabledOnly })}`
)

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

export const syncMesMaterials = () => request('/api/dict/materials/sync-mes', {
  method: 'POST'
})

export const listProcessCategories = ({ enabledOnly } = {}) => request(
  `/api/dict/process-categories${buildQuery({ enabledOnly })}`
)

export const saveProcessCategory = (payload) => {
  const id = payload?.id
  return request(id ? `/api/dict/process-categories/${id}` : '/api/dict/process-categories', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const syncMesProcessTypes = () => request('/api/dict/process-categories/sync-mes', {
  method: 'POST'
})

export const listProcessDetails = ({ categoryId, enabledOnly } = {}) => request(
  `/api/dict/process-details${buildQuery({ categoryId, enabledOnly })}`
)

export const saveProcessDetail = (payload) => {
  const id = payload?.id
  return request(id ? `/api/dict/process-details/${id}` : '/api/dict/process-details', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

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

export const listAssemblyMarkupFees = () => request('/api/dict/assembly-markup-fees')

export const saveAssemblyMarkupFee = (payload) => {
  const id = payload?.id
  return request(id ? `/api/dict/assembly-markup-fees/${id}` : '/api/dict/assembly-markup-fees', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const deleteAssemblyMarkupFee = (id) => request(`/api/dict/assembly-markup-fees/${id}`, {
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
