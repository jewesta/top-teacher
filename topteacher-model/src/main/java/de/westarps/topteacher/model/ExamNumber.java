package de.westarps.topteacher.model;

public record ExamNumber(int number, boolean makeupExam) implements HasDisplayName {

	public ExamNumber {
		if (number < 1) {
			throw new IllegalArgumentException("number must be positive");
		}
	}

	@Override
	public String getDisplayName() {
		return number + "." + (makeupExam ? " (NK)" : "");
	}

	public String getHeaderDisplayName() {
		return "Klausur Nr. " + number;
	}
}
