#!/usr/bin/env python3
"""把演示截图合成 GIF，供 README 首屏使用。

输入 output/demo-frames/*.png，输出 output/demo.gif。
每帧底部加一条说明，默认宽 720，控制在 2MB 以内。
"""
import json
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FRAMES_DIR = os.path.join(ROOT, "output", "demo-frames")
OUT = os.path.join(ROOT, "output", "demo.gif")

WIDTH = 720
BAR = 34          # 底部说明条高度
DURATION = 1600   # 每帧毫秒
FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\msyhbd.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
]


def load_font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def prepare(path, label, font):
    img = Image.open(path).convert("RGB")
    ratio = WIDTH / img.width
    img = img.resize((WIDTH, int(img.height * ratio)), Image.LANCZOS)

    canvas = Image.new("RGB", (img.width, img.height + BAR), (245, 246, 248))
    canvas.paste(img, (0, 0))

    draw = ImageDraw.Draw(canvas)
    draw.rectangle([0, img.height, canvas.width, canvas.height], fill=(31, 41, 55))
    text_w = draw.textlength(label, font=font)
    draw.text(((canvas.width - text_w) / 2, img.height + 8), label, font=font, fill=(243, 244, 246))
    return canvas


def main():
    manifest = os.path.join(FRAMES_DIR, "frames.json")
    if not os.path.exists(manifest):
        raise SystemExit("找不到 frames.json，请先运行 Capture-DemoShots.js")

    with open(manifest, encoding="utf-8") as f:
        shots = json.load(f)
    if not shots:
        raise SystemExit("截图清单为空")

    font = load_font(18)
    frames = [prepare(s["file"], s["label"], font) for s in shots]

    # 统一尺寸（不同页面宽度可能差一点）
    w = max(f.width for f in frames)
    h = max(f.height for f in frames)
    frames = [f if f.size == (w, h) else Image.new("RGB", (w, h), (245, 246, 248)) for f in frames] \
        if len({f.size for f in frames}) > 1 else frames

    frames[0].save(
        OUT,
        save_all=True,
        append_images=frames[1:],
        duration=DURATION,
        loop=0,
        optimize=True,
    )
    size = os.path.getsize(OUT) / 1048576
    print(f"已生成 {OUT}")
    print(f"  {len(frames)} 帧 · {w}×{h} · {size:.2f} MB · 每帧 {DURATION}ms")


if __name__ == "__main__":
    main()
