import { type ReactElement } from 'react';
import React from 'react';
import MDEditor from '@uiw/react-md-editor';
import { ReactAdapterElement, type RenderHooks } from 'Frontend/generated/flow/ReactAdapter';
import '@uiw/react-md-editor/markdown-editor.css';
import '@uiw/react-markdown-preview/markdown.css';
import { markdownPreviewConfiguration } from './ws-markdown-support';
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

  const preview = markdownPreviewConfiguration(markdownExtensions(extensionIds), context);

  return preview.wrap(
    <div
      onDragOver={(event) => {
        if (Array.from(event.dataTransfer?.types ?? []).includes('Files')) {
          event.preventDefault();
          event.dataTransfer.dropEffect = 'none';
        }
      }}
      onDrop={(event) => {
        if (!event.dataTransfer?.files.length) return;
        event.preventDefault();
        event.stopPropagation();
        context.emit('file-drop', {
          fileCount: event.dataTransfer.files.length,
          fileNames: Array.from(event.dataTransfer.files).map((file) => file.name),
          fileTypes: Array.from(event.dataTransfer.files).map((file) => file.type),
        });
      }}
    >
      <MDEditor.Markdown
        key={`${content}:${extensionIds.join(',')}`}
        source={content}
        {...preview.options}
      />
    </div>
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
