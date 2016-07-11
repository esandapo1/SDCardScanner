package com.android.project.sdcardscanner;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.List;

public class MyFile implements Comparable<MyFile>, Parcelable {

    private String name;
    private String ext;
    private long size;

    public MyFile(File f) {
        name = f.getName();
        size = f.length();
        ext = FilenameUtils.getExtension(f.getPath());
        if (TextUtils.isEmpty(ext)) {
            ext = "--no ext--";
        }
    }

    protected MyFile(Parcel in) {
        name = in.readString();
        ext = in.readString();
        size = in.readLong();
    }

    public static final Creator<MyFile> CREATOR = new Creator<MyFile>() {
        @Override
        public MyFile createFromParcel(Parcel in) {
            return new MyFile(in);
        }

        @Override
        public MyFile[] newArray(int size) {
            return new MyFile[size];
        }
    };

    public String getName() {
        return name;
    }

    public String getExt() {
        return ext;
    }

    public long getSize() {
        return size;
    }

    /**
     * Calculate the sum total of all file sizes.
     * @param myFileList
     * @return
     */
    public static long calcSizesSumTotal(List<MyFile> myFileList) {
        long sum = 0;
        for (MyFile myFile : myFileList) {
            sum += myFile.getSize();
        }
        return sum;
    }

    /**
     * find the average file size.
     * @param myFileList
     * @return
     */
    public static long calcAverageFileSize(List<MyFile> myFileList) {
        return calcSizesSumTotal(myFileList) / myFileList.size();
    }

    @Override
    public int compareTo(@NonNull MyFile another) {
        return (int) (another.getSize() - getSize());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(ext);
        dest.writeLong(size);
    }
}
