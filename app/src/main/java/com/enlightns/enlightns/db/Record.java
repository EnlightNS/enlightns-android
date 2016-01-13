package com.enlightns.enlightns.db;

import com.orm.SugarRecord;

public class Record extends SugarRecord<Record> {

    public long ensId;
    public String name;
    public String content;
    public boolean updateActive = false;

    public Record() {
    }

    public Record(long id, String hostname, String content, boolean updateActive) {
        this.ensId = id;
        this.name = hostname;
        this.content = content;
        this.updateActive = updateActive;
    }

    public String getName() {
        return this.name;
    }

}
