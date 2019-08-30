package com.vidyo.vidyoconnector.event;

public class BusBase<T, Call extends CallBase> {

    private T[] value;
    private Call call;

    public BusBase(Call call) {
        this(call, null);
    }

    public BusBase(Call call, T[] value) {
        this.call = call;
        this.value = value;
    }

    public T getValue() {
        return value == null || value.length == 0 ? null : value[0];
    }

    public T[] getValues() {
        return value;
    }

    public Call getCall() {
        return call;
    }

    public boolean hasValues() {
        return getValue() != null;
    }

    @Override
    public String toString() {
        return "BusBase{" + "call=" + call + '}';
    }
}