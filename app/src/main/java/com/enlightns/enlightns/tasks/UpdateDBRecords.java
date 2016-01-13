package com.enlightns.enlightns.tasks;

import android.util.Log;

import com.enlightns.enlightns.api.EnlightnsAPI;
import com.enlightns.enlightns.db.Record;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class UpdateDBRecords {

    private static final String TAG = UpdateDBRecords.class.getName();

    private UpdateDBRecords() {
    }

    public static void updateRecords(List<EnlightnsAPI.ApiRecord> apiRecords) {

        List<Long> apiIds = new ArrayList<>();

        for (EnlightnsAPI.ApiRecord apiRecord : apiRecords) {
            List<Record> dbRecords = Record.find(Record.class, "ens_id = ?", apiRecord.id + "");

            if (!dbRecords.isEmpty()) {
                // Update
                Record dbRecord = dbRecords.get(0);
                dbRecord.name = apiRecord.name;
                dbRecord.content = apiRecord.content;
                dbRecord.save();
                Log.d(TAG, "Updated Record ID" + dbRecord.ensId + ", NAME: " + dbRecord.name + ", CONTENT: " + dbRecord.content);

            } else {
                // Create
                Record newDbRecord = new Record(apiRecord.id, apiRecord.name, apiRecord.content, false);
                newDbRecord.save();
                Log.d(TAG, "Saved New Record ID" + newDbRecord.ensId + ", NAME: " + newDbRecord.name + ", CONTENT: " + newDbRecord.content);

            }
            apiIds.add(apiRecord.id);
        }

        Iterator<Record> allDbRecords = Record.findAll(Record.class);
        while (allDbRecords.hasNext()) {
            Record dbRecord = allDbRecords.next();
            if (!apiIds.contains(dbRecord.ensId)) {
                Log.d(TAG, "Deleting record ID" + dbRecord.ensId);
                dbRecord.delete();
            }
        }

        Iterator<Record> debugDBRecords = Record.findAll(Record.class);
        while (debugDBRecords.hasNext()) {
            Record dbRecord = debugDBRecords.next();
            Log.d(TAG, "Record ID" + dbRecord.ensId + ", NAME: " + dbRecord.name + ", CONTENT: " + dbRecord.content);
        }
    }
}
