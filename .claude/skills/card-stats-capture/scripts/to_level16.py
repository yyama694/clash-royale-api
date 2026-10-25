"""Turn the stats read from the game screens into level-16 values, and check the reading.

Card stats follow  value(level) = floor(base * MULT[level] / 100)  with an integer base (confirmed on
2026-10-25 against the official level-11 values in balance/changes.tsv and the "+n" next-level
increments on the screen). So a value read at level L together with its "+n" increment pins down the
base, which gives the level-16 value and also catches misread digits (no base fits both numbers).

Derived values do not follow it: damage per second is floor(damage / hit speed), and damage to towers
is reduced from the damage. At level 15 the screen's "+n" already gives the level-16 value; below
that, damage per second is recomputed from the card's level-16 damage and hit speed, and anything
else is left for a person to look at.

usage: python -I to_level16.py <stats.tsv> <out.tsv>
  stats.tsv columns: no card form ability level item value plus
"""
import csv
import re
import sys

MULT = {1: 100, 2: 110, 3: 121, 4: 133, 5: 146, 6: 160, 7: 176, 8: 193, 9: 212, 10: 233,
        11: 256, 12: 281, 13: 309, 14: 339, 15: 372, 16: 409}

# "171 x2", "63-595", "-50%", "1.8秒", "空中/地上" ...
NUM_PARTS = re.compile(r'^(\d+(?:-\d+)?)( x\d+)?$')


def bases(value, level, nxt=None):
    lo = value * 100 // MULT[level] - 2
    return [b for b in range(max(lo, 0), lo + 6)
            if b * MULT[level] // 100 == value
            and (nxt is None or b * MULT[level + 1] // 100 == nxt)]


def to16(value, level, plus):
    """Returns (level-16 value, note). plus may be None for a value that does not scale."""
    if plus is None:
        return value, 'no increment: does not scale'
    cands = bases(value, level, value + plus)
    if not cands:
        return None, f'NO BASE fits {value} at L{level} and {value + plus} at L{level + 1}: misread?'
    vals = sorted({b * MULT[16] // 100 for b in cands})
    if len(vals) > 1:
        return None, f'ambiguous {vals}'
    return vals[0], f'base {cands[0]}' if len(cands) == 1 else f'bases {cands}'


DAMAGE_ITEMS = ['ダメージ', '範囲ダメージ', '遠距離ダメージ']


def fix_derived(rows):
    """Second pass for values that fit no base: see the module docstring."""
    by_key = {}
    for r in rows:
        by_key.setdefault((r['no'], r['form'], r['ability']), {})[r['item']] = r
    for r in rows:
        if 'NO BASE' not in r['note']:
            continue
        level = int(r['level'])
        m, pm = NUM_PARTS.match(r['value']), NUM_PARTS.match(r['plus'])
        if level == 15:
            nums, pnums = m.group(1).split('-'), pm.group(1).split('-')
            r['value16'] = '-'.join(str(int(n) + int(p)) for n, p in zip(nums, pnums)) + (m.group(2) or '')
            r['note'] = 'derived value: L15 + increment'
            continue
        group = by_key[(r['no'], r['form'], r['ability'])]
        dmg = next((group[i] for i in DAMAGE_ITEMS if i in group and group[i]['value16'].isdigit()), None)
        speed = group.get('攻撃速度')
        if r['item'] == '毎秒ダメージ' and dmg and speed and re.fullmatch(r'[\d.]+秒', speed['value']):
            hs = float(speed['value'][:-1])
            # check the rule against what the screen shows at the card's own level and the next one
            d_now, d_next = int(dmg['value']), int(dmg['value']) + int(dmg['plus'] or 0)
            v, p = int(r['value']), int(r['plus'] or 0)
            if int(d_now / hs) == v and int(d_next / hs) == v + p:
                r['value16'] = str(int(int(dmg['value16']) / hs))
                r['note'] = f'derived value: floor({dmg["item"]} {dmg["value16"]} / {hs})'
                continue
        r['value16'] = ''
        r['note'] += '; derived value below L15: check by hand'


def main(src, dst):
    out = []
    with open(src, encoding='utf-8') as f:
        for r in csv.DictReader(f, delimiter='\t'):
            level = int(r['level'])
            value, plus = r['value'], r['plus']
            m = NUM_PARTS.match(value)
            if 'レベル' in r['item'] and value.isdigit():
                # unit levels go up one per card level
                v16 = str(int(value) + (16 - level)) if plus else value
                note = 'unit level'
            elif level == 16 or not m:
                v16, note = value, ('read at L16' if level == 16 else 'not a number: does not scale')
            else:
                nums = m.group(1).split('-')
                suffix = m.group(2) or ''
                pm = NUM_PARTS.match(plus) if plus else None
                pnums = pm.group(1).split('-') if pm else [None] * len(nums)
                if len(pnums) != len(nums):
                    v16, note = None, f'increment {plus} does not match {value}'
                else:
                    parts, notes = [], []
                    for n, p in zip(nums, pnums):
                        v, nt = to16(int(n), level, int(p) if p is not None else None)
                        parts.append(str(v) if v is not None else '?')
                        notes.append(nt)
                    v16 = '-'.join(parts) + suffix
                    note = '; '.join(sorted(set(notes)))
                    if '?' in v16:
                        v16 = None
            out.append({**r, 'value16': v16 or '', 'note': note})
    fix_derived(out)
    with open(dst, 'w', encoding='utf-8', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(out[0].keys()), delimiter='\t', lineterminator='\n')
        w.writeheader()
        w.writerows(out)
    problems = [r for r in out if not r['value16'] or 'NO BASE' in r['note'] or 'ambiguous' in r['note']]
    for r in out:
        if r in problems or r['note'].startswith('derived'):
            print(f"{r['no']} {r['card']} {r['form']} {r['item']}: {r['value']} +{r['plus']} (L{r['level']}) -> {r['value16']}  [{r['note']}]")
    print(f'rows: {len(out)}, problems: {len(problems)}')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
