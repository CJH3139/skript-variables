package com.skriptvariables.util;

import ch.njol.skript.variables.Variables;

public final class SkriptVariableStore implements VariableStore {

    public static final SkriptVariableStore INSTANCE = new SkriptVariableStore();

    private SkriptVariableStore() {
    }

    @Override
    public Object get(String name) {
        return Variables.getVariable(name, null, false);
    }

    @Override
    public void set(String name, Object value) {
        Variables.setVariable(name, value, null, false);
    }
}
