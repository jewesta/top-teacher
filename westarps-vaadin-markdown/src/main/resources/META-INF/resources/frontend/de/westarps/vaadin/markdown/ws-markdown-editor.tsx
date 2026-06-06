import { type ReactElement } from 'react';
import React from 'react';
import MDEditor from '@uiw/react-md-editor/nohighlight';
import { ReactAdapterElement, type RenderHooks } from 'Frontend/generated/flow/ReactAdapter';
import '@uiw/react-md-editor/markdown-editor.css';
import '@uiw/react-markdown-preview/markdown.css';
import {
  type MarkdownExtensionId,
  type MarkdownToolbarCommandId,
  markdownCommands,
  markdownExtraCommands,
  markdownPreviewOptions,
  markdownStateIds,
} from './ws-markdown-support';

class MarkdownEditorElement extends ReactAdapterElement {
  protected override render(hooks: RenderHooks): ReactElement | null {
    const [content, setContent] = hooks.useState<string>('content', '');
    const [placeholder] = hooks.useState<string>('placeholder', '');
    const [maxLength] = hooks.useState<number>('maxLength', -1);
    const [extensionsState] = hooks.useState<string>('extensions', '');
    const [hiddenToolbarCommandsState] = hooks.useState<string>('hiddenToolbarCommands', '');
    const markdownOptions = {
      extensions: markdownStateIds<MarkdownExtensionId>(extensionsState),
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
