package de.westarps.topteacher.model.loe;

/**
 * Image discovery without loading binary data or exposing internal hashes.
 */
public record LoeMaterialImageMetadata(String id, String mediaType, int pixelWidth, int pixelHeight, long byteSize) {
}
