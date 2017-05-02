package CS698RF;

import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;

public class RealKeyGroupComparator extends WritableComparator {

  protected RealKeyGroupComparator() {

    super(PairKey.class, true);
  }

  @SuppressWarnings("rawtypes")
  @Override
  public int compare(WritableComparable w1, WritableComparable w2) {

    PairKey key1 = (PairKey) w1;
    PairKey key2 = (PairKey) w2;

    return key1.getFirstWord().compareTo(key2.getFirstWord());
  }
}