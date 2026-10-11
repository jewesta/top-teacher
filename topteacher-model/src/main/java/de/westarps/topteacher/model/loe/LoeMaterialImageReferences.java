package de.westarps.topteacher.model.loe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.DefinitionMap;
import org.commonmark.node.Image;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

public final class LoeMaterialImageReferences {

	private static final Parser PARSER = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();
	private static final Pattern DESTINATION = Pattern.compile("img:([a-z0-9]{8})(?:\\?([^\\s]+))?");
	private static final Pattern WIDTH = Pattern.compile("100|[1-9][0-9]?");
	private static final Pattern INLINE_DESTINATION = Pattern.compile(
			"\\]\\(\\s*<?img:([a-z0-9]{8})(?:\\?[^\\s)>]+)?>?(?:\\s+(?:\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\((?:\\\\.|[^)\\\\])*\\)))?\\s*\\)$");
	private static final Pattern IMAGE_REFERENCE = Pattern.compile(
			"!\\[((?:\\\\[\\s\\S]|[^\\\\\\]])*)\\]\\((img:[^\\s)]+)\\)");

	private LoeMaterialImageReferences() {
	}

	public static Set<String> ids(final String markdown) {
		final Set<String> ids = new LinkedHashSet<>();
		for (final Image image : managedDocument(markdown).images()) {
			target(image.getDestination()).map(Target::id).ifPresent(ids::add);
		}
		return Set.copyOf(ids);
	}

	public static Optional<Target> target(final String destination) {
		final Matcher matcher = DESTINATION.matcher(destination == null ? "" : destination.trim());
		if (!matcher.matches()) {
			return Optional.empty();
		}
		Integer width = null;
		Alignment alignment = Alignment.CENTER;
		boolean alignmentSpecified = false;
		if (matcher.group(2) != null) {
			for (final String parameter : matcher.group(2).split("&", -1)) {
				final String[] pair = parameter.split("=", -1);
				if (pair.length != 2) {
					return Optional.empty();
				}
				if (pair[0].equals("width") && width == null && WIDTH.matcher(pair[1]).matches()) {
					width = Integer.valueOf(pair[1]);
				} else if (pair[0].equals("align") && !alignmentSpecified) {
					final Optional<Alignment> parsed = Alignment.fromQueryValue(pair[1]);
					if (parsed.isEmpty()) {
						return Optional.empty();
					}
					alignment = parsed.orElseThrow();
					alignmentSpecified = true;
				} else {
					return Optional.empty();
				}
			}
		}
		return Optional.of(new Target(matcher.group(1), width, alignment));
	}

	public static String destination(final String id, final Integer width, final Alignment alignment) {
		if (id == null || !id.matches("[a-z0-9]{8}")) {
			throw new IllegalArgumentException("Material image ID must contain eight lowercase letters or digits.");
		}
		if (width != null && (width < 1 || width > 100)) {
			throw new IllegalArgumentException("Material image width must be between 1 and 100.");
		}
		final Alignment resolvedAlignment = alignment == null ? Alignment.CENTER : alignment;
		final List<String> parameters = new ArrayList<>();
		if (width != null) {
			parameters.add("width=" + width);
		}
		if (resolvedAlignment != Alignment.CENTER) {
			parameters.add("align=" + resolvedAlignment.queryValue());
		}
		return "img:" + id + (parameters.isEmpty() ? "" : "?" + String.join("&", parameters));
	}

	public static String replaceId(final String markdown, final String currentId, final String replacementId) {
		if (markdown == null || markdown.isEmpty() || currentId.equals(replacementId)) {
			return markdown == null ? "" : markdown;
		}
		final ManagedDocument document = managedDocument(markdown);
		final TreeSet<Integer> offsets = new TreeSet<>();
		for (final Image image : document.images()) {
			final Optional<Target> destination = target(image.getDestination());
			if (destination.isEmpty() || !destination.orElseThrow().id().equals(currentId)) {
				continue;
			}
			final SourceRange range = sourceRange(image);
			if (range == null) {
				continue;
			}
			final String declaration = markdown.substring(range.start(), range.end());
			final Matcher inline = INLINE_DESTINATION.matcher(declaration);
			if (inline.find() && inline.group(1).equals(currentId)) {
				offsets.add(range.start() + inline.start(1));
				continue;
			}
			final String label = referenceLabel(declaration);
			final LinkReferenceDefinition definition = label == null ? null : document.definitions().get(label);
			if (definition != null && definition.getDestination().equals(image.getDestination())) {
				final SourceRange definitionRange = sourceRange(definition);
				if (definitionRange != null) {
					final String source = markdown.substring(definitionRange.start(), definitionRange.end());
					final int closingLabel = closingBracket(source, 0);
					final int destinationStart = source.indexOf("img:" + currentId, closingLabel + 1);
					if (closingLabel >= 0 && destinationStart >= 0) {
						offsets.add(definitionRange.start() + destinationStart + 4);
					}
				}
			}
		}
		final StringBuilder replaced = new StringBuilder(markdown);
		for (final int offset : offsets.descendingSet()) {
			replaced.replace(offset, offset + currentId.length(), replacementId);
		}
		return replaced.toString();
	}

