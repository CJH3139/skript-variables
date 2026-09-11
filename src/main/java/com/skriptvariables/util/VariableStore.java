package com.skriptvariables.util;

public interface VariableStore {

    Object get(String name);

    void set(String name, Object value);
}
