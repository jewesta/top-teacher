import { type ReactElement } from 'react';
import React from 'react';
import MDEditor from '@uiw/react-md-editor';
import { ReactAdapterElement, type RenderHooks } from 'Frontend/generated/flow/ReactAdapter';
import '@uiw/react-md-editor/markdown-editor.css';
import '@uiw/react-markdown-preview/markdown.css';
import {
  type MarkdownExtensionId,
  markdownPreviewOptions,
  markdownStateIds,
} from './ws-markdown-support';

class MarkdownViewerElement extends ReactAdapterElement {
  protected override render(hooks: RenderHooks): ReactElement | null {
    const [content] = hooks.useState<string>('content', '');
    const [extensionsState] = hooks.useState<string>('extensions', '');
    const markdownOptions = {
      extensions: markdownStateIds<MarkdownExtensionId>(extensionsState),
    };

    return (
      <MDEditor.Markdown
        key={`${content}:${extensionsState}`}
        source={content}
        {...markdownPreviewOptions(markdownOptions)}
      />
    );
  }
}

customElements.define('ws-markdown-viewer', MarkdownViewerElement);
