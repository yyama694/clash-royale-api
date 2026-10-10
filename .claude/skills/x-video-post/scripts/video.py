"""X宣伝用の縦動画(1080x1920)を、ユーザーが編集したプレイ動画のクリップから作る。手順はSKILL.md。

  python video.py probe   <素材フォルダ> <作業フォルダ>   長さ・音声・音量の一覧と、クリップごとのコマ一覧画像
  python video.py images  <config.json>                  帯・カード名・エンドカードの画像
  python video.py preview <config.json>                  画像を実際のコマに重ねた確認用の画像
  python video.py render  <config.json>                  完成版の動画(言語ごとに1本)
  python video.py verify  <config.json>                  完成版の長さ・音量の確認と、つなぎ目のコマの画像
  python video.py light   <config.json>                  Xへの添付用に10MB未満へ縮めた版(<repo>/target/x-upload/)

imageio-ffmpeg(ffmpeg同梱)とPillowが要る。
"""
import json
import re
import subprocess
import sys
from pathlib import Path

import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFilter, ImageFont

FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
REPO = Path(__file__).resolve().parents[4]
ICON = REPO / "src" / "main" / "resources" / "static" / "images" / "og-image.jpg"
UPLOAD_DIR = REPO / "target" / "x-upload"
NOTO_JP = r"C:\Windows\Fonts\NotoSansJP-VF.ttf"
SITE_NAME = "Princess Tower"
SITE_URL = "princess-tower.duckdns.org"

W, H, FPS = 1080, 1920, 24
SHADOW_H = 26
NAVY = (10, 22, 58)
GOLD = (255, 211, 74)
WHITE = (255, 255, 255)
XFADE_SEC = 0.4
FIT = f"scale={W}:{H}:force_original_aspect_ratio=decrease,pad={W}:{H}:(ow-iw)/2:(oh-ih)/2:black,setsar=1"
UPLOAD_LIMIT = 9_500_000
UPLOAD_AUDIO_K = 64


