import json
import os
import secrets
import string
import subprocess
import sys

POOL = os.environ['SPIKE_POOL_ID']
SPIKE = os.environ['SPIKE_WORKDIR']   # a folder OUTSIDE the repository; the generated passwords are written there
AWS = 'aws'

def run(arguments):
    result = subprocess.run([AWS] + arguments, capture_output=True, text=True)
    if result.returncode != 0:
        print('FAILED:', ' '.join(arguments[:4]), '\n', result.stderr.strip())
        sys.exit(1)
    return result.stdout

def password():
    alphabet = string.ascii_letters + string.digits
    body = ''.join(secrets.choice(alphabet) for _ in range(14))
    return body + 'aZ9!'   # satisfies upper, lower, digit, symbol, length 12+

domain = 'pc-identity-spike-' + ''.join(secrets.choice(string.ascii_lowercase + string.digits) for _ in range(6))
run(['cognito-idp', 'create-user-pool-domain', '--user-pool-id', POOL, '--domain', domain])
print('domain created:', domain)

for group in ['ADMIN', 'OWNER_ADMIN', 'VET_ADMIN']:
    run(['cognito-idp', 'create-group', '--user-pool-id', POOL, '--group-name', group,
         '--description', 'Spike: maps to ROLE_' + group])
    print('group created:', group)

users = {
    'spike-admin': ['ADMIN'],
    'spike-owner': ['OWNER_ADMIN'],
    'spike-vet': ['VET_ADMIN'],
    'spike-nogroup': [],
}
secrets_file = {}
for name, groups in users.items():
    pw = password()
    run(['cognito-idp', 'admin-create-user', '--user-pool-id', POOL, '--username', name,
         '--message-action', 'SUPPRESS', '--temporary-password', pw])
    run(['cognito-idp', 'admin-set-user-password', '--user-pool-id', POOL, '--username', name,
         '--password', pw, '--permanent'])
    for group in groups:
        run(['cognito-idp', 'admin-add-user-to-group', '--user-pool-id', POOL, '--username', name,
             '--group-name', group])
    secrets_file[name] = {'password': pw, 'groups': groups}
    print('user created:', name, groups)

with open(SPIKE + '/spike-users.json', 'w') as handle:
    json.dump({'domain': domain, 'users': secrets_file}, handle, indent=2)
print('credentials written to the scratchpad (not committed)')
