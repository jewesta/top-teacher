import type React from 'react';
import type MDEditor from '@uiw/react-md-editor';
import type { ICommand } from '@uiw/react-md-editor/nohighlight';
import type { PluggableList } from 'unified';
import { defaultSchema } from 'rehype-sanitize';

export type MarkdownPreviewContribution = {
  remarkPlugins?: PluggableList;
  rehypePlugins?: PluggableList;
  sanitizeSchema?: (schema: typeof defaultSchema) => typeof defaultSchema;
  components?: React.ComponentProps<typeof MDEditor.Markdown>['components'];
  wrap?: (content: React.ReactElement, context: MarkdownExtensionContext) => React.ReactElement;
};

export type MarkdownExtensionContext = {
  state: Record<string, unknown>;
  emit: (name: string, detail: Record<string, unknown>) => void;
};

export type MarkdownExtension = {
  editorCommands?: () => ICommand[];
  preview?: (context: MarkdownExtensionContext) => MarkdownPreviewContribution;
};

const registeredExtensions = new Map<string, MarkdownExtension>();

export function registerMarkdownExtension(id: string, extension: MarkdownExtension): void {
  if (!id?.trim()) {
    throw new Error('Markdown extension must have a nonempty id');
  }
  registeredExtensions.set(id, extension);
}

export function markdownExtensions(ids: string[]): MarkdownExtension[] {
  return ids.map((id) => {
    const extension = registeredExtensions.get(id);
    if (!extension) {
      throw new Error(`Markdown extension is not registered: ${id}`);
    }
    return extension;
  });
}
