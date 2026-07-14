import { request } from '../api'

export const getMarketSummary = (rfqId) => request(`/api/market/summary/${rfqId}`)
