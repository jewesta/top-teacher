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
} from './ws-markdown-support';
import { markdownExtensions } from './ws-markdown-extensions';

class MarkdownEditorElement extends ReactAdapterElement {
  protected override render(hooks: RenderHooks): ReactElement | null {
    const [content, setContent] = hooks.useState<string>('content', '');
    const [placeholder] = hooks.useState<string>('placeholder', '');
    const [maxLength] = hooks.useState<number>('maxLength', -1);
    const [extensionIds] = hooks.useState<string[]>('extensionIds', []);
    const [hiddenToolbarCommandsState] = hooks.useState<string>('hiddenToolbarCommands', '');
    const extensions = markdownExtensions(extensionIds);
    const hiddenCommands = markdownStateIds<MarkdownToolbarCommandId>(hiddenToolbarCommandsState);

    return (
      <MDEditor
        commands={markdownCommands(extensions, hiddenCommands)}
        extraCommands={markdownExtraCommands(hiddenCommands)}
        previewOptions={markdownPreviewOptions(extensions, { state: {}, emit: () => {} })}
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
