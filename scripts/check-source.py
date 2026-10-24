from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
for name in filter(None, tracked):
    path = root / name
    if path.name in ('local.properties', 'signing.properties', 'keystore.properties'):
        raise SystemExit(f'Local configuration must not be committed: {name}')
    if path.suffix in ('.jks', '.keystore', '.apk'):
        raise SystemExit(f'Private key or generated APK must not be committed: {name}')
    if path.suffix in ('.png', '.jpg', '.jpeg', '.webp') and name not in ('docs/screenshots/full-dock.png', 'docs/screenshots/list-left.png'):
        raise SystemExit(f'Raster asset needs a distribution review: {name}')
    if path.suffix in ('.cer', '.jar', '.png'):
        continue
    text = path.read_text(errors='replace')
    if re.search(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{30,}', text):
        raise SystemExit(f'Potential credential in {name}')
print(f'Checked {sum(bool(x) for x in tracked)} tracked source files')
