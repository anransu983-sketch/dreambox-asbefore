#!/usr/bin/env python3
"""工坊：把 custom-icon/icon.png 生成自适应图标资源（CI 里跑）。

- 前景：原图居中裁方后按密度出图（minSdk 26，只需自适应图标）。
- 背景：取原图平均色生成颜色资源，图标底色和图片同调。
"""
import os
import sys

from PIL import Image

SRC = "custom-icon/icon.png"
if not os.path.exists(SRC):
    print("::error::custom-icon/icon.png 不存在：先在 App 的个性化页工坊里上传图片")
    sys.exit(1)

img = Image.open(SRC).convert("RGB")
w, h = img.size
side = min(w, h)
img = img.crop(((w - side) // 2, (h - side) // 2, (w + side) // 2, (h + side) // 2))

r, g, b = img.resize((1, 1)).getpixel((0, 0))
for density, px in {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}.items():
    out = f"app/res/mipmap-{density}/ic_launcher_custom_foreground.png"
    img.resize((px, px), Image.LANCZOS).save(out)
    print("wrote", out)

with open("app/res/values/launcher_custom_bg.xml", "w", encoding="utf-8") as f:
    f.write(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <color name="launcher_bg_custom">#%02X%02X%02X</color>\n</resources>\n' % (r, g, b)
    )
print("bg sampled #%02X%02X%02X" % (r, g, b))
