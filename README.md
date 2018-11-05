# update-wget-hsts-database

[![Build Status](https://travis-ci.org/Albertus82/update-wget-hsts-database.svg?branch=master)](https://travis-ci.org/Albertus82/update-wget-hsts-database)
[![Build status](https://ci.appveyor.com/api/projects/status/github/Albertus82/update-wget-hsts-database?branch=master&svg=true)](https://ci.appveyor.com/project/Albertus82/update-wget-hsts-database)

Import preloaded *HTTP Strict Transport Security* (HSTS) domains into GNU Wget.

## Requirements

* Java Runtime Environment 1.8
* [Apache Maven](https://maven.apache.org) 3.3.x

## Build

`mvn clean package`

## Usage

`java -jar  update-wget-hsts-database.jar DESTINATION SOURCE`

* `DESTINATION`: the `wget-hsts` file to write/update.
* `SOURCE`: the `transport_security_state_static.json` file, or a URL pointing to it.

## Example

```sh
git clone https://github.com/Albertus82/update-wget-hsts-database.git
cd update-wget-hsts-database
mvn clean package
cd target
java -jar update-wget-hsts-database.jar ~/.wget-hsts https://cs.chromium.org/codesearch/f/chromium/src/net/http/transport_security_state_static.json
```
