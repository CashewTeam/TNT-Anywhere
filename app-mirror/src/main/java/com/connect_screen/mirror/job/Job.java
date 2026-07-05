package com.easycast.source.job;

public interface Job {
    void start() throws YieldException;
}
