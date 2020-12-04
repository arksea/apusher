cd ..
cd tools
set CLASSPATH=./lib/*;./config
groovy -Dfile.encoding=utf-8 test
