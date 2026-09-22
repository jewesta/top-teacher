import { type ReactElement } from 'react';
import React from 'react';
import MDEditor from '@uiw/react-md-editor';
import { ReactAdapterElement, type RenderHooks } from 'Frontend/generated/flow/ReactAdapter';
import '@uiw/react-md-editor/markdown-editor.css';
import '@uiw/react-markdown-preview/markdown.css';
import { markdownPreviewOptions } from './ws-markdown-support';
import { markdownExtensions, type MarkdownExtensionContext } from './ws-markdown-extensions';

type MarkdownViewerContentProps = {
  content: string;
  extensionIds: string[];
  context: MarkdownExtensionContext;
  renderComplete: () => void;
};

function MarkdownViewerContent({
  content,
  extensionIds,
  context,
  renderComplete,
}: MarkdownViewerContentProps): ReactElement {
  React.useLayoutEffect(() => {
    renderComplete();
  });

  return (
    <MDEditor.Markdown
      key={`${content}:${extensionIds.join(',')}`}
      source={content}
      {...markdownPreviewOptions(markdownExtensions(extensionIds), context)}
    />
  );
}

class MarkdownViewerElement extends ReactAdapterElement {
  public hasRendered = false;

  protected override render(hooks: RenderHooks): ReactElement | null {
    this.hasRendered = false;
    const [content] = hooks.useState<string>('content', '');
    const [extensionIds] = hooks.useState<string[]>('extensionIds', []);
    const [extensionState] = hooks.useState<Record<string, unknown>>('extensionState', {});
    const dispatchRenderComplete = hooks.useCustomEvent('render-complete');
    const dispatchExtensionEvent = hooks.useCustomEvent<Record<string, unknown>>('markdown-extension-event');

    return (
      <MarkdownViewerContent
        content={content}
        extensionIds={extensionIds}
        context={{
          state: extensionState,
          emit: (name, detail) => dispatchExtensionEvent({ name, ...detail }),
        }}
        renderComplete={() => {
          this.hasRendered = true;
          dispatchRenderComplete();
        }}
      />
    );
  }
}

customElements.define('ws-markdown-viewer', MarkdownViewerElement);
