package ph.edu.dlsu.anilink.dsa;

import java.util.List;

public class ScheduleBinarySearch {

    public static int search(List<String> sortedTimes, String targetTime) {

        int left = 0;
        int right = sortedTimes.size() - 1;

        while (left <= right) {

            int middle = (left + right) / 2;

            int result = sortedTimes.get(middle).compareTo(targetTime);

            if (result == 0) {
                return middle;
            } else if (result < 0) {
                left = middle + 1;
            } else {
                right = middle - 1;
            }
        }

        return -1;
    }
}
