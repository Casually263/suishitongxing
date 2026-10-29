import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// Read credentials only to detect accidental copies. Never include values in output.
const root = fileURLToPath(new URL('../', import.meta.url));
const listed = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], { cwd: root });
const paths = [...new Set(listed.toString('utf8').split('\0').filter(Boolean))];
const envPath = resolve(root, '.env.local');
const secrets = existsSync(envPath) ? readFileSync(envPath, 'utf8').split(/\r?\n/).flatMap(line => {
  const match = line.match(/^\s*([A-Z_0-9]+)=(.*)$/);
  if (!match || !/(PASSWORD|SECRET|KEY|TOKEN)/.test(match[1])) return [];
  const value = match[2].trim().replace(/^(['"])(.*)\1$/, '$2');
  return value.length >= 8 ? [value] : [];
}) : [];
const failures = [];
let bytes = 0;
const forbiddenPath = /(^|\/)(\.runtime|\.tools|\.git|node_modules|target|dist|playwright-report|test-results|\.idea|\.vscode)(\/|$)|(^|\/)\.env(?:\..+)?$|\.(pem|key|p12|pfx|dump|rdb|aof)$/i;
const tokenPatterns = [
  /\bgh[pousr]_[A-Za-z0-9]{30,}\b/,
  /\bgithub_pat_[A-Za-z0-9_]{30,}\b/,
  /\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{32,}\b/,
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/
];
for (const path of paths) {
  if (path !== '.env.example' && forbiddenPath.test(path)) failures.push({ path, reason: 'excluded local or credential file is tracked' });
  const absolute = resolve(root, path);
  if (!existsSync(absolute)) { failures.push({ path, reason: 'listed file is missing' }); continue; }
  const size = statSync(absolute).size;
  bytes += size;
  if (size > 95 * 1024 * 1024) failures.push({ path, reason: 'file exceeds upload size threshold' });
  const buffer = readFileSync(absolute);
  // Byte-level matching also finds exact credential copies inside binary files.
  if (secrets.some(secret => buffer.includes(Buffer.from(secret)))) failures.push({ path, reason: 'contains a current local secret' });
  if (!buffer.includes(0) && tokenPatterns.some(pattern => pattern.test(buffer.toString('utf8')))) failures.push({ path, reason: 'looks like an access token or private key; inspect locally' });
}
console.log(JSON.stringify({ passed: failures.length === 0, files: paths.length, totalBytes: bytes, localSecretValuesChecked: secrets.length, failures }, null, 2));
if (failures.length) process.exitCode = 1;
