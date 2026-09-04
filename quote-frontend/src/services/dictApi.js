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

export const pageMaterials = ({ enabledOnly, keyword, page, pageSize } = {}) => request(
  `/api/dict/materials/page${buildQuery({ enabledOnly, keyword, page, pageSize })}`
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

export const getLatestMesMaterialSyncRun = () => request('/api/dict/materials/sync-runs/latest')

export const getMesMaterialSyncRun = (syncRunId) => request(`/api/dict/materials/sync-runs/${syncRunId}`)

export const getMesMaterialSyncSchedule = () => request('/api/dict/materials/sync-schedule')

export const saveMesMaterialSyncSchedule = (payload) => request('/api/dict/materials/sync-schedule', {
  method: 'PUT',
  data: payload
})

export const listProcessCategories = ({ enabledOnly } = {}) => request(
  `/api/dict/process-categories${buildQuery({ enabledOnly })}`
)

export const pageProcessCategories = ({ enabledOnly, keyword, page, pageSize } = {}) => request(
  `/api/dict/process-categories/page${buildQuery({ enabledOnly, keyword, page, pageSize })}`
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

export const getLatestMesProcessSyncRun = () => request('/api/dict/process-categories/sync-runs/latest')

export const getMesProcessSyncRun = (syncRunId) => request(`/api/dict/process-categories/sync-runs/${syncRunId}`)

export const getMesProcessSyncSchedule = () => request('/api/dict/process-categories/sync-schedule')

export const saveMesProcessSyncSchedule = (payload) => request('/api/dict/process-categories/sync-schedule', {
  method: 'PUT',
  data: payload
})

export const listProcessDetails = ({ categoryId, enabledOnly } = {}) => request(
  `/api/dict/process-details${buildQuery({ categoryId, enabledOnly })}`
)

export const pageProcessDetails = ({ categoryId, enabledOnly, keyword, page, pageSize } = {}) => request(
  `/api/dict/process-details/page${buildQuery({ categoryId, enabledOnly, keyword, page, pageSize })}`
)

export const saveProcessDetail = (payload) => {
  const id = payload?.id
  return request(id ? `/api/dict/process-details/${id}` : '/api/dict/process-details', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const deleteProcessDetail = (id) => request(`/api/dict/process-details/${id}`, {
  method: 'DELETE'
})

export const listProcessArrangementTree = () => request('/api/dict/process-arrangement/tree')

export const listUnplacedProcessArrangementOptions = ({ keyword, sourceType } = {}) => request(
  `/api/dict/process-arrangement/unplaced-options${buildQuery({ keyword, sourceType })}`
)

export const createProcessArrangementNode = (payload) => request('/api/dict/process-arrangement/nodes', {
  method: 'POST',
  data: payload
})

export const updateProcessArrangementNode = (id, payload) => request(`/api/dict/process-arrangement/nodes/${id}`, {
  method: 'PUT',
  data: payload
})

export const deleteProcessArrangementNode = (id) => request(`/api/dict/process-arrangement/nodes/${id}`, {
  method: 'DELETE'
})

export const placeProcessArrangementItems = (payload) => request('/api/dict/process-arrangement/items/batch', {
  method: 'POST',
  data: payload
})

export const moveProcessArrangementItem = (id, payload) => request(`/api/dict/process-arrangement/items/${id}/move`, {
  method: 'PUT',
  data: payload
})

export const removeProcessArrangementItem = (id) => request(`/api/dict/process-arrangement/items/${id}`, {
  method: 'DELETE'
})

export const listPricingMarkupFees = (pricingScope, feeCategory) => request(
  `/api/dict/pricing-markup-fees${buildQuery({ pricingScope, feeCategory })}`
)

export const createPricingMarkupFee = (payload) => request('/api/dict/pricing-markup-fees', {
  method: 'POST',
  data: payload
})

export const updatePricingMarkupFee = (id, payload) => request(`/api/dict/pricing-markup-fees/${id}`, {
  method: 'PUT',
  data: payload
})

export const deletePricingMarkupFee = (id) => request(`/api/dict/pricing-markup-fees/${id}`, {
  method: 'DELETE'
})

export const listExtraFeeItems = ({ includeDisabled = false } = {}) => request(
  includeDisabled ? '/api/dict/extra-fee-items/all' : '/api/dict/extra-fee-items'
)

export const pageExtraFeeItems = ({ includeDisabled, keyword, page, pageSize } = {}) => request(
  `/api/dict/extra-fee-items/page${buildQuery({ includeDisabled, keyword, page, pageSize })}`
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
