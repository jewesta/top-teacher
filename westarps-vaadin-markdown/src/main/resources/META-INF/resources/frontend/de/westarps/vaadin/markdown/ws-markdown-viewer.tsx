import { type InputHTMLAttributes, type ReactElement } from 'react';
import React from 'react';
import MDEditor from '@uiw/react-md-editor';
import { ReactAdapterElement, type RenderHooks } from 'Frontend/generated/flow/ReactAdapter';
import '@uiw/react-md-editor/markdown-editor.css';
import '@uiw/react-markdown-preview/markdown.css';
import {
  type MarkdownOptions,
  markdownPreviewOptions,
  markdownTagRenderMode,
  markdownTagOptions,
} from './ws-markdown-support';

type TagCheckedChanged = (detail: { key: string; checked: boolean }) => void;
type MarkdownViewerOptions = Pick<MarkdownOptions, 'tag' | 'tagRenderMode' | 'checkedTagKeys'>;
type MarkdownInputProps = InputHTMLAttributes<HTMLInputElement> & { node?: unknown };

type MarkdownViewerContentProps = {
  checkedTagKeys: string[];
  content: string;
  markdownOptions: MarkdownViewerOptions;
  tagCheckedChanged: TagCheckedChanged;
};

function MarkdownViewerContent({
  checkedTagKeys,
  content,
  markdownOptions,
  tagCheckedChanged,
}: MarkdownViewerContentProps): ReactElement {
  const [localCheckedTagKeys, setLocalCheckedTagKeys] = React.useState<string[]>(checkedTagKeys);
  const checkedTagKeySignature = checkedTagKeys.join('\u001f');

  React.useEffect(() => {
    setLocalCheckedTagKeys(checkedTagKeys);
  }, [checkedTagKeySignature]);

  const checkedTagKeySet = React.useMemo(() => new Set(localCheckedTagKeys), [localCheckedTagKeys]);

  const changeTagChecked = (key: string, checked: boolean) => {
    setLocalCheckedTagKeys((currentKeys) => {
      const nextKeys = new Set(currentKeys);
      if (checked) {
        nextKeys.add(key);
      } else {
        nextKeys.delete(key);
      }
      return [...nextKeys];
    });
    tagCheckedChanged({ key, checked });
  };

  const tagCheckbox = (props: MarkdownInputProps) => {
    const { className, value } = props;
    if (!className?.split(' ').includes('ws-markdown-tag-checkbox')) {
      return <input {...props} />;
    }

    const inputProps = { ...props };
    delete inputProps.checked;
    delete inputProps.defaultChecked;
    delete inputProps.disabled;
    delete inputProps.node;
    delete inputProps.onChange;
    delete inputProps.type;

    const key = String(value ?? '');
    return (
      <input
        {...inputProps}
        className={className}
        type="checkbox"
        value={key}
        checked={checkedTagKeySet.has(key)}
        onChange={(event) => changeTagChecked(key, event.currentTarget.checked)}
      />
    );
  };

  return (
    <MDEditor.Markdown
      key={`${content}:${markdownOptions.tag?.namespace ?? ''}:${markdownOptions.tag?.idGenerator ?? ''}:${markdownOptions.tagRenderMode}`}
      source={content}
      {...markdownPreviewOptions({ ...markdownOptions, checkedTagKeys: localCheckedTagKeys })}
      components={{ input: tagCheckbox }}
    />
  );
}

class MarkdownViewerElement extends ReactAdapterElement {
  protected override render(hooks: RenderHooks): ReactElement | null {
    const [content] = hooks.useState<string>('content', '');
    const [tagNamespace] = hooks.useState<string>('tagNamespace', '');
    const [tagToolbarLabel] = hooks.useState<string>('tagToolbarLabel', '');
    const [tagIdGenerator] = hooks.useState<string>('tagIdGenerator', '');
    const [tagRenderMode] = hooks.useState<string>('tagRenderMode', 'DEFAULT');
    const [checkedTagKeys] = hooks.useState<string[]>('checkedTagKeys', []);
    const tagCheckedChanged = hooks.useCustomEvent<{ key: string; checked: boolean }>('tag-checked-changed');
    const markdownOptions = {
      tag: markdownTagOptions(tagNamespace, tagToolbarLabel, tagIdGenerator),
      tagRenderMode: markdownTagRenderMode(tagRenderMode),
      checkedTagKeys,
    };

    return (
      <MarkdownViewerContent
        checkedTagKeys={checkedTagKeys}
        content={content}
        markdownOptions={markdownOptions}
        tagCheckedChanged={tagCheckedChanged}
      />
    );
  }
}

customElements.define('ws-markdown-viewer', MarkdownViewerElement);
