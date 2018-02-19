#!/usr/bin/env bash

set -e

echo 'Creating bintray credentials'

mkdir $HOME/.bintray
cat > $HOME/.bintray/.credentials <<EOF
realm = Bintray API Realm
host = api.bintray.com
user = $BINTRAY_USER
password = $BINTRAY_PASS
EOF
