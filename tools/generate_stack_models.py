"""Compose the original device models without changing their textures or recipes."""
from pathlib import Path
import copy
import json

root = Path(__file__).resolve().parents[1] / 'src/main/resources'
models = root / 'assets/soviet_hifi/models/block'

def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def shifted(elements, y):
    result = copy.deepcopy(elements)
    for element in result:
        element['from'][1] += y
        element['to'][1] += y
        if 'rotation' in element:
            element['rotation']['origin'][1] += y
    return result

amp = json.loads((models / 'amfiton.json').read_text(encoding='utf-8'))
for top in (True, False):
    for mode in ('empty', 'loaded', 'playing'):
        deck = json.loads((models / f'mayak_{mode}.json').read_text(encoding='utf-8'))
        model = copy.deepcopy(deck)
        model['textures'].update(amp['textures'])
        model['elements'] = shifted(deck['elements'], 0 if top else 5) + shifted(amp['elements'], 8 if top else 0)
        save(models / f'stack_{"amp" if top else "deck"}_top_{mode}.json', model)

variants = {}
for facing, rotation in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
    for top in (True, False):
        for count in range(13):
            for playing in (False, True):
                mode = 'empty' if count == 0 else 'playing' if playing else 'loaded'
                key = f'facing={facing},amp_on_top={str(top).lower()},tapes={count},powered={str(playing).lower()}'
                variants[key] = {'model': f'soviet_hifi:block/stack_{"amp" if top else "deck"}_top_{mode}', 'y': rotation}
save(root / 'assets/soviet_hifi/blockstates/stereo_stack.json', {'variants': variants})
save(root / 'data/soviet_hifi/loot_table/blocks/stereo_stack.json', {
    'type': 'minecraft:block',
    'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'soviet_hifi:' + name}],
               'conditions': [{'condition': 'minecraft:survives_explosion'}]} for name in ('mayak', 'amfiton')]
})
for language, title in [('ru_ru', 'Маяк / Амфитон'), ('en_us', 'Mayak / Amfiton Stack')]:
    path = root / f'assets/soviet_hifi/lang/{language}.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    data['block.soviet_hifi.stereo_stack'] = title
    save(path, data)
