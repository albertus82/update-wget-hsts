package com.github.albertus82.wget.picocli;

import com.github.albertus82.wget.util.BuildInfo;

import picocli.CommandLine.IVersionProvider;

public class VersionProvider implements IVersionProvider {

	@Override
	public String[] getVersion() {
		return new String[] { "${COMMAND-FULL-NAME} " + BuildInfo.getProperty("project.version") };
	}

}
