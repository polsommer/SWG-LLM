package script.library;

import script.obj_var;
import script.obj_var_list;

import java.util.Random;

public class list extends script.base_script {

    private static final Random RANDOM = new Random(); // Random instance for pivot selection

    public list() {}

    /**
     * Converts obj_var_list to an array and sorts it using QuickSort.
     * @param ovl The obj_var_list to be sorted.
     * @return Sorted obj_var[] array.
     * @throws InterruptedException
     */
    public static obj_var[] sortToArray(obj_var_list ovl) throws InterruptedException {
        if (ovl == null || ovl.getNumItems() == 0) {
            return new obj_var[0]; // Return empty array if input is null or empty
        }
        obj_var[] listArray = listToArray(ovl);
        return quickSort(0, listArray.length - 1, listArray);
    }

    /**
     * QuickSort implementation for obj_var[].
     * Restores return type to obj_var[] for compatibility with existing scripts.
     * @param left Left index.
     * @param right Right index.
     * @param listArray The array to be sorted.
     * @return Sorted obj_var[] array.
     * @throws InterruptedException
     */
    public static obj_var[] quickSort(int left, int right, obj_var[] listArray) throws InterruptedException {
        if (left >= right) return listArray; // Return if no sorting is needed

        int pivotIndex = left + RANDOM.nextInt(right - left + 1); // Randomized pivot selection
        swap(pivotIndex, right, listArray); // Move pivot to end
        Object pivot = listArray[right].getData();

        int partition = 0;
        boolean validInstance = false;

        if (pivot instanceof Integer) {
            partition = partitionIntArray(left, right, (Integer) pivot, listArray);
            validInstance = true;
        } else if (pivot instanceof Float) {
            partition = partitionFloatArray(left, right, (Float) pivot, listArray);
            validInstance = true;
        } else {
            throw new IllegalArgumentException("Unsupported data type in obj_var_list: " + pivot.getClass().getName());
        }

        if (validInstance) {
            quickSort(left, partition - 1, listArray);
            quickSort(partition + 1, right, listArray);
        }
        
        return listArray; // Ensure compatibility with existing scripts
    }

    /**
     * Partitions the array for QuickSort (Integer version).
     * @param left Left index.
     * @param right Right index.
     * @param pivot Pivot value.
     * @param listArray The array to be partitioned.
     * @return Partition index.
     * @throws InterruptedException
     */
    public static int partitionIntArray(int left, int right, int pivot, obj_var[] listArray) throws InterruptedException {
        int leftPtr = left;
        int rightPtr = right - 1;

        while (true) {
            while (leftPtr < right && listArray[leftPtr].getIntData() < pivot) leftPtr++;
            while (rightPtr > left && listArray[rightPtr].getIntData() > pivot) rightPtr--;

            if (leftPtr >= rightPtr) break;
            swap(leftPtr, rightPtr, listArray);
        }
        swap(leftPtr, right, listArray);
        return leftPtr;
    }

    /**
     * Partitions the array for QuickSort (Float version).
     * @param left Left index.
     * @param right Right index.
     * @param pivot Pivot value.
     * @param listArray The array to be partitioned.
     * @return Partition index.
     * @throws InterruptedException
     */
    public static int partitionFloatArray(int left, int right, float pivot, obj_var[] listArray) throws InterruptedException {
        int leftPtr = left;
        int rightPtr = right - 1;

        while (true) {
            while (leftPtr < right && listArray[leftPtr].getFloatData() < pivot) leftPtr++;
            while (rightPtr > left && listArray[rightPtr].getFloatData() > pivot) rightPtr--;

            if (leftPtr >= rightPtr) break;
            swap(leftPtr, rightPtr, listArray);
        }
        swap(leftPtr, right, listArray);
        return leftPtr;
    }

    /**
     * Swaps two elements in the obj_var array.
     * @param dex1 Index of first element.
     * @param dex2 Index of second element.
     * @param listArray The array containing elements to swap.
     * @throws InterruptedException
     */
    public static void swap(int dex1, int dex2, obj_var[] listArray) throws InterruptedException {
        obj_var temp = listArray[dex1];
        listArray[dex1] = listArray[dex2];
        listArray[dex2] = temp;
    }

    /**
     * Converts obj_var_list to obj_var[] array.
     * @param ovl The obj_var_list to convert.
     * @return Converted obj_var[] array.
     * @throws InterruptedException
     */
    public static obj_var[] listToArray(obj_var_list ovl) throws InterruptedException {
        int cnt = ovl.getNumItems();
        obj_var[] array = new obj_var[cnt];
        for (int i = 0; i < cnt; ++i) {
            array[i] = ovl.getObjVar(i);
        }
        return array;
    }

    /**
     * Converts obj_var_list to an array of obj_var_list[].
     * @param ovl The obj_var_list to convert.
     * @return Converted obj_var_list[] array.
     * @throws InterruptedException
     */
    public static obj_var_list[] listToListArray(obj_var_list ovl) throws InterruptedException {
        int cnt = ovl.getNumItems();
        obj_var_list[] array = new obj_var_list[cnt];
        for (int i = 0; i < cnt; ++i) {
            obj_var ov = ovl.getObjVar(i);
            if (!(ov instanceof obj_var_list)) {
                throw new IllegalArgumentException("Expected obj_var_list but found " + ov.getClass().getName());
            }
            array[i] = (obj_var_list) ov;
        }
        return array;
    }
}

