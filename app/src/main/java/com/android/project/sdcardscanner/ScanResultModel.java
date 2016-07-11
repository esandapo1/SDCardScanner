package com.android.project.sdcardscanner;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ScanResultModel implements Parcelable {

    private long averageFileSize;

    private List<MyFile> myFileList = new ArrayList<>();

    Map<String, Integer> sortedExtensionFrequencyMap = new HashMap<>();

    public ScanResultModel () {}

    public long getAverageFileSize() {
        return averageFileSize;
    }

    public List<MyFile> getMyFileList() {
        return myFileList;
    }

    public Map<String, Integer> getSortedExtensionFrequencyMap() {
        return sortedExtensionFrequencyMap;
    }

    protected ScanResultModel(Parcel in) {
        averageFileSize = in.readLong();
        myFileList = in.createTypedArrayList(MyFile.CREATOR);

        sortedExtensionFrequencyMap = populateSortedFrequentExtensionMap(myFileList);
    }

    public static final Creator<ScanResultModel> CREATOR = new Creator<ScanResultModel>() {
        @Override
        public ScanResultModel createFromParcel(Parcel in) {
            return new ScanResultModel(in);
        }

        @Override
        public ScanResultModel[] newArray(int size) {
            return new ScanResultModel[size];
        }
    };

    /**
     * Add the files to the list and compute required information
     * @param files
     */
    public void addFilesAndCompute(Collection<File> files) {
        if (files.size() == 0) {
            return;
        }
        for (File f : files) {
            MyFile myFile = new MyFile(f);
            myFileList.add(myFile);

        }
        averageFileSize = MyFile.calcAverageFileSize(myFileList);
        sortedExtensionFrequencyMap = populateSortedFrequentExtensionMap(myFileList);

    }

    /**
     * Retrieve and map file extensions and their frequency of occurrence.
     * @param files
     * @return
     */
    private Map<String, Integer> getFileExtensionMap(List<MyFile> files) {

        Map<String, Integer> fileExtensionMap = new HashMap<>();

        for (MyFile file : files) {
            if (fileExtensionMap.containsKey(file.getExt())) {
                int count = fileExtensionMap.get(file.getExt());
                fileExtensionMap.remove(file.getExt());
                count += 1;
                fileExtensionMap.put(file.getExt(), count);
            } else {
                fileExtensionMap.put(file.getExt(), 1);
            }
        }

        return fileExtensionMap;
    }

    /**
     * Sort mapped extension-frequency in descending order.
     * @param files
     * @return
     */
    private Map<String, Integer> populateSortedFrequentExtensionMap(List<MyFile> files) {

        Map<String, Integer> fileExtensionMap = getFileExtensionMap(files);
        List<Map.Entry<String, Integer>> list = new LinkedList<>( fileExtensionMap.entrySet());

        Collections.sort( list, new Comparator<Map.Entry<String, Integer>>()
        {
            @Override
            public int compare( Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2 )
            {
                return (o2.getValue()).compareTo(o1.getValue());
            }
        });

        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : list)
        {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(averageFileSize);
        dest.writeTypedList(myFileList);
    }
}
