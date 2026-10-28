"""Deletes the disposable spike resources. NOT run automatically: run it only when the spike is over and the owner agrees.

Deletes: the domain, then the user pool (which removes its groups, users and app clients). Nothing else is touched.
The pool id and domain are read from the spike's own files, so another user pool in the account cannot be hit.
"""
import json
import os
import subprocess
import sys

POOL = os.environ['SPIKE_POOL_ID']           # the spike pool created for docs/spikes/identity-spike.md
workdir = os.environ['SPIKE_WORKDIR']  # holds spike-users.json with the generated domain name
domain = json.load(open(os.path.join(workdir, 'spike-users.json')))['domain']

def run(arguments):
    result = subprocess.run(['aws'] + arguments, capture_output=True, text=True)
    print(' '.join(arguments[:3]), '->', 'ok' if result.returncode == 0 else result.stderr.strip())
    return result.returncode

# Safety check: only a pool tagged as the spike's may be deleted.
described = subprocess.run(['aws', 'cognito-idp', 'describe-user-pool', '--user-pool-id', POOL,
                            '--query', 'UserPool.UserPoolTags', '--output', 'json'], capture_output=True, text=True)
tags = json.loads(described.stdout or '{}')
if tags.get('purpose') != 'identity-spike':
    sys.exit('Refusing: the pool is not tagged purpose=identity-spike')

run(['cognito-idp', 'delete-user-pool-domain', '--user-pool-id', POOL, '--domain', domain])
run(['cognito-idp', 'delete-user-pool', '--user-pool-id', POOL])
