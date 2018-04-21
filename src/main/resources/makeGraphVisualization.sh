#!/usr/bin/env bash

#args
#$1 output folder
#$2 output file name

hadoop fs -getmerge $1 data.txt
echo "digraph Giraph {" > giraph.dot
cat data.txt >> giraph.dot
echo "}" >> giraph.dot
circo -T png -o $2 giraph.dot

rm data.txt
rm giraph.dot
rm .data.txt.crc
