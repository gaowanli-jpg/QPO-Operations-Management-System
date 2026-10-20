import { request } from '../api'

export const refreshEngineerQuotes = (rfqId) => request(`/api/tasks/engineer/refresh-quotes/${rfqId}`, {
  method: 'POST'
})

export const getTask = (taskId) => request(`/api/tasks/${taskId}`)
