package com.dig.event;

import java.io.Serializable;
import java.math.BigInteger;

public class BaseEvent implements Serializable {

    private BigInteger lastesBlockNumber;

    public BigInteger getLastesBlockNumber() {
        return lastesBlockNumber;
    }

    public void setLastesBlockNumber(BigInteger lastesBlockNumber) {
        this.lastesBlockNumber = lastesBlockNumber;
    }
}
