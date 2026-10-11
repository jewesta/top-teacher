export type MaterialImageTarget = {
  id: string;
  width?: string;
  alignment: 'left' | 'center' | 'right';
};

const targetPattern = /^img:([a-z0-9]{8})(?:\?([^\s]+))?$/;

export function parseMaterialImageTarget(value: string): MaterialImageTarget | undefined {
  const match = targetPattern.exec(value);
  if (!match) return undefined;
  let width: string | undefined;
  let alignment: MaterialImageTarget['alignment'] = 'left';
  let alignmentSpecified = false;
  for (const parameter of match[2]?.split('&') ?? []) {
    const pair = parameter.split('=');
    if (pair.length !== 2) return undefined;
    if (pair[0] === 'width' && !width && /^(?:100|[1-9][0-9]?)$/.test(pair[1])) {
      width = pair[1];
    } else if (pair[0] === 'align' && !alignmentSpecified
      && (pair[1] === 'left' || pair[1] === 'center' || pair[1] === 'right')) {
      alignment = pair[1];
      alignmentSpecified = true;
    } else {
      return undefined;
    }
  }
  return width ? { id: match[1], width, alignment } : { id: match[1], alignment };
}
