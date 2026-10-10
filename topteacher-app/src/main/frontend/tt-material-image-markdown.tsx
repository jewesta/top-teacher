import React, { type ImgHTMLAttributes, type ReactElement } from 'react';
import { commands, type ICommand } from '@uiw/react-md-editor/nohighlight';
import { defaultSchema } from 'rehype-sanitize';
import {
  registerMarkdownExtension,
  type MarkdownExtensionContext,
} from '@vaadin/flow-frontend/de/westarps/vaadin/markdown/ws-markdown-extensions';
import './tt-material-image-markdown.css';

type MarkdownNode = {
  type: string;
  url?: string;
  alt?: string;
  children?: MarkdownNode[];
  data?: Record<string, unknown>;
};

type ManagedImageState = {
  url: string;
  pixelWidth: number;
  pixelHeight: number;
};

type ManagedImageProps = ImgHTMLAttributes<HTMLImageElement> & {
  node?: unknown;
  dataMaterialImageId?: string;
  dataMaterialImageWidth?: string;
  dataMaterialImageMissing?: string;
  dataMaterialPixelWidth?: string;
  dataMaterialPixelHeight?: string;
};

const targetPattern = /^img:([a-z0-9]{8})(?:\?width=(100|[1-9][0-9]?))?$/;
const skippedNodeTypes = new Set(['code', 'inlineCode', 'html']);

function materialImageCommand(): ICommand {
  return {
    ...commands.image,
    name: 'material-image',
    keyCommand: 'material-image',
    shortcuts: undefined,
    buttonProps: {
      'aria-label': 'Bild einfügen',
      title: 'Bild einfügen',
    },
    execute: (state, api) => api.textArea.dispatchEvent(new CustomEvent('markdown-extension-event', {
      bubbles: true,
      composed: true,
      detail: {
        name: 'material-image-requested',
        selectionStart: state.selection.start,
        selectionEnd: state.selection.end,
        selectedText: state.selectedText,
      },
    })),
  };
}

function remarkMaterialImages(context: MarkdownExtensionContext) {
  return () => (tree: MarkdownNode) => transformImages(tree, imageStates(context));
}

function transformImages(node: MarkdownNode | undefined, images: Map<string, ManagedImageState>): void {
  if (!node?.children || skippedNodeTypes.has(node.type)) return;
  node.children.forEach((child) => {
    if (child.type === 'image' && typeof child.url === 'string' && child.url.startsWith('img:')) {
      const match = targetPattern.exec(child.url.trim());
      const id = match?.[1];
      const image = id ? images.get(id) : undefined;
      child.url = image?.url ?? '';
      child.data = {
        ...(child.data ?? {}),
        hProperties: {
          dataMaterialImageId: id ?? '',
          dataMaterialImageWidth: match?.[2] ?? '',
          dataMaterialImageMissing: image ? 'false' : 'true',
          dataMaterialPixelWidth: image?.pixelWidth?.toString() ?? '',
          dataMaterialPixelHeight: image?.pixelHeight?.toString() ?? '',
        },
      };
      return;
    }
    transformImages(child, images);
  });
}

function imageStates(context: MarkdownExtensionContext): Map<string, ManagedImageState> {
  const state = context.state.materialImages;
  if (!state || typeof state !== 'object' || Array.isArray(state)) return new Map();
  return new Map(Object.entries(state as Record<string, ManagedImageState>));
}

function imageSanitizeSchema(schema: typeof defaultSchema): typeof defaultSchema {
  return {
    ...schema,
    attributes: {
      ...schema.attributes,
      img: [
        ...(schema.attributes?.img ?? []),
        'dataMaterialImageId',
        'dataMaterialImageWidth',
        'dataMaterialImageMissing',
        'dataMaterialPixelWidth',
        'dataMaterialPixelHeight',
      ],
    },
  };
}

function ManagedImage(props: ManagedImageProps): ReactElement {
  const [expanded, setExpanded] = React.useState(false);
  const {
    node: _node,
    dataMaterialImageId: _id,
    dataMaterialImageWidth: width,
    dataMaterialImageMissing: missing,
    dataMaterialPixelWidth: pixelWidth,
    dataMaterialPixelHeight: pixelHeight,
    ...imageProps
  } = props;
  if (missing === 'true' || !imageProps.src) {
    return <span className="tt-material-image-missing" role="img" aria-label={imageProps.alt}>
      Bild nicht verfügbar: {imageProps.alt || 'ohne Beschreibung'}
    </span>;
  }
  const image = <img
    {...imageProps}
    className="tt-material-image"
    width={pixelWidth ? Number(pixelWidth) : undefined}
    height={pixelHeight ? Number(pixelHeight) : undefined}
    style={{ width: width ? `${width}%` : undefined }}
  />;
  return <>
    <button type="button" className="tt-material-image-open" onClick={() => setExpanded(true)}
      aria-label={`${imageProps.alt || 'Bild'} vergrößern`} title="Bild vergrößern">
      {image}
    </button>
    {expanded && <div className="tt-material-image-overlay" role="dialog" aria-modal="true"
      aria-label={imageProps.alt || 'Bild'} onClick={() => setExpanded(false)}>
      <button type="button" className="tt-material-image-close" onClick={() => setExpanded(false)}
        aria-label="Bildansicht schließen">×</button>
      <img {...imageProps} className="tt-material-image-expanded" />
    </div>}
  </>;
}

registerMarkdownExtension('tt-material-image', {
  editorCommands: () => [materialImageCommand()],
  preview: (context) => ({
    remarkPlugins: [remarkMaterialImages(context)],
    sanitizeSchema: imageSanitizeSchema,
    components: { img: ManagedImage },
  }),
});
