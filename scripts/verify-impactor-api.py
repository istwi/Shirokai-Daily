#!/usr/bin/env python3
"""Read-only public ABI check of the user-supplied Impactor JAR; never executes mod classes."""
import hashlib,json,pathlib,subprocess,sys,zipfile
jar=pathlib.Path(sys.argv[1]).resolve()
with zipfile.ZipFile(jar) as z:
    descriptor=json.loads(z.read('fabric.mod.json'))
    assert descriptor['id']=='impactor' and descriptor['version']=='5.3.5+1.21.1',descriptor.get('version')
checks={
 'net.impactdev.impactor.api.economy.EconomyService':[' static net.impactdev.impactor.api.economy.EconomyService instance();','account(net.impactdev.impactor.api.economy.currency.Currency, java.util.UUID);','save(net.impactdev.impactor.api.economy.accounts.Account);'],
 'net.impactdev.impactor.api.economy.accounts.Account':['depositAsync(java.math.BigDecimal);'],
 'net.impactdev.impactor.api.economy.currency.CurrencyProvider':['currency(net.kyori.adventure.key.Key);'],
 'net.impactdev.impactor.api.economy.transactions.EconomyTransaction':['boolean successful();']}
for cls,expected in checks.items():
    text=subprocess.check_output(['javap','-public','-classpath',str(jar),cls],text=True)
    for signature in expected:assert signature in text,(cls,signature)
print('Impactor 5.3.5 public ABI verified. SHA-256:',hashlib.sha256(jar.read_bytes()).hexdigest())
