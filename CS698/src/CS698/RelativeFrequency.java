/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package CS698;

import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

import javafx.util.Pair;

public class RelativeFrequency {

  @SuppressWarnings("rawtypes")
public static class TokenizerMapper 
       extends Mapper<Object, Text, Pair, IntWritable>{
    
    /*private Text word = new Text();
    private Text neighbor = new Text();
    private Text allvalue = new Text();*/
      
    @SuppressWarnings("unchecked")
	public void map(Object key, Text value, Context context
                    ) throws IOException, InterruptedException {
      StringTokenizer itr = new StringTokenizer(value.toString());
      String word = null;
      String neighbor = null;
      String allvalue = "*";
      while (itr.hasMoreTokens()) {
    	  neighbor = itr.nextToken();
    	  if(word != null){  		  
        	  context.write(new Pair (word , allvalue), new IntWritable(1));
        	  context.write(new Pair (word , neighbor), new IntWritable(1));
    	  } 
    	  word = neighbor;
      }
    }
  }
  
  /*public static class RFParitioner extends Partitioner< Pair , IntWritable>{

	@Override
	public int getPartition(Pair wordpair, IntWritable arg1, int arg2) {
		return wordpair.getKey().hashCode();
	}  
  }*/
  
  
  public static class IntSumReducer 
       extends Reducer<Pair,IntWritable,Pair,IntWritable> {
    private IntWritable result = new IntWritable();

    public void reduce(Pair key, Iterable<IntWritable> values, 
                       Context context
                       ) throws IOException, InterruptedException {	
    	int sum = 0;
    	int total = 0;
    	for ( IntWritable val : values){
    		sum = sum + val.get();
    	}
    	if(key.getValue() == "*"){
    		total = sum;
    	}
    	else{
    		context.write(key, new IntWritable(sum / total));
    	}   
      }        
  }

  public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
    if (otherArgs.length < 2) {
      System.err.println("Usage: MissingPoker <in> [<in>...] <out>");
      System.exit(2);
    }
    Job job = Job.getInstance(conf, "Missing Poker");
    job.setJarByClass(RelativeFrequency.class);
    job.setMapperClass(TokenizerMapper.class);
   // job.setGroupingComparatorClass(cls);
    job.setCombinerClass(IntSumReducer.class);
    job.setReducerClass(IntSumReducer.class);
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(IntWritable.class);
    for (int i = 0; i < otherArgs.length - 1; ++i) {
      FileInputFormat.addInputPath(job, new Path(otherArgs[i]));
    }
    FileOutputFormat.setOutputPath(job,
      new Path(otherArgs[otherArgs.length - 1]));
    System.exit(job.waitForCompletion(true) ? 0 : 1);
  }
}
