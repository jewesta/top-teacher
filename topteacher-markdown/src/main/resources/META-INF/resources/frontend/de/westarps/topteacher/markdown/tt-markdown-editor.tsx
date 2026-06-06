import { type ReactElement } from 'react';
import React from 'react';
import MDEditor from '@uiw/react-md-editor/nohighlight';
import { ReactAdapterElement, type RenderHooks } from 'Frontend/generated/flow/ReactAdapter';
import '@uiw/react-md-editor/markdown-editor.css';
import '@uiw/react-markdown-preview/markdown.css';
import {
  topTeacherMarkdownCommands,
  topTeacherMarkdownExtraCommands,
  topTeacherMarkdownPreviewOptions,
} from './tt-markdown-support';

class TopTeacherMarkdownEditorElement extends ReactAdapterElement {
  protected override render(hooks: RenderHooks): ReactElement | null {
    const [content, setContent] = hooks.useState<string>('content', '');
    const [placeholder] = hooks.useState<string>('placeholder', '');
    const [maxLength] = hooks.useState<number>('maxLength', -1);

    return (
      <MDEditor
        commands={topTeacherMarkdownCommands}
        extraCommands={topTeacherMarkdownExtraCommands}
        previewOptions={topTeacherMarkdownPreviewOptions}
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

customElements.define('tt-markdown-editor', TopTeacherMarkdownEditorElement);
