#!/usr/bin/env bash
set -euo pipefail
cd /workspace/Shirokai-Daily
mkdir -p /workspace/tools /workspace/.gradle-user
if [[ ! -x /workspace/tools/jdk-21.0.13+9/bin/javac ]]; then
  artifact=OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz
  base=https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9
  curl -fsSL --retry 2 "$base/$artifact" -o /tmp/shirokai-jdk.tar.gz
  curl -fsSL --retry 2 "$base/$artifact.sha256.txt" -o /tmp/shirokai-jdk.sha256
  python3 - <<'PY'
import pathlib,hashlib,tarfile
p=pathlib.Path('/tmp/shirokai-jdk.tar.gz')
assert hashlib.sha256(p.read_bytes()).hexdigest()==pathlib.Path('/tmp/shirokai-jdk.sha256').read_text().split()[0]
with tarfile.open(p) as t:t.extractall('/workspace/tools',filter='data')
PY
fi
source scripts/cloud-env.sh
# Translate inherited proxy settings for Java without exposing credentials or disabling TLS.
python3 - <<'PY'
import os,pathlib,urllib.parse
p=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY',''))
lines=['org.gradle.workers.max=2','org.gradle.jvmargs=-Xmx1G']
if p.hostname:
    for protocol in ('https','http'):
        lines.extend([f'systemProp.{protocol}.proxyHost={p.hostname}',f'systemProp.{protocol}.proxyPort={p.port or 80}'])
if pathlib.Path('/etc/ssl/certs/java/cacerts').exists():
    lines.append('systemProp.javax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts')
# This isolated directory is owned by this helper; do not overwrite arbitrary user Gradle settings.
pathlib.Path('/workspace/.gradle-user/gradle.properties').write_text('\n'.join(lines)+'\n')
PY
./gradlew -PcoreOnly test
if [[ "${1:-}" != --core-only ]]; then ./gradlew build; fi
