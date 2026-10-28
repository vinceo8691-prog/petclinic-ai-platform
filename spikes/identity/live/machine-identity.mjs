// Session 2, part 1: what the agent's machine identity is, and what it is not.
// Run with SPIKE_WORKDIR pointing at the folder that holds spike-agent.json and spike-users.json.
import fs from 'node:fs'

const work = process.env.SPIKE_WORKDIR
const agent = JSON.parse(fs.readFileSync(`${work}/spike-agent.json`, 'utf8'))
const users = JSON.parse(fs.readFileSync(`${work}/spike-users.json`, 'utf8'))
const SPA_CLIENT = process.env.SPIKE_SPA_CLIENT_ID
const HOST = `https://${users.domain}.auth.us-east-1.amazoncognito.com`
const basic = Buffer.from(`${agent.ClientId}:${agent.ClientSecret}`).toString('base64')
const decode = (token) => JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'))

async function token(parameters, authorization) {
  const headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
  if (authorization) headers.Authorization = authorization
  const response = await fetch(`${HOST}/oauth2/token`, { method: 'POST', headers, body: new URLSearchParams(parameters) })
  return { status: response.status, body: await response.json() }
}

const results = {}

// 1. The legitimate machine token.
const machine = await token({ grant_type: 'client_credentials', scope: 'petclinic-api/agent.read' }, `Basic ${basic}`)
const claims = decode(machine.body.access_token)
results.machineToken = {
  status: machine.status,
  claims: { token_use: claims.token_use, client_id: claims.client_id, sub: claims.sub, scope: claims.scope,
    username: claims.username ?? '(absent)', groups: claims['cognito:groups'] ?? '(absent)', aud: claims.aud ?? '(absent)',
    lifetimeSeconds: claims.exp - claims.iat, issuer: claims.iss },
  expiresInReported: machine.body.expires_in,
}

// 2. Things the machine identity must NOT be able to do.
results.cannot = {}
const withoutSecret = await token({ grant_type: 'client_credentials', client_id: agent.ClientId, scope: 'petclinic-api/agent.read' })
results.cannot.clientCredentialsWithoutSecret = { status: withoutSecret.status, error: withoutSecret.body.error }
const wrongSecret = await token({ grant_type: 'client_credentials', scope: 'petclinic-api/agent.read' }, `Basic ${Buffer.from(`${agent.ClientId}:wrong`).toString('base64')}`)
results.cannot.clientCredentialsWithWrongSecret = { status: wrongSecret.status, error: wrongSecret.body.error }
const widerScope = await token({ grant_type: 'client_credentials', scope: 'openid' }, `Basic ${basic}`)
results.cannot.askForAScopeItWasNotGranted = { status: widerScope.status, error: widerScope.body.error }
const asUser = await token({ grant_type: 'password', username: 'spike-owner', password: 'x', scope: 'petclinic-api/agent.read' }, `Basic ${basic}`)
results.cannot.passwordGrant = { status: asUser.status, error: asUser.body.error }
const authCode = await token({ grant_type: 'authorization_code', code: 'abc', redirect_uri: 'http://localhost:5199/callback' }, `Basic ${basic}`)
results.cannot.authorizationCodeGrant = { status: authCode.status, error: authCode.body.error }
// The public SPA client must not be able to act as a machine either.
const spaAsMachine = await token({ grant_type: 'client_credentials', client_id: SPA_CLIENT, scope: 'petclinic-api/agent.read' })
results.cannot.spaClientAsMachine = { status: spaAsMachine.status, error: spaAsMachine.body.error }

fs.writeFileSync(`${work}/agent-token.json`, JSON.stringify({ token: machine.body.access_token }))
console.log(JSON.stringify(results, null, 2))
