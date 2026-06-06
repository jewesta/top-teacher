import React from 'react';
import {
  commands,
  executeCommand,
  selectWord,
  type ExecuteState,
  type ICommand,
  type TextRange,
  type TextAreaTextApi,
} from '@uiw/react-md-editor/nohighlight';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import type { PluggableList } from 'unified';

type MarkdownNode = {
  type: string;
  value?: string;
  url?: string;
  children?: MarkdownNode[];
  data?: Record<string, unknown>;
};

export type MarkdownTagIdGenerator = 'NEXT_NUMBER';
export type MarkdownTagRenderMode = 'DEFAULT' | 'CHECKBOX';
export type MarkdownToolbarCommandId = 'IMAGE';

export type MarkdownTagOptions = {
  namespace: string;
  toolbarLabel: string;
  idGenerator: MarkdownTagIdGenerator;
};

export type MarkdownOptions = {
  tag?: MarkdownTagOptions;
  tagRenderMode?: MarkdownTagRenderMode;
  checkedTagKeys?: string[];
  hiddenToolbarCommands?: MarkdownToolbarCommandId[];
};

type TagRange = {
  full: TextRange;
  label: TextRange;
};

const defaultTagToolbarLabel = 'Tag markieren';
const numericTagIdPattern = /^[1-9]\d*$/;
const skippedNodeTypes = new Set(['code', 'inlineCode', 'html']);
const toolbarCommandNames: Record<MarkdownToolbarCommandId, string[]> = {
  IMAGE: ['image'],
};

export function markdownStateIds<T extends string>(stateValue: string): T[] {
  return stateValue
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean) as T[];
}

export function markdownTagOptions(
  namespace: string,
  toolbarLabel: string,
  idGenerator: string,
): MarkdownTagOptions | undefined {
  const normalizedNamespace = namespace.trim();
  if (!normalizedNamespace) {
    return undefined;
  }
  return {
    namespace: normalizedNamespace,
    toolbarLabel: toolbarLabel.trim() || defaultTagToolbarLabel,
    idGenerator: idGenerator === 'NEXT_NUMBER' ? idGenerator : 'NEXT_NUMBER',
  };
}

export function markdownTagRenderMode(value: string): MarkdownTagRenderMode {
  return value === 'CHECKBOX' ? 'CHECKBOX' : 'DEFAULT';
}

export function markdownCommands(options: MarkdownOptions): ICommand[] {
  const extensionCommands = options.tag ? [tagCommand(options.tag)] : [];
  const commandsWithExtensions = extensionCommands.reduce(insertBeforeFirstDivider, commands.getCommands());
  return filterCommands(commandsWithExtensions, hiddenCommandNames(options.hiddenToolbarCommands ?? []));
}

export function markdownExtraCommands(options: MarkdownOptions): ICommand[] {
  return filterCommands(commands.getExtraCommands(), hiddenCommandNames(options.hiddenToolbarCommands ?? []));
}

export function markdownPreviewOptions(options: Pick<MarkdownOptions, 'tag' | 'tagRenderMode' | 'checkedTagKeys'>) {
  const tagRenderMode = options.tagRenderMode ?? 'DEFAULT';
  const sanitizeSchema = options.tag ? tagSanitizeSchema(defaultSchema, tagRenderMode) : defaultSchema;

  return {
    remarkPlugins: options.tag
      ? ([remarkTags(options.tag, tagRenderMode, new Set(options.checkedTagKeys ?? []))] as PluggableList)
      : [],
    rehypePlugins: [[rehypeSanitize, sanitizeSchema]] as PluggableList,
  };
}

function tagCommand(tag: MarkdownTagOptions): ICommand {
  return {
    name: 'tag',
    keyCommand: 'tag',
    buttonProps: { 'aria-label': tag.toolbarLabel, title: tag.toolbarLabel },
    icon: (
      <svg width="14" height="14" role="img" viewBox="0 0 16 16">
        <path
          fill="currentColor"
          d="M11.65 1.2 14.8 4.35 6.35 12.8 2.4 13.6 3.2 9.65 11.65 1.2Zm-.7 2.1L4.6 9.65l1.75 1.75 6.35-6.35-1.75-1.75ZM1.5 14.5h13v1h-13v-1Z"
        />
      </svg>
    ),
    execute: (state: ExecuteState, api: TextAreaTextApi) => {
      const taggedRange = tagAtSelection(state.text, state.selection, tag);
      if (taggedRange) {
        unwrapTag(state.text, api, taggedRange, state.selection);
        return;
      }

      const prefix = '[';
      const suffix = `](${tag.namespace}:${nextTagId(state.text, tag)})`;
      const selection = state.selectedText
        ? state.selection
        : selectWord({
            text: state.text,
            selection: state.selection,
            prefix,
            suffix,
          });
      const selectedState = api.setSelectionRange(selection);
      if (selectedState.selectedText.includes('\n')) {
        return;
      }
      executeCommand({
        api,
        selectedText: selectedState.selectedText,
        selection,
        prefix,
        suffix,
      });
    },
  };
}

