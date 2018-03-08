#!/bin/bash
# don't forget to run dos2unix on the file since it came from win

# kill rmiregistry if it is running
PID=`ps -eaf | grep rmiregistry | grep -v grep | awk '{print $2}'`
if [[ "" !=  "$PID" ]]; then
  echo "killing rmiregistry $PID"
  kill -9 $PID
 sleep 2s
fi

# kill controller if it is running
PID=`ps -eaf | grep DDPController | grep -v grep | awk '{print $2}'`
if [[ "" !=  "$PID" ]]; then
  echo "killing controller $PID"
  kill -9 $PID
 sleep 2s
fi


# run rmi registry
echo "starting rmiregistry"
rmiregistry -J-Djava.class.path=.:DDPController2.0.jar &

sleep 2s

# run controller
sudo java -jar DDPController2.0.jar

