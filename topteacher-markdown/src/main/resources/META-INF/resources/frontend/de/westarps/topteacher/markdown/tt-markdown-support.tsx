import React from 'react';
import {
  commands,
  executeCommand,
  selectWord,
  type ExecuteState,
  type ICommand,
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

export type MarkdownExtensionId = 'EH_CRITERIA';
export type MarkdownToolbarCommandId = 'IMAGE';

export type TopTeacherMarkdownOptions = {
  extensions: MarkdownExtensionId[];
  hiddenToolbarCommands?: MarkdownToolbarCommandId[];
};

type TopTeacherMarkdownExtension = {
  id: MarkdownExtensionId;
  command?: ICommand;
  remarkPlugins?: PluggableList;
  extendSanitizeSchema?: (schema: typeof defaultSchema) => typeof defaultSchema;
};

const criterionUrlPrefix = 'eh:';
const criterionUrlPattern = /\(eh:([1-9]\d*)\)/g;
const criterionUrlExactPattern = /^eh:([1-9]\d*)$/;
const skippedNodeTypes = new Set(['code', 'inlineCode', 'html']);
const toolbarCommandNames: Record<MarkdownToolbarCommandId, string[]> = {
  IMAGE: ['image'],
};

const criterionCommand: ICommand = {
  name: 'criterion',
  keyCommand: 'criterion',
  buttonProps: { 'aria-label': 'Kriterium markieren', title: 'Kriterium markieren' },
  icon: (
    <svg width="14" height="14" role="img" viewBox="0 0 16 16">
      <path
        fill="currentColor"
        d="M11.65 1.2 14.8 4.35 6.35 12.8 2.4 13.6 3.2 9.65 11.65 1.2Zm-.7 2.1L4.6 9.65l1.75 1.75 6.35-6.35-1.75-1.75ZM1.5 14.5h13v1h-13v-1Z"
      />
    </svg>
  ),
  execute: (state: ExecuteState, api: TextAreaTextApi) => {
    const prefix = '[';
    const suffix = `](${criterionUrlPrefix}${nextCriterionId(state.text)})`;
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

const ehCriteriaExtension: TopTeacherMarkdownExtension = {
  id: 'EH_CRITERIA',
  command: criterionCommand,
  remarkPlugins: [remarkCriteria] as PluggableList,
  extendSanitizeSchema: (schema) => ({
    ...schema,
    tagNames: [...(schema.tagNames ?? []), 'mark', 'span'],
    attributes: {
      ...schema.attributes,
      mark: [...(schema.attributes?.mark ?? []), ['className', 'tt-criterion-highlight']],
      span: [...(schema.attributes?.span ?? []), ['className', 'tt-criterion', 'tt-criterion-badge']],
    },
  }),
};

const extensionsById = new Map<MarkdownExtensionId, TopTeacherMarkdownExtension>([
  [ehCriteriaExtension.id, ehCriteriaExtension],
]);

export function markdownStateIds<T extends string>(stateValue: string): T[] {
  return stateValue
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean) as T[];
}

export function topTeacherMarkdownCommands(options: TopTeacherMarkdownOptions): ICommand[] {
  const extensionCommands = enabledExtensions(options.extensions)
    .map((extension) => extension.command)
    .filter((command): command is ICommand => Boolean(command));
  const commandsWithExtensions = extensionCommands.reduce(insertBeforeFirstDivider, commands.getCommands());
  return filterCommands(commandsWithExtensions, hiddenCommandNames(options.hiddenToolbarCommands ?? []));
}

export function topTeacherMarkdownExtraCommands(options: TopTeacherMarkdownOptions): ICommand[] {
  return filterCommands(commands.getExtraCommands(), hiddenCommandNames(options.hiddenToolbarCommands ?? []));
}

export function topTeacherMarkdownPreviewOptions(options: Pick<TopTeacherMarkdownOptions, 'extensions'>) {
  const extensions = enabledExtensions(options.extensions);
  const remarkPlugins = extensions.flatMap((extension) => extension.remarkPlugins ?? []) as PluggableList;
  const sanitizeSchema = extensions.reduce(
    (schema, extension) => extension.extendSanitizeSchema?.(schema) ?? schema,
    defaultSchema,
  );

  return {
    remarkPlugins,
    rehypePlugins: [[rehypeSanitize, sanitizeSchema]] as PluggableList,
  };
}

function enabledExtensions(ids: MarkdownExtensionId[]): TopTeacherMarkdownExtension[] {
  const uniqueIds = new Set(ids);
  return [...uniqueIds]
    .map((id) => extensionsById.get(id))
    .filter((extension): extension is TopTeacherMarkdownExtension => Boolean(extension));
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

function remarkCriteria() {
  return (tree: MarkdownNode) => {
    transformCriteria(tree);
  };
}

function transformCriteria(node: MarkdownNode) {
  if (!node.children || skippedNodeTypes.has(node.type)) {
    return;
  }

  node.children = node.children.flatMap((child) => {
    const criterionId = ehCriterionId(child);
    if (criterionId) {
      return [criterionNode(criterionId, child.children ?? [])];
    }
    transformCriteria(child);
    return [child];
  });
}

function ehCriterionId(node: MarkdownNode): string | null {
  if (node.type !== 'link' || typeof node.url !== 'string') {
    return null;
  }
  const match = criterionUrlExactPattern.exec(node.url.trim());
  return match ? match[1] : null;
}

function criterionNode(id: string, children: MarkdownNode[]): MarkdownNode {
  return {
    type: 'ttCriterion',
    data: {
      hName: 'span',
      hProperties: {
        className: ['tt-criterion'],
      },
    },
    children: [
      {
        type: 'ttCriterionHighlight',
        data: {
          hName: 'mark',
          hProperties: {
            className: ['tt-criterion-highlight'],
          },
        },
        children,
      },
      {
        type: 'ttCriterionBadge',
        data: {
          hName: 'span',
          hProperties: {
            className: ['tt-criterion-badge'],
          },
        },
        children: [{ type: 'text', value: id }],
      },
    ],
  };
}

function nextCriterionId(markdown: string): number {
  let nextId = 1;
  for (const match of markdown.matchAll(criterionUrlPattern)) {
    const id = Number.parseInt(match[1], 10);
    if (id >= nextId) {
      nextId = id + 1;
    }
  }
  return nextId;
}
