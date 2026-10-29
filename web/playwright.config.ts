import { defineConfig } from '@playwright/test'
import { existsSync } from 'node:fs'

const edge = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const executablePath = process.env.PLAYWRIGHT_BROWSER_EXECUTABLE || (existsSync(edge) ? edge : undefined)
export default defineConfig({
  testDir: './tests/e2e',
  workers: 1,
  fullyParallel: false,
  timeout: 45000,
  reporter: [['list'], ['json', { outputFile: 'test-results/results.json' }]],
  use: { baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:5173', headless: true, launchOptions: { executablePath }, trace: 'off' },
})
