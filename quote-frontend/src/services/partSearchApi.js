import { request } from '../api'

const BASE_PATH = '/api/market/part-search'

export const listRfqPartSearchItems = (params = {}) => request(`${BASE_PATH}/rfq-items`, { params })

export const getRfqPartSearchItem = (itemId) => request(`${BASE_PATH}/rfq-items/${itemId}`)

export const listSalesPartSearchItems = (params = {}) => request(`${BASE_PATH}/sales-items`, { params })

export const getSalesPartSearchItem = (itemId) => request(`${BASE_PATH}/sales-items/${itemId}`)
