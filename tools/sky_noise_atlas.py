# The animated weather's cloud noise, from Sébastien Hillaire's TileableVolumeNoise (MIT,
# https://github.com/sebh/TileableVolumeNoise), as its main.cpp generates it after GPU Pro 7's
# "Real-Time Volumetric Cloudscapes": noiseShapePacked.tga, the clouds' base shape (Perlin-Worley remapped by a
# low-frequency Worley fBm), 128^3, and noiseErosionPacked.tga, the detail that erodes it (Worley fBm), 32^3.
# Built on macOS with its include paths' backslashes turned, malloc.h as stdlib.h, `unsigned char(x)` casts as
# `(unsigned char)(x)` and ppl's parallel_for as one thread per slice.
#
# This turns one of them into a grey atlas of its slices, for res/raw:
#   python3 tools/sky_noise_atlas.py noiseShapePacked.tga 128 16 shape.png
#   cwebp -lossless -exact shape.png -o app/src/main/res/raw/sky_cloud_shape.webp
#   python3 tools/sky_noise_atlas.py noiseErosionPacked.tga 32 8 detail.png
#   cwebp -lossless -exact detail.png -o app/src/main/res/raw/sky_cloud_detail.webp
# The TGA holds texel (s, t, r) at row r, column t * size + s; slice r of the atlas is at column r % across, row
# r / across, its texel (s, t) at x = s, y = t.
#
# The values are then stretched linearly over 0..1, from their 0.5th to their 99.5th percentile: the packed shape
# runs only from about 0.76 to 0.97, and the samplers' remaps (by the coverage, by the detail) assume noise over 0..1,
# as GPU Pro 7's figures show it; stretched, a coverage threshold carves the noise's shapes rather than letting all of
# it through or none. The shapes themselves are unchanged.
import subprocess
import sys

src, size, across, out = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', src, '-f', 'rawvideo', '-pix_fmt', 'gray', '-'],
                     capture_output=True, check=True).stdout
assert len(raw) == size ** 3, len(raw)
ordered = sorted(raw)
lo, hi = ordered[len(raw) // 200], ordered[len(raw) - 1 - len(raw) // 200]
raw = bytes(max(0, min(255, round((v - lo) * 255 / (hi - lo)))) for v in raw)
down = size // across
width, height = across * size, down * size
atlas = bytearray(width * height)
for r in range(size):
    ox, oy = (r % across) * size, (r // across) * size
    row = raw[r * size * size:(r + 1) * size * size]
    for t in range(size):
        start = (oy + t) * width + ox
        atlas[start:start + size] = row[t * size:(t + 1) * size]
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'gray', '-s', f'{width}x{height}',
                '-i', '-', out], input=bytes(atlas), check=True)
