// A reusable authorization code + PKCE login against the spike pool's hosted login page (Session 1's flow, as a module).
import { chromium } from 'playwright-core'
import crypto from 'node:crypto'
import fs from 'node:fs'

const work = process.env.SPIKE_WORKDIR
const config = JSON.parse(fs.readFileSync(`${work}/spike-users.json`, 'utf8'))
const CLIENT = process.env.SPIKE_SPA_CLIENT_ID
const REDIRECT = 'http://localhost:5199/callback'
export const HOST = `https://${config.domain}.auth.us-east-1.amazoncognito.com`

const b64url = (buffer) => buffer.toString('base64url')

/** Logs a spike user in through the hosted page and returns their access token. */
export async function employeeAccessToken(username) {
  const verifier = b64url(crypto.randomBytes(48))
  const challenge = b64url(crypto.createHash('sha256').update(verifier).digest())
  const browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe' })
  try {
    const page = await (await browser.newContext()).newPage()
    let redirectUrl = null
    await page.route('http://localhost:5199/**', (route) => { redirectUrl = route.request().url(); return route.fulfill({ status: 200, body: 'callback' }) })
    const params = new URLSearchParams({ response_type: 'code', client_id: CLIENT, redirect_uri: REDIRECT, scope: 'openid profile', state: 'xyz', code_challenge: challenge, code_challenge_method: 'S256' })
    await page.goto(`${HOST}/oauth2/authorize?${params}`)
    await page.locator('input[name="username"]:visible').fill(username)
    await page.locator('input[name="password"]:visible').fill(config.users[username].password)
    await page.locator('input[name="signInSubmitButton"]:visible').click()
    await page.waitForTimeout(2500)
    const code = new URL(redirectUrl).searchParams.get('code')
    const body = new URLSearchParams({ grant_type: 'authorization_code', client_id: CLIENT, code, redirect_uri: REDIRECT, code_verifier: verifier })
    const response = await fetch(`${HOST}/oauth2/token`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body })
    return (await response.json()).access_token
  } finally {
    await browser.close()
  }
}
