"""Build the site's card stats data and its labels in the site's 10 languages.

inputs : stats_l11_l16.tsv (add_level11.py), the game's texts.csv, the site's messages_ja.properties
outputs: <out_dir>/stats.tsv            -> src/main/resources/cards/stats.tsv
         <out_dir>/messages_<lang>.txt  -> lines to add to src/main/resources/messages*.properties
         and a report of what was left out (no official wording, unknown card, ...)

Only labels and words found in texts.csv are used, so every language shows the game's own wording
(CLAUDE.md: translations are backed by the official text). Rows without such wording are left out
and listed in the report.

usage: python -X utf8 -I build_site_stats.py <stats_l11_l16.tsv> <texts.csv> <messages_ja.properties> <out_dir> <as-of yyyy-mm-dd>
  as-of: the day the screens were read (shown on the site)
"""
import collections
import csv
import os
import re
import sys

# site language -> texts.csv column
LANGS = {'en': 'EN', 'ja': 'JP', 'es': 'ES', 'pt': 'PT', 'de': 'DE', 'fr': 'FR', 'it': 'IT', 'ru': 'RU',
         'tr': 'TR', 'ko': 'KR'}
FORMS = {'n': 'normal', 'e': 'evolution', 'hs': 'hero', 'cs': 'champion'}
LEVEL_TEMPLATE = 'TID_SPELL_ATTRIBUTE_SPAWN_CHARACTER_LEVEL'
SECONDS = 'TID_VALUE_SECONDS'
# Words the page uses around the table: "Ability" (champion/hero section), "Challenge Level" (level 11)
# and "Level <n>" (the level switch)
EXTRA = ['TID_ABILITY', 'TID_BUTTON_TOURNAMENT_LEVEL', 'TID_SPELL_ITEM_LEVEL']
# Messages with a <placeholder>, which becomes the ICU argument {0}
WITH_ARGUMENT = {SECONDS, 'TID_SPELL_ITEM_LEVEL'}
SECONDS = 'TID_VALUE_SECONDS'
# When one Japanese label matches several TIDs, the first prefix in this list wins
PREFERRED = ['TID_SPELL_ATTRIBUTE_', 'TID_ABILITY_STAT_', 'TID_TYPE_', 'TID_TARGETS_', 'TID_SPEED_', 'TID_MELEE_',
             'TID_CHARACTER_']
AVOID = ('TID_AUTOCHESS_', 'TID_WALLBREAKER_MINI_', 'TID_CAPTURE_DPS', 'TID_BOOST_ENDED', 'TID_BUILDING_CAPS',
         'TID_CARD_TYPE_')
# Labels that match several TIDs with different wording in some language: chosen by looking at them
# (2026-10-25). Same-wording candidates are left to PREFERRED.
DEFAULT = {
    '効果時間': 'TID_SPELL_ATTRIBUTE_DURATION', '毎秒ダメージ': 'TID_SPELL_ATTRIBUTE_DAMAGE_PER_SECOND',
    'ザップ': 'TID_ZAPEVO_COUNT', 'ブースト': 'TID_SPELL_ATTRIBUTE_BOOST', 'ボーナスダメージ': 'TID_BUFF_DAMAGE',
    '建物': 'TID_TARGETS_BUILDINGS', 'シールドのヒットポイント': 'TID_SPELL_ATTRIBUTE_SHIELD_HEALTH',
    '生成速度': 'TID_SPELL_ATTRIBUTE_SPAWN_TIME',
}
# Labels whose right TID depends on the card (same Japanese, different English)
BY_CARD = {
    ('Mirror', 'ノーマルカードレベル'): 'TID_MIRRORED_COMMON_LEVEL', ('Mirror', 'レアカードレベル'): 'TID_MIRRORED_RARE_LEVEL',
    ('Mirror', 'スーパーレアカードレベル'): 'TID_MIRRORED_EPIC_LEVEL',
    ('Mirror', 'ウルトラレアカードレベル'): 'TID_MIRRORED_LEGENDARY_LEVEL',
    ('Clone', 'シールドのヒットポイント'): 'TID_SPELL_ATTRIBUTE_CLONE_SHIELD_HEALTH',
    ('Goblin Curse', '効果時間'): 'TID_SPELL_ATTRIBUTE_CURSE_DURATION',
}


