package org.gnu.wget;

import lombok.Data;

@Data
public class WgetHstsDatabaseEntry {

	private String hostname;
	private int port = 0;
	private boolean inclSubdomains;
	private int created;
	private int maxAge;

}