	private static ManagedDocument managedDocument(final String markdown) {
		final List<Image> images = new ArrayList<>();
		final DefinitionMap<LinkReferenceDefinition> definitions = new DefinitionMap<>(LinkReferenceDefinition.class);
		PARSER.parse(markdown == null ? "" : markdown).accept(new AbstractVisitor() {
			@Override
			public void visit(final Image image) {
				if (target(image.getDestination()).isPresent()) {
					images.add(image);
				}
				/* Nested image syntax in alternative text does not render another image. */
			}

			@Override
			public void visit(final LinkReferenceDefinition definition) {
				definitions.putIfAbsent(definition.getLabel(), definition);
			}
		});
		return new ManagedDocument(images, definitions);
	}

	private static SourceRange sourceRange(final Node node) {
		if (node.getSourceSpans().isEmpty()) {
			return null;
		}
		final int start = node.getSourceSpans().stream().mapToInt(span -> span.getInputIndex()).min().orElseThrow();
		final int end = node.getSourceSpans().stream().mapToInt(span -> span.getInputIndex() + span.getLength())
				.max().orElseThrow();
		return new SourceRange(start, end);
	}

	private static String referenceLabel(final String declaration) {
		if (!declaration.startsWith("![")) {
			return null;
		}
		final int close = closingBracket(declaration, 1);
		if (close < 0) {
			return null;
		}
		final String remaining = declaration.substring(close + 1).trim();
		if (remaining.isEmpty() || remaining.equals("[]")) {
			return declaration.substring(2, close);
		}
		return remaining.startsWith("[") && remaining.endsWith("]")
				? remaining.substring(1, remaining.length() - 1) : null;
	}

	private static int closingBracket(final String value, final int opening) {
		int depth = 0;
		for (int index = opening; index < value.length(); index++) {
			final char character = value.charAt(index);
			if (character == '\\') {
				index++;
			} else if (character == '[') {
				depth++;
			} else if (character == ']' && --depth == 0) {
				return index;
			}
		}
		return -1;
	}

	public static Optional<Occurrence> findAt(final String markdown, final int selectionStart, final int selectionEnd) {
		final String source = markdown == null ? "" : markdown;
		final int start = Math.max(0, Math.min(selectionStart, source.length()));
		final int end = Math.max(start, Math.min(selectionEnd, source.length()));
		final Matcher matcher = IMAGE_REFERENCE.matcher(source);
		while (matcher.find()) {
			final Optional<Target> target = target(matcher.group(2));
			if (target.isEmpty()) {
				continue;
			}
			final boolean containsCaret = start == end && start >= matcher.start() && start <= matcher.end();
			final boolean intersectsSelection = start < end && start < matcher.end() && end > matcher.start();
			if (containsCaret || intersectsSelection) {
				final Target parsed = target.orElseThrow();
				return Optional.of(new Occurrence(parsed.id(), unescapeDescription(matcher.group(1)), parsed.width(),
						parsed.alignment(), matcher.start(), matcher.end()));
			}
		}
		return Optional.empty();
	}

	private static String unescapeDescription(final String value) {
		final StringBuilder unescaped = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			final char character = value.charAt(index);
			if (character == '\\' && index + 1 < value.length()
					&& (value.charAt(index + 1) == '\\' || value.charAt(index + 1) == '[' || value.charAt(index + 1) == ']')) {
				unescaped.append(value.charAt(++index));
			} else {
				unescaped.append(character);
			}
		}
		return unescaped.toString();
	}

	public enum Alignment {
		LEFT,
		CENTER,
		RIGHT;

		public String queryValue() {
			return name().toLowerCase(Locale.ROOT);
		}

		private static Optional<Alignment> fromQueryValue(final String value) {
			return List.of(values()).stream().filter(alignment -> alignment.queryValue().equals(value)).findFirst();
		}
	}

	public record Target(String id, Integer width, Alignment alignment) {
	}

	public record Occurrence(String id, String description, Integer width, Alignment alignment, int start, int end) {
	}

	private record ManagedDocument(List<Image> images, DefinitionMap<LinkReferenceDefinition> definitions) {
	}

	private record SourceRange(int start, int end) {
	}
}
