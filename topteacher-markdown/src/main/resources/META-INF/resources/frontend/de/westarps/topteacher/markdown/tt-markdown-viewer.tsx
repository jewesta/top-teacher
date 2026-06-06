import { type ReactElement } from 'react';
import React from 'react';
import MDEditor from '@uiw/react-md-editor';
import { ReactAdapterElement, type RenderHooks } from 'Frontend/generated/flow/ReactAdapter';
import '@uiw/react-md-editor/markdown-editor.css';
import '@uiw/react-markdown-preview/markdown.css';
import { topTeacherMarkdownPreviewOptions } from './tt-markdown-support';

class TopTeacherMarkdownViewerElement extends ReactAdapterElement {
  protected override render(hooks: RenderHooks): ReactElement | null {
    const [content] = hooks.useState<string>('content', '');

    return <MDEditor.Markdown key={content} source={content} {...topTeacherMarkdownPreviewOptions} />;
  }
}

customElements.define('tt-markdown-viewer', TopTeacherMarkdownViewerElement);
