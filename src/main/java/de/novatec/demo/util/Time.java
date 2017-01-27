package de.novatec.demo.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum Time {
	HOURS_IN_DAY(24), MINUTES_IN_HOUR(60), SECONDS_IN_MINUTE(60), MILLISECONDS_IN_SECONDS(1000);
	@Getter
	private int value;

	@AllArgsConstructor
	public enum START_OF_WORKDAY {
		HOUR(8), MINUTE(00), SECOND(0);
		@Getter
		private int value;
	}

	@AllArgsConstructor
	public enum END_OF_WORKDAY {
		HOUR(17), MINUTE(00);
		@Getter
		private int value;
	}

	@AllArgsConstructor
	public enum DISTRIBUTION {
		SECOND(MILLISECONDS_IN_SECONDS.getValue()), MINUTE(SECONDS_IN_MINUTE.getValue() * SECOND.getValue()), HOUR(
				MINUTES_IN_HOUR.getValue() * MINUTE.getValue()), DAY(
						HOURS_IN_DAY.getValue() * HOUR.getValue()), WEEK(7 * DAY.getValue());
		@Getter
		private long value;
	}
}