// Session 2, part 2: the delegation grant (Option 1b) end to end against real Cognito and the running spike API.
// The "UI" holds the employee's token. The "agent" is constructed from ONLY a handle and its own machine credentials,
// and records everything it sends and receives, so we can prove what it never saw.
import fs from 'node:fs'
import { employeeAccessToken, HOST } from './pkce.mjs'

const work = process.env.SPIKE_WORKDIR
const API = process.env.SPIKE_API ?? 'http://localhost:9969'
const agentCredentials = JSON.parse(fs.readFileSync(`${work}/spike-agent.json`, 'utf8'))
const decode = (token) => JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'))

/** Everything the agent ever holds, sends or receives is kept here so it can be searched afterwards. */
class StubAgent {
  constructor(handle) {
    this.handle = handle               // the ONLY thing the UI hands over
    this.log = []
    this.machineToken = null
  }

  async machineLogin() {
    const basic = Buffer.from(`${agentCredentials.ClientId}:${agentCredentials.ClientSecret}`).toString('base64')
    const response = await fetch(`${HOST}/oauth2/token`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', Authorization: `Basic ${basic}` }, body: new URLSearchParams({ grant_type: 'client_credentials', scope: 'petclinic-api/agent.read' }) })
    const body = await response.json()
    this.machineToken = body.access_token
    this.log.push({ step: 'machine token', received: body })
  }

  async call(method, path, { withHandle = true, withMachineToken = true, handle = this.handle } = {}) {
    const headers = {}
    if (withMachineToken) headers.Authorization = `Bearer ${this.machineToken}`
    if (withHandle) headers['X-Agent-Grant'] = handle
    const response = await fetch(`${API}${path}`, { method, headers })
    const text = await response.text()
    this.log.push({ step: `${method} ${path}`, sentHeaders: headers, status: response.status, received: text })
    return { status: response.status, text }
  }
}

const results = { steps: [], proofs: {} }
const record = (name, outcome) => results.steps.push({ name, ...outcome })

// ---- The UI's side: employees log in and create grants with their own token.
const ownerToken = await employeeAccessToken('spike-owner')
const vetToken = await employeeAccessToken('spike-vet')
const createGrant = async (token) => {
  const response = await fetch(`${API}/api/agent-grants`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } })
  return { status: response.status, body: await response.json().catch(() => ({})) }
}
const ownerGrant = await createGrant(ownerToken)
record('employee (OWNER_ADMIN) creates a grant', { status: ownerGrant.status, expiresAt: ownerGrant.body.expiresAt, allowedOperations: ownerGrant.body.allowedOperations })
results.proofs.grantResponseHasNoJwt = !JSON.stringify(ownerGrant.body).includes('eyJ')

// ---- The agent's side: starts from a handle and its own credentials only.
const agent = new StubAgent(ownerGrant.body.handle)
await agent.machineLogin()
const machineClaims = decode(agent.machineToken)
record('agent machine token', { client_id: machineClaims.client_id, scope: machineClaims.scope, hasUserClaims: Boolean(machineClaims['cognito:groups'] || machineClaims.username) })

const found = await agent.call('GET', '/api/agent-tools/owner-lookup?lastName=Franklin')
record('agent: owner lookup (grant + machine token)', { status: found.status })

// ---- Proof 1: the employee's token never reached the agent (searched in everything it held, sent or received).
const everythingTheAgentSaw = JSON.stringify({ handle: agent.handle, machineToken: agent.machineToken, log: agent.log })
const ownerSubject = decode(ownerToken).sub
results.proofs.employeeTokenNeverReachedAgent = !everythingTheAgentSaw.includes(ownerToken)
results.proofs.employeeIdentityOnlyAsServerSideSubject = !everythingTheAgentSaw.includes(ownerSubject)

