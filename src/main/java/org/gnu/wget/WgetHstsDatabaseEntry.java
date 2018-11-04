package org.gnu.wget;

import lombok.Data;

@Data
public class WgetHstsDatabaseEntry {

	private String hostname;
	private int port = 0;
	private boolean inclSubdomains;
	private int created;
	private int maxAge;

	@Override
	public String toString() {
		return String.format("%s\t%d\t%d\t%d\t%d", hostname, port, inclSubdomains ? 1 : 0, created, maxAge);
	}

}
