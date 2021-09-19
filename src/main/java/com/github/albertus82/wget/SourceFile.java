package com.github.albertus82.wget;

import java.nio.file.Path;

import lombok.Value;

@Value
class SourceFile {
	Path path;
	boolean temp;
}
