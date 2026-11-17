#!/usr/bin/env python3
"""Convert the reviewed user table into the alpha.12 runtime reward profile."""
import json
from pathlib import Path
root = Path(__file__).resolve().parents[1]
profile = root / 'reward-profiles/tabelas-usuario'
plan = json.loads((profile / 'recompensas-planejadas.json').read_text())
ids = {'basica':'caixa_inicial_rara','epica':'caixa_epica','paradoxo':'caixa_paradoxo','ultra_beast':'caixa_ultra_beasts','mitica':'caixa_mitica','lendaria':'caixa_lendaria','shiny':'shiny'}
for module in ('daily','playtime','hunts'):
    for row in plan[module]:
        for r in row['fixed'] + [r for bonus in row['bonus'] for r in bonus['rewards']]:
            if r['kind'] == 'CRATE_KEY': r['id'] = ids[r['name']]
plan['status'] = 'REVIEWED_SOURCE_TABLE_NOT_RUNTIME_CONFIG'
plan['notes'] = ['5000h: VIP removido; alternativa de peso 10 permanece sem bônus.','Básica = caixa_inicial_rara; Shiny = shiny.','Bônus: chance de cada opção reduzida para um quarto da original; fixas preservadas.','Runtime: Daily alpha.12; instalar somente os arquivos na pasta config/shirokai-daily.']
(profile/'recompensas-planejadas.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2)+'\n')
out = profile/'config/shirokai-daily'
out.mkdir(parents=True,exist_ok=True)
rewards={}
def spec(r,weight):
    kind=r['kind']; value=r.get('id',r.get('currency','')); chance=0.25 if weight>0 else 1.0
    if kind=='LUCKPERMS_GROUP':
        kind='COMMAND';value=f"lp user {{uuid}} parent addtemp {r['group']} {r['durationDays']}d accumulate"
    elif kind=='POKEMON': kind='RANDOM_LEGENDARY_SHINY_6IV';value='legendary'
    return dict(type=kind,value=value,amount=r['amount'],chance=chance,weight=weight,permission='',condition='')
def refs(prefix,row):
    result=[]
    for i,r in enumerate(row['fixed']):
        name=f'{prefix}_fixed_{i+1}';rewards[name]=spec(r,0);result.append(name)
    for bonus in row['bonus']:
        assert len(bonus['rewards'])<=1
        name=f"{prefix}_bonus_{bonus['weight']}"
        if bonus['rewards']: rewards[name]=spec(bonus['rewards'][0],bonus['weight'])
        else: rewards[name]=dict(type='ITEM',value='minecraft:paper',amount=1,chance=0.0,weight=bonus['weight'],permission='',condition='')
        result.append(name)
    return result
daily=dict(days=30,streakPolicy='RESET',graceDays=1,completion='RESTART',rewards={str(r['day']):refs(f"daily_{r['day']}",r) for r in plan['daily']},vipRewards=[])
playtime=dict(afk=True,afkSeconds=300,goals=[dict(id=f"hours_{r['hours']}",period='PERMANENT',seconds=r['hours']*3600,rewards=refs(f"playtime_{r['hours']}",r)) for r in plan['playtime']])
hunts=dict(dailyCount=0,weeklyCount=0,random=True,retainCompleted=False,excludeLegendary=True,whitelist=[],blacklist=[],hourlyCount=3,rotationSeconds=3600,randomSpecies=True,pool=[dict(id=f"capture_{r['difficulty'].lower()}",action='CAPTURE',species='',type='',difficulty=r['difficulty'],amount=i+1,weight=[8,5,3,2,1][i],rewards=refs(f"hunt_{r['difficulty'].lower()}",r)) for i,r in enumerate(plan['hunts'])])
for name,data in [('daily',daily),('playtime',playtime),('hunts',hunts),('rewards',rewards)]:
    (out/f'{name}.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
print(f'Generated {len(rewards)} rewards; 30 daily days, 28 milestones, 5 hunt tiers')
