package no.nordicsemi.android.mvt_ppg.viewmodels;

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
        if(!file.exists()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("Unable to File " + e);
            }
        }
        try {
            FileWriter writer = new FileWriter(file);

            writer.write("number");
            writer.write(',');
            writer.write("time");
            writer.write(',');
            writer.write("value_int");
            writer.write(',');
            writer.write("value_float");
            writer.write('\n');

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
