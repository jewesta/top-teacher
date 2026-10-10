import { commands, type ICommand } from '@uiw/react-md-editor/nohighlight';
import React from 'react';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import type { PluggableList } from 'unified';
import {
  type MarkdownExtension,
  type MarkdownExtensionContext,
  type MarkdownPreviewContribution,
} from './ws-markdown-extensions';
import { markdownBlockPluginsFirst, markdownBlockSanitizeSchema, remarkMarkdownBlocks } from './ws-markdown-blocks';
import './ws-markdown-blocks.css';

export type MarkdownToolbarCommandId = 'IMAGE' | 'FOOTNOTE' | 'ALERT' | 'NUMBERED';

type MarkdownAnchorProperties = React.AnchorHTMLAttributes<HTMLAnchorElement> & {
  node?: unknown;
  'data-footnote-ref'?: unknown;
  'data-footnote-backref'?: unknown;
};

const toolbarCommandNames: Record<MarkdownToolbarCommandId, string[]> = {
  IMAGE: ['image'],
  FOOTNOTE: ['footnote'],
  ALERT: ['alert'],
  NUMBERED: ['numbered'],
};

const alertCommand: ICommand = {
  name: 'alert',
  keyCommand: 'alert',
  buttonProps: { 'aria-label': 'Hinweisblock einfügen...', title: 'Hinweisblock einfügen...' },
  icon: <svg width="12" height="12" viewBox="0 0 16 16" aria-hidden="true">
    <path d="M2 2h12v9H7l-4 3v-3H2z" fill="none" stroke="currentColor" strokeWidth="1.5" />
    <path d="M8 4v3m0 1v1" stroke="currentColor" strokeWidth="1.5" />
  </svg>,
  execute: (_state, api) => api.textArea.dispatchEvent(new CustomEvent('markdown-extension-event', {
    bubbles: true,
    composed: true,
    detail: {
      name: 'alert-requested',
      selectionStart: api.textArea.selectionStart,
      selectionEnd: api.textArea.selectionEnd,
    },
  })),
};

const numberedCommand: ICommand = {
  name: 'numbered',
  keyCommand: 'numbered',
  buttonProps: { 'aria-label': 'Zeilen nummerieren', title: 'Zeilen nummerieren' },
  icon: <svg width="14" height="12" viewBox="0 0 18 16" aria-hidden="true">
    <path d="M7 3h10M7 8h10M7 13h10M1 1h1v4M1 7h2v2H1v2h2M1 12h2v3H1" fill="none"
      stroke="currentColor" strokeWidth="1.2" />
  </svg>,
  execute: (state, api) => {
    const start = state.selection.start === 0 ? 0 : state.text.lastIndexOf('\n', state.selection.start - 1) + 1;
    const lastSelected = state.selection.end > state.selection.start && state.text[state.selection.end - 1] === '\n'
      ? state.selection.end - 1 : state.selection.end;
    const nextNewline = state.text.indexOf('\n', lastSelected);
    const end = nextNewline < 0 ? state.text.length : nextNewline;
    const selected = state.text.slice(start, end) || 'Text';
    const block = `> [!NUMBERED]\n${selected.split('\n').map((line) => `> ${line}`).join('\n')}`;
    const before = start > 0 && state.text[start - 2] !== '\n' ? '\n' : '';
    const after = end < state.text.length && state.text[end + 1] !== '\n' ? '\n' : '';
    api.setSelectionRange({ start, end });
    api.replaceSelection(`${before}${block}${after}`);
    api.setSelectionRange({ start: start + before.length + 16, end: start + before.length + block.length });
  },
};

const footnoteCommand: ICommand = {
  name: 'footnote',
  keyCommand: 'footnote',
  buttonProps: {
    'aria-label': 'Fußnote einfügen...',
    title: 'Fußnote einfügen...',
  },
  icon: <span aria-hidden="true" style={{ fontSize: '1rem', fontWeight: 600 }}>¹</span>,
  execute: (_state, api) => {
    const selectionEnd = api.textArea.selectionEnd;
    api.textArea.dispatchEvent(new CustomEvent('markdown-extension-event', {
      bubbles: true,
      composed: true,
      detail: {
        name: 'footnote-requested',
        selectionEnd,
      },
    }));
  },
};

export function markdownStateIds<T extends string>(stateValue: string): T[] {
  return stateValue
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean) as T[];
}

export function markdownCommands(
  extensions: MarkdownExtension[],
  hiddenToolbarCommands: MarkdownToolbarCommandId[],
): ICommand[] {
  const extensionCommands = [
    ...extensions.flatMap((extension) => extension.editorCommands?.() ?? []),
    footnoteCommand,
    alertCommand,
    numberedCommand,
  ];
  const commandsWithExtensions = extensionCommands.reduceRight(insertBeforeFirstDivider, commands.getCommands());
  return filterCommands(commandsWithExtensions, hiddenCommandNames(hiddenToolbarCommands));
}

export function markdownExtraCommands(hiddenToolbarCommands: MarkdownToolbarCommandId[]): ICommand[] {
  return filterCommands(commands.getExtraCommands(), hiddenCommandNames(hiddenToolbarCommands));
}

