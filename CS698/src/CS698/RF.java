package CS698;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.io.WritableUtils;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.partition.HashPartitioner;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.util.GenericOptionsParser;

import CS698RF.PairKey;
import CS698RF.PairValue;
import CS698RF.Record;

public class RF {
  
  public static class MapClass 
         extends Mapper<LongWritable, Text, CompositeKey, Record<Integer>> {
    
    private final CompositeKey key = new CompositeKey();
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
  
  public static class Combiner extends Reducer<CompositeKey, Record<Integer>, CompositeKey, Record<Integer>> {
    final Record<Integer> val = new Record<Integer>();
    
    public void reduce(CompositeKey key, Iterable<Record<Integer>> values, Context context)
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
  
  public static class WordPairReducer extends Reducer<CompositeKey, Record<Integer>, String, DoubleWritable> {
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

    public void reduce(CompositeKey key, Iterable<Record<Integer>> values, Context context)
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
    
    job.setJarByClass(RF.class);
    job.setMapperClass(MapClass.class);
    job.setCombinerClass(Combiner.class);

    job.setSortComparatorClass(CompositeKeyComparator.class);
    job.setPartitionerClass(ActualKeyPartitioner.class);
    job.setGroupingComparatorClass(ActualKeyGroupingComparator.class);

    job.setMapOutputKeyClass(CompositeKey.class);
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

  public class CompositeKey implements WritableComparable<CompositeKey> {

	  private String first;
	  private String second;

	  public CompositeKey() {
	  }

	  public CompositeKey(String left, String right) {

	    this.first = left;
	    this.second = right;
	  }

	  @Override
	  public String toString() {

	    return (new StringBuilder()).append(first).append(' ').append(second).toString();
	  }

	  @Override
	  public void readFields(DataInput in) throws IOException {

	    first = WritableUtils.readString(in);
	    second = WritableUtils.readString(in);
	  }

	  @Override
	  public void write(DataOutput out) throws IOException {

	    WritableUtils.writeString(out, first);
	    WritableUtils.writeString(out, second);
	  }

	  @Override
	  public int compareTo(CompositeKey o) {

	    int result = first.compareTo(o.first);
	    if (0 == result) {
	      result = second.compareTo(o.second);
	    }
	    return result;
	  }
	  
	  public String getFirstWord() {
	    
	    return first;
	    }
	     
	    public void setFirstWord(String str) {
	     
	    this.first = str;
	    }
	     
	    public String getSecondWord() {
	     
	    return second;
	    }
	     
	    public void setSecondWord(String wd) {
	     
	    this.second = wd;
	    }

	    public void setWord(String left, String right) {
	      // TODO Auto-generated method stub
	      this.first = left;
	      this.second = right;
	    }
	}
  
  
  public class ActualKeyPartitioner extends Partitioner<CompositeKey, Record<Integer>> {

	  HashPartitioner<Text, Record<Integer>> hashPartitioner = new HashPartitioner<Text, Record<Integer>>();
	  Text newKey = new Text();

	  @Override
	  public int getPartition(CompositeKey key, Record<Integer> value, int numReduceTasks) {

	    try {
	      // Execute the default partitioner over the first part of the key
	      newKey.set(key.getFirstWord());
	      return hashPartitioner.getPartition(newKey, value, numReduceTasks);
	    } catch (Exception e) {
	      e.printStackTrace();
	      return (int) (Math.random() * numReduceTasks); // this would return a
	                                                     // random value in the
	                                                     // range
	      // [0,numReduceTasks)
	    }
	  }
	}
  
  public class ActualKeyGroupingComparator extends WritableComparator {

	  protected ActualKeyGroupingComparator() {

	    super(CompositeKey.class, true);
	  }

	  @SuppressWarnings("rawtypes")
	  @Override
	  public int compare(WritableComparable w1, WritableComparable w2) {

	    CompositeKey key1 = (CompositeKey) w1;
	    CompositeKey key2 = (CompositeKey) w2;

	    return key1.getFirstWord().compareTo(key2.getFirstWord());
	  }
	}
  
  public class CompositeKeyComparator extends WritableComparator {
	  protected CompositeKeyComparator() {
	    super(CompositeKey.class, true);
	  }

	  @SuppressWarnings("rawtypes")
	  @Override
	  public int compare(WritableComparable w1, WritableComparable w2) {

	    CompositeKey key1 = (CompositeKey) w1;
	    CompositeKey key2 = (CompositeKey) w2;

	    // (first check on udid)
	    int compare = key1.getFirstWord().compareTo(key2.getFirstWord());

	    if (compare == 0) {
	      // only if we are in the same input group should we try and sort by value
	      // (datetime)
	      return key1.getSecondWord().compareTo(key2.getSecondWord());
	    }

	    return compare;
	  }
	}
  
  public class CompositeValue implements WritableComparable<CompositeValue>{

	  private Text text = new Text();
	  private IntWritable val = new IntWritable();
	  @Override
	  public void write(DataOutput out) throws IOException {
	    text.write(out);
	    val.write(out);
	    
	  }

	  @Override
	  public void readFields(DataInput in) throws IOException {
	    text.readFields(in);
	    val.readFields(in);
	    
	  }
	  
	  public String toString() {

	    return (new StringBuilder()).append(text).append(' ').append(val).toString();
	  }

	  @Override
	  public int compareTo(CompositeValue o) {
	    int result = text.compareTo(o.text);
	    if (0 == result) {
	      result = val.compareTo(o.val);
	    }
	    return result;
	  }

	  public Text getText() {
	    return text;
	  }

	  public void setText(Text text) {
	    this.text = text;
	  }

	  public IntWritable getVal() {
	    return val;
	  }

	  public void setVal(IntWritable val) {
	    this.val = val;
	  }

	}

  
  public class Record<E extends Number> implements Comparable<Record<E>>, WritableComparable<Record<E>> {
	  private E value;
	  private String key="";

	  Record(E value, String key) {
	    this.value = value;
	    this.setKey(key);
	  }

	  public Record() {
	 
	  }

	  @Override
	  public int compareTo(Record<E> rc) {
	    if (this.value.doubleValue() > rc.value.doubleValue()) {
	      return 1;
	    } 
	    else if (this.value.doubleValue() <rc.value.doubleValue()){
	      return -1;
	    }
	    else {
	      return (this.getKey().compareTo(rc.getKey()));
	    }
	  }

	  public E getValue() {
	    return value;
	  }

	  public void setValue(E value) {
	    this.value = value;
	  }

	  public String getKey() {
	    return key;
	  }

	  public void setKey(String key) {
	    this.key = new String(key);
	  }

	  @Override
	  public void write(DataOutput out) throws IOException {
	    WritableUtils.writeString(out, key);
	    out.writeInt(value.intValue());
	  }

	  @SuppressWarnings("unchecked")
	  @Override
	  public void readFields(DataInput in) throws IOException {
	    key = WritableUtils.readString(in);
	    Integer i = in.readInt();
	    setValue((E) i);
	  }
	}
}
