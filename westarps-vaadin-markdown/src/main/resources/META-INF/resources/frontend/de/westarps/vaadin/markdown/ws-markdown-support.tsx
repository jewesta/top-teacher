import React, { type MouseEvent, type ReactElement } from 'react';
import {
  commands,
  executeCommand,
  getSurroundingWord,
  selectWord,
  type ExecuteState,
  type ICommand,
  type TextState,
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

export type MarkdownTagValueOption = {
  value: string;
  label: string;
};

export type MarkdownTagValueSelectorOptions = {
  options: MarkdownTagValueOption[];
  defaultValue: string;
  separator: string;
  toolbarIconText: string;
  customOptionLabel: string;
  customOptionAriaLabel: string;
  customPlaceholder: string;
  customValuePattern: string;
  removeLabel: string;
};

export type MarkdownTagOptions = {
  namespace: string;
  toolbarLabel: string;
  idGenerator: MarkdownTagIdGenerator;
  valueSelector?: MarkdownTagValueSelectorOptions;
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
  target: TextRange;
  key: TextRange;
  value?: TextRange;
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
  valueOptionValues: string[] = [],
  valueOptionLabels: string[] = [],
  valueDefault = '',
  valueSeparator = '',
  valueToolbarIconText = '',
  valueCustomOptionLabel = '',
  valueCustomOptionAriaLabel = '',
  valueCustomPlaceholder = '',
  valueCustomPattern = '',
  valueRemoveLabel = '',
): MarkdownTagOptions | undefined {
  const normalizedNamespace = namespace.trim();
  if (!normalizedNamespace) {
    return undefined;
  }
  return {
    namespace: normalizedNamespace,
    toolbarLabel: toolbarLabel.trim() || defaultTagToolbarLabel,
    idGenerator: idGenerator === 'NEXT_NUMBER' ? idGenerator : 'NEXT_NUMBER',
    valueSelector: markdownTagValueSelectorOptions(
      valueOptionValues,
      valueOptionLabels,
      valueDefault,
      valueSeparator,
      valueToolbarIconText,
      valueCustomOptionLabel,
      valueCustomOptionAriaLabel,
      valueCustomPlaceholder,
      valueCustomPattern,
      valueRemoveLabel,
    ),
  };
}

function markdownTagValueSelectorOptions(
  values: string[],
  labels: string[],
  defaultValue: string,
  separator: string,
  toolbarIconText: string,
  customOptionLabel: string,
  customOptionAriaLabel: string,
  customPlaceholder: string,
  customValuePattern: string,
  removeLabel: string,
): MarkdownTagValueSelectorOptions | undefined {
  if (values.length === 0 || values.length !== labels.length || !defaultValue || !separator || !customValuePattern) {
    return undefined;
  }
  return {
    options: values.map((value, index) => ({ value, label: labels[index] ?? value })),
    defaultValue,
    separator,
    toolbarIconText,
    customOptionLabel,
    customOptionAriaLabel,
    customPlaceholder,
    customValuePattern,
    removeLabel,
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
  return tag.valueSelector ? tagValueSelectorCommand(tag, tag.valueSelector) : simpleTagCommand(tag);
}

function simpleTagCommand(tag: MarkdownTagOptions): ICommand {
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

function tagValueSelectorCommand(
  tag: MarkdownTagOptions,
  selector: MarkdownTagValueSelectorOptions,
): ICommand {
  return {
    name: 'tag',
    keyCommand: 'group',
    groupName: 'tag',
    buttonProps: { 'aria-label': tag.toolbarLabel, title: tag.toolbarLabel },
    icon: (
      <span className="ws-markdown-tag-command-icon" aria-hidden="true">
        {selector.toolbarIconText}
      </span>
    ),
    execute: () => {},
    children: ({ textApi, close }) => (
      <TagValueSelectorPanel tag={tag} textApi={textApi} close={close} />
    ),
  };
}

type TagValueSelectorPanelProps = {
  tag: MarkdownTagOptions;
  textApi?: TextAreaTextApi;
  close: () => void;
};

function TagValueSelectorPanel({ tag, textApi, close }: TagValueSelectorPanelProps): ReactElement {
  const selector = tag.valueSelector!;
  const state = textApi ? textState(textApi) : null;
  const existingTag = state ? tagAtSelection(state.text, state.selection, tag) : null;
  const applicable = Boolean(state && textApi && (existingTag || applicableSelection(state)));
  const currentValue = state && existingTag ? tagValue(state.text, existingTag, selector) : undefined;
  const customSelected = Boolean(
    existingTag && currentValue && !selector.options.some((option) => equivalentValue(option.value, currentValue)),
  );

  const choose = (value: string, custom: boolean) => (event: MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    if (!textApi) {
      return;
    }
    const currentState = textState(textApi);
    applyTagValue(currentState, textApi, tag, value, custom);
    close();
  };

  const remove = (event: MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    if (!state || !textApi || !existingTag) {
      return;
    }
    unwrapTag(state.text, textApi, existingTag, state.selection);
    close();
  };

  return (
    <div className="ws-markdown-tag-value-selector" role="group" aria-label={tag.toolbarLabel}>
      <div className="ws-markdown-tag-value-options">
        <span className="ws-markdown-tag-value-spacer" aria-hidden="true" />
        {selector.options.map((option) => (
          <button
            type="button"
            key={option.value}
            disabled={!applicable}
            aria-pressed={Boolean(currentValue && equivalentValue(currentValue, option.value))}
            onMouseDown={(event) => event.preventDefault()}
            onClick={choose(option.value, false)}
          >
            {option.label}
          </button>
        ))}
        <button
          type="button"
          className="ws-markdown-tag-custom-value"
          disabled={!applicable}
          aria-label={selector.customOptionAriaLabel}
          aria-pressed={customSelected}
          title={selector.customOptionAriaLabel}
          onMouseDown={(event) => event.preventDefault()}
          onClick={choose(selector.customPlaceholder, true)}
        >
          {selector.customOptionLabel}
        </button>
      </div>
      {!applicable && <span className="ws-markdown-tag-selection-hint">Text markieren</span>}
      {existingTag && (
        <button
          type="button"
          className="ws-markdown-tag-remove"
          onMouseDown={(event) => event.preventDefault()}
          onClick={remove}
        >
          {selector.removeLabel}
        </button>
      )}
    </div>
  );
}

function applicableSelection(state: TextState): boolean {
  const selection = effectiveSelection(state);
  const selectedText = state.text.slice(selection.start, selection.end);
  return Boolean(selectedText.trim()) && !selectedText.includes('\n') && !containsMarkdownLink(selectedText);
}

function effectiveSelection(state: TextState): TextRange {
  return state.selection.start === state.selection.end
    ? getSurroundingWord(state.text, state.selection.start)
    : state.selection;
}

function containsMarkdownLink(value: string): boolean {
  return /\[[^\]\n]*]\([^\s)]+\)/.test(value);
}

function applyTagValue(
  state: TextState,
  api: TextAreaTextApi,
  tag: MarkdownTagOptions,
  chosenValue: string,
  custom: boolean,
) {
  const selector = tag.valueSelector!;
  const existingTag = tagAtSelection(state.text, state.selection, tag);
  if (existingTag) {
    const existingValue = tagValue(state.text, existingTag, selector);
    if (custom && existingTag.value && existingValue && !selector.options.some(
      (option) => equivalentValue(option.value, existingValue),
    )) {
      api.setSelectionRange(existingTag.value);
      return;
    }
    replaceTagTarget(api, existingTag, tagTarget(
      state.text.slice(existingTag.key.start, existingTag.key.end),
      chosenValue,
      selector,
      custom,
    ));
    if (custom) {
      selectCurrentTagValue(api, tag);
    }
    return;
  }

  const selection = effectiveSelection(state);
  const selectedText = state.text.slice(selection.start, selection.end);
  if (!selectedText.trim() || selectedText.includes('\n') || containsMarkdownLink(selectedText)) {
    api.setSelectionRange(state.selection);
    return;
  }

  const key = nextTagId(state.text, tag);
  api.setSelectionRange(selection);
  executeCommand({
    api,
    selectedText,
    selection,
    prefix: '[',
    suffix: `](${tag.namespace}:${tagTarget(key, chosenValue, selector, custom)})`,
  });
  if (custom) {
    selectCurrentTagValue(api, tag);
  }
}

function tagTarget(
  key: string,
  chosenValue: string,
  selector: MarkdownTagValueSelectorOptions,
  custom: boolean,
): string {
  if (!custom && equivalentValue(chosenValue, selector.defaultValue)) {
    return key;
  }
  return `${key}${selector.separator}${chosenValue}`;
}

function replaceTagTarget(api: TextAreaTextApi, tag: TagRange, target: string) {
  const preserveInnerSelection = isSelectionInside(
    { start: api.textArea.selectionStart, end: api.textArea.selectionEnd },
    tag.label,
  );
  const selectionStart = preserveInnerSelection ? api.textArea.selectionStart - tag.label.start : 0;
  const selectionEnd = preserveInnerSelection ? api.textArea.selectionEnd - tag.label.start : tag.label.end - tag.label.start;
  api.setSelectionRange(tag.target);
  api.replaceSelection(target);
  api.setSelectionRange({
    start: tag.label.start + selectionStart,
    end: tag.label.start + selectionEnd,
  });
}

function selectCurrentTagValue(api: TextAreaTextApi, tag: MarkdownTagOptions) {
  const state = textState(api);
  const currentTag = tagAtSelection(state.text, state.selection, tag);
  if (currentTag?.value) {
    api.setSelectionRange(currentTag.value);
  }
}

function textState(api: TextAreaTextApi): TextState {
  const selection = { start: api.textArea.selectionStart, end: api.textArea.selectionEnd };
  return {
    text: api.textArea.value,
    selection,
    selectedText: api.textArea.value.slice(selection.start, selection.end),
  };
}

function tagValue(
  markdown: string,
  tag: TagRange,
  selector: MarkdownTagValueSelectorOptions,
): string {
  return tag.value ? markdown.slice(tag.value.start, tag.value.end) : selector.defaultValue;
}

function equivalentValue(left: string, right: string): boolean {
  return left === right || left.replace(',', '.') === right.replace(',', '.');
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
    const targetText = match[2] ?? '';
    const labelStart = fullStart + 1;
    const labelEnd = labelStart + labelText.length;
    const fullEnd = fullStart + match[0].length;
    const targetStart = fullStart + match[0].lastIndexOf(`${tag.namespace}:`) + tag.namespace.length + 1;
    const targetEnd = targetStart + targetText.length;
    const separatorIndex = tag.valueSelector ? targetText.indexOf(tag.valueSelector.separator) : -1;
    const keyEnd = separatorIndex < 0 ? targetEnd : targetStart + separatorIndex;
    const valueStart = separatorIndex < 0 ? -1 : keyEnd + tag.valueSelector!.separator.length;

    if (isSelectionInsideTag(selection, { start: fullStart, end: fullEnd })
        || isSameSelection(selection, { start: fullStart, end: fullEnd })) {
      return {
        full: { start: fullStart, end: fullEnd },
        label: { start: labelStart, end: labelEnd },
        target: { start: targetStart, end: targetEnd },
        key: { start: targetStart, end: keyEnd },
        value: valueStart < 0 ? undefined : { start: valueStart, end: targetEnd },
      };
    }
  }
  return null;
}

