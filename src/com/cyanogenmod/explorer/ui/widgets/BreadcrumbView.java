/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.explorer.ui.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.cyanogenmod.explorer.R;
import com.cyanogenmod.explorer.model.DiskUsage;
import com.cyanogenmod.explorer.model.MountPoint;
import com.cyanogenmod.explorer.tasks.FilesystemAsyncTask;
import com.cyanogenmod.explorer.util.FileHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A view that holds a navigation breadcrumb pattern.
 */
public class BreadcrumbView extends RelativeLayout implements Breadcrumb, OnClickListener {

    /**
     * @hide
     */
    HorizontalScrollView mScrollView;
    private ViewGroup mBreadcrumbBar;
    /**
     * @hide
     */
    ImageView mFilesystemInfo;
    /**
     * @hide
     */
    ProgressBar mDiskUsageInfo;
    /**
     * @hide
     */
    View mLoading;
    private FilesystemAsyncTask mFilesystemAsyncTask;

    private int mFreeDiskSpaceWarningLevel = 95;

    private List<BreadcrumbListener> mBreadcrumbListeners;

    private String mCurrentPath;

    /**
     * Constructor of <code>BreadcrumbView</code>.
     *
     * @param context The current context
     */
    public BreadcrumbView(Context context) {
        super(context);
        init();
    }

    /**
     * Constructor of <code>BreadcrumbView</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public BreadcrumbView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * Constructor of <code>BreadcrumbView</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyle The default style to apply to this view. If 0, no style
     *        will be applied (beyond what is included in the theme). This may
     *        either be an attribute resource, whose value will be retrieved
     *        from the current theme, or an explicit style resource.
     */
    public BreadcrumbView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    /**
     * Method that initializes the view. This method loads all the necessary
     * information and create an appropriate layout for the view
     */
    private void init() {
        //Initialize the listeners
        this.mBreadcrumbListeners =
              Collections.synchronizedList(new ArrayList<BreadcrumbListener>());

        //Add the view of the breadcrumb
        addView(inflate(getContext(), R.layout.breadcrumb_view, null));

        //Recovery all views
        this.mScrollView = (HorizontalScrollView)findViewById(R.id.breadcrumb_scrollview);
        this.mBreadcrumbBar = (ViewGroup)findViewById(R.id.breadcrumb);
        this.mFilesystemInfo = (ImageView)findViewById(R.id.ab_filesystem_info);
        this.mDiskUsageInfo = (ProgressBar)findViewById(R.id.breadcrumb_diskusage);
        this.mLoading = findViewById(R.id.breadcrumb_loading);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFreeDiskSpaceWarningLevel(int percentage) {
        this.mFreeDiskSpaceWarningLevel = percentage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addBreadcrumbListener(BreadcrumbListener listener) {
        this.mBreadcrumbListeners.add(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeBreadcrumbListener(BreadcrumbListener listener) {
        this.mBreadcrumbListeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startLoading() {
        //Show/Hide views
        this.post(new Runnable() {
            @Override
            public void run() {
                BreadcrumbView.this.mFilesystemInfo.setVisibility(View.INVISIBLE);
                BreadcrumbView.this.mDiskUsageInfo.setVisibility(View.INVISIBLE);
                BreadcrumbView.this.mLoading.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endLoading() {
        //Show/Hide views
        this.post(new Runnable() {
            @Override
            public void run() {
                BreadcrumbView.this.mLoading.setVisibility(View.INVISIBLE);
                BreadcrumbView.this.mFilesystemInfo.setVisibility(View.VISIBLE);
                BreadcrumbView.this.mDiskUsageInfo.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changeBreadcrumbPath(final String newPath) {
        //Sets the current path
        this.mCurrentPath = newPath;

        //Update the mount point information
        updateMountPointInfo();

        //Remove all views
        this.mBreadcrumbBar.removeAllViews();

        //The first is always the root
        this.mBreadcrumbBar.addView(createBreadcrumbItem(new File(FileHelper.ROOT_DIRECTORY)));

        //Add the rest of the path
        String[] dirs = newPath.split(File.separator);
        for (int i = 1; i < dirs.length; i++) {
            this.mBreadcrumbBar.addView(createItemDivider());
            this.mBreadcrumbBar.addView(createBreadcrumbItem(createFile(dirs, i)));
        }

        //Set scrollbar at the end
        this.mScrollView.post(new Runnable() {
            @Override
            public void run() {
                BreadcrumbView.this.mScrollView.fullScroll(View.FOCUS_RIGHT);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void updateMountPointInfo() {
      //Cancel the current execution (if any) and launch again
        if (this.mFilesystemAsyncTask != null && this.mFilesystemAsyncTask.isRunning()) {
           this.mFilesystemAsyncTask.cancel(true);
        }
        final ImageView filesystemInfo = (ImageView)findViewById(R.id.ab_filesystem_info);
        final ProgressBar diskUsageInfo = (ProgressBar)findViewById(R.id.breadcrumb_diskusage);
        this.mFilesystemAsyncTask =
                new FilesystemAsyncTask(
                        getContext(), filesystemInfo,
                        diskUsageInfo, this.mFreeDiskSpaceWarningLevel);
        this.mFilesystemAsyncTask.execute(this.mCurrentPath);
    }

    /**
     * Method that creates a new path divider.
     *
     * @return View The path divider
     */
    private View createItemDivider() {
        LayoutInflater inflater =
                (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        return inflater.inflate(R.layout.breadcrumb_item_divider, this.mBreadcrumbBar, false);
    }

    /**
     * Method that creates a new split path.
     *
     * @param dir The path
     * @return BreadcrumbItem The view create
     */
    private BreadcrumbItem createBreadcrumbItem(File dir) {
        LayoutInflater inflater =
                (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        BreadcrumbItem item =
                (BreadcrumbItem)inflater.inflate(
                        R.layout.breadcrumb_item, this.mBreadcrumbBar, false);
        item.setText(dir.getName().length() != 0 ? dir.getName() : dir.getPath());
        item.setItemPath(dir.getPath());
        item.setOnClickListener(this);
        return item;
    }

    /**
     * Method that creates the a new file reference for a partial
     * breadcrumb item.
     *
     * @param dirs The split strings directory
     * @param pos The position up to which to create
     * @return File The file reference
     */
    @SuppressWarnings("static-method")
    private File createFile(String[] dirs, int pos) {
        File parent = new File(FileHelper.ROOT_DIRECTORY);
        for (int i = 1; i < pos; i++) {
            parent = new File(parent, dirs[i]);
        }
        return new File(parent, dirs[pos]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClick(View v) {
        BreadcrumbItem item = (BreadcrumbItem)v;
        for (int i = 0; i < this.mBreadcrumbListeners.size(); i++) {
            this.mBreadcrumbListeners.get(i).onBreadcrumbItemClick(item);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MountPoint getMountPointInfo() {
        if (this.mFilesystemInfo != null) {
            return (MountPoint)this.mFilesystemInfo.getTag();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DiskUsage getDiskUsageInfo() {
        if (this.mDiskUsageInfo != null) {
            return (DiskUsage)this.mDiskUsageInfo.getTag();
        }
        return null;
    }

}
