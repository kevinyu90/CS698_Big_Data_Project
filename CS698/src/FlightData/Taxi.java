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
package FlightData;

import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class Taxi {

	public static class TaxiMapper 
		extends Mapper<LongWritable, Text, Text, Text>{
    

		private Text origin = new Text();
		private Text dest = new Text();
      
		public void map(LongWritable key, Text value, Context context
                    ) throws IOException, InterruptedException {
    	

			 
			if(key.get() > 0){

				int taxiIn = 0;
				int taxiOut = 0;
				String line = null;
					line = value.toString();
					String str[] = line.split(",");
					origin.set(str[16]);
					dest.set(str[17]);
					try {
						taxiIn = Integer.parseInt(str[19]);
						if(taxiIn > 0){
							context.write(dest, new Text(taxiIn+",1"));
						}
						taxiOut = Integer.parseInt(str[20]);
						if(taxiOut > 0){
							context.write(origin, new Text(taxiOut+",1"));
						}

					}
					catch (java.lang.NumberFormatException e) {
						return;
					}
					
				
			}
    	
    	
    	

		}
	}
	
		public static class TaxiCombiner extends Reducer<Text, Text, Text, Text> {

		    
		    public void reduce(Text key, Iterable<Text> value, Context context)
		        throws IOException, InterruptedException {
		    	int totalTaxi = 0;
		    	int count = 0;
		    	for(Text val : value){
		    		String[] s1 = val.toString().split(",");
		    		totalTaxi += Integer.parseInt(s1[0]);
		    		count += Integer.parseInt(s1[1]);
		    	}
		    	System.out.println("Combine"+ (new String(totalTaxi+","+count)));
		    	context.write(key, new Text(new String(totalTaxi + "," + count)));
		    	
		    	
		    	

		    }
		  }	
		
	  public static class TaxiReducer 
	       extends Reducer<Text,Text,Text,DoubleWritable> {
		    
		
		    public void reduce(Text key, Iterable<Text> values, 
		                       Context context
		                       ) throws IOException, InterruptedException {
			      int sum = 0;
			      int count = 0;
			      for (Text val : values) {
			    	  String[] s = val.toString().split(",");  
			    	  sum += Integer.parseInt(s[0]);  
			    	  count += Integer.parseInt(s[1]); 
			      }
			      
			      System.out.println("reduce"+ (new String(key+","+(sum*1.0/count))));
			      context.write(key, new DoubleWritable(sum*1.0/count));
		    }
	  }

  public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
    if (otherArgs.length < 2) {
      System.err.println("Usage: Taxi <in> [<in>...] <out>");
      System.exit(2);
    }
    Job job = Job.getInstance(conf, "Taxi Time");
    job.setJarByClass(Taxi.class);
    job.setMapperClass(TaxiMapper.class);
    job.setCombinerClass(TaxiCombiner.class);
    job.setReducerClass(TaxiReducer.class);
    job.setMapOutputKeyClass(Text.class);
    job.setMapOutputValueClass(Text.class);
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
