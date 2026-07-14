package com.hellblade.poepricer.collector.repository;

public class CursorConflictException extends RuntimeException {

    public CursorConflictException(String streamKey) {
        super("Collector cursor changed concurrently for stream " + streamKey);
    }
}
