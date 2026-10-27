import re, pathlib
d = pathlib.Path(__file__).parent
h = (d/'index.html').read_text()
for name in ['art.js','volume.js','acc.js','costumes.js','warp.js','engine.js','studio.js']:
    h = h.replace(f'<script src="{name}"></script>', '<script>\n' + (d/name).read_text() + '\n</script>')
(d.parent/'yadimo-studio.html').write_text(h)
print('ok', len(h))
