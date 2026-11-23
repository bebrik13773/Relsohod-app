import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  APP_VERSION_STORAGE_KEY,
  CURRENT_APP_VERSION,
  LATEST_CHANGELOG,
  checkAppVersionAgainstStorage,
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

test('checkAppVersionAgainstStorage compares currentVersion with localStorage safely', () => {
  const outdatedStorage = {
    getItem: (key: string) => (key === APP_VERSION_STORAGE_KEY ? '2.2.0' : null),
  };
  const upgradeResult = checkAppVersionAgainstStorage({
    storage: outdatedStorage,
    onboardingDone: true,
    isAutostart: true,
  });
  assert.equal(upgradeResult.changed, true);
  assert.equal(upgradeResult.shouldShowChangelog, true);
  assert.equal(upgradeResult.storedVersion, '2.2.0');
  assert.equal(upgradeResult.currentVersion, CURRENT_APP_VERSION);
  assert.deepEqual(upgradeResult.release, LATEST_CHANGELOG);

  const upToDateStorage = {
    getItem: (key: string) => (key === APP_VERSION_STORAGE_KEY ? CURRENT_APP_VERSION : null),
  };
  const sameResult = checkAppVersionAgainstStorage({
    storage: upToDateStorage,
    onboardingDone: true,
  });
  assert.equal(sameResult.changed, false);
  assert.equal(sameResult.shouldShowChangelog, false);

  const brokenStorage = {
    getItem: () => {
      throw new Error('SecurityError');
    },
  };
  const brokenResult = checkAppVersionAgainstStorage({
    storage: brokenStorage,
    onboardingDone: false,
  });
  assert.equal(brokenResult.changed, true);
  assert.equal(brokenResult.shouldShowChangelog, false);

  const noStorageResult = checkAppVersionAgainstStorage({ storage: null });
  assert.equal(noStorageResult.changed, false);
  assert.equal(noStorageResult.shouldShowChangelog, false);
});

test('parseChangelogSections extracts categories and bullet points', () => {
  const parsed = parseChangelogSections(`# Log\n\n## 2.4.0\n### Новое\n- Экспорт\n- Импорт\n### Исправления\n- Фикс CI\n`);
  assert.ok(parsed);
  assert.equal(parsed.version, '2.4.0');
  assert.equal(parsed.sections.length, 2);
  assert.deepEqual(parsed.sections[0].items, ['Экспорт', 'Импорт']);
  assert.deepEqual(parsed.sections[1].items, ['Фикс CI']);
});
