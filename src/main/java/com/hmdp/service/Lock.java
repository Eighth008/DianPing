package com.hmdp.service;

public interface Lock {
    public abstract boolean lock(Long time);
    public abstract void unlock();
}
