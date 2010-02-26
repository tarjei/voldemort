#!/bin/bash

#
#   Copyright 2008-2009 LinkedIn, Inc
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

if [ $# -gt 1 ];
then
	echo 'USAGE: bin/voldemort-server.sh [voldemort_home]'
	exit 1
fi

base_dir=$(dirname $0)/..

for file in $base_dir/dist/*.jar;
do
  CLASSPATH=$CLASSPATH:$file
done

for file in $base_dir/lib/*.jar;
do
  CLASSPATH=$CLASSPATH:$file
done

for file in $base_dir/contrib/hadoop-store-builder/lib/*.jar;
do
  CLASSPATH=$CLASSPATH:$file
done

CLASSPATH=$CLASSPATH:$base_dir/dist/resources
set -x
# import   config settings from config directory
if [ -f "$1/config/server-env.sh" ]; then 
    . $1/config/server-env.sh
fi


if [ -z "$VOLD_OPTS" ]; then
  VOLD_OPTS="-Xmx2G -server -Dcom.sun.management.jmxremote"
fi
# set the log4j.config. Use -Dlog4j.debug=true if you got problems
if [ -z $LOG_CONFIG ] ; then
    LOG_CONFIG=$(readlink -f "file://$1/src/java/log4j.properties" )
    if [ ! -f $LOG_CONFIG ]; then
        echo "No logfile found. starting without it."
        LOG_CONFIG=""
    fi
fi

java  $VOLD_OPTS -cp $CLASSPATH  -Dlog4j.configuration=$LOG_CONFIG voldemort.server.VoldemortServer $@
