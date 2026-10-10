import type { PluggableList, Plugin } from 'unified';
import { defaultSchema } from 'rehype-sanitize';

type MarkdownNode = {
  type: string;
  value?: string;
  children?: MarkdownNode[];
  position?: { start: { line: number; offset: number }; end: { line: number; offset: number } };
  data?: Record<string, unknown>;
};

export const markdownAlertTypes = {
  NOTE: { label: 'Hinweis' },
  TIP: { label: 'Tipp' },
  IMPORTANT: { label: 'Wichtig' },
  WARNING: { label: 'Warnung' },
  CAUTION: { label: 'Vorsicht' },
} as const;

const markerPattern = /^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION|NUMBERED)\][ \t]*(?:\n|$)/;

export const remarkMarkdownBlocks: Plugin = function () {
  return (tree, file) => {
    const root = tree as unknown as MarkdownNode;
    const source = String(file.value);
    const definitions: string[] = [];
    collectDefinitions(root, source, definitions);
    transformBlocks(root, source.split(/\r\n|\n|\r/), definitions.join('\n\n'),
      (value) => this.parse(value) as unknown as MarkdownNode);
  };
};

/* The preview library prepends its own alert parser, so our renderer must run first. */
export function markdownBlockPluginsFirst(type: 'remark' | 'rehype', plugins: PluggableList): PluggableList {
  return type === 'remark'
    ? [remarkMarkdownBlocks, ...plugins.filter((plugin) => plugin !== remarkMarkdownBlocks)]
    : plugins;
}

function collectDefinitions(node: MarkdownNode, source: string, definitions: string[]): void {
  if ((node.type === 'definition' || node.type === 'footnoteDefinition') && node.position) {
    definitions.push(source.slice(node.position.start.offset, node.position.end.offset));
  }
  node.children?.forEach((child) => collectDefinitions(child, source, definitions));
}

function transformBlocks(
  node: MarkdownNode,
  sourceLines: string[],
  definitions: string,
  parse: (value: string) => MarkdownNode,
): void {
  node.children?.forEach((child) => {
    if (child.type !== 'blockquote') {
      transformBlocks(child, sourceLines, definitions, parse);
      return;
    }
    const paragraph = child.children?.[0];
    const first = paragraph?.children?.[0];
    const marker = paragraph?.type === 'paragraph' && first?.type === 'text'
      ? markerPattern.exec(first.value ?? '') : null;
    if (!marker || !first || !paragraph) {
      transformBlocks(child, sourceLines, definitions, parse);
      return;
    }
    if (!marker[0].endsWith('\n') && paragraph.children!.length > 1
      && paragraph.children![1].type !== 'break') {
      return;
    }
    if (marker[1] === 'NUMBERED') {
      if (node.type === 'root') numberedBlock(child, sourceLines, definitions, parse);
      return;
    }
    const type = marker[1] as keyof typeof markdownAlertTypes;
    const alert = markdownAlertTypes[type];
    first.value = (first.value ?? '').slice(marker[0].length);
    if (!first.value) paragraph.children!.shift();
    if (paragraph.children?.[0]?.type === 'break') paragraph.children.shift();
    if (!paragraph.children?.length) child.children!.shift();
    child.data = htmlData('div', ['ws-markdown-alert', `ws-markdown-alert-${type.toLowerCase()}`]);
    child.children!.unshift({
      type: 'paragraph',
      data: htmlData('div', ['ws-markdown-alert-title']),
      children: [
        {
          type: 'paragraph',
          data: { hName: 'span', hProperties: { className: ['ws-markdown-alert-icon'], ariaHidden: 'true' } },
          children: [],
        },
        { type: 'text', value: alert.label },
      ],
    });
    child.children!.forEach((content) => transformBlocks(content, sourceLines, definitions, parse));
  });
}

function numberedBlock(
  node: MarkdownNode,
  sourceLines: string[],
  definitions: string,
  parse: (value: string) => MarkdownNode,
): void {
  if (!node.position) return;
  const lines = sourceLines.slice(node.position.start.line, node.position.end.line);
  /* Prefixing each source line prevents list/heading syntax from turning one line into a block. */
  const prefix = 'WSLINE ';
  node.children = lines.map((line, index) => {
    const lineNumber = index + 1;
    const content = line.replace(/^ {0,3}>[ \t]?/, '');
    const parsed = parse(`${prefix}${content}\n\n${definitions}`);
    const inline = parsed.children?.[0]?.children ?? [];
    if (inline[0]?.type === 'text') inline[0].value = inline[0].value?.slice(prefix.length);
    return {
      type: 'paragraph',
      data: htmlData('div', ['ws-markdown-numbered-line']),
      children: [
        {
          type: 'paragraph',
          data: htmlData('span', ['ws-markdown-line-number']),
          children: [{ type: 'text', value: lineNumber === 1 || lineNumber % 5 === 0 ? String(lineNumber) : '' }],
        },
        {
          type: 'paragraph',
          data: htmlData('span', ['ws-markdown-line-text']),
          children: inline,
        },
      ],
    };
  });
  node.data = htmlData('div', ['ws-markdown-numbered']);
}

function htmlData(hName: string, className: string[]) {
  return { hName, hProperties: { className } };
}

export function markdownBlockSanitizeSchema(schema: typeof defaultSchema): typeof defaultSchema {
  return {
    ...schema,
    attributes: {
      ...schema.attributes,
      div: [...(schema.attributes?.div ?? []), ['className',
        'ws-markdown-alert', 'ws-markdown-alert-note', 'ws-markdown-alert-tip',
        'ws-markdown-alert-important', 'ws-markdown-alert-warning', 'ws-markdown-alert-caution',
        'ws-markdown-alert-title', 'ws-markdown-numbered', 'ws-markdown-numbered-line']],
      span: [...(schema.attributes?.span ?? []), 'ariaHidden', ['className',
        'ws-markdown-alert-icon', 'ws-markdown-line-number', 'ws-markdown-line-text']],
    },
  };
}
