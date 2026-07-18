import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const readDoc = (...segments) => readFileSync(resolve(currentDir, '..', '..', 'docs', ...segments), 'utf8')

const routerSource = readSource('router', 'index.js')
const appVue = readSource('App.vue')
const mappingDoc = () => readDoc('QPO权限用户体系', '02-权限点清单与现有角色映射.md')

const parseRoles = (raw) => raw
  .split(',')
  .map(role => role.replaceAll("'", '').trim())
  .filter(Boolean)

const routeRoleEntries = () => {
  const routeRegex = /\{ path: '([^']+)', component: (\w+), meta: \{ roles: \[([^\]]+)] } }/g
  return [...routerSource.matchAll(routeRegex)].map(match => ({
    path: match[1],
    component: match[2],
    roles: parseRoles(match[3])
  }))
}

const menuRoleEntries = () => {
  const menuRegex = /<el-menu-item v-if="canSee\(\[([^\]]+)]\)" index="([^"]+)">/g
  return [...appVue.matchAll(menuRegex)].map(match => ({
    path: match[2],
    roles: parseRoles(match[1])
  }))
}

describe('QPO permission stage 0 guardrails', () => {
  test('freezes current protected route role configuration', () => {
    const routes = Object.fromEntries(routeRoleEntries().map(route => [route.path, route.roles]))

    expect(routes).toEqual({
      '/market': ['MARKET', 'ADMIN'],
      '/quote-process': ['MARKET', 'ADMIN'],
      '/release': ['MARKET', 'ADMIN'],
      '/engineering': ['ENGINEER', 'ADMIN'],
      '/material-process': ['ENGINEER', 'ADMIN'],
      '/orders': ['MARKET', 'ADMIN'],
      '/history-orders': ['MARKET', 'ADMIN'],
      '/users': ['ADMIN'],
      '/audit-logs': ['ADMIN'],
      '/quote-template': ['ADMIN'],
      '/pricing': ['ENGINEER', 'ADMIN']
    })

    for (const [path, roles] of Object.entries(routes)) {
      expect(roles, path).toContain('ADMIN')
      expect(roles.every(role => ['ADMIN', 'MARKET', 'ENGINEER'].includes(role)), path).toBe(true)
    }
  })

  test('freezes current sidebar menu visibility snapshot', () => {
    const menuEntries = Object.fromEntries(menuRoleEntries().map(entry => [entry.path, entry.roles]))

    expect(menuEntries).toEqual({
      '/market': ['MARKET', 'ADMIN'],
      '/quote-process': ['MARKET', 'ADMIN'],
      '/release': ['MARKET', 'ADMIN'],
      '/quote-template': ['ADMIN'],
      '/engineering': ['ENGINEER', 'ADMIN'],
      '/material-process': ['ENGINEER', 'ADMIN'],
      '/orders': ['MARKET', 'ADMIN'],
      '/history-orders': ['MARKET', 'ADMIN'],
      '/users': ['ADMIN'],
      '/audit-logs': ['ADMIN']
    })
  })

  test('maps every protected frontend route to a target permission code', () => {
    const doc = mappingDoc()

    for (const route of routeRoleEntries()) {
      expect(doc, route.path).toContain(`| \`${route.path}\` |`)
    }

    expect(doc).toContain('前端路由权限迁移表')
    expect(doc).toContain('`market:rfq:view`')
    expect(doc).toContain('`engineer:pricing:view`')
    expect(doc).toContain('`system:audit:view`')
  })
})