function tagSanitizeSchema(schema: typeof defaultSchema, tagRenderMode: MarkdownTagRenderMode): typeof defaultSchema {
  const tagNames = [...(schema.tagNames ?? []), 'mark', 'span'];
  if (tagRenderMode === 'CHECKBOX') {
    tagNames.push('input');
  }

  return {
    ...schema,
    tagNames,
    attributes: {
      ...schema.attributes,
      input: [
        ...(schema.attributes?.input ?? []),
        ['className', 'ws-markdown-tag-checkbox'],
        ['type', 'checkbox'],
        'defaultChecked',
        'value',
      ],
      mark: [...(schema.attributes?.mark ?? []), ['className', 'ws-markdown-tag-highlight']],
      span: [
        ...(schema.attributes?.span ?? []),
        ['className', 'ws-markdown-tag', 'ws-markdown-tag-badge'],
      ],
    },
  };
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

function tagAtSelection(markdown: string, selection: TextRange, tag: MarkdownTagOptions): TagRange | null {
  for (const match of markdown.matchAll(tagMarkdownPattern(tag))) {
    const fullStart = match.index ?? 0;
    const labelText = match[1] ?? '';
    const labelStart = fullStart + 1;
    const labelEnd = labelStart + labelText.length;
    const fullEnd = fullStart + match[0].length;

    if (isSelectionInside(selection, { start: labelStart, end: labelEnd })
        || isSameSelection(selection, { start: fullStart, end: fullEnd })) {
      return {
        full: { start: fullStart, end: fullEnd },
        label: { start: labelStart, end: labelEnd },
      };
    }
  }
  return null;
}

function isSelectionInside(selection: TextRange, range: TextRange): boolean {
  return selection.start >= range.start && selection.end <= range.end;
}

function isSameSelection(selection: TextRange, range: TextRange): boolean {
  return selection.start === range.start && selection.end === range.end;
}

function unwrapTag(markdown: string, api: TextAreaTextApi, tag: TagRange, originalSelection: TextRange) {
  const labelText = markdown.slice(tag.label.start, tag.label.end);
  const preserveInnerSelection = isSelectionInside(originalSelection, tag.label);
  const selectionStart = preserveInnerSelection ? originalSelection.start - tag.label.start : 0;
  const selectionEnd = preserveInnerSelection ? originalSelection.end - tag.label.start : labelText.length;

  api.setSelectionRange(tag.full);
  api.replaceSelection(labelText);
  api.setSelectionRange({
    start: tag.full.start + selectionStart,
    end: tag.full.start + selectionEnd,
  });
}

function remarkTags(
  tag: MarkdownTagOptions,
  tagRenderMode: MarkdownTagRenderMode,
  checkedTagKeys: Set<string>,
) {
  return () => (tree: MarkdownNode) => {
    transformTags(tree, tag, tagRenderMode, checkedTagKeys);
  };
}

function transformTags(
  node: MarkdownNode | undefined,
  tag: MarkdownTagOptions,
  tagRenderMode: MarkdownTagRenderMode,
  checkedTagKeys: Set<string>,
) {
  if (!node?.children || skippedNodeTypes.has(node.type)) {
    return;
  }

  node.children = node.children.flatMap((child) => {
    const tagId = tagIdFromNode(child, tag);
    if (tagId) {
      return [tagNode(tagId, child.children ?? [], tagRenderMode, checkedTagKeys.has(tagId))];
    }
    transformTags(child, tag, tagRenderMode, checkedTagKeys);
    return [child];
  });
}

function tagIdFromNode(node: MarkdownNode, tag: MarkdownTagOptions): string | null {
  if (node.type !== 'link' || typeof node.url !== 'string') {
    return null;
  }
  const match = tagUrlExactPattern(tag).exec(node.url.trim());
  return match ? match[1] : null;
}

function tagNode(
  id: string,
  children: MarkdownNode[],
  tagRenderMode: MarkdownTagRenderMode,
  checked: boolean,
): MarkdownNode {
  return {
    type: 'wsMarkdownTag',
    data: {
      hName: 'span',
      hProperties: {
        className: ['ws-markdown-tag'],
      },
    },
    children: [
      {
        type: 'wsMarkdownTagHighlight',
        data: {
          hName: 'mark',
          hProperties: {
            className: ['ws-markdown-tag-highlight'],
          },
        },
        children,
      },
      {
        type: 'wsMarkdownTagBadge',
        data: {
          hName: 'span',
          hProperties: {
            className: ['ws-markdown-tag-badge'],
          },
        },
        children: [{ type: 'text', value: id }],
      },
      ...(tagRenderMode === 'CHECKBOX' ? [tagCheckboxNode(id, checked)] : []),
    ],
  };
}

function tagCheckboxNode(id: string, checked: boolean): MarkdownNode {
  return {
    type: 'wsMarkdownTagCheckbox',
    data: {
      hName: 'input',
      hProperties: {
        className: ['ws-markdown-tag-checkbox'],
        type: 'checkbox',
        defaultChecked: checked,
        value: id,
      },
    },
  };
}

function nextTagId(markdown: string, tag: MarkdownTagOptions): string {
  switch (tag.idGenerator) {
    case 'NEXT_NUMBER':
      return String(nextNumberTagId(markdown, tag));
    default:
      return '1';
  }
}

function nextNumberTagId(markdown: string, tag: MarkdownTagOptions): number {
  let nextId = 1;
  for (const match of markdown.matchAll(tagUrlPattern(tag))) {
    const value = match[1] ?? '';
    if (!numericTagIdPattern.test(value)) {
      continue;
    }
    const id = Number.parseInt(value, 10);
    if (id >= nextId) {
      nextId = id + 1;
    }
  }
  return nextId;
}

function tagUrlPattern(tag: MarkdownTagOptions): RegExp {
  return new RegExp(`\\(${escapedNamespace(tag)}:([^\\s)]+)\\)`, 'g');
}

function tagUrlExactPattern(tag: MarkdownTagOptions): RegExp {
  return new RegExp(`^${escapedNamespace(tag)}:([^\\s)]+)$`);
}

function tagMarkdownPattern(tag: MarkdownTagOptions): RegExp {
  return new RegExp(`\\[([^\\]\\n]*)\\]\\(${escapedNamespace(tag)}:[^\\s)]+\\)`, 'g');
}

function escapedNamespace(tag: MarkdownTagOptions): string {
  return escapeRegExp(tag.namespace);
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
