export const FEEDBACK_EMAIL = 'bober@craft-bober.ru';
export const ANDROID_RELEASE_APK_URL =
  'https://github.com/bebrik13773/Reslohid/releases/latest/download/Relsokhod-release.apk';
export const ANDROID_RELEASES_PAGE_URL =
  'https://github.com/bebrik13773/Reslohid/releases/latest';

export type FeedbackKind = 'bug' | 'suggestion';

export interface FeedbackMailtoParams {
  kind: FeedbackKind;
  message?: string;
  appVersion: string;
  cityLabel: string;
  platformLabel?: string;
  environmentDetails?: string;
}

export function buildFeedbackMailtoUrl(params: FeedbackMailtoParams): string {
  const {
    kind,
    message = '',
    appVersion,
    cityLabel,
    platformLabel = 'Веб-версия',
    environmentDetails,
  } = params;

  const subject =
    kind === 'bug'
      ? '[Рельсоход] Сообщение об ошибке'
      : '[Рельсоход] Предложение по улучшению';

  const trimmedMessage = message.trim();
  const promptPlaceholder =
    kind === 'bug'
      ? 'Опишите, что произошло и на каком шаге возникла ошибка:'
      : 'Опишите вашу идею или предложение:';

  const bodyLines = [
    trimmedMessage.length > 0 ? trimmedMessage : promptPlaceholder,
    '',
    '---',
    `Тип: ${kind === 'bug' ? 'Ошибка' : 'Предложение'}`,
    `Платформа: ${platformLabel}`,
    `Версия: ${appVersion}`,
    `Город: ${cityLabel}`,
  ];

  if (environmentDetails && environmentDetails.trim().length > 0) {
    bodyLines.push(`Окружение: ${environmentDetails.trim()}`);
  }

  const encodedSubject = encodeURIComponent(subject);
  const encodedBody = encodeURIComponent(bodyLines.join('\n'));
  return `mailto:${FEEDBACK_EMAIL}?subject=${encodedSubject}&body=${encodedBody}`;
}