def norm(s):
    s = s.replace('\\n', '').replace('\n', '').replace(' ', '').replace('　', '')
    for a, b in (('（', '('), ('）', ')'), ('：', ':'), ('／', '/'), ('〜', '~'), ('～', '~')):
        s = s.replace(a, b)
    return s.replace(':', '').strip()


def load_texts(path):
    with open(path, encoding='utf-8') as f:
        rd = csv.reader(f)
        head = next(rd)
        next(rd)
        idx = {h: i for i, h in enumerate(head)}
        texts = {}
        for row in rd:
            if len(row) >= len(head):
                texts[row[0]] = {lang: row[idx[col]] for lang, col in LANGS.items()}
    by_jp = collections.defaultdict(list)
    for tid, t in texts.items():
        if t['ja']:
            by_jp[norm(t['ja'])].append(tid)
    return texts, by_jp


def pick(tids):
    tids = [t for t in tids if not t.startswith(AVOID)] or tids
    for p in PREFERRED:
        hit = sorted(t for t in tids if t.startswith(p))
        if hit:
            return hit[0]
    return sorted(tids)[0]


def card_names(messages_ja):
    names = {}
    for line in open(messages_ja, encoding='utf-8'):
        m = re.match(r'^card\.((?:[^=\\]|\\.)+)=(.*)$', line.rstrip('\n'))
        if m:
            en = m.group(1).replace('\\ ', ' ')
            names[norm(m.group(2))] = en
    return names


def clean(text):
    # texts.csv keeps line breaks as a literal \n; a label on the site is one line
    return re.sub(r'\s+', ' ', text.replace('\\n', ' ')).strip()


def prop_escape(text):
    return text.replace('\\', '\\\\')


class Builder:
    def __init__(self, texts, by_jp):
        self.texts, self.by_jp = texts, by_jp
        self.characters = {norm(texts[t]['ja']): t for t in texts if t.startswith('TID_CHARACTER_') and texts[t]['ja']}
        self.messages = {}   # key -> {lang: text}
        self.problems = collections.Counter()

    def use(self, tid):
        key = 'stat.' + tid
        if key not in self.messages:
            t = self.texts[tid]
            self.messages[key] = {lang: clean(t[lang]) for lang in LANGS}
        return key

    def label(self, card, item):
        if (card, item) in BY_CARD:
            return self.use(BY_CARD[(card, item)])
        if item in DEFAULT:
            return self.use(DEFAULT[item])
        tids = self.by_jp.get(norm(item))
        if tids:
            return self.use(pick(tids))
        m = re.fullmatch(r'(.+?)の?レベル', item)
        if m and norm(m.group(1)) in self.characters:
            ch = self.characters[norm(m.group(1))]
            key = f'stat.{ch}.level'
            if key not in self.messages:
                tpl, name = self.texts[LEVEL_TEMPLATE], self.texts[ch]
                self.messages[key] = {lang: clean(re.sub(r'<[A-Z_]+>', name[lang], tpl[lang])) for lang in LANGS}
            return key
        return None

    def word(self, w, item):
        if w == '無限':
            # "infinite" has a range-specific wording in some languages (de: Unendlich / Endlos)
            return '{t:' + self.use('TID_INFINITE_RANGE' if '射程' in item else 'TID_INFINITE') + '}'
        if w in DEFAULT:
            return '{t:' + self.use(DEFAULT[w]) + '}'
        tids = self.by_jp.get(norm(w))
        if tids:
            return '{t:' + self.use(pick(tids)) + '}'
        if norm(w) in self.characters:
            return '{t:' + self.use(self.characters[norm(w)]) + '}'
        return None

    def value(self, v, item):
        """Game text -> site token string; None when some word has no official wording."""
        if v == '':
            return ''
        self.use(SECONDS)
        m = re.fullmatch(r'(\d+)分(\d+)秒', v)
        if m:
            return '{s:%d}' % (int(m.group(1)) * 60 + int(m.group(2)))
        out = []
        for piece in re.split(r'(-(?=\d)|~| x\d+$)', v):
            if not piece:
                continue
            if piece in ('-', '~') or re.fullmatch(r' x\d+', piece):
                out.append(piece)
            elif re.fullmatch(r'[+-]?\d+(\.\d+)?%?|\d+x\d+', piece):
                out.append(piece)
            elif re.fullmatch(r'\d+(\.\d+)?秒', piece):
                out.append('{s:' + piece[:-1] + '}')
            else:
                w = self.word(piece.strip(), item)
                if w is None:
                    self.problems['word: ' + piece] += 1
                    return None
                out.append(w)
        return ''.join(out)


