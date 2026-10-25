"""Put the output of build_site_stats.py into the site's resources.

Copies stats.tsv to src/main/resources/cards/stats.tsv and replaces the generated block of stat.* keys
at the end of each messages*.properties (the block starts at MARKER), so running it again after a new
capture does not duplicate keys.

usage: python -X utf8 -I install_site_stats.py <build output dir> <repository root>
"""
import os
import shutil
import sys

LANGS = ['en', 'ja', 'es', 'pt', 'de', 'fr', 'it', 'ru', 'tr', 'ko']
MARKER = '# カードのステータスの項目名と値の言葉。'


def main(build_dir, repo):
    res = os.path.join(repo, 'src', 'main', 'resources')
    os.makedirs(os.path.join(res, 'cards'), exist_ok=True)
    shutil.copyfile(os.path.join(build_dir, 'stats.tsv'), os.path.join(res, 'cards', 'stats.tsv'))
    for lang in LANGS:
        path = os.path.join(res, 'messages.properties' if lang == 'en' else f'messages_{lang}.properties')
        with open(path, encoding='utf-8', newline='') as f:
            text = f.read()
        at = text.find(MARKER)
        if at >= 0:
            text = text[:at]
        text = text.rstrip('\n') + '\n\n'
        with open(os.path.join(build_dir, f'messages_{lang}.txt'), encoding='utf-8') as f:
            block = f.read()
        text += MARKER + 'ゲームのtexts.csv(公式の表記)から card-stats-capture スキルの build_site_stats.py が書き出したもの。手で直さない\n' + block
        with open(path, 'w', encoding='utf-8', newline='') as f:
            f.write(text)
        print(path, block.count('\n'), 'keys')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
