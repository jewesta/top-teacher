import React from 'react';
import { commands, executeCommand, selectWord, type ExecuteState, type ICommand, type TextAreaTextApi } from '@uiw/react-md-editor/nohighlight';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import type { PluggableList } from 'unified';

type MarkdownNode = {
  type: string;
  value?: string;
  children?: MarkdownNode[];
  data?: Record<string, unknown>;
};

const criterionPrefix = '::eh[';
const criterionSuffix = ']';
const criterionPattern = /::eh\[([^\]\n]+)\]/g;
const skippedNodeTypes = new Set(['code', 'inlineCode', 'html']);

const criterionCommand: ICommand = {
  name: 'criterion',
  keyCommand: 'criterion',
  prefix: criterionPrefix,
  suffix: criterionSuffix,
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
    const selection = state.selectedText
      ? state.selection
      : selectWord({
          text: state.text,
          selection: state.selection,
          prefix: criterionPrefix,
          suffix: criterionSuffix,
        });
    const selectedState = api.setSelectionRange(selection);
    if (selectedState.selectedText.includes('\n')) {
      return;
    }
    executeCommand({
      api,
      selectedText: selectedState.selectedText,
      selection: state.selection,
      prefix: criterionPrefix,
      suffix: criterionSuffix,
    });
  },
};

const insertCriterionCommand = (baseCommands: ICommand[]): ICommand[] => {
  const dividerIndex = baseCommands.findIndex((command) => command.keyCommand === 'divider');
  if (dividerIndex < 0) {
    return [...baseCommands, criterionCommand];
  }
  return [...baseCommands.slice(0, dividerIndex), criterionCommand, ...baseCommands.slice(dividerIndex)];
};

export const topTeacherMarkdownCommands = insertCriterionCommand(commands.getCommands());
export const topTeacherMarkdownExtraCommands = commands.getExtraCommands();

const sanitizeSchema = {
  ...defaultSchema,
  tagNames: [...(defaultSchema.tagNames ?? []), 'mark'],
  attributes: {
    ...defaultSchema.attributes,
    mark: [...(defaultSchema.attributes?.mark ?? []), ['className', 'tt-criterion-highlight']],
  },
};

export const topTeacherMarkdownPreviewOptions = {
  remarkPlugins: [remarkCriteria] as PluggableList,
  rehypePlugins: [[rehypeSanitize, sanitizeSchema]] as PluggableList,
};

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
    if (child.type === 'text' && typeof child.value === 'string') {
      return criterionNodes(child.value);
    }
    transformCriteria(child);
    return [child];
  });
}

function criterionNodes(value: string): MarkdownNode[] {
  const nodes: MarkdownNode[] = [];
  let lastIndex = 0;
  for (const match of value.matchAll(criterionPattern)) {
    if (match.index === undefined) {
      continue;
    }
    if (match.index > lastIndex) {
      nodes.push({ type: 'text', value: value.slice(lastIndex, match.index) });
    }
    nodes.push({
      type: 'ttCriterion',
      data: {
        hName: 'mark',
        hProperties: {
          className: ['tt-criterion-highlight'],
        },
      },
      children: [{ type: 'text', value: match[1] }],
    });
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < value.length) {
    nodes.push({ type: 'text', value: value.slice(lastIndex) });
  }
  return nodes.length ? nodes : [{ type: 'text', value }];
}
