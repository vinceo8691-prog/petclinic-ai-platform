"""Creates the agent's machine identity in the spike pool: a resource server with one read scope, and a confidential
app client that can only use the client-credentials grant. Writes the client secret to SPIKE_WORKDIR (never committed).
"""
import json
import os
import subprocess
import sys

POOL = os.environ['SPIKE_POOL_ID']
workdir = os.environ['SPIKE_WORKDIR']

def run(arguments):
    result = subprocess.run(['aws'] + arguments, capture_output=True, text=True)
    if result.returncode != 0:
        print('FAILED:', ' '.join(arguments[:3]), '\n', result.stderr.strip())
        sys.exit(1)
    return result.stdout

# One resource server, one scope. The scope names the agent's whole machine-level permission: it does not name any
# PetClinic data. The API grants no authority to a machine token that has no delegation grant.
run(['cognito-idp', 'create-resource-server', '--user-pool-id', POOL, '--identifier', 'petclinic-api',
     '--name', 'PetClinic API spike', '--scopes', 'ScopeName=agent.read,ScopeDescription=Machine identity of the agent'])
print('resource server created: petclinic-api (scope agent.read)')

output = run([
    'cognito-idp', 'create-user-pool-client', '--user-pool-id', POOL, '--client-name', 'petclinic-agent',
    '--generate-secret',
    '--allowed-o-auth-flows', 'client_credentials',
    '--allowed-o-auth-scopes', 'petclinic-api/agent.read',
    '--allowed-o-auth-flows-user-pool-client',
    '--supported-identity-providers', 'COGNITO',
    '--access-token-validity', '5', '--token-validity-units', 'AccessToken=minutes',
    '--query', 'UserPoolClient.{ClientId:ClientId,ClientSecret:ClientSecret}', '--output', 'json'])
client = json.loads(output)
with open(os.path.join(workdir, 'spike-agent.json'), 'w') as handle:
    json.dump(client, handle, indent=2)
print('agent client created:', client['ClientId'], '(secret written to the work folder, not to the repository)')
