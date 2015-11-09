package com.example.alex.sometrial;

import junit.framework.Assert;

import java.util.HashSet;
import java.util.LinkedList;

/**
 * Created by Alex on 11/8/2015.
 */
public class MyLinkedHashSet<T> {
    private HashSet<T> hashSet = new HashSet<T>();
    private LinkedList<T> linkedList = new LinkedList<T>();

    public void clear() {
        hashSet.clear();
        linkedList.clear();
    }

    public void add(T item) {
        if(item == null) {
            throw new IllegalArgumentException("item is null");
        }
        else if(hashSet.contains(item)) {
            throw new IllegalArgumentException("trying to add an item that already exists");
        }
        Assert.assertEquals(hashSet.size(), linkedList.size());

        hashSet.add(item);
        linkedList.add(item);
    }

    public boolean contains(T item) {
        if(item == null) {
            throw new IllegalArgumentException("item is null");
        }
        return hashSet.contains(item);
    }

    public final LinkedList<T> getLinkedList() {
        return linkedList;
    }

    public T getLast() {
        if(linkedList.size() == 0) {
            throw new IllegalStateException("trying to get last itme of an empty list");
        }
        return linkedList.getLast();
    }

    public int size() {
        Assert.assertEquals(hashSet.size(), linkedList.size());
        return linkedList.size();
    }

}
