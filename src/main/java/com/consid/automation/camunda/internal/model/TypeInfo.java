package com.consid.automation.camunda.internal.model;

/**
 * The type axis of a {@link FieldDescriptor}. Each permitted implementation
 * carries only the constraints meaningful for its type family. The expression
 * builder dispatches on it with an exhaustive switch, so adding a type family is
 * one new permit plus one new switch arm.
 */
public sealed interface TypeInfo
    permits StringTypeInfo, NumberTypeInfo, BooleanTypeInfo,
            ArrayTypeInfo, ObjectTypeInfo, UnknownTypeInfo {
}