def ffmpeg(args, check=True):
    r = subprocess.run([FFMPEG, "-hide_banner", *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and r.returncode != 0:
        print(r.stderr[-3000:])
        raise SystemExit(f"ffmpeg failed: {args[:8]}")
    return r.stderr


def duration(path):
    h, m, s = re.search(r"Duration: (\d+):(\d+):([\d.]+)", ffmpeg(["-i", str(path)], check=False)).groups()
    return int(h) * 3600 + int(m) * 60 + float(s)


def loudness(path):
    err = ffmpeg(["-nostats", "-i", str(path), "-vn", "-af", "loudnorm=print_format=json", "-f", "null", "-"])
    d = json.loads(re.search(r"\{[^{}]*\"input_i\"[^{}]*\}", err, re.S).group(0))
    return float(d["input_i"]), float(d["input_tp"])


def font(size, weight=900):
    f = ImageFont.truetype(NOTO_JP, size)
    f.set_variation_by_axes([weight])
    return f


def text_w(draw, s, f, stroke=0):
    left, _, right, _ = draw.textbbox((0, 0), s, font=f, stroke_width=stroke)
    return right - left


def load(config_path):
    cfg = json.loads(Path(config_path).read_text(encoding="utf-8"))
    cfg["src_dir"], cfg["out_dir"], cfg["work_dir"] = (Path(cfg[k]) for k in ("src_dir", "out_dir", "work_dir"))
    cfg["work_dir"].mkdir(parents=True, exist_ok=True)
    cfg.setdefault("band_height", 236)
    cfg.setdefault("end_seconds", 4.0)
    cfg.setdefault("target_lufs", -17.0)
    cfg.setdefault("label_prefix", {lang: "vs" for lang in cfg["langs"]})
    return cfg


def probe(src_dir, work_dir):
    src_dir, work_dir = Path(src_dir), Path(work_dir)
    work_dir.mkdir(parents=True, exist_ok=True)
    for f in sorted(src_dir.glob("*.mp4")):
        info = ffmpeg(["-i", str(f)], check=False)
        streams = [re.sub(r"\s+", " ", l.strip())[:150] for l in info.splitlines() if re.search(r"Stream #.*(Video|Audio)", l)]
        i, tp = loudness(f)
        print(f"== {f.name}: {duration(f):.2f}s  loudness {i:.1f} LUFS / peak {tp:.1f} dBTP")
        for s in streams:
            print("   ", s)
        sheet = work_dir / f"sheet_{f.stem}.jpg"
        ffmpeg(["-y", "-i", str(f), "-vf", "fps=2/3,scale=270:480,tile=6x3:padding=4", "-frames:v", "1", "-q:v", "3", str(sheet)])
        print("    sheet:", sheet)


def make_band(cfg, lang):
    bh = cfg["band_height"]
    img = Image.new("RGBA", (W, bh + SHADOW_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    top, bottom = (8, 18, 50), (20, 44, 104)
    for y in range(bh):
        t = y / (bh - 1)
        d.line([(0, y), (W, y)], fill=tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))
    d.rectangle([0, bh - 6, W, bh - 1], fill=GOLD + (255,))
    for y in range(SHADOW_H):
        d.line([(0, bh + y), (W, bh + y)], fill=(0, 0, 0, round(110 * (1 - y / SHADOW_H) ** 2)))
    d.text((W // 2, round(bh * 0.28)), cfg["langs"][lang]["title"], font=font(54), fill=GOLD, anchor="mm", stroke_width=3, stroke_fill=NAVY)
    return img


def make_label(cfg, lang, idx):
    bh = cfg["band_height"]
    img = Image.new("RGBA", (W, bh), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    name = cfg["clips"][idx]["label"][lang]
    prefix = cfg["label_prefix"][lang]
    pill_txt = f"{idx + 1}/{len(cfg['clips'])}"
    pill_f, pre_f = font(48), font(50)
    pill_w, pill_h = text_w(d, pill_txt, pill_f) + 48, 76
    pre_w = text_w(d, prefix, pre_f) if prefix else 0
    gap_pre = 18 if prefix else 0
    size = 86
    while True:
        name_f = font(size)
        total = pill_w + 30 + pre_w + gap_pre + text_w(d, name, name_f, 5)
        if total <= W - 72 or size <= 50:
            break
        size -= 2
    cy = round(bh * 0.67)
    x = (W - total) // 2
    d.rounded_rectangle([x, cy - pill_h // 2, x + pill_w, cy + pill_h // 2], radius=pill_h // 2, fill=GOLD)
    d.text((x + pill_w // 2, cy), pill_txt, font=pill_f, fill=NAVY, anchor="mm")
    x += pill_w + 30
    if prefix:
        d.text((x, cy + 4), prefix, font=pre_f, fill=GOLD, anchor="lm")
        x += pre_w + gap_pre
    d.text((x, cy), name, font=name_f, fill=WHITE, anchor="lm", stroke_width=5, stroke_fill=NAVY)
    return img


def wrap(d, s, f, max_w, by_char):
    units = list(s) if by_char else s.split(" ")
    sep = "" if by_char else " "
    lines, cur = [], ""
    for u in units:
        nxt = cur + (sep if cur else "") + u
        if cur and text_w(d, nxt, f) > max_w:
            lines.append(cur)
            cur = u
        else:
            cur = nxt
    lines.append(cur)
    return lines


def make_endcard(cfg, lang, bg_path):
    t = cfg["langs"][lang]
    bg = Image.open(bg_path).convert("RGB").filter(ImageFilter.GaussianBlur(22))
    img = Image.alpha_composite(bg.convert("RGBA"), Image.new("RGBA", (W, H), (6, 14, 40, 205)))

    size, top = 440, 360
    icon = Image.open(ICON).convert("RGBA").resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(mask).ellipse([6, 6, size * 4 - 6, size * 4 - 6], fill=255)
    icon.putalpha(mask.resize((size, size), Image.LANCZOS))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([W // 2 - size // 2 - 30, top - 30, W // 2 + size // 2 + 30, top + size + 30], fill=GOLD + (90,))
    img = Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(28)))
    img.alpha_composite(icon, (W // 2 - size // 2, top))

    # ImageDraw does not alpha-blend onto RGBA, so the translucent URL pill goes on its own layer.
    url_f = font(48, 700)
    uw = text_w(ImageDraw.Draw(img), SITE_URL, url_f) + 80
    pill = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(pill).rounded_rectangle([W // 2 - uw // 2, 1290, W // 2 + uw // 2, 1386], radius=48, fill=(255, 255, 255, 36), outline=GOLD, width=4)
    img = Image.alpha_composite(img, pill)

    d = ImageDraw.Draw(img)
    d.text((W // 2, 930), t["end1"], font=font(62, 800), fill=WHITE, anchor="mm", stroke_width=3, stroke_fill=NAVY)
    d.text((W // 2, 1020), t["end2"], font=font(66), fill=GOLD, anchor="mm", stroke_width=3, stroke_fill=NAVY)
    d.text((W // 2, 1190), SITE_NAME, font=font(104), fill=WHITE, anchor="mm", stroke_width=5, stroke_fill=NAVY)
    d.text((W // 2, 1338), SITE_URL, font=url_f, fill=WHITE, anchor="mm")

    disc_f = font(30, 500)
    by_char = lang in ("ja", "ko")
    lines = [l for part in t["disclaimer"].split("\n") for l in wrap(d, part, disc_f, 1000, by_char)]
    y = 1740 - (len(lines) - 1) * 24
    for line in lines:
        d.text((W // 2, y), line, font=disc_f, fill=(205, 214, 236), anchor="mm")
        y += 48
    return img.convert("RGB")


def images(cfg):
    work = cfg["work_dir"]
    bg = work / "end_bg.png"
    ffmpeg(["-y", "-sseof", "-0.3", "-i", str(cfg["src_dir"] / cfg["clips"][-1]["file"]), "-vf", FIT, "-frames:v", "1", str(bg)])
    for lang in cfg["langs"]:
        make_band(cfg, lang).save(work / f"band_{lang}.png")
        for i in range(len(cfg["clips"])):
            make_label(cfg, lang, i).save(work / f"label_{lang}_{i}.png")
        make_endcard(cfg, lang, bg).save(work / f"end_{lang}.png")
    print("images:", work)


def preview(cfg):
    work, n = cfg["work_dir"], len(cfg["clips"])
    for i, clip in enumerate(cfg["clips"]):
        ffmpeg(["-y", "-ss", "2", "-i", str(cfg["src_dir"] / clip["file"]), "-vf", FIT, "-frames:v", "1", str(work / f"frame_{i}.png")])
    for lang in cfg["langs"]:
        tiles = []
        for i in range(n):
            img = Image.open(work / f"frame_{i}.png").convert("RGBA")
            img.alpha_composite(Image.open(work / f"band_{lang}.png"))
            img.alpha_composite(Image.open(work / f"label_{lang}_{i}.png"))
            tiles.append(img.convert("RGB"))
        tiles.append(Image.open(work / f"end_{lang}.png").convert("RGB"))
        cols = 4
        tw, th = W // 3, H // 3
        sheet = Image.new("RGB", (tw * cols, th * ((len(tiles) + cols - 1) // cols)), (0, 0, 0))
        for k, t in enumerate(tiles):
            sheet.paste(t.resize((tw, th), Image.LANCZOS), ((k % cols) * tw, (k // cols) * th))
        sheet.save(work / f"preview_{lang}.jpg", quality=88)
        tiles[0].save(work / f"preview_{lang}_first.png")
        print(f"preview {lang}:", work / f"preview_{lang}.jpg", "/ full size:", work / f"preview_{lang}_first.png", "/ end card:", work / f"end_{lang}.png")


def render(cfg):
    work, out = cfg["work_dir"], cfg["out_dir"]
    out.mkdir(parents=True, exist_ok=True)
    meta = []
    for clip in cfg["clips"]:
        p = cfg["src_dir"] / clip["file"]
        meta.append((p, duration(p), cfg["target_lufs"] - loudness(p)[0]))
    end_sec = cfg["end_seconds"]
    for lang, t in cfg["langs"].items():
        segs = []
        for i, (p, dur, gain) in enumerate(meta):
            seg = work / f"seg_{lang}_{i}.mkv"
            # The first frame doubles as the X thumbnail, so the first label must be visible from frame 0.
            label_fade = ",fade=t=in:st=0:d=0.3:alpha=1" if i > 0 else ""
            fc = (
                f"[0:v]fps={FPS},{FIT},format=yuv420p[base];"
                "[1:v]format=rgba[b];"
                f"[2:v]format=rgba{label_fade}[l];"
                "[base][b]overlay=0:0:shortest=1[t1];"
                "[t1][l]overlay=0:0:shortest=1,format=yuv420p[v];"
                f"[0:a]aresample=48000,volume={gain:.2f}dB,afade=t=in:d=0.15,afade=t=out:st={dur - 0.2:.3f}:d=0.2[a]"
            )
            ffmpeg(["-y", "-i", str(p),
                    "-loop", "1", "-framerate", str(FPS), "-i", str(work / f"band_{lang}.png"),
                    "-loop", "1", "-framerate", str(FPS), "-i", str(work / f"label_{lang}_{i}.png"),
                    "-filter_complex", fc, "-map", "[v]", "-map", "[a]",
                    "-c:v", "libx264", "-preset", "medium", "-crf", "12", "-c:a", "pcm_s16le", str(seg)])
            segs.append(seg)
            print(f"  seg {lang} {i + 1}/{len(meta)} ({dur:.2f}s, gain {gain:+.2f} dB)")

        total = sum(duration(s) for s in segs)
        n = len(segs)
        inputs = [a for s in segs for a in ("-i", str(s))]
        inputs += ["-loop", "1", "-framerate", str(FPS), "-t", str(end_sec), "-i", str(work / f"end_{lang}.png")]
        fc = (
            "".join(f"[{i}:v][{i}:a]" for i in range(n)) + f"concat=n={n}:v=1:a=1[vc][ac];"
            f"[vc]fps={FPS},settb=AVTB,setsar=1,format=yuv420p[v1];"
            f"[{n}:v]fps={FPS},settb=AVTB,setsar=1,format=yuv420p[v2];"
            f"[v1][v2]xfade=transition=fade:duration={XFADE_SEC}:offset={total - XFADE_SEC:.3f}[v];"
            f"[ac]afade=t=out:st={total - 0.5:.3f}:d=0.5,apad=pad_dur={end_sec},alimiter=limit=0.84:level=0[a]"
        )
        dst = out / t["file"]
        ffmpeg(["-y", *inputs, "-filter_complex", fc, "-map", "[v]", "-map", "[a]",
                "-c:v", "libx264", "-preset", "slow", "-crf", "16", "-profile:v", "high", "-pix_fmt", "yuv420p",
                "-maxrate", "12M", "-bufsize", "24M", "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
                "-t", f"{total + end_sec - XFADE_SEC:.3f}", "-movflags", "+faststart", str(dst)])
        print("done:", dst)


def verify(cfg):
    work, out = cfg["work_dir"], cfg["out_dir"]
    starts, acc = [], 0.0
    for clip in cfg["clips"]:
        starts.append(acc)
        acc += duration(cfg["src_dir"] / clip["file"])
    times = [0.0, starts[1] - 0.3, starts[1] + 0.15, starts[1] + 0.6, acc - XFADE_SEC / 2, acc + cfg["end_seconds"] - 1.0]
    for lang, t in cfg["langs"].items():
        video = out / t["file"]
        info = ffmpeg(["-i", str(video)], check=False)
        print(f"== {video.name}  {video.stat().st_size / 1024 / 1024:.1f} MB")
        for l in info.splitlines():
            if re.search(r"Duration|Stream #.*(Video|Audio)", l):
                print("   ", re.sub(r"\s+", " ", l.strip())[:150])
        i, tp = loudness(video)
        print(f"    loudness {i:.1f} LUFS / peak {tp:.1f} dBTP")
        v = re.findall(r"time=(\S+)", ffmpeg(["-i", str(video), "-map", "0:v", "-f", "null", "-"]))[-1]
        a = re.findall(r"time=(\S+)", ffmpeg(["-i", str(video), "-map", "0:a", "-f", "null", "-"]))[-1]
        print(f"    decoded length: video {v} / audio {a}")
        tiles = []
        for k, ts in enumerate(times):
            p = work / f"verify_{lang}_{k}.png"
            ffmpeg(["-y", "-ss", f"{ts:.2f}", "-i", str(video), "-frames:v", "1", str(p)])
            tiles.append(Image.open(p).convert("RGB").resize((270, 480)))
        sheet = Image.new("RGB", (270 * len(tiles), 480))
        for k, tile in enumerate(tiles):
            sheet.paste(tile, (k * 270, 0))
        sheet_path = work / f"verify_{lang}.jpg"
        sheet.save(sheet_path, quality=88)
        print("    frames at", ", ".join(f"{ts:.1f}s" for ts in times), "->", sheet_path)


def light(cfg):
    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    for lang, t in cfg["langs"].items():
        src = cfg["out_dir"] / t["file"]
        dst = UPLOAD_DIR / f"x_upload_{lang}.mp4"
        video_k = int((UPLOAD_LIMIT * 8 / duration(src) / 1000 - UPLOAD_AUDIO_K) * 0.97)
        common = ["-y", "-i", str(src), "-vf", "scale=720:1280:flags=lanczos", "-c:v", "libx264", "-preset", "veryslow",
                  "-b:v", f"{video_k}k", "-profile:v", "high", "-pix_fmt", "yuv420p", "-passlogfile", str(cfg["work_dir"] / f"pass_{lang}")]
        ffmpeg([*common, "-pass", "1", "-an", "-f", "null", "NUL"])
        ffmpeg([*common, "-pass", "2", "-c:a", "aac", "-b:a", f"{UPLOAD_AUDIO_K}k", "-ar", "48000", "-movflags", "+faststart", str(dst)])
        size = dst.stat().st_size
        print(f"{dst}: {size:,} bytes (video {video_k}k){'' if size < 10 * 1024 * 1024 else '  !! over 10MB'}")


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "probe":
        probe(sys.argv[2], sys.argv[3])
    else:
        {"images": images, "preview": preview, "render": render, "verify": verify, "light": light}[cmd](load(sys.argv[2]))
