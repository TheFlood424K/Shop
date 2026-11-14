package com.snowgears.shop.util;

import java.util.Objects;
import java.util.UUID;

/**
 * Filter criteria for looking up a player's past shop transactions.
 * All fields are optional; null or default values mean "no constraint".
 */
public class TransactionLookupFilter {

    private final UUID ownerUUID;
    private final long startTime;
    private final long endTime;
    private final String customerName;
    private final ShopActionType actionType;
    private final int limit;

    private TransactionLookupFilter(Builder builder) {
        this.ownerUUID = builder.ownerUUID;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.customerName = builder.customerName;
        this.actionType = builder.actionType;
        this.limit = builder.limit;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public String getCustomerName() {
        return customerName;
    }

    public ShopActionType getActionType() {
        return actionType;
    }

    public int getLimit() {
        return limit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionLookupFilter that = (TransactionLookupFilter) o;
        return startTime == that.startTime && endTime == that.endTime && limit == that.limit
                && Objects.equals(ownerUUID, that.ownerUUID)
                && Objects.equals(customerName, that.customerName)
                && actionType == that.actionType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerUUID, startTime, endTime, customerName, actionType, limit);
    }

    @Override
    public String toString() {
        return "TransactionLookupFilter{" +
                "ownerUUID=" + ownerUUID +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", customerName='" + customerName + '\'' +
                ", actionType=" + actionType +
                ", limit=" + limit +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID ownerUUID;
        private long startTime;
        private long endTime;
        private String customerName;
        private ShopActionType actionType;
        private int limit;

        private Builder() {
        }

        public Builder ownerUUID(UUID ownerUUID) {
            this.ownerUUID = ownerUUID;
            return this;
        }

        public Builder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(long endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder customerName(String customerName) {
            this.customerName = customerName;
            return this;
        }

        public Builder actionType(ShopActionType actionType) {
            this.actionType = actionType;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public TransactionLookupFilter build() {
            return new TransactionLookupFilter(this);
        }
    }
}
