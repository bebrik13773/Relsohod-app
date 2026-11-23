import test from 'node:test';
import assert from 'node:assert/strict';
import {
  ANDROID_RELEASES_PAGE_URL,
  ANDROID_RELEASE_APK_URL,
  FEEDBACK_EMAIL,
  buildFeedbackMailtoUrl,
} from './feedback';

test('feedback and Android APK download constants point to bober@craft-bober.ru and latest release', () => {
  assert.equal(FEEDBACK_EMAIL, 'bober@craft-bober.ru');
  assert.equal(
    ANDROID_RELEASE_APK_URL,
    'https://github.com/bebrik13773/Reslohid/releases/latest/download/Relsokhod-release.apk'
  );
  assert.equal(
    ANDROID_RELEASES_PAGE_URL,
    'https://github.com/bebrik13773/Reslohid/releases/latest'
  );
});

test('buildFeedbackMailtoUrl formats bug and suggestion mailto links cleanly', () => {
  const bugUrl = buildFeedbackMailtoUrl({
    kind: 'bug',
    message: 'Кнопка не нажалась',
    appVersion: '2.3.1',
    cityLabel: 'Санкт-Петербург',
    platformLabel: 'Веб-версия',
    environmentDetails: '1440x900',
  });

  assert.ok(bugUrl.startsWith('mailto:bober@craft-bober.ru?subject='));
  const decodedBug = decodeURIComponent(bugUrl);
  assert.ok(decodedBug.includes('[Рельсоход] Сообщение об ошибке'));
  assert.ok(decodedBug.includes('Кнопка не нажалась'));
  assert.ok(decodedBug.includes('Версия: 2.3.1'));
  assert.ok(decodedBug.includes('Город: Санкт-Петербург'));
  assert.ok(decodedBug.includes('Окружение: 1440x900'));

  const ideaUrl = buildFeedbackMailtoUrl({
    kind: 'suggestion',
    appVersion: '2.3.1',
    cityLabel: 'Омск',
  });
  const decodedIdea = decodeURIComponent(ideaUrl);
  assert.ok(decodedIdea.includes('[Рельсоход] Предложение по улучшению'));
  assert.ok(decodedIdea.includes('Тип: Предложение'));
  assert.ok(decodedIdea.includes('Город: Омск'));
});
