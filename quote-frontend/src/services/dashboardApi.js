import { request } from '../api'

export const getDashboardOverview = () => request('/api/dashboard/overview')
