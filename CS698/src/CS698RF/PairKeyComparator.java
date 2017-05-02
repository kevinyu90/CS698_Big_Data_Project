package CS698RF;

import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;

public class PairKeyComparator extends WritableComparator {
  protected PairKeyComparator() {
    super(PairKey.class, true);
  }

  @SuppressWarnings("rawtypes")
  @Override
  public int compare(WritableComparable w1, WritableComparable w2) {

    PairKey key1 = (PairKey) w1;
    PairKey key2 = (PairKey) w2;

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