function isSelectionInsideTag(selection: TextRange, range: TextRange): boolean {
  return isSelectionInside(selection, range) && (selection.start < range.end || selection.start === range.start);
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
    const tagTarget = tagTargetFromNode(child, tag);
    if (tagTarget) {
      return [tagNode(tagTarget, child.children ?? [], tagRenderMode, checkedTagKeys, tag)];
    }
    transformTags(child, tag, tagRenderMode, checkedTagKeys);
    return [child];
  });
}

function tagTargetFromNode(node: MarkdownNode, tag: MarkdownTagOptions): string | null {
  if (node.type !== 'link' || typeof node.url !== 'string') {
    return null;
  }
  const match = tagUrlExactPattern(tag).exec(node.url.trim());
  return match ? match[1] : null;
}

function tagNode(
  target: string,
  children: MarkdownNode[],
  tagRenderMode: MarkdownTagRenderMode,
  checkedTagKeys: Set<string>,
  tag: MarkdownTagOptions,
): MarkdownNode {
  const reference = tagReference(target, tag);
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
            ...(tag.valueSelector
              ? { ariaLabel: `${reference.key}: ${reference.displayValue}` }
              : {}),
          },
        },
        children: [{ type: 'text', value: reference.displayValue }],
      },
      ...(tagRenderMode === 'CHECKBOX' ? [tagCheckboxNode(reference.key, checkedTagKeys.has(reference.key))] : []),
    ],
  };
}

