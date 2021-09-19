package com.github.albertus82.wget;

import picocli.CommandLine.IVersionProvider;

public class VersionProvider implements IVersionProvider {

	@Override
	public String[] getVersion() {
		return new String[] { "${COMMAND-FULL-NAME} " + BuildInfo.getProperty("project.version") };
	}

}
