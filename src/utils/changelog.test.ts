import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  CURRENT_APP_VERSION,
  LATEST_CHANGELOG,
  parseChangelogSections,
  shouldShowChangelogOnLaunch,
} from './changelog';

test('CURRENT_APP_VERSION stays aligned with Android build.gradle.kts and CHANGELOG.md', () => {
  const gradle = readFileSync(resolve('android/app/build.gradle.kts'), 'utf8');
  const versionMatch = gradle.match(/versionName\s*=\s*"([^"]+)"/);
  assert.ok(versionMatch, 'Expected versionName in android/app/build.gradle.kts');
  const baseAndroidVersion = versionMatch[1].split('-')[0];
  assert.equal(CURRENT_APP_VERSION, baseAndroidVersion);

  const changelogMd = readFileSync(resolve('CHANGELOG.md'), 'utf8');
  const parsed = parseChangelogSections(changelogMd);
  assert.ok(parsed, 'Expected CHANGELOG.md top section to parse');
  assert.equal(parsed.version, CURRENT_APP_VERSION);
  assert.ok(LATEST_CHANGELOG.sections.length >= 2);
});

test('shouldShowChangelogOnLaunch triggers when localStorage version changed', () => {
  assert.equal(
    shouldShowChangelogOnLaunch({
      storedVersion: '2.2.0',
      currentVersion: '2.3.0',
      onboardingDone: true,
      isAutostart: false,
    }),
    true
  );

  // Also triggers even with ?autostart=1 when stored version explicitly differs
  assert.equal(
    shouldShowChangelogOnLaunch({
      storedVersion: '2.2.0',
      currentVersion: '2.3.0',
      onboardingDone: true,
      isAutostart: true,
    }),
    true
  );

  // Returning user upgrading from build without version tracking
  assert.equal(
    shouldShowChangelogOnLaunch({
      storedVersion: null,
      currentVersion: '2.3.0',
      onboardingDone: true,
      isAutostart: false,
    }),
    true
  );

  // Same version -> do not show
  assert.equal(
    shouldShowChangelogOnLaunch({
      storedVersion: '2.3.0',
      currentVersion: '2.3.0',
      onboardingDone: true,
      isAutostart: false,
    }),
    false
  );

  // Brand-new user in onboarding -> wait until next update
  assert.equal(
    shouldShowChangelogOnLaunch({
      storedVersion: null,
      currentVersion: '2.3.0',
      onboardingDone: false,
      isAutostart: false,
    }),
    false
  );
});

test('parseChangelogSections extracts categories and bullet points', () => {
  const parsed = parseChangelogSections(`# Log\n\n## 2.4.0\n### Новое\n- Экспорт\n- Импорт\n### Исправления\n- Фикс CI\n`);
  assert.ok(parsed);
  assert.equal(parsed.version, '2.4.0');
  assert.equal(parsed.sections.length, 2);
  assert.deepEqual(parsed.sections[0].items, ['Экспорт', 'Импорт']);
  assert.deepEqual(parsed.sections[1].items, ['Фикс CI']);
});
