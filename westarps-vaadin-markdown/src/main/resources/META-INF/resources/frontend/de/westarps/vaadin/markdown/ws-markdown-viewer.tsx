import { type ReactElement } from 'react';
import React from 'react';
import MDEditor from '@uiw/react-md-editor';
import { ReactAdapterElement, type RenderHooks } from 'Frontend/generated/flow/ReactAdapter';
import '@uiw/react-md-editor/markdown-editor.css';
import '@uiw/react-markdown-preview/markdown.css';
import {
  markdownPreviewOptions,
  markdownTagOptions,
} from './ws-markdown-support';

class MarkdownViewerElement extends ReactAdapterElement {
  protected override render(hooks: RenderHooks): ReactElement | null {
    const [content] = hooks.useState<string>('content', '');
    const [tagNamespace] = hooks.useState<string>('tagNamespace', '');
    const [tagToolbarLabel] = hooks.useState<string>('tagToolbarLabel', '');
    const [tagIdGenerator] = hooks.useState<string>('tagIdGenerator', '');
    const markdownOptions = {
      tag: markdownTagOptions(tagNamespace, tagToolbarLabel, tagIdGenerator),
    };

    return (
      <MDEditor.Markdown
        key={`${content}:${tagNamespace}:${tagIdGenerator}`}
        source={content}
        {...markdownPreviewOptions(markdownOptions)}
      />
    );
  }
}

customElements.define('ws-markdown-viewer', MarkdownViewerElement);
