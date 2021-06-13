Wget Update HSTS Database
=========================

[![Build Status](https://github.com/albertus82/wget-update-hsts-database/workflows/build/badge.svg)](https://github.com/albertus82/wget-update-hsts-database/actions)
[![Build status](https://ci.appveyor.com/api/projects/status/github/albertus82/wget-update-hsts-database?branch=master&svg=true)](https://ci.appveyor.com/project/albertus82/wget-update-hsts-database)
[![Known Vulnerabilities](https://snyk.io/test/github/albertus82/wget-update-hsts-database/badge.svg?targetFile=pom.xml)](https://snyk.io/test/github/albertus82/wget-update-hsts-database?targetFile=pom.xml)

Import preloaded *HTTP Strict Transport Security* (HSTS) domains into GNU Wget.

## Minimum requirements

* Java SE Development Kit 8

## Build

`./mvnw clean verify`

## Usage

`java -jar wget-update-hsts-database.jar <destination> <source>`

* `<destination>`: the `wget-hsts` file to write/update.
* `<source>`: the `transport_security_state_static.json` file, or a URL pointing to it.

## Example

```sh
git clone https://github.com/albertus82/wget-update-hsts-database.git
cd wget-update-hsts-database
mvn clean verify
cd target
java -jar wget-update-hsts-database.jar ~/.wget-hsts https://github.com/chromium/chromium/raw/master/net/http/transport_security_state_static.json
```

### Output

```
Downloading 'https://github.com/chromium/chromium/raw/master/net/http/transport_security_state_static.json'... 10454 kB fetched
Parsing source file '/tmp/hsts-1508536545025252107.json'... 96703 entries found
Parsing destination file '/home/pi/.wget-hsts'... 90899 entries found
Computing entries to delete... 559
Computing entries to update... none
Computing entries to insert... 6105
Collecting entries to write... done
Backing up existing file '/home/pi/.wget-hsts'... -> '/home/pi/.wget-hsts.bak.gz'
Updating destination file '/home/pi/.wget-hsts'... done
```