export function markdownPreviewOptions(extensions: MarkdownExtension[], context: MarkdownExtensionContext) {
  return markdownPreviewConfiguration(extensions, context).options;
}

export function markdownPreviewConfiguration(extensions: MarkdownExtension[], context: MarkdownExtensionContext) {
  const contributions: MarkdownPreviewContribution[] = extensions.flatMap((extension) =>
    extension.preview ? [extension.preview(context)] : [],
  );
  const schema = contributions.reduce(
    (current, contribution) => contribution.sanitizeSchema?.(current) ?? current,
    markdownBlockSanitizeSchema(defaultSchema),
  );
  return {
    options: {
      pluginsFilter: markdownBlockPluginsFirst,
      remarkPlugins: [remarkMarkdownBlocks,
        ...contributions.flatMap((contribution) => contribution.remarkPlugins ?? [])] as PluggableList,
      rehypePlugins: [
        ...contributions.flatMap((contribution) => contribution.rehypePlugins ?? []),
        [rehypeSanitize, schema],
      ] as PluggableList,
      components: Object.assign(
        { a: MarkdownAnchor, div: MarkdownDiv },
        ...contributions.map((contribution) => contribution.components ?? {}),
      ),
      remarkRehypeOptions: {
        footnoteLabel: 'Fußnoten',
        footnoteBackLabel: 'Zurück zum Text',
      },
    },
    wrap: (content: React.ReactElement): React.ReactElement => contributions.reduceRight(
      (wrapped, contribution) => contribution.wrap?.(wrapped, context) ?? wrapped, content,
    ),
  };
}

type MarkdownDivProperties = React.HTMLAttributes<HTMLDivElement> & { node?: unknown };

function MarkdownDiv({ node: _node, children, ...properties }: MarkdownDivProperties): React.ReactElement {
  return properties.className?.split(' ').includes('ws-markdown-numbered')
    ? <NumberedBlock {...properties}>{children}</NumberedBlock>
    : <div {...properties}>{children}</div>;
}

function NumberedBlock({ children, ...properties }: React.HTMLAttributes<HTMLDivElement>): React.ReactElement {
  const content = React.useRef<HTMLDivElement>(null);
  const [overflowing, setOverflowing] = React.useState<number[]>([]);
  React.useLayoutEffect(() => {
    const element = content.current;
    if (!element) return;
    let disposed = false;
    const measure = () => {
      if (disposed) return;
      const lines = Array.from(element.querySelectorAll<HTMLElement>('.ws-markdown-line-text'));
      const overflow = lines.flatMap((line, index) => line.scrollWidth > line.clientWidth + 1 ? [index + 1] : []);
      setOverflowing((previous) => previous.join(',') === overflow.join(',') ? previous : overflow);
    };
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    document.fonts.ready.then(measure);
    measure();
    return () => { disposed = true; observer.disconnect(); };
  }, [children]);
  return <div {...properties}>
    <div ref={content}>{children}</div>
    {overflowing.length > 0 && <p className="ws-markdown-numbered-overflow" role="status">
      {overflowing.length === 1 ? 'Zeile' : 'Zeilen'} {overflowing.join(', ')}:
      {' '}Text wird rechts abgeschnitten. Füge bei Bedarf einen Zeilenumbruch ein.
    </p>}
  </div>;
}

function MarkdownAnchor(properties: MarkdownAnchorProperties): React.ReactElement | null {
  const isFootnoteReference = Object.prototype.hasOwnProperty.call(properties, 'data-footnote-ref');
  const isFootnoteBackReference = Object.prototype.hasOwnProperty.call(properties, 'data-footnote-backref');
  const {
    node: _node,
    children,
    'data-footnote-ref': _footnoteReference,
    'data-footnote-backref': _footnoteBackReference,
    ...anchorProperties
  } = properties;

  if (isFootnoteBackReference) {
    return null;
  }
  if (isFootnoteReference) {
    return <span>{children}</span>;
  }
  return <a {...anchorProperties}>{children}</a>;
}

function insertBeforeFirstDivider(baseCommands: ICommand[], command: ICommand): ICommand[] {
  const dividerIndex = baseCommands.findIndex((baseCommand) => baseCommand.keyCommand === 'divider');
  if (dividerIndex < 0) {
    return [...baseCommands, command];
  }
  return [...baseCommands.slice(0, dividerIndex), command, ...baseCommands.slice(dividerIndex)];
}

function hiddenCommandNames(hiddenToolbarCommands: MarkdownToolbarCommandId[]): Set<string> {
  return new Set(hiddenToolbarCommands.flatMap((command) => toolbarCommandNames[command] ?? []));
}

function filterCommands(commandsToFilter: ICommand[], hiddenCommandNames: Set<string>): ICommand[] {
  return commandsToFilter.flatMap((command) => {
    if (isHiddenCommand(command, hiddenCommandNames)) {
      return [];
    }
    if ('children' in command && Array.isArray(command.children)) {
      const children = filterCommands(command.children, hiddenCommandNames);
      return children.length > 0 ? [{ ...command, children } as ICommand] : [];
    }
    return [command];
  });
}

function isHiddenCommand(command: ICommand, hiddenCommandNames: Set<string>): boolean {
  return [command.name, command.keyCommand].some((name) => Boolean(name && hiddenCommandNames.has(name)));
}
