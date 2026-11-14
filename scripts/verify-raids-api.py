#!/usr/bin/env python3
"""Read-only ABI inspection of the public Raid predicates. Does not execute any mod class."""
import hashlib,json,pathlib,subprocess,sys,zipfile
jar=pathlib.Path(sys.argv[1]).resolve()
with zipfile.ZipFile(jar) as z:
    descriptor=json.loads(z.read('fabric.mod.json'))
    assert descriptor['id']=='shirokai_raids'
actor='com.cobblemon.mod.common.api.battles.model.actor.BattleActor'
for owner,method in [('com.shirokai.raids.TraditionalRaidBattles','isBossActor'),('com.shirokai.raids.ShirokaiRaids','isCaptureActor')]:
    text=subprocess.check_output(['javap','-public','-classpath',str(jar),owner],text=True)
    assert f'public static boolean {method}({actor});' in text,(owner,method)
print('Public Shirokai Raids API verified:',descriptor['version'])
print('SHA-256:',hashlib.sha256(jar.read_bytes()).hexdigest())
