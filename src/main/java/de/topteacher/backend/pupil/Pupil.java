package de.topteacher.backend.pupil;

public record Pupil(Integer id, String name, String surname, PupilLifecycle lifecycle) {

	public Pupil {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		if (surname == null || surname.isBlank()) {
			throw new IllegalArgumentException("surname must not be blank");
		}
		if (lifecycle == null) {
			throw new IllegalArgumentException("lifecycle must not be null");
		}
	}
}
