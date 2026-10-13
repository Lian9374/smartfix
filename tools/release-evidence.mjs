import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import path from 'node:path';

// Hash executable sources separately from docs/results, avoiding a circular
// "test report changes the tested revision" claim. Never reads local secrets.
export function releaseIdentity(root, jar) {
  const git = (...args) => execFileSync('git', args, { cwd: root, windowsHide: true, encoding: 'utf8' }).trim();
  const files = git('ls-files', '--cached', '--others', '--exclude-standard', '--', 'src', 'pom.xml', 'tools')
    .split(/\r?\n/).filter(Boolean).sort();
  const source = createHash('sha256');
  for (const file of files) {
    source.update(file + '\0');
    source.update(readFileSync(path.join(root, file)));
  }
  return {
    sourceCommit: git('rev-parse', 'HEAD'),
    sourceFingerprint: source.digest('hex'),
    artifactSha256: createHash('sha256').update(readFileSync(jar)).digest('hex'),
    sourceChanges: git('status', '--porcelain', '--', 'src', 'pom.xml', 'tools').split(/\r?\n/).filter(Boolean)
  };
}
