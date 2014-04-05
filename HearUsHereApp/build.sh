#!/bin/bash

if [ "$1" == "" ]; then
    echo "Usage: $0 <project>|universal"
    echo
    exit 1
fi

if [ "$1" == "universal" ]; then
    ant prepare-universal release
else
    ant prepare-project -Dproject=$1 release
fi

