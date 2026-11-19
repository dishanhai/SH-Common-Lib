"""Dependency-free asset/registration contract checks; not a substitute for game tests."""
import json
from pathlib import Path
r=Path(__file__).resolve().parents[1]
a=r/'common/src/main/resources/assets/sh_common_lib'
load=lambda p: json.loads(p.read_text())
assert load(a/'models/block/fake_fumo.json')['parent']=='sh_common_lib:block/dishanhai_fumo'
model=load(a/'models/block/dishanhai_fumo.json')
assert model['credit']=='Model by dishanhai' and model['elements']
for texture in model['textures'].values():
 if not texture.startswith('#'):
  namespace,path=texture.split(':'); assert (a.parent/namespace/'textures'/f'{path}.png').is_file()
variants=load(a/'blockstates/fake_fumo.json')['variants']
assert set(variants)=={f'facing={d}' for d in ('north','east','south','west')}
assert [variants[f'facing={d}'].get('y',0) for d in ('north','east','south','west')]==[0,90,180,270]
for locale in ('en_us','zh_cn','zh_tw'):
 assert load(a/f'lang/{locale}.json')['block.sh_common_lib.fake_fumo']
for platform,kind in [('forge-1.20.1','forge'),('neoforge-1.21.1','neoforge'),('neoforge-26.1.2','neoforge')]:
 name='Forge' if kind=='forge' else 'NeoForge'
 code=(r/f'{platform}/src/main/java/com/dishanhai/sh_common_lib/{kind}/{name}ShCommonLib.java').read_text()
 assert code.count('event.accept(FAKE_FUMO_ITEM.get())')==1
 assert 'FakeFumoBlock' in code
 folder='loot_tables' if kind=='forge' else 'loot_table'
 loot=load(r/f'{platform}/src/main/resources/data/sh_common_lib/{folder}/blocks/fake_fumo.json')
 assert loot['pools'][0]['entries']==[{'type':'minecraft:item','name':'sh_common_lib:fake_fumo'}]
 assert loot['pools'][0]['rolls']==1
for source in ('common','neoforge-26.1.2'):
 code=(r/f'{source}/src/main/java/com/dishanhai/sh_common_lib/internal/block/FakeFumoBlock.java').read_text()
 assert 'EntityBlock' not in code and 'getTicker' not in code and 'DANCING' not in code
 assert 'level.playSound(null' in code and 'if (!level.isClientSide())' in code
 assert 'player.isShiftKeyDown()' in code and '!player.getMainHandItem().isEmpty()' in code
native=load(r/'neoforge-26.1.2/src/main/resources/assets/sh_common_lib/items/fake_fumo.json')
assert native['model']['model']=='sh_common_lib:item/fake_fumo'
print('Fake Fumo asset/registration contracts passed (three platform trees).')
