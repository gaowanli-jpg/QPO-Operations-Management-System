import { beforeEach, describe, expect, test, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { createPinia, setActivePinia } from 'pinia'
import {
  adjustDepartmentCapacity,
  createDepartment,
  deleteDepartment,
  listDepartments,
  resetDepartmentCapacity
} from '../services/departmentApi'
import { listProcesses, syncProcessesFromErp } from '../services/processApi'
import {
  createMachine,
  createMachineCapability,
  deleteMachine,
  deleteMachineCapability,
  listMachineCapabilities,
  listMachines,
  updateMachine
} from '../services/machineApi'
import { useReferenceDataStore } from './referenceDataStore'

vi.mock('../services/departmentApi', () => ({
  adjustDepartmentCapacity: vi.fn(),
  createDepartment: vi.fn(),
  deleteDepartment: vi.fn(),
  listDepartments: vi.fn(),
  resetDepartmentCapacity: vi.fn()
}))

vi.mock('../services/processApi', () => ({
  listProcesses: vi.fn(),
  syncProcessesFromErp: vi.fn()
}))

vi.mock('../services/machineApi', () => ({
  createMachine: vi.fn(),
  createMachineCapability: vi.fn(),
  deleteMachine: vi.fn(),
  deleteMachineCapability: vi.fn(),
  listMachineCapabilities: vi.fn(),
  listMachines: vi.fn(),
  updateMachine: vi.fn()
}))

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
})

describe('referenceDataStore', () => {
  test('loads and caches departments unless forced', async () => {
    listDepartments.mockResolvedValue([{ id: 1, department_name: 'CNC' }])
    const referenceStore = useReferenceDataStore()

    await referenceStore.loadDepartments()
    await referenceStore.loadDepartments()
    await referenceStore.loadDepartments({ force: true })

    expect(referenceStore.departments).toEqual([{ id: 1, department_name: 'CNC' }])
    expect(listDepartments).toHaveBeenCalledTimes(2)
  })

  test('department mutations reload departments', async () => {
    listDepartments.mockResolvedValue([{ id: 1 }])
    createDepartment.mockResolvedValue({ success: true })
    adjustDepartmentCapacity.mockResolvedValue({ success: true })
    resetDepartmentCapacity.mockResolvedValue({ success: true })
    deleteDepartment.mockResolvedValue({ success: true })
    const referenceStore = useReferenceDataStore()

    await referenceStore.createDepartment({ departmentName: 'CNC' })
    await referenceStore.adjustDepartmentCapacity(1, 10)
    await referenceStore.resetDepartmentCapacity(1, 400)
    await referenceStore.deleteDepartment(1)

    expect(createDepartment).toHaveBeenCalledWith({ departmentName: 'CNC' })
    expect(adjustDepartmentCapacity).toHaveBeenCalledWith(1, 10)
    expect(resetDepartmentCapacity).toHaveBeenCalledWith(1, 400)
    expect(deleteDepartment).toHaveBeenCalledWith(1)
    expect(listDepartments).toHaveBeenCalledTimes(4)
  })

  test('loads and syncs processes through the store', async () => {
    listProcesses.mockResolvedValue([{ id: 7, process_name: 'Milling' }])
    syncProcessesFromErp.mockResolvedValue({ importedCount: 3 })
    const referenceStore = useReferenceDataStore()

    await referenceStore.loadProcesses()
    const result = await referenceStore.syncProcessesFromErp('WORK_HOUR')

    expect(syncProcessesFromErp).toHaveBeenCalledWith('WORK_HOUR')
    expect(result).toEqual({ importedCount: 3 })
    expect(listProcesses).toHaveBeenCalledTimes(2)
    expect(referenceStore.processes).toEqual([{ id: 7, process_name: 'Milling' }])
  })

  test('loads machine setup data from cached reference endpoints', async () => {
    listDepartments.mockResolvedValue([{ id: 1 }])
    listMachines.mockResolvedValue([{ id: 2 }])
    listMachineCapabilities.mockResolvedValue([{ id: 3 }])
    listProcesses.mockResolvedValue([{ id: 4 }])
    const referenceStore = useReferenceDataStore()

    const result = await referenceStore.loadMachineSetupData()

    expect(result).toEqual({
      departments: [{ id: 1 }],
      machines: [{ id: 2 }],
      capabilities: [{ id: 3 }],
      processes: [{ id: 4 }]
    })
  })

  test('machine mutations reload machines and capabilities', async () => {
    listMachines.mockResolvedValue([{ id: 2 }])
    listMachineCapabilities.mockResolvedValue([{ id: 3 }])
    createMachine.mockResolvedValue({ success: true })
    updateMachine.mockResolvedValue({ success: true })
    deleteMachine.mockResolvedValue({ success: true })
    createMachineCapability.mockResolvedValue({ success: true })
    deleteMachineCapability.mockResolvedValue({ success: true })
    const referenceStore = useReferenceDataStore()

    await referenceStore.createMachine({ machineCode: 'M-1' })
    await referenceStore.updateMachine(2, { machineName: 'Milling 1' })
    await referenceStore.deleteMachine(2)
    await referenceStore.createMachineCapability({ machineId: 2, processId: 4 })
    await referenceStore.deleteMachineCapability(3)

    expect(createMachine).toHaveBeenCalledWith({ machineCode: 'M-1' })
    expect(updateMachine).toHaveBeenCalledWith(2, { machineName: 'Milling 1' })
    expect(deleteMachine).toHaveBeenCalledWith(2)
    expect(createMachineCapability).toHaveBeenCalledWith({ machineId: 2, processId: 4 })
    expect(deleteMachineCapability).toHaveBeenCalledWith(3)
    expect(listMachines).toHaveBeenCalledTimes(3)
    expect(listMachineCapabilities).toHaveBeenCalledTimes(5)
  })

  test('machine view relies on store mutation reloads instead of forcing full setup reloads', () => {
    const source = readFileSync(
      fileURLToPath(new URL('../views/MachinesView.vue', import.meta.url)),
      'utf8'
    )

    expect(source).not.toMatch(
      /referenceStore\.(createMachine|updateMachine|deleteMachine|createMachineCapability|deleteMachineCapability)[\s\S]{0,800}await loadData\(\)/
    )
  })
})
