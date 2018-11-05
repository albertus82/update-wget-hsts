package org.gnu.wget;

import lombok.Data;
import lombok.NonNull;

@Data
public class WgetHstsDatabaseEntry {

	@NonNull
	private final String hostname;

	private int port;

	private boolean includeSubdomains;

	private int created;

	private int maxAge;

}
