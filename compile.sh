#!/usr/bin/env bash
thrift_file='./scala/src/main/thrift/Rs.thrift';
python_gen_dir='./python';
scala_gen_dir='thriftgenerated';

thrift --version
echo '[INFO] Removing previously generated Python code...'
rm -rf ./python/Rs/
sleep 5
echo '[INFO] Compiling for Python'
thrift -r --gen py -out $python_gen_dir $thrift_file
echo '[DEBUG] thrift -r --gen py -out' ${python_gen_dir} ${thrift_file}
echo '[INFO] Putting compiled python files to' ${python_gen_dir}

echo '[INFO] Compiling for Scala'
cd scala/src/main
echo '[INFO] Removing previous files from' ${scala_gen_dir}
rm -rf $scala_gen_dir
cd ../../
sbt compile
cd ..
echo '[INFO] Done'