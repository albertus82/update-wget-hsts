package org.gnu.wget;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class WgetHstsEntry {

	@NonNull
	private final String hostname;

	private int port;

	private boolean includeSubdomains;

	private int created;

	private int maxAge;

	public String toString() {
		return String.format("%s\t%d\t%d\t%d\t%d", hostname, port, includeSubdomains ? 1 : 0, created, maxAge);
	}

}
