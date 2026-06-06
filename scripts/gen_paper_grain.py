#!/usr/bin/env python3
"""Generate a seamlessly-tileable paper-grain PNG for the Android theme.

Salt-and-pepper grain (both light and dark flecks) so it reads as paper tooth
rather than a one-sided darkening. Seamlessness: we tile the raw noise 3x3,
blur, then crop the centre tile, so the blur kernel never sees a hard edge and
the result wraps cleanly. Output is RGBA with modest alpha; final subtlety is
tuned in Compose via the draw alpha, not here.

Usage: python3 gen_paper_grain.py <out_path>
"""
import sys
import random
from PIL import Image, ImageFilter

N = 160            # tile size in px
BLUR = 0.6         # softens pure static into a finer paper tooth
MAX_ALPHA = 46     # peak opacity of a fleck in the baked PNG (0-255)
SEED = 20260606

def main(out_path):
    random.seed(SEED)
    # Grayscale value noise centred on 128.
    base = Image.new("L", (N, N))
    base.putdata([random.randint(0, 255) for _ in range(N * N)])

    # Tile 3x3, blur, crop centre -> seamless blurred noise.
    big = Image.new("L", (N * 3, N * 3))
    for ox in range(3):
        for oy in range(3):
            big.paste(base, (ox * N, oy * N))
    big = big.filter(ImageFilter.GaussianBlur(BLUR))
    noise = big.crop((N, N, N * 2, N * 2))

    out = Image.new("RGBA", (N, N))
    px = noise.load()
    op = out.load()
    for y in range(N):
        for x in range(N):
            v = px[x, y]              # 0..255, ~centred on 128
            d = v - 128               # signed deviation
            alpha = int(min(MAX_ALPHA, abs(d) * MAX_ALPHA / 128))
            colour = 255 if d >= 0 else 0   # light fleck vs dark fleck
            op[x, y] = (colour, colour, colour, alpha)

    out.save(out_path, "PNG", optimize=True)
    print(f"wrote {out_path} ({N}x{N}, max alpha {MAX_ALPHA})")

if __name__ == "__main__":
    main(sys.argv[1])
