# update-wget-hsts-database

Import preloaded HTTP Strict Transport Security (HSTS) domains in Wget.

## Build

`mvn clean package`

## Usage

`java -jar  update-wget-hsts-database.jar DESTINATION SOURCE`

* `DESTINATION`: the `~/.wget-hsts` file to write/update.
* `SOURCE`: the `transport_security_state_static.json file`, or a URL pointing to it.

### Example
`java -jar update-wget-hsts-database.jar ~/.wget-hsts https://cs.chromium.org/codesearch/f/chromium/src/net/http/transport_security_state_static.json`
