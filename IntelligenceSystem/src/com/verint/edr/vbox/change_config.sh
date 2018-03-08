#!/bin/bash

CONFIG_FILE="/home/efsuser/config.dat"
CONFIG_KEY="EsIndexPrefix"

# check for arguments
if [ $# -eq 0 ]
  then
    echo "No prefix argument supplied"
    exit 1
fi

echo changing to $1
# use sed to change the prefix
sed -i -e "s/\($CONFIG_KEY *= *\).*/\1$1/" $CONFIG_FILE

echo finished, bye bye
exit 100
