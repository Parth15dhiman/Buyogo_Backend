package com.factory.buyogobackend.repository.projection;

public interface TopDefectLineProjection {
    String getLineId();
    long getTotalDefects();
    long getEventCount();
}
