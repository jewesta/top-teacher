package de.westarps.topteacher.backend.export;

import java.util.List;

public interface PdfRenderer {

	byte[] renderA5Pdf(String html);

	byte[] imposeA5OnA4Landscape(byte[] a5Pdf);

	byte[] merge(List<byte[]> pdfs);
}
