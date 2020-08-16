package com.example.mbreath.realtimeaccelerometer;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class SaveCSV {

    File file;
    public SaveCSV(File file){
        this.file = file;
    }
    public void save(List<String[]> list){
        Log.i("myTag", "save");
        if(!file.exists()){
            Log.i("myTag", "file not exists");
            try {
                file.createNewFile();
                Log.i("myTag", "create new file");
            } catch (IOException e) {
                Log.i("myTag", "couldn't create new file");
                throw new RuntimeException("Unable to File " + e);
            }
        }
        Log.i("myTag", "file exists");
        try {
            FileWriter writer = new FileWriter(file);
            Log.i("myTag", "create new filewriter");
            for(int i = 0; i < list.size(); i++){
                String[] row = list.get(i);
                for(int j = 0; j < row.length; j++)
                {
                    writer.write(row[j]);
                    if(j != row.length - 1){
                        writer.write(',');
                    }
                    else{
                        writer.write('\n');
                    }
                }
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {

            throw new RuntimeException("Unable to write to File " + e);
        }
    }
}
