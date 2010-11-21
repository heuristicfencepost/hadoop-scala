package org.apache.hadoop.examples

import java.util.Random

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.conf.Configured
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.LongWritable
import org.apache.hadoop.io.Text
import org.apache.hadoop.mapreduce._
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat
import org.apache.hadoop.mapreduce.lib.map.InverseMapper
import org.apache.hadoop.mapreduce.lib.map.RegexMapper
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat
import org.apache.hadoop.mapreduce.lib.reduce.LongSumReducer
import org.apache.hadoop.util.Tool
import org.apache.hadoop.util.ToolRunner

class Grep extends Configured with Tool {

  def run(args:Array[String]):Int = {
    if (args.length < 3) {
      System.out.println("Grep <inDir> <outDir> <regex> [<group>]")
      ToolRunner.printGenericCommandUsage(System.out)
      return 2
    }

    val tempDir =
      new Path("grep-temp-"+
          Integer.toString(new Random().nextInt(Integer.MAX_VALUE)))

    val conf = getConf();
    conf.set(RegexMapper.PATTERN, args(2))
    if (args.length == 4)
      conf.set(RegexMapper.GROUP, args(3))

    val grepJob = new Job(conf)

    try {

      grepJob setJobName "grep-search"

      FileInputFormat.setInputPaths(grepJob, args(0))

      grepJob setMapperClass classOf[RegexMapper[_]]

      grepJob setCombinerClass classOf[LongSumReducer[_]]
      grepJob setReducerClass classOf[LongSumReducer[_]]

      FileOutputFormat.setOutputPath(grepJob, tempDir);
      grepJob setOutputFormatClass classOf[SequenceFileOutputFormat[_,_]]
      grepJob setOutputKeyClass classOf[Text]
      grepJob setOutputValueClass classOf[LongWritable]

      grepJob waitForCompletion true

      val sortJob = new Job(conf)
      sortJob setJobName "grep-sort"

      FileInputFormat.setInputPaths(sortJob, tempDir)
      sortJob setInputFormatClass classOf[SequenceFileInputFormat[_,_]]

      sortJob setMapperClass classOf[InverseMapper[_,_]]

      sortJob setNumReduceTasks 1                 // write a single file
      FileOutputFormat.setOutputPath(sortJob, new Path(args(1)))
      sortJob setSortComparatorClass          // sort by decreasing freq
        classOf[LongWritable.DecreasingComparator]

      sortJob waitForCompletion true
    }
    finally {
      FileSystem.get(conf).delete(tempDir, true)
    }
    0
  }
}

object Grep {

  def main(args:Array[String]):Unit = {
    val res = ToolRunner.run(new Configuration(), new Grep(), args);
    System.exit(res);
  }
}