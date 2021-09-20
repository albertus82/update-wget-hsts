package com.github.albertus82.wget;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAccessor;
import java.util.logging.Level;

import lombok.extern.java.Log;
import picocli.CommandLine.IVersionProvider;

@Log
public class VersionProvider implements IVersionProvider {

	@Override
	public String[] getVersion() {
		return new String[] { "${COMMAND-FULL-NAME} " + BuildInfo.getProperty("project.version") + " (" + DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(getVersionTimestamp()) + ')' };
	}

	private static TemporalAccessor getVersionTimestamp() {
		try {
			return DateTimeFormatter.ISO_ZONED_DATE_TIME.parse(BuildInfo.getProperty("version.timestamp"));
		}
		catch (final RuntimeException e) {
			log.log(Level.FINE, "Invalid version timestamp, falling back to current datetime:", e);
			return ZonedDateTime.now();
		}
	}

}
