package CS698RF;

import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.util.GenericOptionsParser;

public class WordRF {
  
  public static class MapClass 
         extends Mapper<LongWritable, Text, PairKey, Record<Integer>> {
    
    private final PairKey key = new PairKey();
    private final Record<Integer> val = new Record<Integer>();
    Pattern WORD_PT = Pattern.compile("^\\W*(\\w+[\\W\\w]*\\w)\\W*$", Pattern.UNICODE_CHARACTER_CLASS);
    @Override
    public void map(LongWritable inKey, Text inValue, 
                    Context context) throws IOException, InterruptedException {
      StringTokenizer itr = new StringTokenizer(inValue.toString());
      String left = null;
      String right = null;
      while (itr.hasMoreTokens()) {
        if (itr.hasMoreTokens()) {
          left = itr.nextToken();
          Matcher m = WORD_PT.matcher(left);
          if(!m.find()) {
            continue;
          }
          left = m.group(1);
        }
        while (itr.hasMoreTokens()) {
          right = itr.nextToken();
          Matcher m = WORD_PT.matcher(right);
          if(!m.find()) {
            break;
          }
          right = m.group(1);          
          key.setWord(left, right);
          val.setKey(right);
          val.setValue(1);
          context.write(key, val);
          left = right;
        }
      }   
    }
  }
  
  public static class Combiner extends Reducer<PairKey, Record<Integer>, PairKey, Record<Integer>> {
    final Record<Integer> val = new Record<Integer>();
    
    @Override
    public void reduce(PairKey key, Iterable<Record<Integer>> values, Context context)
        throws IOException, InterruptedException {
      int subtotal = 0;
      for (Record<Integer> value : values) {
          subtotal += value.getValue();
      }
      val.setKey(key.getSecondWord());
      val.setValue(subtotal);
      context.write(key, val);
    }
  }
  
  public static class WordPairReducer extends Reducer<PairKey, Record<Integer>, String, DoubleWritable> {
    DoubleWritable dv = new DoubleWritable();
    StringBuffer sb = new StringBuffer();
    TreeSet<Record<Double>> treeSet = new TreeSet<Record<Double>>();
    double min_frequency = 0;
    int MIN_OCCURRENCE = 150;
        
    protected void cleanup(Context context) throws IOException, InterruptedException {
      while (!treeSet.isEmpty()) {
        Record<Double> rc = treeSet.pollLast();
        dv.set(rc.getValue());
        context.write(rc.getKey(), dv);
      }
    }

    @Override
    public void reduce(PairKey key, Iterable<Record<Integer>> values, Context context)
        throws IOException, InterruptedException {
      int total = 0;
      int subtotal = 0;
      String currentText = "";
      double rf;
      ArrayList<Record<Integer>> array = new ArrayList<Record<Integer>>();
      for (Record<Integer> value : values) {
        total += (int)value.getValue();
        Record<Integer> r = new Record<Integer>(value.getValue(), value.getKey());
        array.add(r);
      }
      if (total < MIN_OCCURRENCE) {
        return;
      }
      for (Record<Integer> value : array) {
        if (subtotal == 0) {
          currentText = value.getKey();
        }

        if (value.getKey().equals(currentText)) {
          subtotal += value.getValue();
        } else {
          rf = ((double) subtotal * 100) / total;
          sb = new StringBuffer(key.getFirstWord());

          if (rf > min_frequency) {
            treeSet.add(new Record<Double>(rf, sb.append(" ").append(currentText)
                .append("(").append(subtotal).append("/").append(total).append(")").toString()));
            if (treeSet.size() > 100) {
              treeSet.pollFirst();
              min_frequency = treeSet.first().getValue();
            }
          }
          subtotal = value.getValue();
          currentText = value.getKey();
        }
      }
      rf = ((double) subtotal * 100) / total;
      sb = new StringBuffer(key.getFirstWord());
      if (rf > min_frequency) {
        treeSet.add(new Record<Double>(rf, sb.append(" ").append(currentText)
            .append("(").append(subtotal).append("/").append(total).append(")").toString()));
        if (treeSet.size() > 100) {
          treeSet.pollFirst();
        }
      }
    }
  }

  public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
    if (otherArgs.length != 2) {
      System.err.println("Usage: WordPairFrequency <in> <out>");
      System.exit(2);
    }
    long start = System.currentTimeMillis();
    
    Job job = Job.getInstance(conf, "WordPairFrequency");
    
    job.setJarByClass(WordRF.class);
    job.setMapperClass(MapClass.class);
    job.setCombinerClass(Combiner.class);

    job.setSortComparatorClass(PairKeyComparator.class);
    job.setPartitionerClass(RealKeyPartitioner.class);
    job.setGroupingComparatorClass(RealKeyGroupComparator.class);

    job.setMapOutputKeyClass(PairKey.class);
    job.setMapOutputValueClass(Record.class);
    
    job.setReducerClass(WordPairReducer.class);
    job.setOutputKeyClass(String.class);
    job.setOutputValueClass(DoubleWritable.class);
    
    FileInputFormat.addInputPath(job, new Path(otherArgs[0]));
    FileOutputFormat.setOutputPath(job, new Path(otherArgs[1]));
    boolean b = job.waitForCompletion(true);
    
    System.out.println("Elapsed time is " + (System.currentTimeMillis() -  start) + " ms");
    System.exit(b ? 0 : 1);
  }

}
