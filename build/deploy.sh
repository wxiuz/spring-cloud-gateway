#!/bin/bash

cd "$( dirname "$0" )"
cd ..

current_dir=`pwd`
echo "current directory : $current_dir"

# VERSION, need to update per version.
version="gateway-2.0.2.1002"
echo ${version}"."`date +%s`  > ${current_dir}/VERSION

mvn clean package -P km -DskipTests -Dmaven.javadoc.skip=true
