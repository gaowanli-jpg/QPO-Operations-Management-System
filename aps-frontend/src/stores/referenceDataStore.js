import { defineStore } from 'pinia'
import {
  adjustDepartmentCapacity as adjustDepartmentCapacityRequest,
  createDepartment as createDepartmentRequest,
  deleteDepartment as deleteDepartmentRequest,
  listDepartments,
  resetDepartmentCapacity as resetDepartmentCapacityRequest
} from '../services/departmentApi'
import { listProcesses, syncProcessesFromErp as syncProcessesFromErpRequest } from '../services/processApi'
import {
  createMachine as createMachineRequest,
  createMachineCapability as createMachineCapabilityRequest,
  deleteMachine as deleteMachineRequest,
  deleteMachineCapability as deleteMachineCapabilityRequest,
  listMachineCapabilities,
  listMachines,
  updateMachine as updateMachineRequest
} from '../services/machineApi'

export const useReferenceDataStore = defineStore('referenceData', {
  state: () => ({
    departments: [],
    departmentsLoaded: false,
    processes: [],
    processesLoaded: false,
    machines: [],
    machinesLoaded: false,
    machineCapabilities: [],
    machineCapabilitiesLoaded: false
  }),
  actions: {
    async loadDepartments(options = {}) {
      if (this.departmentsLoaded && !options.force) return this.departments
      this.departments = await listDepartments()
      this.departmentsLoaded = true
      return this.departments
    },

    async reloadDepartments() {
      return this.loadDepartments({ force: true })
    },

    async createDepartment(payload) {
      const result = await createDepartmentRequest(payload)
      await this.reloadDepartments()
      return result
    },

    async adjustDepartmentCapacity(id, deltaHours) {
      const result = await adjustDepartmentCapacityRequest(id, deltaHours)
      await this.reloadDepartments()
      return result
    },

    async resetDepartmentCapacity(id, monthlyAvailableHours) {
      const result = await resetDepartmentCapacityRequest(id, monthlyAvailableHours)
      await this.reloadDepartments()
      return result
    },

    async deleteDepartment(id) {
      const result = await deleteDepartmentRequest(id)
      await this.reloadDepartments()
      return result
    },

    async loadProcesses(options = {}) {
      if (this.processesLoaded && !options.force) return this.processes
      this.processes = await listProcesses()
      this.processesLoaded = true
      return this.processes
    },

    async reloadProcesses() {
      return this.loadProcesses({ force: true })
    },

    async syncProcessesFromErp(processType = '') {
      const result = await syncProcessesFromErpRequest(processType)
      await this.reloadProcesses()
      return result
    },

    async loadMachines(options = {}) {
      if (this.machinesLoaded && !options.force) return this.machines
      this.machines = await listMachines()
      this.machinesLoaded = true
      return this.machines
    },

    async reloadMachines() {
      return this.loadMachines({ force: true })
    },

    async loadMachineCapabilities(options = {}) {
      if (this.machineCapabilitiesLoaded && !options.force) return this.machineCapabilities
      this.machineCapabilities = await listMachineCapabilities()
      this.machineCapabilitiesLoaded = true
      return this.machineCapabilities
    },

    async reloadMachineCapabilities() {
      return this.loadMachineCapabilities({ force: true })
    },

    async loadMachineSetupData(options = {}) {
      const [departments, machines, capabilities, processes] = await Promise.all([
        this.loadDepartments(options),
        this.loadMachines(options),
        this.loadMachineCapabilities(options),
        this.loadProcesses(options)
      ])
      return { departments, machines, capabilities, processes }
    },

    async reloadMachineSetupData() {
      return this.loadMachineSetupData({ force: true })
    },

    async createMachine(payload) {
      const result = await createMachineRequest(payload)
      await Promise.all([this.reloadMachines(), this.reloadMachineCapabilities()])
      return result
    },

    async updateMachine(id, payload) {
      const result = await updateMachineRequest(id, payload)
      await Promise.all([this.reloadMachines(), this.reloadMachineCapabilities()])
      return result
    },

    async deleteMachine(id) {
      const result = await deleteMachineRequest(id)
      await Promise.all([this.reloadMachines(), this.reloadMachineCapabilities()])
      return result
    },

    async createMachineCapability(payload) {
      const result = await createMachineCapabilityRequest(payload)
      await this.reloadMachineCapabilities()
      return result
    },

    async deleteMachineCapability(id) {
      const result = await deleteMachineCapabilityRequest(id)
      await this.reloadMachineCapabilities()
      return result
    }
  }
})
