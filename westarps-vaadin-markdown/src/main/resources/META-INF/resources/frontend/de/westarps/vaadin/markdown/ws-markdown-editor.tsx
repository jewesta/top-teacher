import { type ReactElement } from 'react';
import React from 'react';
import MDEditor from '@uiw/react-md-editor/nohighlight';
import { ReactAdapterElement, type RenderHooks } from 'Frontend/generated/flow/ReactAdapter';
import '@uiw/react-md-editor/markdown-editor.css';
import '@uiw/react-markdown-preview/markdown.css';
import {
  type MarkdownToolbarCommandId,
  markdownCommands,
  markdownExtraCommands,
  markdownPreviewOptions,
  markdownStateIds,
  markdownTagOptions,
} from './ws-markdown-support';

class MarkdownEditorElement extends ReactAdapterElement {
  protected override render(hooks: RenderHooks): ReactElement | null {
    const [content, setContent] = hooks.useState<string>('content', '');
    const [placeholder] = hooks.useState<string>('placeholder', '');
    const [maxLength] = hooks.useState<number>('maxLength', -1);
    const [tagNamespace] = hooks.useState<string>('tagNamespace', '');
    const [tagToolbarLabel] = hooks.useState<string>('tagToolbarLabel', '');
    const [tagIdGenerator] = hooks.useState<string>('tagIdGenerator', '');
    const [tagValueOptionValues] = hooks.useState<string[]>('tagValueOptionValues', []);
    const [tagValueOptionLabels] = hooks.useState<string[]>('tagValueOptionLabels', []);
    const [tagValueDefault] = hooks.useState<string>('tagValueDefault', '');
    const [tagValueSeparator] = hooks.useState<string>('tagValueSeparator', '');
    const [tagValueToolbarIconText] = hooks.useState<string>('tagValueToolbarIconText', '');
    const [tagValueCustomOptionLabel] = hooks.useState<string>('tagValueCustomOptionLabel', '');
    const [tagValueCustomOptionAriaLabel] = hooks.useState<string>('tagValueCustomOptionAriaLabel', '');
    const [tagValueCustomPlaceholder] = hooks.useState<string>('tagValueCustomPlaceholder', '');
    const [tagValueCustomPattern] = hooks.useState<string>('tagValueCustomPattern', '');
    const [tagValueRemoveLabel] = hooks.useState<string>('tagValueRemoveLabel', '');
    const [hiddenToolbarCommandsState] = hooks.useState<string>('hiddenToolbarCommands', '');
    const markdownOptions = {
      tag: markdownTagOptions(
        tagNamespace,
        tagToolbarLabel,
        tagIdGenerator,
        tagValueOptionValues,
        tagValueOptionLabels,
        tagValueDefault,
        tagValueSeparator,
        tagValueToolbarIconText,
        tagValueCustomOptionLabel,
        tagValueCustomOptionAriaLabel,
        tagValueCustomPlaceholder,
        tagValueCustomPattern,
        tagValueRemoveLabel,
      ),
      hiddenToolbarCommands: markdownStateIds<MarkdownToolbarCommandId>(hiddenToolbarCommandsState),
    };

    return (
      <MDEditor
        commands={markdownCommands(markdownOptions)}
        extraCommands={markdownExtraCommands(markdownOptions)}
        previewOptions={markdownPreviewOptions(markdownOptions)}
        textareaProps={{
          placeholder,
          maxLength: maxLength >= 0 ? maxLength : undefined,
        }}
        value={content}
        visibleDragbar={false}
        onChange={(value) => setContent(value ?? '')}
      />
    );
  }
}

customElements.define('ws-markdown-editor', MarkdownEditorElement);
