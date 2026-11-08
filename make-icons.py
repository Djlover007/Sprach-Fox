# Zeichnet das Sprach-Fox-Icon (Fuchs mit Sprechblase) in den Ordner "icons".
from PIL import Image, ImageDraw
import os

OR = (245, 124, 31, 255)
OR2 = (214, 92, 12, 255)
CR = (255, 241, 220, 255)
DK = (42, 22, 8, 255)
BG = (20, 163, 127, 255)
WH = (255, 255, 255, 255)

HEAD = [(12, 10), (38, 30), (62, 30), (88, 10), (93, 50), (50, 93), (7, 50)]
LC = [(7, 50), (38, 60), (50, 93)]
RC = [(93, 50), (62, 60), (50, 93)]
LE = [(17, 19), (31, 31), (18, 42)]
RE = [(83, 19), (69, 31), (82, 42)]


def fox(d, u, cx, cy):
    def P(pts):
        return [(cx + (x - 50) * u, cy + (y - 50) * u) for x, y in pts]
    d.polygon(P(HEAD), fill=OR)
    d.polygon(P(LC), fill=CR)
    d.polygon(P(RC), fill=CR)
    d.polygon(P(LE), fill=OR2)
    d.polygon(P(RE), fill=OR2)
    d.polygon(P([(30, 47), (42, 52), (31, 54)]), fill=DK)
    d.polygon(P([(70, 47), (58, 52), (69, 54)]), fill=DK)
    d.polygon(P([(43, 81), (57, 81), (50, 93)]), fill=DK)


def bubble(d, u, cx, cy):
    # Sprechblase unten rechts mit drei Punkten
    def X(x):
        return cx + (x - 50) * u
    def Y(y):
        return cy + (y - 50) * u
    d.rounded_rectangle((X(66), Y(66), X(104), Y(94)), radius=9 * u, fill=WH)
    d.polygon([(X(72), Y(92)), (X(82), Y(92)), (X(68), Y(103))], fill=WH)
    for x in (76, 85, 94):
        d.ellipse((X(x - 3), Y(77), X(x + 3), Y(83)), fill=BG)


def render(size, mode):
    S = 4
    N = size * S
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if mode == 'fg':
        u = 0.52 * N / 80
        fox(d, u, N / 2 - N * 0.04, N / 2 - N * 0.03)
        bubble(d, u, N / 2 - N * 0.04, N / 2 - N * 0.03)
    else:
        if mode == 'square':
            d.rounded_rectangle((0, 0, N - 1, N - 1), radius=int(N * 0.22), fill=BG)
        else:
            d.ellipse((0, 0, N - 1, N - 1), fill=BG)
        u = 0.62 * N / 80
        fox(d, u, N / 2 - N * 0.05, N / 2 - N * 0.03)
        bubble(d, u, N / 2 - N * 0.05, N / 2 - N * 0.03)
    return im.resize((size, size), Image.LANCZOS)


DENS = {'mdpi': (48, 108), 'hdpi': (72, 162), 'xhdpi': (96, 216),
        'xxhdpi': (144, 324), 'xxxhdpi': (192, 432)}

for k, (legacy, fg) in DENS.items():
    p = 'icons/mipmap-' + k
    os.makedirs(p, exist_ok=True)
    render(legacy, 'square').save(p + '/ic_launcher.png')
    render(legacy, 'round').save(p + '/ic_launcher_round.png')
    render(fg, 'fg').save(p + '/ic_launcher_foreground.png')
render(512, 'square').save('icon-preview.png')
print('Fuchs-Icons erzeugt')
