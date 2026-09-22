import { commands, type ICommand } from '@uiw/react-md-editor/nohighlight';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import type { PluggableList } from 'unified';
import {
  type MarkdownExtension,
  type MarkdownExtensionContext,
  type MarkdownPreviewContribution,
} from './ws-markdown-extensions';

export type MarkdownToolbarCommandId = 'IMAGE';

const toolbarCommandNames: Record<MarkdownToolbarCommandId, string[]> = {
  IMAGE: ['image'],
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
  const extensionCommands = extensions.flatMap((extension) => extension.editorCommands?.() ?? []);
  const commandsWithExtensions = extensionCommands.reduceRight(insertBeforeFirstDivider, commands.getCommands());
  return filterCommands(commandsWithExtensions, hiddenCommandNames(hiddenToolbarCommands));
}

export function markdownExtraCommands(hiddenToolbarCommands: MarkdownToolbarCommandId[]): ICommand[] {
  return filterCommands(commands.getExtraCommands(), hiddenCommandNames(hiddenToolbarCommands));
}

export function markdownPreviewOptions(extensions: MarkdownExtension[], context: MarkdownExtensionContext) {
  const contributions: MarkdownPreviewContribution[] = extensions.flatMap((extension) =>
    extension.preview ? [extension.preview(context)] : [],
  );
  const schema = contributions.reduce(
    (current, contribution) => contribution.sanitizeSchema?.(current) ?? current,
    defaultSchema,
  );
  return {
    remarkPlugins: contributions.flatMap((contribution) => contribution.remarkPlugins ?? []) as PluggableList,
    rehypePlugins: [
      ...contributions.flatMap((contribution) => contribution.rehypePlugins ?? []),
      [rehypeSanitize, schema],
    ] as PluggableList,
    components: Object.assign({}, ...contributions.map((contribution) => contribution.components ?? {})),
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
