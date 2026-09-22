import React, { type ButtonHTMLAttributes, type InputHTMLAttributes, type MouseEvent, type ReactElement } from 'react';
import { Popover } from '@vaadin/react-components/Popover.js';
import {
  executeCommand,
  getSurroundingWord,
  type ICommand,
  type TextState,
  type TextRange,
  type TextAreaTextApi,
} from '@uiw/react-md-editor/nohighlight';
import { defaultSchema } from 'rehype-sanitize';
import {
  registerMarkdownExtension,
  type MarkdownExtensionContext,
} from '@vaadin/flow-frontend/de/westarps/vaadin/markdown/ws-markdown-extensions';
import './tt-criterion-markdown.css';

type MarkdownNode = {
  type: string;
  value?: string;
  url?: string;
  children?: MarkdownNode[];
  data?: Record<string, unknown>;
};

type MarkdownTagRenderMode = 'DEFAULT' | 'CHECKBOX';

type MarkdownTagValueOption = {
  value: string;
  label: string;
};

type MarkdownTagValueSelectorOptions = {
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

type MarkdownTagOptions = {
  namespace: string;
  toolbarLabel: string;
  idGenerator: 'NEXT_NUMBER';
  valueSelector?: MarkdownTagValueSelectorOptions;
};

type TagRange = {
  full: TextRange;
  label: TextRange;
  target: TextRange;
  key: TextRange;
  value?: TextRange;
};

type CriterionAward = {
  label: string;
  pointUnits: number;
  awardedUnits: number;
  maxAwardableUnits: number;
};

const criterionTag: MarkdownTagOptions = {
  namespace: 'eh',
  toolbarLabel: 'Kriterium mit Punkten markieren',
  idGenerator: 'NEXT_NUMBER',
  valueSelector: {
    options: ['0,5', '1', '1,5', '2', '2,5', '3', '3,5', '4'].map((value) => ({ value, label: value })),
    defaultValue: '1',
    separator: '/',
    toolbarIconText: 'P',
    customOptionLabel: '4+',
    customOptionAriaLabel: 'Andere Punktzahl eingeben',
    customPlaceholder: '?',
    customValuePattern: '(?:0[.,]5|(?:[1-9]\\d{0,1}|[1-8]\\d{2}|9(?:[0-8]\\d|9[0-8]))(?:[.,][05])?|999(?:[.,]0)?)',
    removeLabel: 'Kriterium entfernen',
  },
};

const numericTagIdPattern = /^[1-9]\d*$/;
const skippedNodeTypes = new Set(['code', 'inlineCode', 'html']);

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
      <span className="tt-criterion-command-icon" aria-hidden="true">
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
    <div className="tt-criterion-value-selector" role="group" aria-label={tag.toolbarLabel}>
      <div className="tt-criterion-value-options">
        <span className="tt-criterion-value-spacer" aria-hidden="true" />
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
          className="tt-criterion-custom-value"
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
      {!applicable && <span className="tt-criterion-selection-hint">Text markieren</span>}
      {existingTag && (
        <button
          type="button"
          className="tt-criterion-remove"
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
    tagNames.push('input', 'button');
  }

  return {
    ...schema,
    tagNames,
    attributes: {
      ...schema.attributes,
      input: [
        ...(schema.attributes?.input ?? []),
        ['className', 'tt-criterion-checkbox'],
        ['type', 'checkbox'],
        'defaultChecked',
        'value',
      ],
      mark: [...(schema.attributes?.mark ?? []), ['className', 'tt-criterion-highlight']],
      span: [
        ...(schema.attributes?.span ?? []),
        ['className', 'tt-criterion', 'tt-criterion-badge'],
        'dataCriterionKey',
      ],
      button: [
        ...(schema.attributes?.button ?? []),
        ['className', 'tt-criterion-badge', 'tt-criterion-point-button'],
        ['type', 'button'],
        'dataCriterionKey',
        'ariaLabel',
      ],
    },
  };
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
  awards: Map<string, CriterionAward>,
) {
  return () => (tree: MarkdownNode) => {
    transformTags(tree, tag, tagRenderMode, awards);
  };
}

function transformTags(
  node: MarkdownNode | undefined,
  tag: MarkdownTagOptions,
  tagRenderMode: MarkdownTagRenderMode,
  awards: Map<string, CriterionAward>,
) {
  if (!node?.children || skippedNodeTypes.has(node.type)) {
    return;
  }

  node.children = node.children.flatMap((child) => {
    const tagTarget = tagTargetFromNode(child, tag);
    if (tagTarget) {
      return [tagNode(tagTarget, child.children ?? [], tagRenderMode, awards, tag)];
    }
    transformTags(child, tag, tagRenderMode, awards);
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
  awards: Map<string, CriterionAward>,
  tag: MarkdownTagOptions,
): MarkdownNode {
  const reference = tagReference(target, tag);
  const award = awards.get(reference.key);
  const displayValue = tagRenderMode === 'CHECKBOX' ? formatPointUnits(award?.awardedUnits ?? 0) : reference.displayValue;
  const badgeNode: MarkdownNode = {
    type: 'ttCriterionBadge',
    data: {
      hName: tagRenderMode === 'CHECKBOX' ? 'button' : 'span',
      hProperties: {
        className: tagRenderMode === 'CHECKBOX'
          ? ['tt-criterion-badge', 'tt-criterion-point-button']
          : ['tt-criterion-badge'],
        ...(tagRenderMode === 'CHECKBOX' ? { type: 'button', dataCriterionKey: reference.key } : {}),
        ...(tag.valueSelector
          ? { ariaLabel: tagRenderMode === 'CHECKBOX'
            ? `${award?.label ?? reference.key}: ${displayValue} von ${formatPointUnits(award?.pointUnits ?? 0)} ${award?.pointUnits === 2 ? 'Punkt' : 'Punkten'}`
            : `${reference.key}: ${reference.displayValue}` }
          : {}),
      },
    },
    children: [{ type: 'text', value: displayValue }],
  };
  const checkboxNode = tagRenderMode === 'CHECKBOX' ? tagCheckboxNode(reference.key, award) : null;
  return {
    type: 'ttCriterion',
    data: {
      hName: 'span',
      hProperties: {
        className: ['tt-criterion'],
        dataCriterionKey: reference.key,
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
        children: [...children, badgeNode, ...(checkboxNode ? [checkboxNode] : [])],
      },
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

function formatPointUnits(units: number): string {
  return units % 2 === 0 ? String(units / 2) : `${Math.floor(units / 2)},5`;
}

function tagCheckboxNode(id: string, award: CriterionAward | undefined): MarkdownNode {
  return {
    type: 'ttCriterionCheckbox',
    data: {
      hName: 'input',
      hProperties: {
        className: ['tt-criterion-checkbox'],
        type: 'checkbox',
        defaultChecked: Boolean(award && award.awardedUnits === award.pointUnits),
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

type CriterionInputProps = InputHTMLAttributes<HTMLInputElement> & { node?: unknown };
type CriterionButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & { node?: unknown };
type CriterionSpanProps = React.HTMLAttributes<HTMLSpanElement> & { node?: unknown };
const CriterionContext = React.createContext<MarkdownExtensionContext | null>(null);

function useCriterionContext(): MarkdownExtensionContext {
  const context = React.useContext(CriterionContext);
  if (!context) throw new Error('Criterion preview context is missing');
  return context;
}

function criterionAwards(context: MarkdownExtensionContext): Map<string, CriterionAward> {
  return new Map<string, CriterionAward>(Object.entries(
    context.state.criterionAwards && typeof context.state.criterionAwards === 'object'
      ? context.state.criterionAwards as Record<string, CriterionAward>
      : {},
  ));
}

function criterionAward(context: MarkdownExtensionContext, key: string): CriterionAward | undefined {
  const awards = context.state.criterionAwards;
  return awards && typeof awards === 'object'
    ? (awards as Record<string, CriterionAward>)[key]
    : undefined;
}

function criterionPillWidth(pointUnits: number): string {
  const largestPartialValue = formatPointUnits(Math.max(0, pointUnits - 1));
  return `${Math.max(3, formatPointUnits(pointUnits).length, largestPartialValue.length)}ch`;
}

function criterionKey(props: Record<string, unknown>): string {
  return String(props['data-criterion-key'] ?? props.dataCriterionKey ?? '');
}

function CriterionCheckbox(props: CriterionInputProps): ReactElement {
  const context = useCriterionContext();
  const key = String(props.value ?? '');
  const award = criterionAward(context, key);
  const [awardedUnits, setAwardedUnits] = React.useState(award?.awardedUnits ?? 0);
  const inputRef = React.useRef<HTMLInputElement>(null);
  React.useEffect(() => setAwardedUnits(award?.awardedUnits ?? 0), [award?.awardedUnits]);
  React.useEffect(() => {
    if (inputRef.current) {
      inputRef.current.indeterminate = awardedUnits > 0 && awardedUnits < (award?.pointUnits ?? 0);
    }
  }, [awardedUnits, award?.pointUnits]);

  const inputProps = { ...props };
  delete inputProps.checked;
  delete inputProps.defaultChecked;
  delete inputProps.disabled;
  delete inputProps.node;
  delete inputProps.onChange;
  delete inputProps.type;

  const fullAvailable = awardedUnits === award?.pointUnits
    || (award?.maxAwardableUnits ?? 0) >= (award?.pointUnits ?? 0);
  return (
    <input
      {...inputProps}
      ref={inputRef}
      type="checkbox"
      checked={Boolean(award && awardedUnits === award.pointUnits)}
      disabled={!award || !fullAvailable}
      aria-label={`${award?.label ?? key}: ${formatPointUnits(awardedUnits)} von ${formatPointUnits(award?.pointUnits ?? 0)} ${award?.pointUnits === 2 ? 'Punkt' : 'Punkten'}`}
      onChange={() => {
        if (!award) return;
        const next = awardedUnits === award.pointUnits ? 0 : award.pointUnits;
        setAwardedUnits(next);
        context.emit('criterion-award-changed', { key, pointUnits: next });
      }}
    />
  );
}

function criterionInput(props: CriterionInputProps): ReactElement {
  return props.className?.split(' ').includes('tt-criterion-checkbox')
    ? <CriterionCheckbox {...props} />
    : <input {...props} />;
}

function CriterionPointButton(props: CriterionButtonProps): ReactElement {
  const context = useCriterionContext();
  const key = criterionKey(props as Record<string, unknown>);
  const award = criterionAward(context, key);
  const [target, setTarget] = React.useState<HTMLButtonElement | null>(null);
  const buttonProps = { ...props };
  delete buttonProps.node;
  delete (buttonProps as Record<string, unknown>).dataCriterionKey;
  delete (buttonProps as Record<string, unknown>)['data-criterion-key'];

  return (
    <span className="tt-criterion-point-slot">
      <button {...buttonProps} ref={setTarget} type="button">
        {formatPointUnits(award?.awardedUnits ?? 0)}
      </button>
      {award && (
        <Popover target={target ?? undefined} position="bottom" trigger={['click']}>
          <div className="tt-criterion-award-popover" role="group" aria-label={`${award.label} Punkte`}>
            <button type="button" aria-label={`${award.label} Punkte verringern`}
              disabled={award.awardedUnits <= 0}
              onClick={() => context.emit('criterion-award-changed', { key, pointUnits: award.awardedUnits - 1 })}>−</button>
            <span>{formatPointUnits(award.awardedUnits)}</span>
            <button type="button" aria-label={`${award.label} Punkte erhöhen`}
              disabled={award.awardedUnits >= award.maxAwardableUnits}
              onClick={() => context.emit('criterion-award-changed', { key, pointUnits: award.awardedUnits + 1 })}>+</button>
          </div>
        </Popover>
      )}
    </span>
  );
}

function criterionButton(props: CriterionButtonProps): ReactElement {
  return props.className?.split(' ').includes('tt-criterion-point-button')
    ? <CriterionPointButton {...props} />
    : <button {...props} />;
}

function criterionSpan(props: CriterionSpanProps): ReactElement {
  const context = useCriterionContext();
  const spanProps = { ...props };
  delete spanProps.node;
  const key = criterionKey(props as Record<string, unknown>);
  if (!props.className?.split(' ').includes('tt-criterion')) {
    return <span {...spanProps} />;
  }
  const active = Array.isArray(context.state.highlightedCriterionKeys)
    && context.state.highlightedCriterionKeys.includes(key);
  return <span {...spanProps}
    className={`${props.className ?? ''}${active ? ' tt-criterion-active' : ''}`}
    onMouseEnter={() => context.emit('criterion-highlight-changed', { key, active: true })}
    onMouseLeave={() => context.emit('criterion-highlight-changed', { key, active: false })}
    onFocusCapture={() => context.emit('criterion-highlight-changed', { key, active: true })}
    onBlurCapture={(event) => {
      if (!event.currentTarget.contains(event.relatedTarget)) {
        context.emit('criterion-highlight-changed', { key, active: false });
      }
    }} />;
}

registerMarkdownExtension('tt-criterion', {
  editorCommands: () => [tagValueSelectorCommand(criterionTag, criterionTag.valueSelector!)],
  preview: (context) => {
    const renderMode: MarkdownTagRenderMode = context.state.criterionCheckboxes === true ? 'CHECKBOX' : 'DEFAULT';
    const awards = criterionAwards(context);
    return {
      remarkPlugins: [remarkTags(criterionTag, renderMode, awards)],
      sanitizeSchema: (schema) => tagSanitizeSchema(schema, renderMode),
      wrap: (content, currentContext) =>
        <CriterionContext.Provider value={currentContext}>{content}</CriterionContext.Provider>,
      components: renderMode === 'CHECKBOX' ? {
        input: criterionInput,
        button: criterionButton,
        span: criterionSpan,
      } : undefined,
    };
  },
});
