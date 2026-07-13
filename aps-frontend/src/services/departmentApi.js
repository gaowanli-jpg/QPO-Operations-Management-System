import { request } from '../api'

export const listDepartments = () => request('/api/aps/pmc/departments')

export const createDepartment = (payload) => request('/api/aps/pmc/departments', {
  method: 'POST',
  data: payload
})

export const adjustDepartmentCapacity = (departmentId, deltaHours) => request(
  `/api/aps/pmc/departments/${departmentId}/adjust-capacity`,
  {
    method: 'POST',
    data: { deltaHours }
  }
)

export const resetDepartmentCapacity = (departmentId, monthlyAvailableHours) => request(
  `/api/aps/pmc/departments/${departmentId}/reset-capacity`,
  {
    method: 'POST',
    data: { monthlyAvailableHours }
  }
)

export const deleteDepartment = (departmentId) => request(`/api/aps/pmc/departments/${departmentId}`, {
  method: 'DELETE'
})
