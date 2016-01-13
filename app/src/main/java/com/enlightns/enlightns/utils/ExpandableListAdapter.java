package com.enlightns.enlightns.utils;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.enlightns.enlightns.R;
import com.enlightns.enlightns.db.Record;
import com.enlightns.enlightns.updater.NetworkChangeReceiver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpandableListAdapter extends BaseExpandableListAdapter {

    private Context mContext;
    private ListRefresh mListRefreshListener;
    private List<String> mListDataHeader;
    private Map<String, List<Record>> mListDataChild;

    private Map<Integer, boolean[]> mChildCheckStates;

    public ExpandableListAdapter(Context context, List<String> listDataHeader,
                                 Map<String, List<Record>> listChildData, ListRefresh listRefresh) {
        this.mContext = context;
        this.mListDataHeader = listDataHeader;
        this.mListDataChild = listChildData;

        this.mListRefreshListener = listRefresh;

        mChildCheckStates = new HashMap<>();
    }

    @Override
    public Record getChild(int groupPosition, int childPosition) {
        return this.mListDataChild.get(this.mListDataHeader.get(groupPosition))
                .get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public View getChildView(final int groupPosition, final int childPosition,
                             boolean isLastChild, View convertView, ViewGroup parent) {

        final Record record = getChild(groupPosition, childPosition);

        ChildViewHolder childViewHolder;

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) this.mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.list_record_row, null);

            childViewHolder = new ChildViewHolder();

            childViewHolder.name = (TextView) convertView.findViewById(R.id.list_record_name);
            childViewHolder.content = (TextView) convertView.findViewById(R.id.list_record_content);
            childViewHolder.checkBoxUpdate = (CheckBox) convertView.findViewById(R.id.list_record_should_update);

            convertView.setTag(R.layout.list_record_row, childViewHolder);
        } else {
            childViewHolder = (ChildViewHolder) convertView.getTag(R.layout.list_record_row);
        }

        Log.d("GetChildView", "Record: " + record.ensId + ", Group: " + groupPosition + ", Child: " + childPosition);

        childViewHolder.name.setText(record.name);
        childViewHolder.content.setText(record.content);

        childViewHolder.checkBoxUpdate.setOnCheckedChangeListener(null);

        if (mChildCheckStates.containsKey(groupPosition)) {
            boolean getChecked[] = mChildCheckStates.get(groupPosition);
            getChecked[childPosition] = record.updateActive;
            setChecked(childViewHolder.checkBoxUpdate, getChecked[childPosition]);

        } else {
            boolean[] getChecked = new boolean[getChildrenCount(groupPosition)];
            getChecked[childPosition] = record.updateActive;
            mChildCheckStates.put(groupPosition, getChecked);
            setChecked(childViewHolder.checkBoxUpdate, getChecked[childPosition]);
        }

        childViewHolder.checkBoxUpdate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {

                    boolean[] getChecked = mChildCheckStates.get(groupPosition);
                    getChecked[childPosition] = isChecked;
                    mChildCheckStates.put(groupPosition, getChecked);
                    NetworkChangeReceiver.retryRecordUpdateDelayed(
                            mContext, 5, NetworkChangeReceiver.MANUAL_UPDATE_ACTION);

                } else {

                    boolean[] getChecked = mChildCheckStates.get(groupPosition);
                    getChecked[childPosition] = isChecked;
                    mChildCheckStates.put(groupPosition, getChecked);
                }

                record.updateActive = isChecked;
                record.save();

                mListRefreshListener.refreshList();
            }
        });

        return convertView;
    }

    private void setChecked(CheckBox checkBox, boolean checked) {
        ViewGroup parent = (ViewGroup) checkBox.getParent();
        parent.removeView(checkBox);
        checkBox.setChecked(checked);
        parent.addView(checkBox);
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return this.mListDataChild.get(this.mListDataHeader.get(groupPosition)).size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return this.mListDataHeader.get(groupPosition);
    }

    @Override
    public int getGroupCount() {
        return this.mListDataHeader.size();
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded,
                             View convertView, ViewGroup parent) {
        String headerTitle = (String) getGroup(groupPosition);
        GroupViewHolder groupViewHolder;
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) this.mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.list_record_header, null);

            groupViewHolder = new GroupViewHolder();
            groupViewHolder.header = (TextView) convertView.findViewById(R.id.list_record_header_title);
            convertView.setTag(groupViewHolder);
        } else {
            groupViewHolder = (GroupViewHolder) convertView.getTag();
        }

        groupViewHolder.header.setText(headerTitle);

        return convertView;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    class ChildViewHolder {
        TextView name;
        TextView content;
        CheckBox checkBoxUpdate;
    }

    class GroupViewHolder {
        TextView header;
    }
}
