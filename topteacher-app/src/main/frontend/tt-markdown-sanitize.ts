import type { Schema } from 'hast-util-sanitize';

type SanitizeAttribute = NonNullable<Schema['attributes']>[string][number];

export function allowClassNames(
  definitions: SanitizeAttribute[] | null | undefined,
  ...classNames: string[]
): SanitizeAttribute[] {
  const existing = definitions ?? [];
  if (existing.includes('className')) {
    return [...existing];
  }

  const allowedValues = existing.flatMap((definition) =>
    Array.isArray(definition) && definition[0] === 'className' ? definition.slice(1) : []);
  const otherDefinitions = existing.filter((definition) =>
    !Array.isArray(definition) || definition[0] !== 'className');
  return [
    ['className', ...allowedValues, ...classNames] as SanitizeAttribute,
    ...otherDefinitions,
  ];
}