function tagReference(target: string, tag: MarkdownTagOptions): { key: string; displayValue: string } {
  const selector = tag.valueSelector;
  if (!selector) {
    return { key: target, displayValue: target };
  }
  const separatorIndex = target.indexOf(selector.separator);
  if (separatorIndex < 0) {
    return { key: target, displayValue: optionLabel(selector.defaultValue, selector) };
  }
  const key = target.slice(0, separatorIndex);
  const value = target.slice(separatorIndex + selector.separator.length);
  return { key, displayValue: optionLabel(value, selector) };
}

function optionLabel(value: string, selector: MarkdownTagValueSelectorOptions): string {
  const option = selector.options.find((candidate) => equivalentValue(candidate.value, value));
  if (option) {
    return option.label;
  }
  if (!new RegExp(`^(?:${selector.customValuePattern})$`).test(value)) {
    return selector.customPlaceholder;
  }
  return value.replace('.', ',');
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
    const target = match[1] ?? '';
    const value = tag.valueSelector ? target.split(tag.valueSelector.separator, 1)[0] ?? '' : target;
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
  return new RegExp(`\\[([^\\]\\n]*)\\]\\(${escapedNamespace(tag)}:([^\\s)]+)\\)`, 'g');
}

function escapedNamespace(tag: MarkdownTagOptions): string {
  return escapeRegExp(tag.namespace);
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
