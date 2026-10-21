'use strict';

// Dev builds: node scripts/dev-build.js [--install]
// Builds "Quantm Dev" (its own app id, installs next to the public app) as <version>-dev.N, where
// <version> is the next release in gradle.properties, and uploads the APK as a prerelease of the
// private dev-builds repo tagged android-<version>-dev.N. Dev builds update only from there.
// Prereleases are invisible to desktop dev builds (they don't allow prereleases), so the two never mix.
// With --install it also installs onto every phone or emulator adb can see.
//
// Needs: the GitHub CLI signed in with access to the dev repo, dev-update-token.txt (git-ignored,
// a read-only token for only the dev repo, built into the app to download updates), and
// keystore.properties for signing.

const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const DEV_REPO = process.env.QUANTM_DEV_REPO || 'drumplayer1234/Quantm-Dev';

function fail(message) {
  console.error('\n' + message + '\n');
  process.exit(1);
}

function run(exe, args, options = {}) {
  return execFileSync(exe, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], cwd: ROOT, ...options });
}

function gh(args) {
  for (const exe of ['gh', path.join(process.env.ProgramFiles || 'C:\\Program Files', 'GitHub CLI', 'gh.exe')]) {
    try {
      return run(exe, args).trim();
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
  }
  return fail('The GitHub CLI (gh) is not installed. Install it with: winget install GitHub.cli');
}

/** 1.2.3 -> 1002003000; dev builds add N, the release adds 999 */
function baseCode(version) {
  const [major, minor, patch] = version.split('.').map(n => parseInt(n, 10) || 0);
  return (major * 10000 + minor * 100 + patch) * 1000;
}

function main() {
  const install = process.argv.includes('--install');
  const props = fs.readFileSync(path.join(ROOT, 'gradle.properties'), 'utf8');
  const version = (/^quantm\.version=(.+)$/m.exec(props) || [])[1];
  if (!version || !/^\d+\.\d+\.\d+$/.test(version.trim())) fail('gradle.properties needs quantm.version=X.Y.Z (the next release).');
  if (!fs.existsSync(path.join(ROOT, 'keystore.properties'))) fail('Missing keystore.properties (the release signing key).');
  const token = fs.existsSync(path.join(ROOT, 'dev-update-token.txt')) ? fs.readFileSync(path.join(ROOT, 'dev-update-token.txt'), 'utf8').trim() : '';
  if (!/^[\w-]{20,255}$/.test(token)) fail('dev-update-token.txt is missing or isn\'t a GitHub token.');

  const tags = gh(['api', `repos/${DEV_REPO}/releases?per_page=100`, '--paginate', '--jq', '.[].tag_name']).split(/\s+/);
  const pattern = new RegExp('^android-' + version.replace(/\./g, '\\.') + '-dev\\.(\\d+)$');
  const n = tags.reduce((max, tag) => Math.max(max, Number((pattern.exec(tag) || [])[1] || 0)), 0) + 1;
  if (n > 998) fail('Too many dev builds for ' + version + '; bump quantm.version.');
  const devVersion = `${version}-dev.${n}`;
  const code = baseCode(version) + n;

  console.log(`Building Quantm Dev ${devVersion} (versionCode ${code})...`);
  const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
  execFileSync(gradlew, [':app:assembleDev', `-Pquantm.version=${devVersion}`, `-Pquantm.versionCode=${code}`, '--console=plain', '-q'], {
    cwd: ROOT, stdio: 'inherit', shell: process.platform === 'win32',
  });

  const built = path.join(ROOT, 'app', 'build', 'outputs', 'apk', 'dev', 'app-dev.apk');
  const dist = path.join(ROOT, 'dist');
  fs.mkdirSync(dist, { recursive: true });
  const apk = path.join(dist, `Quantm-Dev-${devVersion}.apk`);
  fs.copyFileSync(built, apk);

  const notes = (process.env.QUANTM_DEV_NOTES || '').trim() || `Quantm for Android dev build ${devVersion}.`;
  gh(['release', 'create', `android-${devVersion}`, apk, '--repo', DEV_REPO, '--prerelease',
    '--title', `Quantm Android ${devVersion}`, '--notes', notes]);
  console.log(`Published android-${devVersion} to ${DEV_REPO}`);
  console.log(apk);

  if (install) {
    const adb = path.join(process.env.LOCALAPPDATA || '', 'Android', 'Sdk', 'platform-tools', 'adb.exe');
    const devices = run(fs.existsSync(adb) ? adb : 'adb', ['devices']).split('\n').slice(1)
      .map(l => l.trim().split(/\s+/)).filter(p => p[1] === 'device').map(p => p[0]);
    if (!devices.length) console.log('No phone or emulator connected; skipped installing.');
    for (const device of devices) {
      run(fs.existsSync(adb) ? adb : 'adb', ['-s', device, 'install', '-r', apk]);
      console.log('Installed on ' + device);
    }
  }
}

main();
