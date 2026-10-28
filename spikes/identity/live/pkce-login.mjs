import { chromium } from 'playwright-core'
import crypto from 'node:crypto'
import fs from 'node:fs'

const base = process.env.SPIKE_WORKDIR // the folder outside the repository that holds spike-users.json
const config = JSON.parse(fs.readFileSync(`${base}/spike-users.json`, 'utf8'))
const POOL = process.env.SPIKE_POOL_ID
const CLIENT = process.env.SPIKE_SPA_CLIENT_ID
const REGION = 'us-east-1'
const REDIRECT = 'http://localhost:5199/callback'
const HOST = `https://${config.domain}.auth.${REGION}.amazoncognito.com`
const API = 'http://localhost:9969'

const b64url = (buffer) => buffer.toString('base64url')
const makePkce = () => {
  const verifier = b64url(crypto.randomBytes(48))
  return { verifier, challenge: b64url(crypto.createHash('sha256').update(verifier).digest()) }
}

const browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe' })

/** Drives the hosted login page and returns the authorization code Cognito sent to the redirect URI. */
async function obtainCode(username, password, { challenge, withPkce = true } = {}) {
  const context = await browser.newContext()
  const page = await context.newPage()
  let redirectUrl = null
  await page.route('http://localhost:5199/**', (route) => {
    redirectUrl = route.request().url()
    return route.fulfill({ status: 200, contentType: 'text/html', body: 'callback' })
  })
  const params = new URLSearchParams({
    response_type: 'code', client_id: CLIENT, redirect_uri: REDIRECT, scope: 'openid profile', state: 'xyz',
  })
  if (withPkce) { params.set('code_challenge', challenge); params.set('code_challenge_method', 'S256') }
  await page.goto(`${HOST}/oauth2/authorize?${params}`)
  await page.locator('input[name="username"]:visible').fill(username)
  await page.locator('input[name="password"]:visible').fill(password)
  await page.locator('input[name="signInSubmitButton"]:visible').click()
  await page.waitForTimeout(2500)
  const url = redirectUrl ? new URL(redirectUrl) : null
  const text = url ? '' : (await page.locator('body').innerText()).slice(0, 200)
  await context.close()
  return { code: url?.searchParams.get('code') ?? null, state: url?.searchParams.get('state') ?? null, pageText: text }
}

async function exchange(code, verifier, redirect = REDIRECT) {
  const body = new URLSearchParams({ grant_type: 'authorization_code', client_id: CLIENT, code, redirect_uri: redirect })
  if (verifier) body.set('code_verifier', verifier)
  const response = await fetch(`${HOST}/oauth2/token`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body })
  return { status: response.status, body: await response.json() }
}

const decode = (token) => JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'))
const results = { tokens: {}, pkce: {}, claims: {}, api: {} }

// 1. A normal authorization code + PKCE login for every user.
for (const [username, info] of Object.entries(config.users)) {
  const pkce = makePkce()
  const { code, state, pageText } = await obtainCode(username, info.password, { challenge: pkce.challenge })
  if (!code) { results.tokens[username] = { error: 'no code', pageText }; continue }
  const result = await exchange(code, pkce.verifier)
  results.tokens[username] = { exchangeStatus: result.status, stateEchoed: state === 'xyz', hasRefresh: Boolean(result.body.refresh_token), token: result.body.access_token, idToken: result.body.id_token }
  const claims = decode(result.body.access_token)
  results.claims[username] = {
    token_use: claims.token_use, client_id: claims.client_id, aud: claims.aud ?? '(absent)', iss: claims.iss,
    scope: claims.scope, groups: claims['cognito:groups'] ?? '(absent)', username: claims.username,
    lifetimeSeconds: claims.exp - claims.iat, sub: claims.sub, keys: Object.keys(claims).sort().join(','),
  }
}

// 2. PKCE enforcement attempts, using spike-owner.
const owner = config.users['spike-owner']
{
  const pkce = makePkce()
  const first = await obtainCode('spike-owner', owner.password, { challenge: pkce.challenge })
  const wrongVerifier = await exchange(first.code, makePkce().verifier)
  results.pkce.wrongVerifier = { status: wrongVerifier.status, error: wrongVerifier.body.error }
}
{
  const pkce = makePkce()
  const second = await obtainCode('spike-owner', owner.password, { challenge: pkce.challenge })
  const noVerifier = await exchange(second.code, null)
  results.pkce.missingVerifierWhenChallengeWasSent = { status: noVerifier.status, error: noVerifier.body.error }
}
{
  const pkce = makePkce()
  const third = await obtainCode('spike-owner', owner.password, { challenge: pkce.challenge })
  const first = await exchange(third.code, pkce.verifier)
  const replay = await exchange(third.code, pkce.verifier)
  results.pkce.codeReuse = { firstStatus: first.status, replayStatus: replay.status, replayError: replay.body.error }
}
{
  const pkce = makePkce()
  const fourth = await obtainCode('spike-owner', owner.password, { challenge: pkce.challenge })
  const wrongRedirect = await exchange(fourth.code, pkce.verifier, 'http://localhost:5199/other')
  results.pkce.wrongRedirectUri = { status: wrongRedirect.status, error: wrongRedirect.body.error }
}
{
  // The question that matters most: does Cognito REQUIRE PKCE for this public client?
  const noPkce = await obtainCode('spike-owner', owner.password, { withPkce: false })
  if (noPkce.code) {
    const result = await exchange(noPkce.code, null)
    results.pkce.noPkceAtAll = { codeIssued: true, exchangeStatus: result.status, accessTokenIssued: Boolean(result.body.access_token), error: result.body.error }
  } else {
    results.pkce.noPkceAtAll = { codeIssued: false, pageText: noPkce.pageText }
  }
}

// 3. What each token can do at the API (the matrix).
async function call(token, method, path) {
  const response = await fetch(`${API}${path}`, { method, headers: token ? { Authorization: `Bearer ${token}` } : {} })
  return response.status
}
for (const [username, info] of Object.entries(results.tokens)) {
  if (!info.token) continue
  results.api[username] = {
    whoami: await call(info.token, 'GET', '/api/whoami'),
    ownersGet: await call(info.token, 'GET', '/api/owners-demo'),
    ownersPost: await call(info.token, 'POST', '/api/owners-demo'),
    vetsGet: await call(info.token, 'GET', '/api/vets-demo'),
  }
  // The ID token must not be accepted as an access token.
  results.api[username].idTokenAtApi = await call(info.idToken, 'GET', '/api/whoami')
}
results.api.noToken = { whoami: await call(null, 'GET', '/api/whoami') }

await browser.close()
// Persist tokens only in the scratchpad; print a version without token values.
fs.writeFileSync(`${base}/live-results.json`, JSON.stringify(results, null, 2))
const printable = JSON.parse(JSON.stringify(results))
for (const info of Object.values(printable.tokens)) { delete info.token; delete info.idToken }
console.log(JSON.stringify(printable, null, 2))
