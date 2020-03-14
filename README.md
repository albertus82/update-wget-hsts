# Wget Update HSTS Database

[![Build Status](https://travis-ci.org/albertus82/wget-update-hsts-database.svg?branch=master)](https://travis-ci.org/albertus82/wget-update-hsts-database)
[![Build status](https://ci.appveyor.com/api/projects/status/github/albertus82/wget-update-hsts-database?branch=master&svg=true)](https://ci.appveyor.com/project/albertus82/wget-update-hsts-database)

Import preloaded *HTTP Strict Transport Security* (HSTS) domains into GNU Wget.

## Minimum requirements

* Java SE Development Kit 8
* [Apache Maven](https://maven.apache.org) 3.3.x

## Build

`mvn clean verify`

## Usage

`java -jar wget-update-hsts-database.jar DESTINATION SOURCE`

* `DESTINATION`: the `wget-hsts` file to write/update.
* `SOURCE`: the `transport_security_state_static.json` file, or a URL pointing to it.

## Example

```sh
git clone https://github.com/albertus82/wget-update-hsts-database.git
cd wget-update-hsts-database
mvn clean verify
cd target
java -jar wget-update-hsts-database.jar ~/.wget-hsts https://cs.chromium.org/codesearch/f/chromium/src/net/http/transport_security_state_static.json
```

### Output

```
Downloading 'https://cs.chromium.org/codesearch/f/chromium/src/net/http/transport_security_state_static.json'... 10454 kB fetched
Parsing source file '/tmp/hsts-1508536545025252107.json'... 96703 entries found
Parsing destination file '/home/pi/.wget-hsts'... 90899 entries found
Computing entries to delete... 559
Computing entries to update... none
Computing entries to insert... 6105
Collecting entries to write... done
Backing up existing file '/home/pi/.wget-hsts'... -> '/home/pi/.wget-hsts.bak.gz'
Updating destination file '/home/pi/.wget-hsts'... done
```
