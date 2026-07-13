import { request } from '../api'
import { listDepartments } from './departmentApi'
import { listProcesses } from './processApi'

export const listMachines = () => request('/api/aps/pmc/machines')

export const createMachine = (payload) => request('/api/aps/pmc/machines', {
  method: 'POST',
  data: payload
})

export const updateMachine = (machineId, payload) => request(`/api/aps/pmc/machines/${machineId}`, {
  method: 'PUT',
  data: payload
})

export const deleteMachine = (machineId) => request(`/api/aps/pmc/machines/${machineId}`, {
  method: 'DELETE'
})

export const listMachineCapabilities = () => request('/api/aps/pmc/machine-capabilities')

export const createMachineCapability = (payload) => request('/api/aps/pmc/machine-capabilities', {
  method: 'POST',
  data: payload
})

export const deleteMachineCapability = (capabilityId) => request(
  `/api/aps/pmc/machine-capabilities/${capabilityId}`,
  { method: 'DELETE' }
)

export const listMachineSetupData = async () => {
  const [departments, machines, capabilities, processes] = await Promise.all([
    listDepartments(),
    listMachines(),
    listMachineCapabilities(),
    listProcesses()
  ])

  return { departments, machines, capabilities, processes }
}
