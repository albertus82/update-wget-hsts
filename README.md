Update Wget HSTS
=========================

[![Latest release](https://img.shields.io/github/release/albertus82/update-wget-hsts.svg)](https://github.com/albertus82/update-wget-hsts/releases/latest)
[![Build](https://github.com/albertus82/update-wget-hsts/actions/workflows/build.yml/badge.svg)](https://github.com/albertus82/update-wget-hsts/actions/workflows/build.yml)
[![Known Vulnerabilities](https://snyk.io/test/github/albertus82/update-wget-hsts/badge.svg?targetFile=pom.xml)](https://snyk.io/test/github/albertus82/update-wget-hsts?targetFile=pom.xml)

Import preloaded *HTTP Strict Transport Security* (HSTS) domains into GNU Wget.

## Minimum requirements

* Java SE Development Kit 8

## Build

`./mvnw clean verify`

## Usage

`java -jar update-wget-hsts.jar <destination> <source>`

* `<destination>`: the `wget-hsts` file to write/update.
* `<source>`: the `transport_security_state_static.json` file, or a URL pointing to it.

## Example

```sh
git clone https://github.com/albertus82/update-wget-hsts.git
cd update-wget-hsts
./mvnw clean verify
cd target
java -jar update-wget-hsts.jar ~/.wget-hsts https://github.com/chromium/chromium/raw/master/net/http/transport_security_state_static.json
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
