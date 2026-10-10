"""投稿文をXの数え方(twitter-text)で数える。Xのカウンタと一致することを2026-10-10に確かめた。

  python xcount.py <本文.txt> [<本文.txt> ...]
"""
import re
import sys
from pathlib import Path

URL = re.compile(r"https?://\S+")
EMOJI = re.compile("[\U0001F000-\U0001FAFF☀-➿]️?")


def weight(ch):
    c = ord(ch)
    if c <= 4351 or 8192 <= c <= 8205 or 8208 <= c <= 8223 or 8242 <= c <= 8247:
        return 1
    return 2


def count(text):
    rest, urls = URL.subn("", text)
    rest, emojis = EMOJI.subn("", rest)
    return 23 * urls + 2 * emojis + sum(weight(ch) for ch in rest)


if __name__ == "__main__":
    for p in sys.argv[1:]:
        n = count(Path(p).read_text(encoding="utf-8").rstrip("\n"))
        print(f"{p}: {n} / 280{'  !! over' if n > 280 else ''}")
