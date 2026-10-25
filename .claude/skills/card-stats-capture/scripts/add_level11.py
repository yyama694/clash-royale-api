"""Add level-11 values to the level-16 table made by to_level16.py.

How each value moves between levels (found on 2026-10-25 from the L15 cards' "+n" and the official
level-11 values; see SKILL.md):
  * scaled values (hitpoints, damage, ...): floor(base * MULT[level] / 100) with an integer base.
    From the L16 value the base is unique, because the L16 multiplier is above 4.
  * damage per second: floor(damage / hit speed), from the same group's damage.
  * damage to towers: either damage - floor(damage * p / 100) for a fixed percentage p, or a scaled
    value of its own. Both are tried; a value is given only when every rule that fits the L16 value
    (and the screen's L15 "+n", when there is one) agrees on level 11.
  * unit levels ("... level"): 5 lower.
  * everything else does not change with level.
Rows whose level-11 value cannot be pinned down get an empty value11 and a note.

usage: python -X utf8 -I add_level11.py <stats_l16.tsv> <stats.tsv(as read)> <out.tsv>
"""
import csv
import collections
import re
import sys

MULT = {11: 256, 15: 372, 16: 409}
DAMAGE = ['ダメージ', '範囲ダメージ', '遠距離ダメージ']
TOWER = ('タワーへのダメージ', 'タワーへの毎秒ダメージ')
NUM = re.compile(r'^(\d+(?:-\d+)?)( x\d+)?$')
SCALING_WORDS = ('ダメージ', 'ヒットポイント', 'HP', '回復')


def base_from(v16):
    b = v16 * 100 // MULT[16]
    for c in (b - 1, b, b + 1, b + 2):
        if c * MULT[16] // 100 == v16:
            return c
    return None


def scaled11(v16):
    b = base_from(v16)
    return None if b is None else b * MULT[11] // 100


def parts(v):
    m = NUM.match(v)
    return (m.group(1).split('-'), m.group(2) or '') if m else (None, None)


def join(nums, suffix):
    return None if any(n is None for n in nums) else '-'.join(str(n) for n in nums) + suffix


def main(l16_path, read_path, out_path):
    rows = list(csv.DictReader(open(l16_path, encoding='utf-8'), delimiter='\t'))
    # labels that showed a "+n" somewhere below level 16 are known to change with level
    scaling = {r['item'] for r in csv.DictReader(open(read_path, encoding='utf-8'), delimiter='\t')
               if r['plus'] and int(r['level']) < 16}
    groups = collections.defaultdict(dict)
    for r in rows:
        groups[(r['no'], r['form'], r['ability'])][r['item']] = r

    def damage_row(g):
        return next((g[i] for i in DAMAGE if i in g), None)

    # pass 1: plain scaled values and things that do not change
    for r in rows:
        v = r['value16']
        nums, suffix = parts(v)
        item = r['item']
        if 'レベル' in item and v.isdigit():
            r['value11'], r['how11'] = str(int(v) - 5), 'unit level'
        elif nums is None:
            r['value11'], r['how11'] = v, 'does not change'
        elif item == '毎秒ダメージ' or item in TOWER:
            r['value11'], r['how11'] = None, 'later'
        elif item in scaling or (any(w in item for w in SCALING_WORDS) and r['level'] == '16'):
            r['value11'] = join([scaled11(int(n)) for n in nums], suffix)
            r['how11'] = 'scaled' if item in scaling else 'scaled (guessed from the label: no +n seen)'
        else:
            r['value11'], r['how11'] = v, 'does not change (no +n seen)'

    # pass 2: damage per second
    for r in rows:
        if r['item'] != '毎秒ダメージ':
            continue
        g = groups[(r['no'], r['form'], r['ability'])]
        d, sp = damage_row(g), g.get('攻撃速度')
        ok = d and sp and re.fullmatch(r'[\d.]+秒', sp['value16']) and d['value11'] and d['value16'].isdigit() \
            and r['value16'].isdigit() and int(int(d['value16']) / float(sp['value16'][:-1])) == int(r['value16'])
        if ok:
            r['value11'], r['how11'] = str(int(int(d['value11']) / float(sp['value16'][:-1]))), 'damage / hit speed'
        elif r['note'].startswith('derived'):
            # the L15 "+n" already showed that this value does not follow the multipliers
            r['value11'], r['how11'] = None, 'unknown (neither damage / hit speed nor the multipliers fit)'
        else:
            nums, suffix = parts(r['value16'])
            r['value11'] = join([scaled11(int(n)) for n in nums], suffix) if nums else None
            r['how11'] = 'scaled (damage / hit speed did not fit)' if r['value11'] else 'unknown'

    # pass 3: damage to towers
    for r in rows:
        if r['item'] not in TOWER:
            continue
        g = groups[(r['no'], r['form'], r['ability'])]
        if r['item'] == 'タワーへの毎秒ダメージ':
            src = g.get('毎秒ダメージ') or g.get('毒の毎秒ダメージ')
        else:
            src = damage_row(g)
        nums, suffix = parts(r['value16'])
        if nums is None:
            r['value11'], r['how11'] = r['value16'], 'does not change'
            continue
        snums, ssuffix = parts(src['value16']) if src else (None, None)
        s11nums, _ = parts(src['value11']) if src and src['value11'] else (None, None)
        usable_src = snums is not None and s11nums is not None and len(snums) == len(nums) == len(s11nums) \
            and ssuffix == suffix
        values, hows = [], []
        for k, n in enumerate(nums):
            t16 = int(n)
            answers, how = set(), []
            if usable_src:
                s16, s11 = int(snums[k]), int(s11nums[k])
                for p in range(100):
                    if s16 - s16 * p // 100 == t16:
                        answers.add(s11 - s11 * p // 100)
                        how.append(f'{p}%')
            b = None if r['note'].startswith('derived') else base_from(t16)
            if b is not None:
                answers.add(b * MULT[11] // 100)
                how.append('scaled')
            values.append(answers.pop() if len(answers) == 1 else None)
            hows.append(' / '.join(how) if len(answers) == 0 and values[-1] is not None
                        else f'rules disagree ({", ".join(how)})')
        r['value11'] = join(values, suffix)
        r['how11'] = 'tower: ' + '; '.join(sorted(set(hows)))

    with open(out_path, 'w', encoding='utf-8', newline='') as f:
        fields = list(rows[0].keys())
        w = csv.DictWriter(f, fieldnames=fields, delimiter='\t', lineterminator='\n')
        w.writeheader()
        for r in rows:
            r['value11'] = r['value11'] or ''
            w.writerow(r)
    counts = collections.Counter(r['how11'].split(':')[0] if r['value11'] else 'UNKNOWN' for r in rows)
    for r in rows:
        if not r['value11'] or 'guessed' in r['how11'] or 'disagree' in r['how11'] or 'did not fit' in r['how11']:
            print(f"{r['no']} {r['card']} {r['form']} {r['ability']} {r['item']}: L16 {r['value16']} -> L11 {r['value11'] or '?'}  [{r['how11']}]")
    print(dict(counts))


if __name__ == '__main__':
    main(*sys.argv[1:4])
