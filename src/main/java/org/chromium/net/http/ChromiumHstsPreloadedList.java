package org.chromium.net.http;

import java.util.List;

import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class ChromiumHstsPreloadedList {

	@SerializedName("pinsets")
	private List<ChromiumHstsPreloadedPinset> pinsets = null;

	@SerializedName("entries")
	private List<ChromiumHstsPreloadedEntry> entries = null;

}