def main(stats_path, texts_path, messages_ja, out_dir, as_of):
    texts, by_jp = load_texts(texts_path)
    names = card_names(messages_ja)
    b = Builder(texts, by_jp)
    for tid in EXTRA:
        b.use(tid)
    rows = list(csv.DictReader(open(stats_path, encoding='utf-8'), delimiter='\t'))
    normal = {(r['no'], r['item']): r['value16'] for r in rows if r['form'] == 'n'}
    out, left_out = [], []
    for r in rows:
        if r['form'] not in FORMS:
            continue   # hero body stats are the same as the normal ones
        if r['form'] == 'e' and normal.get((r['no'], r['item'])) == r['value16']:
            continue   # evolution: only what differs from the normal card
        card = names.get(norm(r['card']))
        if not card:
            b.problems['card: ' + r['card']] += 1
            continue
        key = b.label(card, r['item'])
        v16, v11 = b.value(r['value16'], r['item']), b.value(r['value11'], r['item'])
        if key is None or v16 is None or (r['value11'] and v11 is None):
            left_out.append(f"{card}\t{r['form']}\t{r['item']}\t{r['value16']}\t" +
                            ('no official label' if key is None else 'no official wording for the value'))
            continue
        out.append((card, FORMS[r['form']], key, v16, v11))

    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, 'stats.tsv'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('# カードのステータス。ゲーム内のカード情報の画面から読み取り、レベル16とレベル11の値にしたもの。\n')
        f.write('# 作り方は .claude/skills/card-stats-capture (このファイルは build_site_stats.py が書き出す。手で直さない)。\n')
        f.write('# card<TAB>form(normal|evolution|hero|champion)<TAB>項目のメッセージキー<TAB>レベル16の値<TAB>レベル11の値(空=求められない)\n')
        f.write('# 値の {s:1.8} は秒(stat.TID_VALUE_SECONDS)、{t:キー} はその言葉のメッセージ。並びはゲームの画面の順\n')
        f.write(f'@asof\t{as_of}\n')
        for row in out:
            f.write('\t'.join(row) + '\n')
    for lang in LANGS:
        with open(os.path.join(out_dir, f'messages_{lang}.txt'), 'w', encoding='utf-8', newline='\n') as f:
            for key in sorted(b.messages):
                text = b.messages[key][lang]
                if key[len('stat.'):] in WITH_ARGUMENT:
                    text = re.sub(r'<[A-Za-z]+>', '{0}', text)
                f.write(f'{key}={prop_escape(text)}\n')
    with open(os.path.join(out_dir, 'left_out.tsv'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(left_out) + '\n')
    empty = [k for k, v in b.messages.items() for lang in LANGS if not v[lang]]
    print(f'rows: {len(out)}  left out: {len(left_out)}  message keys: {len(b.messages)}  cards: {len({r[0] for r in out})}')
    print('problems:', dict(b.problems))
    print('empty translations:', empty)


if __name__ == '__main__':
    main(*sys.argv[1:6])
