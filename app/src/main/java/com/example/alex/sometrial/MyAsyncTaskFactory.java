package com.example.alex.sometrial;

import android.os.AsyncTask;

/**
 * Created by Alex on 11/8/2015.
 */
public class MyAsyncTaskFactory<T extends AsyncTask> {
    private Class<T> clazz;

    public MyAsyncTaskFactory(Class<T> clazz) {
        if(clazz == null) {
            throw new IllegalArgumentException("passed in a null class");
        }
        this.clazz = clazz;
    }

    public T newAsyncTask() throws IllegalAccessException, InstantiationException {
        return clazz.newInstance();
    }
}
