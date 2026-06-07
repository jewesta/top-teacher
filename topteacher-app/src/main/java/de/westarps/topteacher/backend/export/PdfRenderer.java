package de.westarps.topteacher.backend.export;

public interface PdfRenderer {

	byte[] renderA5Pdf(String html);

	byte[] imposeA5OnA4Landscape(byte[] a5Pdf);
}
