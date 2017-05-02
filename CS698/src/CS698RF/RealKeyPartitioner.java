package CS698RF;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.lib.partition.HashPartitioner;

public class RealKeyPartitioner extends Partitioner<PairKey, Record<Integer>> {

  HashPartitioner<Text, Record<Integer>> hashPartitioner = new HashPartitioner<Text, Record<Integer>>();
  Text newKey = new Text();

  @Override
  public int getPartition(PairKey key, Record<Integer> value, int numReduceTasks) {

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
