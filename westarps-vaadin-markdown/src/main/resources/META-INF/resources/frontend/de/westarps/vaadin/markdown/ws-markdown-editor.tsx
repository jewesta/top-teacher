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
  private droppedFiles: File[] = [];

  public takeDroppedFiles(): File[] {
    const files = this.droppedFiles;
    this.droppedFiles = [];
    return files;
  }

  public focusAt(position: number): void {
    requestAnimationFrame(() => {
      const textarea = this.querySelector('textarea');
      if (!textarea) return;
      textarea.focus();
      textarea.setSelectionRange(position, position);
    });
  }

  protected override render(hooks: RenderHooks): ReactElement | null {
    const [content, setContent] = hooks.useState<string>('content', '');
    const [placeholder] = hooks.useState<string>('placeholder', '');
    const [maxLength] = hooks.useState<number>('maxLength', -1);
    const [extensionIds] = hooks.useState<string[]>('extensionIds', []);
    const [extensionState] = hooks.useState<Record<string, unknown>>('extensionState', {});
    const [hiddenToolbarCommandsState] = hooks.useState<string>('hiddenToolbarCommands', '');
    const dispatchExtensionEvent = hooks.useCustomEvent<Record<string, unknown>>('markdown-extension-event');
    const extensions = markdownExtensions(extensionIds);
    const hiddenCommands = markdownStateIds<MarkdownToolbarCommandId>(hiddenToolbarCommandsState);
    const context = {
      state: extensionState,
      emit: (name: string, detail: Record<string, unknown>) => dispatchExtensionEvent({ name, ...detail }),
    };
    const handleFileDrop = (event: React.DragEvent<HTMLElement>) => {
      if (!event.dataTransfer?.files.length) return;
      event.preventDefault();
      event.stopPropagation();
      this.droppedFiles = Array.from(event.dataTransfer.files);
      const textarea = this.querySelector('textarea');
      dispatchExtensionEvent({
        name: 'file-drop',
        fileCount: this.droppedFiles.length,
        fileNames: this.droppedFiles.map((file) => file.name),
        fileTypes: this.droppedFiles.map((file) => file.type),
        selectionStart: textarea?.selectionStart ?? content.length,
        selectionEnd: textarea?.selectionEnd ?? content.length,
      });
    };

    return (
      <div
        style={{ height: '100%' }}
        onDragOver={(event) => {
          if (Array.from(event.dataTransfer?.types ?? []).includes('Files')) {
            event.preventDefault();
            event.dataTransfer.dropEffect = 'copy';
          }
        }}
        onDrop={handleFileDrop}
      >
        <MDEditor
          commands={markdownCommands(extensions, hiddenCommands)}
          extraCommands={markdownExtraCommands(hiddenCommands)}
          previewOptions={markdownPreviewOptions(extensions, context)}
          textareaProps={{
            placeholder,
            maxLength: maxLength >= 0 ? maxLength : undefined,
          }}
          value={content}
          visibleDragbar={false}
          onChange={(value) => setContent(value ?? '')}
        />
      </div>
    );
  }
}

customElements.define('ws-markdown-editor', MarkdownEditorElement);