// ---- Proof 2: the agent's credentials alone, and each secret alone, are useless.
const attempts = [
  ['machine token ALONE (no handle): lookup', () => agent.call('GET', '/api/agent-tools/owner-lookup?lastName=Franklin', { withHandle: false })],
  ['handle ALONE (no machine token): lookup', () => agent.call('GET', '/api/agent-tools/owner-lookup?lastName=Franklin', { withMachineToken: false })],
  ['agent writes: POST owners', () => agent.call('POST', '/api/owners-demo')],
  ['agent writes: PUT owners', () => agent.call('PUT', '/api/owners-demo')],
  ['agent writes: DELETE owners', () => agent.call('DELETE', '/api/owners-demo')],
  ['agent reads another endpoint: GET owners', () => agent.call('GET', '/api/owners-demo')],
  ['agent reads vets', () => agent.call('GET', '/api/vets-demo')],
  ['agent calls whoami', () => agent.call('GET', '/api/whoami')],
  ['agent creates its own grant', () => agent.call('POST', '/api/agent-grants')],
  ['agent POSTs to the lookup', () => agent.call('POST', '/api/agent-tools/owner-lookup?lastName=Franklin')],
  ['agent uses a made-up handle', () => agent.call('GET', '/api/agent-tools/owner-lookup?lastName=Franklin', { handle: 'pcg_madeup' })],
]
for (const [name, run] of attempts) {
  const outcome = await run()
  record(name, { status: outcome.status })
}

// ---- Proof 3: an employee cannot present a handle; a VET_ADMIN's grant cannot read owners; ambiguity is reported.
const employeeWithHandle = await fetch(`${API}/api/agent-tools/owner-lookup?lastName=Franklin`, { headers: { Authorization: `Bearer ${ownerToken}`, 'X-Agent-Grant': agent.handle } })
record('employee token + a handle', { status: employeeWithHandle.status })
const vetGrant = await createGrant(vetToken)
const vetAgent = new StubAgent(vetGrant.body.handle)
await vetAgent.machineLogin()
const vetLookup = await vetAgent.call('GET', '/api/agent-tools/owner-lookup?lastName=Franklin')
record("VET_ADMIN's grant: owner lookup", { status: vetLookup.status })
const ambiguous = await agent.call('GET', '/api/agent-tools/owner-lookup?lastName=Davis')
record('ambiguous name "Davis"', { status: ambiguous.status, lookupStatus: JSON.parse(ambiguous.text).status, candidates: JSON.parse(ambiguous.text).owners.length })

// ---- Proof 4: the grant is revocable at once, and it expires by itself.
const revokedGrant = await createGrant(ownerToken)
const revokedAgent = new StubAgent(revokedGrant.body.handle)
await revokedAgent.machineLogin()
const before = await revokedAgent.call('GET', '/api/agent-tools/owner-lookup?lastName=Franklin')
const revoke = await fetch(`${API}/api/agent-grants/${revokedGrant.body.grantId}`, { method: 'DELETE', headers: { Authorization: `Bearer ${ownerToken}` } })
const after = await revokedAgent.call('GET', '/api/agent-tools/owner-lookup?lastName=Franklin')
record('revocation: before, revoke, after', { before: before.status, revoke: revoke.status, after: after.status })

const shortGrant = await createGrant(ownerToken)
const shortAgent = new StubAgent(shortGrant.body.handle)
await shortAgent.machineLogin()
const early = await shortAgent.call('GET', '/api/agent-tools/owner-lookup?lastName=Franklin')
const ttlSeconds = Number(process.env.SPIKE_GRANT_TTL_SECONDS ?? 25)
await new Promise((resolve) => setTimeout(resolve, (ttlSeconds + 3) * 1000))
const late = await shortAgent.call('GET', '/api/agent-tools/owner-lookup?lastName=Franklin')
record(`expiry (grant TTL ${ttlSeconds} s): at once, then after waiting`, { early: early.status, late: late.status })

fs.writeFileSync(`${work}/grant-flow-results.json`, JSON.stringify(results, null, 2))
console.log(JSON.stringify(results, null, 2))
