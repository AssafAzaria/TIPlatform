#!/bin/bash

# simply restart the server to get the changed config
echo killing efs_filesmonitor
killall efs_filesmonitor

# wait for 20 seconds
sleep 20s

# run again
echo restarting efs_filesmonitor
cd /home/efsuser/
./efs_filesmonitor

exit 0

