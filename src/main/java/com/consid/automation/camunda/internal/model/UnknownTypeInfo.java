package com.consid.automation.camunda.internal.model;

/**
 * Sentinel for properties whose type the resolver couldn't determine. The
 * expression builder emits only a {@code field=null} check (no type clause).
 */
public record UnknownTypeInfo() implements TypeInfo {

    public static final UnknownTypeInfo INSTANCE = new UnknownTypeInfo();
}
