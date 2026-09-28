"""Renders the Play Store icon (512×512) and feature graphic (1024×500) from the app's palette.

    python3 tools/gen_store_art.py   # needs Pillow
"""
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..")
OUT = os.path.join(ROOT, "fastlane", "metadata", "android", "en-US", "images")
FONT = os.path.join(ROOT, "app", "src", "main", "res", "font", "unbounded.ttf")
NIGHT = (11, 14, 23)
DAWN = (255, 138, 61)
INK = (242, 237, 230)
SKY = [(11, 14, 23), (42, 15, 30), (122, 31, 26), (224, 102, 31)]


def sky(w, h, horizon):
    img = Image.new("RGB", (w, h), NIGHT)
    d = ImageDraw.Draw(img)
    for y in range(horizon):
        t = y / horizon * (len(SKY) - 1)
        i = min(int(t), len(SKY) - 2)
        f = t - i
        c = tuple(int(SKY[i][k] + (SKY[i + 1][k] - SKY[i][k]) * f) for k in range(3))
        d.line([(0, y), (w, y)], fill=c)
    return img


def sun(img, cx, cy, r, horizon):
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([cx - r * 2, cy - r * 2, cx + r * 2, cy + r * 2], fill=DAWN + (110,))
    glow = glow.filter(ImageFilter.GaussianBlur(r * 0.8))
    img.paste(glow, (0, 0), glow)
    d = ImageDraw.Draw(img)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=DAWN)
    d.rectangle([0, horizon, img.width, img.height], fill=(7, 9, 15))
    d.rectangle([0, horizon, img.width, horizon + max(3, r // 18)], fill=INK)


def icon():
    s = 512
    img = sky(s, s, 330)
    sun(img, s // 2, 330, 120, 330)
    ImageDraw.Draw(img).rectangle([130, 370, 382, 380], fill=INK + (0,))
    img.save(os.path.join(OUT, "icon.png"))


def feature():
    w, h = 1024, 500
    img = sky(w, h, 380)
    sun(img, 800, 380, 110, 380)
    d = ImageDraw.Draw(img)
    title = ImageFont.truetype(FONT, 76)
    title.set_variation_by_axes([800])
    sub = ImageFont.truetype(FONT, 30)
    sub.set_variation_by_axes([500])
    d.text((60, 120), "RiseAnd", font=title, fill=INK)
    d.text((60, 205), "Aaaaaaagh!", font=title, fill=DAWN)
    d.text((62, 310), "scan · shake · slide", font=sub, fill=INK)
    img.save(os.path.join(OUT, "featureGraphic.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    icon()
    feature()
