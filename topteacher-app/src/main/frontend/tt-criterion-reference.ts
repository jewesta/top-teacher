export const CRITERION_NAMESPACE = 'crit';
export const CRITERION_POINTS_PARAMETER = 'points';

export type CriterionTarget = {
  key: string;
  pointValue?: string;
  validParameters: boolean;
  valueRange?: { start: number; end: number };
};

export function parseCriterionTarget(target: string): CriterionTarget {
  const queryStart = target.indexOf('?');
  const key = queryStart < 0 ? target : target.slice(0, queryStart);
  if (queryStart < 0) {
    return { key, validParameters: true };
  }

  const query = target.slice(queryStart + 1);
  const parts = query.split('&');
  const separator = query.indexOf('=');
  if (parts.length !== 1 || separator < 0 || query.includes('#')) {
    return { key, validParameters: false };
  }
  try {
    const name = decodeQueryValue(query.slice(0, separator));
    if (name !== CRITERION_POINTS_PARAMETER) {
      return { key, validParameters: false };
    }
    return {
      key,
      pointValue: decodeQueryValue(query.slice(separator + 1)).trim(),
      validParameters: true,
      valueRange: { start: queryStart + separator + 2, end: target.length },
    };
  } catch {
    return { key, validParameters: false };
  }
}

export function criterionTarget(key: string, pointValue: string, custom: boolean): string {
  return !custom && Number(pointValue.replace(',', '.')) === 1
    ? key
    : `${key}?${CRITERION_POINTS_PARAMETER}=${pointValue}`;
}

function decodeQueryValue(value: string): string {
  return decodeURIComponent(value.replace(/\+/g, ' '));
}
